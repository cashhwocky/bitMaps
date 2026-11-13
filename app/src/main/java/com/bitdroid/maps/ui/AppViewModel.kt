package com.bitdroid.maps.ui

import android.app.Application
import android.location.Location
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitdroid.maps.data.Census
import com.bitdroid.maps.data.Nominatim
import com.bitdroid.maps.data.Geo
import com.bitdroid.maps.data.LatLon
import com.bitdroid.maps.data.Mode
import com.bitdroid.maps.data.Photon
import com.bitdroid.maps.data.Place
import com.bitdroid.maps.data.Recents
import com.bitdroid.maps.data.Route
import com.bitdroid.maps.data.Settings
import com.bitdroid.maps.data.Updater
import com.bitdroid.maps.data.Valhalla
import com.bitdroid.maps.nav.LocationTracker
import com.bitdroid.maps.nav.Music
import com.bitdroid.maps.nav.Navigator
import com.bitdroid.maps.nav.Progress
import com.bitdroid.maps.nav.Voice
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder

enum class Screen { SETUP, EXPLORE, SEARCH, PLACE, ROUTE, NAV, SETTINGS, VOICE }

data class Category(val label: String, val query: String, val tag: String)

val categories = listOf(
    Category("Coffee", "cafe", "amenity:cafe"),
    Category("Food", "restaurant", "amenity:restaurant"),
    Category("Gas", "fuel", "amenity:fuel"),
    Category("Parking", "parking", "amenity:parking"),
    Category("Groceries", "supermarket", "shop:supermarket"),
    Category("Charging", "charging_station", "amenity:charging_station"),
)

