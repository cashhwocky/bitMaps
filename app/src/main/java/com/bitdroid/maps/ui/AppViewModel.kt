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
import com.bitdroid.maps.data.Search
import com.bitdroid.maps.data.TomTomSearch
import com.bitdroid.maps.data.Place
import com.bitdroid.maps.data.Recents
import com.bitdroid.maps.data.Route
import com.bitdroid.maps.data.Settings
import com.bitdroid.maps.data.Updater
import com.bitdroid.maps.data.Router
import com.bitdroid.maps.data.Valhalla
import com.bitdroid.maps.data.Units
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

    // ---------- tabs, Work, saved places, stats ----------

    /** Bottom bar on Explore: 0 Explore, 1 Saved, 2 Drive, 3 You. */
    var tab by mutableStateOf(0); private set
    var work by mutableStateOf(recentsStore.work()); private set
    var workMinutes by mutableStateOf<Int?>(null); private set
    private var workEtaAt = 0L
    var saved by mutableStateOf(recentsStore.saved()); private set
    var stats by mutableStateOf(recentsStore.stats()); private set
    /** Map layers sheet. */
    var showLayers by mutableStateOf(false)

    fun selectTab(i: Int) {
        if (i == tab) return
        if (tab == 2) stopDrive()
        tab = i
        if (i == 2) startDrive()
    }

    fun saveWork(p: Place?) {
        recentsStore.setWork(p)
        work = p
        workMinutes = null
        workEtaAt = 0L
        refreshTiles()
    }

    fun isWork(p: Place) = work?.let { Geo.distance(it.pos, p.pos) < 25 } == true
    fun isSaved(p: Place) = saved.any { it.id == p.id || Geo.distance(it.pos, p.pos) < 15 }

    fun toggleSaved(p: Place) {
        saved = if (isSaved(p)) saved.filterNot { it.id == p.id || Geo.distance(it.pos, p.pos) < 15 } else listOf(p) + saved
        recentsStore.setSaved(saved)
    }

    private fun leaveDrive() { if (tab == 2) { stopDrive(); tab = 0 } }

    fun goTo(p: Place) {
        leaveDrive()
        place = p
        screen = Screen.ROUTE
        computeRoutes()
    }

    fun goWork() { work?.let(::goTo) }

    /** "Good morning" etc. — no name, just the time of day. */
    fun greeting(): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (h) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night drive?"
        }
    }

    fun setLayer(satellite: Boolean? = null, traffic: Boolean? = null, buildings: Boolean? = null) {
        satellite?.let { settings.satellite = it }
        traffic?.let { settings.trafficLayer = it }
        buildings?.let { settings.buildings3d = it }
    }

    // ---------- navigation extras ----------

    /** When you add a stop, the trip goes there first and then continues to this place. */
    var finalStop by mutableStateOf<Place?>(null); private set
    var alongResults by mutableStateOf<List<Place>>(emptyList()); private set
    var alongLoading by mutableStateOf(false); private set
    var alongLabel by mutableStateOf<String?>(null); private set
    private var tripStartM = 0.0
    private var tripStartAt = 0L

    /** Speed limit where you are, in the app's units (mph or km/h), or null when unknown. */
    fun speedLimit(): Int? {
        val r = navigator?.route ?: return null
        val kmh = r.limitAt(navigator?.segmentIndex ?: return null) ?: return null
        return if (Units.imperial) (Math.round(kmh / 1.609344 / 5.0) * 5).toInt() else kmh
    }

    /** Finds gas / coffee / food a little way ahead on your route, closest to the route first. */
    fun searchAlong(c: Category) {
        val nav = navigator ?: return
        val r = nav.route
        val start = nav.segmentIndex
        // Look around a point ~8 km ahead (or the destination if it's closer).
        val aheadM = (r.cumulative.getOrElse(start) { 0.0 } + 8000).coerceAtMost(r.length)
        val idx = r.cumulative.indexOfFirst { it >= aheadM }.let { if (it < 0) r.points.lastIndex else it }
        val center = r.points[idx]
        alongLabel = c.label
        alongLoading = true
        alongResults = emptyList()
        viewModelScope.launch {
            val found = runCatching {
                val intent = Search.intent(c.label)
                if (intent != null) Search.nearby(intent, center, searchKey(), c.query) else Photon.search(c.query, center, osmTag = c.tag, nearbyOnly = true)
            }.getOrDefault(emptyList())
            val ahead = r.points.subList(start.coerceIn(0, r.points.lastIndex), r.points.size).filterIndexed { i, _ -> i % 4 == 0 }
            fun detour(p: Place) = ahead.minOfOrNull { Geo.distance(it, p.pos) } ?: Double.MAX_VALUE
            alongResults = found.map { it to detour(it) }.filter { it.second < 3000 }.sortedBy { it.second }.take(8).map { it.first }
            alongLoading = false
        }
    }

    fun closeAlong() { alongLabel = null; alongResults = emptyList() }

    /** Go to [stop] first, then carry on to where you were going. */
    fun addStop(stop: Place) {
        val dest = finalStop ?: place ?: return
        finalStop = dest
        closeAlong()
        place = stop
        val from = here() ?: return
        viewModelScope.launch {
            runCatching { Router.route(from, stop.pos, mode, settings.avoid, movingHeading(), trafficKey()).first() }.getOrNull()?.let { r ->
                navigator = Navigator(r)
                routes = listOf(r)
                selected = 0
                voice.say("Adding a stop at ${stop.name}.")
                cameraTick++
            }
        }
    }

    /** Text you can send someone: where you're going and when you'll get there. */
    fun etaShareText(): String? {
        val p = progress ?: return null
        val dest = finalStop ?: place ?: return null
        val arrive = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(System.currentTimeMillis() + (p.remainingS * 1000).toLong()))
        val link = "https://www.openstreetmap.org/?mlat=${dest.lat}&mlon=${dest.lon}#map=16/${dest.lat}/${dest.lon}"
        return "I'm on my way to ${dest.name} — I'll be there around $arrive (${Units.duration(p.remainingS)} away).\n$link"
    }

    /** Drive tab: free driving with no destination — tilted camera that follows you, live street and speed. */
    var driveMode by mutableStateOf(false); private set
    var driveStreet by mutableStateOf<String?>(null); private set
    private var driveStreetAt: LatLon? = null

    fun startDrive() {
        driveMode = true
        following = true
        driveStreetAt = null
        here()?.let(::updateDriveStreet)
        cameraTick++
    }

    fun stopDrive() {
        if (!driveMode) return
        driveMode = false
        cameraTick++
    }

    private fun updateDriveStreet(me: LatLon) {
        if (driveStreetAt != null && Geo.distance(driveStreetAt!!, me) < 60) return
        driveStreetAt = me
        viewModelScope.launch {
            runCatching { Photon.reverse(me.lat, me.lon) }.getOrNull()?.let { p ->
                // "123 Ethan's Way" → "Ethan's Way"
                driveStreet = p.name.replace(Regex("^\\d+[A-Za-z]?\\s+"), "")
            }
        }
    }
    private var hereNameAt: LatLon? = null
    private var homeEtaAt = 0L

    /** Where the map tile sits on Explore (the map view is clipped to it). */
    var mapTile by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    fun goHome() {
        val h = home ?: return
        leaveDrive()
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
        work?.let { w ->
            val t = System.currentTimeMillis()
            if (t - workEtaAt > 5 * 60_000) {
                workEtaAt = t
                if (Geo.distance(me, w.pos) < 60) workMinutes = 0
                else viewModelScope.launch {
                    runCatching { Router.route(me, w.pos, Mode.DRIVE, settings.avoid, null, trafficKey()).first() }.getOrNull()?.let {
                        workMinutes = Math.round(it.durationS / 60.0).toInt().coerceAtLeast(1)
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
            runCatching { Router.route(me, h.pos, Mode.DRIVE, settings.avoid, null, trafficKey()).first() }.getOrNull()?.let {
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
    /** During navigation: your position snapped onto the route, for drawing the arrow. */
    var navLocation by mutableStateOf<Location?>(null); private set
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
            val me = here()
            val key = searchKey()
            Search.intent(q)?.let { intent ->
                // "gas", "coffee near me"…: every one around you, closest first.
                if (me != null) { runSearch { Search.nearby(intent, me, key, q, ::showPartial) }; return@launch }
            }
            if (!Census.looksLikeAddress(q)) {
                runSearch(keepOrder = true) { Search.text(q, me, key, ::showPartial) }
                return@launch
            }
            runSearch {
                // Addresses: ask OpenStreetMap, the US Census and Nominatim together; exact house matches go first.
                coroutineScope {
                    val osm = async { runCatching { Photon.search(q, here()) }.getOrDefault(emptyList()) }
                    val tt = async { key?.let { k -> runCatching { TomTomSearch.search(q, here(), k, limit = 10, radiusM = null) }.getOrNull() } ?: emptyList() }
                    val census = async { delay(250); Census.search(q, hereName) }
                    val nom = async { delay(400); runCatching { Nominatim.address(q, here()) }.getOrDefault(emptyList()) }
                    val exact = ArrayList<Place>()
                    for (p in tt.await().filter { it.kind == "house" } + census.await() + nom.await().filter { it.kind == "house" }) {
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
        searchJob = viewModelScope.launch {
            val me = here()
            val intent = Search.intent(c.label)
            runSearch {
                if (me != null && intent != null) Search.nearby(intent, me, searchKey(), c.query, ::showPartial)
                else Photon.search(c.query, me, osmTag = c.tag, nearbyOnly = me != null)
            }
        }
    }

    /** First results are on screen: stop the loading spinner while the rest fills in quietly. */
    private fun showPartial(list: List<Place>) {
        if (list.isEmpty()) return
        results = list
        searching = false
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
                routes = Router.route(from, dest.pos, mode, settings.avoid, movingHeading(), trafficKey())
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
        tripStartAt = System.currentTimeMillis()
        tripStartM = 0.0
        muted = !settings.voice
        voice.muted = muted
        screen = Screen.NAV
        cameraTick++
        location?.let(::onLocation)
    }

    fun end() {
        // Count the trip (distance actually covered) for the You tab.
        navigator?.let { nav ->
            val done = (nav.route.length - (progress?.remainingM ?: nav.route.length)).coerceAtLeast(0.0)
            if (done > 200) {
                recentsStore.addTrip(done, (System.currentTimeMillis() - tripStartAt) / 1000.0)
                stats = recentsStore.stats()
            }
        }
        finalStop = null
        closeAlong()
        navigator = null
        navLocation = null
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
        if (driveMode && screen == Screen.EXPLORE) updateDriveStreet(LatLon(loc.latitude, loc.longitude))
        if (first && screen == Screen.EXPLORE) cameraTick++
        if (screen == Screen.EXPLORE) refreshTiles()
        if (screen == Screen.ROUTE && routes.isEmpty() && !routing && routeError != null) computeRoutes()
        val nav = navigator ?: return
        val step = nav.update(loc)
        navLocation = nav.snapped
        progress = step.progress
        step.speech?.let { voice.say(it) }
        if (step.progress.arrived) {
            val next = finalStop
            if (next != null && !rerouting) {
                // Reached the stop: carry on to the original destination.
                finalStop = null
                place = next
                rerouting = true
                viewModelScope.launch {
                    try {
                        Router.route(LatLon(loc.latitude, loc.longitude), next.pos, mode, settings.avoid, movingHeading(), trafficKey()).firstOrNull()?.let { r ->
                            navigator = Navigator(r)
                            routes = listOf(r)
                            selected = 0
                            voice.say("Continuing to ${next.name}.")
                            cameraTick++
                        }
                    } catch (_: Exception) {
                    } finally {
                        rerouting = false
                    }
                }
            }
            return
        }
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
                val heading = movingHeading()
                val r = Router.route(LatLon(loc.latitude, loc.longitude), dest.pos, mode, settings.avoid, heading, trafficKey()).first()
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
        getApplication<Application>().getSharedPreferences("recents", 0).edit().remove("list").apply()
        recents = emptyList()
    }

    /** Returns false when there's nothing to go back to (the app should close). */
    fun back(): Boolean {
        when (screen) {
            Screen.EXPLORE -> { if (tab == 0) return false; selectTab(0) }
            Screen.SETUP -> return false
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

    /** TomTom key for live traffic: yours from Settings, else the one built into the app. */
    /** TomTom key for place search (works even if live traffic is switched off). */
    fun searchKey(): String? = settings.tomtomKey.trim().ifBlank { com.bitdroid.maps.BuildConfig.TOMTOM_KEY }.ifBlank { null }

    fun trafficKey(): String? = if (!settings.liveTraffic) null
        else settings.tomtomKey.trim().ifBlank { com.bitdroid.maps.BuildConfig.TOMTOM_KEY }.ifBlank { null }

    /** The direction you're driving, so routes start on the right side of the road (never a U-turn on a highway). */
    private fun movingHeading(): Float? {
        val l = location ?: return null
        return if (l.hasBearing() && l.hasSpeed() && l.speed > 2f) l.bearing else null
    }

    fun here(): LatLon? = location?.let { LatLon(it.latitude, it.longitude) }

    override fun onCleared() {
        tracker.stop()
        music.stop()
        voice.shutdown()
    }
}