private val usStates = mapOf(
    "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA", "Colorado" to "CO",
    "Connecticut" to "CT", "Delaware" to "DE", "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID",
    "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA",
    "Maine" to "ME", "Maryland" to "MD", "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN",
    "Mississippi" to "MS", "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV",
    "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY", "North Carolina" to "NC",
    "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK", "Oregon" to "OR", "Pennsylvania" to "PA",
    "Rhode Island" to "RI", "South Carolina" to "SC", "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX",
    "Utah" to "UT", "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA", "West Virginia" to "WV",
    "Wisconsin" to "WI", "Wyoming" to "WY",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    val tracker = LocationTracker(app)
    val music = Music(app)
    private val voice = Voice(app, settings)
    private val recentsStore = Recents(app)

    var screen by mutableStateOf(if (settings.setupDone) Screen.EXPLORE else Screen.SETUP); private set
    /** Where Back from Settings goes. */
    private var beforeSettings = Screen.EXPLORE

    var location by mutableStateOf<Location?>(null); private set
    var recents by mutableStateOf(recentsStore.load()); private set

    // Bloom tiles on Explore
    var home by mutableStateOf(recentsStore.home()); private set
    var homeMinutes by mutableStateOf<Int?>(null); private set
    var hereName by mutableStateOf<String?>(null); private set
    private var hereNameAt: LatLon? = null
    private var homeEtaAt = 0L

    /** Where the map tile sits on Explore (the map view is clipped to it). */
    var mapTile by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    fun goHome() {
        val h = home ?: return
        place = h
        screen = Screen.ROUTE
        computeRoutes()
    }

    fun saveHome(p: Place?) {
        recentsStore.setHome(p)
        home = p
        homeMinutes = null
        homeEtaAt = 0L
        refreshTiles()
    }

    fun isHome(p: Place) = home?.let { Geo.distance(it.pos, p.pos) < 25 } == true

    /** Town name for the map chip and drive time home for the Home tile (cheap, cached). */
    private fun refreshTiles() {
        val me = here() ?: return
        if (hereNameAt == null || Geo.distance(hereNameAt!!, me) > 1500) {
            hereNameAt = me
            viewModelScope.launch {
                runCatching { Photon.reverse(me.lat, me.lon) }.getOrNull()?.let { p ->
                    val parts = p.detail.split(", ").filter { it.isNotBlank() }
                    hereName = when {
                        parts.size >= 2 -> parts.takeLast(2).joinToString(", ") { stateShort(it) }
                        parts.size == 1 -> parts[0]
                        else -> p.name
                    }
                }
            }
        }
        val h = home ?: return
        val now = System.currentTimeMillis()
        if (now - homeEtaAt < 5 * 60_000) return
        homeEtaAt = now
        if (Geo.distance(me, h.pos) < 60) { homeMinutes = 0; return }
        viewModelScope.launch {
            runCatching { Valhalla.route(me, h.pos, Mode.DRIVE, settings.avoid).first() }.getOrNull()?.let {
                homeMinutes = Math.round(it.durationS / 60.0).toInt().coerceAtLeast(1)
            }
        }
    }

    private fun stateShort(s: String): String = usStates[s] ?: s

    // Search
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<Place>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    private var searchJob: Job? = null

    // Place + route
    var place by mutableStateOf<Place?>(null); private set
    var mode by mutableStateOf(Mode.DRIVE); private set
    var routes by mutableStateOf<List<Route>>(emptyList()); private set
    var selected by mutableStateOf(0); private set
    var routing by mutableStateOf(false); private set
    var routeError by mutableStateOf<String?>(null); private set
    private var routeJob: Job? = null

    // Navigation
    var progress by mutableStateOf<Progress?>(null); private set
    var following by mutableStateOf(true)
    var muted by mutableStateOf(!settings.voice); private set
    private var navigator: Navigator? = null
    private var rerouting = false
    private var lastReroute = 0L

    /** Bumped when the camera should jump (place opened, route found, overview…). */
    var cameraTick by mutableStateOf(0); private set

    // In-app updates
    var update by mutableStateOf<com.bitdroid.maps.data.Update?>(null); private set
    var showUpdate by mutableStateOf(false)
    var updateProgress by mutableStateOf<Float?>(null); private set
    var updateMessage by mutableStateOf<String?>(null); private set
    var checkingUpdate by mutableStateOf(false); private set

    init {
        viewModelScope.launch { tracker.location.collect { it?.let(::onLocation) } }
        Updater.schedule(app, settings.autoUpdates)
        if (settings.autoUpdates) checkForUpdate(manual = false)
    }

    fun finishSetup() {
        settings.setupDone = true
        screen = Screen.EXPLORE
        onPermissionsChanged()
        cameraTick++
    }

    fun setAutoUpdates(on: Boolean) {
        settings.autoUpdates = on
        Updater.schedule(getApplication(), on)
    }

    fun checkForUpdate(manual: Boolean) {
        viewModelScope.launch {
            checkingUpdate = true
            updateMessage = null
            try {
                update = Updater.check(getApplication())
                if (manual) {
                    if (update != null) showUpdate = true else updateMessage = "✅ You're on the latest version."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                if (manual) updateMessage = "Couldn't check for updates. Check your connection."
            } finally {
                checkingUpdate = false
            }
        }
    }

    /** Downloads the new APK inside the app and hands it to Android's installer. */
    fun installUpdate() {
        val u = update ?: return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            runCatching { app.startActivity(Updater.installPermissionIntent(app)) }
            updateMessage = "Allow bitMaps to install updates, then tap Update again."
            return
        }
        if (updateProgress != null) return
        viewModelScope.launch {
            updateMessage = null
            updateProgress = 0f
            try {
                val apk = Updater.download(app, u) { p -> updateProgress = p }
                updateMessage = "Installing…"
                Updater.install(app, apk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                updateMessage = "Download failed. Try again."
            } finally {
                updateProgress = null
            }
        }
    }

    // ---------- voice ----------

    val sampleLine get() = "In ${com.bitdroid.maps.data.Units.spoken(152.0)}, turn right onto Main Street."

    fun openVoice() { screen = Screen.VOICE }

    fun testVoice() = voice.preview(sampleLine)

    fun onPermissionsChanged() {
        tracker.start()
        if (settings.musicInNav || settings.musicOnMap) music.start()
    }

    fun refreshMusicAccess() {
        if (settings.musicInNav || settings.musicOnMap) music.start() else music.stop()
    }

    // ---------- search ----------

    fun openSearch() {
        screen = Screen.SEARCH
        refreshTiles()
    }

    /** Result of the mic button on the search bar. */
    fun voiceQuery(text: String) {
        screen = Screen.SEARCH
        updateQuery(text)
    }

    fun updateQuery(q: String) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) { results = emptyList(); searching = false; searchError = null; return }
        searchJob = viewModelScope.launch {
            delay(300)
            runSearch {
                if (!Census.looksLikeAddress(q)) return@runSearch Photon.search(q, here())
                // Addresses: ask OpenStreetMap, the US Census and Nominatim together; exact house matches go first.
                coroutineScope {
                    val osm = async { runCatching { Photon.search(q, here()) }.getOrDefault(emptyList()) }
                    val census = async { delay(250); Census.search(q, hereName) }
                    val nom = async { delay(400); runCatching { Nominatim.address(q, here()) }.getOrDefault(emptyList()) }
                    val exact = ArrayList<Place>()
                    for (p in census.await() + nom.await().filter { it.kind == "house" }) {
                        if (exact.none { Geo.distance(it.pos, p.pos) < 40 }) exact.add(p)
                    }
                    exact + osm.await().filter { p -> exact.none { Geo.distance(it.pos, p.pos) < 30 } }
                }
            }
        }
    }

    fun searchCategory(c: Category) {
        screen = Screen.SEARCH
        query = c.label
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch { Photon.search(c.query, here(), osmTag = c.tag, nearbyOnly = here() != null) } }
    }

    private suspend fun runSearch(keepOrder: Boolean = false, block: suspend () -> List<Place>) {
        searching = true
        searchError = null
        try {
            val found = block()
            val me = here()
            // Exact address matches stay on top; everything else is sorted by distance.
            fun exactMatch(p: Place) = p.id.startsWith("census:") || (p.id.startsWith("nom:") && p.kind == "house")
            val exact = found.filter(::exactMatch)
            val rest = found.filterNot(::exactMatch)
            results = exact + if (me != null && !keepOrder) rest.sortedBy { Geo.distance(me, it.pos) } else rest
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            searchError = "Couldn't search right now. Check your connection."
            results = emptyList()
        } finally {
            searching = false
        }
    }

    // ---------- places ----------

    fun open(p: Place) {
        place = p
        recents = recentsStore.add(p)
        routes = emptyList()
        screen = Screen.PLACE
        cameraTick++
    }

    fun dropPin(lat: Double, lon: Double) {
        if (screen == Screen.NAV) return
        val pin = Place("pin:$lat,$lon", "Dropped pin", String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon), "Pin", "pin", lat, lon)
        place = pin
        routes = emptyList()
        screen = Screen.PLACE
        cameraTick++
        viewModelScope.launch {
            val named = runCatching { Photon.reverse(lat, lon) }.getOrNull()
            if (named != null && place?.id == pin.id) {
                place = pin.copy(name = named.name, detail = named.detail.ifBlank { pin.detail }, category = named.category, kind = named.kind)
            }
        }
    }

    // ---------- routing ----------

    fun directions() {
        if (place == null) return
        screen = Screen.ROUTE
        computeRoutes()
    }

    fun setTravelMode(m: Mode) {
        if (m == mode) return
        mode = m
        computeRoutes()
    }

    fun selectRoute(i: Int) { selected = i }

    fun computeRoutes() {
        val dest = place ?: return
        val from = here()
        routeJob?.cancel()
        if (from == null) {
            routeError = "Waiting for your location…"
            routes = emptyList()
            return
        }
        routeJob = viewModelScope.launch {
            routing = true
            routeError = null
            try {
                routes = Valhalla.route(from, dest.pos, mode, settings.avoid)
                selected = 0
                cameraTick++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                routes = emptyList()
                routeError = e.message?.takeIf { it.isNotBlank() && !it.startsWith("HTTP") } ?: "Couldn't find a route. Check your connection."
            } finally {
                routing = false
            }
        }
    }

    // ---------- navigation ----------

    fun start() {
        val r = routes.getOrNull(selected) ?: return
        navigator = Navigator(r)
        routes = listOf(r)
        selected = 0
        progress = null
        following = true
        muted = !settings.voice
        voice.muted = muted
        screen = Screen.NAV
        cameraTick++
        location?.let(::onLocation)
    }

    fun end() {
        navigator = null
        progress = null
        voice.stop()
        routes = emptyList()
        screen = if (place != null) Screen.PLACE else Screen.EXPLORE
        cameraTick++
    }

    fun toggleMute() {
        muted = !muted
        voice.muted = muted
        if (muted) voice.stop()
    }

    fun overview() { following = false; cameraTick++ }
    fun recenter() { following = true; cameraTick++ }

    private fun onLocation(loc: Location) {
        val first = location == null
        location = loc
        if (first && screen == Screen.EXPLORE) cameraTick++
        if (screen == Screen.EXPLORE) refreshTiles()
        if (screen == Screen.ROUTE && routes.isEmpty() && !routing && routeError != null) computeRoutes()
        val nav = navigator ?: return
        val step = nav.update(loc)
        progress = step.progress
        step.speech?.let { voice.say(it) }
        if (step.progress.arrived) return
        val now = System.currentTimeMillis()
        if (step.offRoute && !rerouting && now - lastReroute > 10_000) reroute(loc)
    }

    private fun reroute(loc: Location) {
        val dest = place ?: return
        rerouting = true
        lastReroute = System.currentTimeMillis()
        voice.say("Rerouting.")
        viewModelScope.launch {
            try {
                val heading = if (loc.hasBearing() && loc.speed > 2f) loc.bearing else null
                val r = Valhalla.route(LatLon(loc.latitude, loc.longitude), dest.pos, mode, settings.avoid, heading).first()
                if (screen == Screen.NAV) {
                    navigator = Navigator(r)
                    routes = listOf(r)
                    selected = 0
                }
            } catch (_: Exception) {
                // Keep the old route; we'll try again on the next off-route fix.
            } finally {
                rerouting = false
            }
        }
    }

    // ---------- settings + back ----------

    fun openSettings() {
        if (screen != Screen.SETTINGS) beforeSettings = screen
        screen = Screen.SETTINGS
    }

    fun clearRecents() {
        getApplication<Application>().getSharedPreferences("recents", 0).edit().clear().apply()
        recents = emptyList()
    }

    /** Returns false when there's nothing to go back to (the app should close). */
    fun back(): Boolean {
        when (screen) {
            Screen.EXPLORE, Screen.SETUP -> return false
            Screen.SEARCH -> { screen = Screen.EXPLORE; query = ""; results = emptyList() }
            Screen.PLACE -> { place = null; screen = Screen.EXPLORE }
            Screen.ROUTE -> { routeJob?.cancel(); routing = false; routes = emptyList(); routeError = null; screen = Screen.PLACE; cameraTick++ }
            Screen.NAV -> end()
            Screen.VOICE -> screen = Screen.SETTINGS
            Screen.SETTINGS -> {
                screen = beforeSettings
                if (screen == Screen.ROUTE) computeRoutes()
            }
        }
        return true
    }

    // ---------- geo: links ----------

    fun handleGeo(uri: Uri?) {
        if (uri == null || uri.scheme != "geo") return
        val ssp = uri.schemeSpecificPart ?: return
        val coords = ssp.substringBefore('?').split(',')
        val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val lon = coords.getOrNull(1)?.substringBefore(';')?.toDoubleOrNull() ?: 0.0
        val q = ssp.substringAfter("q=", "").substringBefore('&').let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        val qCoords = Regex("""^\s*(-?\d+(\.\d+)?)\s*,\s*(-?\d+(\.\d+)?)\s*(\((.*)\))?""").find(q)
        when {
            qCoords != null -> {
                val la = qCoords.groupValues[1].toDouble()
                val lo = qCoords.groupValues[3].toDouble()
                val label = qCoords.groupValues[6]
                if (label.isNotBlank()) open(Place("geo:$la,$lo", label, String.format(java.util.Locale.US, "%.5f, %.5f", la, lo), "Place", "pin", la, lo))
                else dropPin(la, lo)
            }
            q.isNotBlank() -> { screen = Screen.SEARCH; updateQuery(q) }
            lat != 0.0 || lon != 0.0 -> dropPin(lat, lon)
        }
    }

    fun here(): LatLon? = location?.let { LatLon(it.latitude, it.longitude) }

    override fun onCleared() {
        tracker.stop()
        music.stop()
        voice.shutdown()
    }
}
