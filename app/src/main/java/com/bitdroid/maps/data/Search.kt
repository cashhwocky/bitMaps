package com.bitdroid.maps.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Smarter place search. Several free sources run at the same time and their results are merged:
 *  - "gas", "coffee", "food near me"… → every matching place around you from OpenStreetMap (Overpass),
 *    plus TomTom's places when a key is built in. Sorted by distance.
 *  - Names and chains ("sheetz", "mcdonalds") → nearby name matches from OpenStreetMap, TomTom and Photon,
 *    best text match first, then closest.
 */
object Search {

    /** A kind of place people type ("gas") → the OpenStreetMap tags that describe it. */
    class Intent(val label: String, val kind: String, val tags: List<String>, val tomtom: String)

    private val intents: List<Pair<List<String>, Intent>> = listOf(
        listOf("gas", "gas station", "gas stations", "fuel", "petrol", "gasoline", "diesel") to
            Intent("Gas station", "fuel", listOf("\"amenity\"=\"fuel\""), "petrol station"),
        listOf("coffee", "cafe", "cafes", "coffee shop", "coffee shops", "café") to
            Intent("Cafe", "cafe", listOf("\"amenity\"=\"cafe\""), "cafe"),
        listOf("food", "restaurant", "restaurants", "eat", "dinner", "lunch", "breakfast", "places to eat") to
            Intent("Restaurant", "restaurant", listOf("\"amenity\"~\"^(restaurant|fast_food|food_court)$\""), "restaurant"),
        listOf("fast food", "drive thru", "drive through") to
            Intent("Fast food", "fast_food", listOf("\"amenity\"=\"fast_food\""), "fast food"),
        listOf("groceries", "grocery", "grocery store", "grocery stores", "supermarket", "supermarkets") to
            Intent("Grocery store", "supermarket", listOf("\"shop\"~\"^(supermarket|grocery)$\""), "grocery store"),
        listOf("convenience store", "convenience", "corner store") to
            Intent("Convenience store", "convenience", listOf("\"shop\"=\"convenience\""), "convenience store"),
        listOf("parking", "parking lot", "parking garage") to
            Intent("Parking", "parking", listOf("\"amenity\"=\"parking\"", "\"name\""), "parking"),
        listOf("charging", "ev charging", "charger", "chargers", "charging station", "ev charger", "tesla supercharger") to
            Intent("Charging station", "charging_station", listOf("\"amenity\"=\"charging_station\""), "electric vehicle station"),
        listOf("pharmacy", "pharmacies", "drugstore", "drug store") to
            Intent("Pharmacy", "pharmacy", listOf("\"amenity\"=\"pharmacy\""), "pharmacy"),
        listOf("atm", "atms", "cash") to
            Intent("ATM", "atm", listOf("\"amenity\"=\"atm\""), "atm"),
        listOf("bank", "banks") to
            Intent("Bank", "bank", listOf("\"amenity\"=\"bank\""), "bank"),
        listOf("hotel", "hotels", "motel", "motels", "lodging") to
            Intent("Hotel", "hotel", listOf("\"tourism\"~\"^(hotel|motel)$\""), "hotel"),
        listOf("hospital", "hospitals", "er", "emergency room") to
            Intent("Hospital", "hospital", listOf("\"amenity\"=\"hospital\""), "hospital"),
        listOf("urgent care", "clinic", "clinics", "doctor") to
            Intent("Clinic", "clinic", listOf("\"amenity\"~\"^(clinic|doctors)$\""), "medical clinic"),
        listOf("bar", "bars", "pub", "pubs") to
            Intent("Bar", "bar", listOf("\"amenity\"~\"^(bar|pub)$\""), "bar"),
        listOf("gym", "gyms", "fitness") to
            Intent("Gym", "gym", listOf("\"leisure\"=\"fitness_centre\""), "fitness club"),
        listOf("park", "parks", "playground") to
            Intent("Park", "park", listOf("\"leisure\"=\"park\"", "\"name\""), "park"),
        listOf("car wash", "carwash") to
            Intent("Car wash", "car_wash", listOf("\"amenity\"=\"car_wash\""), "car wash"),
        listOf("mall", "malls", "shopping") to
            Intent("Mall", "mall", listOf("\"shop\"=\"mall\""), "shopping center"),
        listOf("pizza") to
            Intent("Pizza", "restaurant", listOf("\"cuisine\"~\"pizza\""), "pizza"),
        listOf("auto parts", "car parts") to
            Intent("Auto parts", "car_parts", listOf("\"shop\"=\"car_parts\""), "auto parts"),
        listOf("mechanic", "car repair", "auto repair", "tire shop", "tires") to
            Intent("Car repair", "car_repair", listOf("\"shop\"~\"^(car_repair|tyres)$\""), "car repair"),
        listOf("post office") to
            Intent("Post office", "post_office", listOf("\"amenity\"=\"post_office\""), "post office"),
        listOf("library", "libraries") to
            Intent("Library", "library", listOf("\"amenity\"=\"library\""), "library"),
        listOf("movie theater", "movies", "cinema", "movie theatre") to
            Intent("Cinema", "cinema", listOf("\"amenity\"=\"cinema\""), "movie theater"),
        listOf("hardware store", "hardware") to
            Intent("Hardware store", "hardware", listOf("\"shop\"~\"^(hardware|doityourself)$\""), "hardware store"),
        listOf("liquor store", "abc store", "liquor") to
            Intent("Liquor store", "alcohol", listOf("\"shop\"=\"alcohol\""), "liquor store"),
        listOf("ice cream") to
            Intent("Ice cream", "ice_cream", listOf("\"amenity\"=\"ice_cream\""), "ice cream"),
        listOf("bakery", "bakeries") to
            Intent("Bakery", "bakery", listOf("\"shop\"=\"bakery\""), "bakery"),
        listOf("rest area", "rest stop") to
            Intent("Rest area", "rest_area", listOf("\"highway\"=\"rest_area\""), "rest area"),
    )

    private val filler = Regex("\\b(near me|nearby|near here|around me|close by|closest|nearest|around here|in my area|open now)\\b")

    /** "gas near me" → the gas station intent. Null for names, chains and addresses. */
    fun intent(query: String): Intent? {
        val q = query.lowercase(Locale.US).replace(filler, " ").replace(Regex("\\s+"), " ").trim()
        if (q.isEmpty()) return null
        return intents.firstOrNull { (words, _) -> q in words }?.second
    }

    /** Everything of one kind around you, closest first. */
    suspend fun nearby(intent: Intent, near: LatLon, tomtomKey: String?, fallbackQuery: String, partial: (List<Place>) -> Unit = {}): List<Place> = coroutineScope {
        val osm = async { capped(7000) { Overpass.nearby(intent, near) } }
        val tt = async { if (tomtomKey == null) emptyList() else capped(5000) { TomTomSearch.search(intent.tomtom, near, tomtomKey, limit = 40, radiusM = 25_000) } }
        val tag = intent.tags.first().replace("\"", "").replace("=", ":").substringBefore("~")
        val photon = async { capped(5000) { Photon.search(fallbackQuery, near, osmTag = if (tag.contains(':')) tag else null) } }
        fun sorted(l: List<Place>) = merge(l).map { relabel(it, intent) }.sortedBy { Geo.distance(near, it.pos) }
        // Fast sources first, OpenStreetMap's full list fills in when it lands (never more than ~7 s).
        val quick = tt.await() + photon.await()
        if (osm.isActive && quick.isNotEmpty()) partial(sorted(quick))
        sorted(osm.await() + quick)
    }

    /** Everything found for "gas" is labelled the same way ("Gas station", not "Petrol station"). */
    private fun relabel(p: Place, intent: Intent): Place = if (p.kind == intent.kind || intent.kind == "restaurant") p.copy(category = intent.label) else p

    /** Names, chains, streets, cities: best text match first, then closest. */
    suspend fun text(query: String, near: LatLon?, tomtomKey: String?, partial: (List<Place>) -> Unit = {}): List<Place> = coroutineScope {
        val photon = async { capped(5000) { Photon.search(query, near) } }
        val tt = async { if (tomtomKey == null) emptyList() else capped(5000) { TomTomSearch.search(query, near, tomtomKey, limit = 25, radiusM = null) } }
        val osmNames = async { if (near == null) emptyList() else capped(6000) { Overpass.named(query, near) } }
        val quick = merge(tt.await() + photon.await())
        // Show the fast results right away; the full OpenStreetMap name search lands a moment later.
        if (osmNames.isActive) partial(rank(quick, query, near))
        rank(merge(quick + osmNames.await()), query, near)
    }

    private fun rank(all: List<Place>, query: String, near: LatLon?): List<Place> {
        val nq = norm(query)
        fun textScore(p: Place): Int {
            val n = norm(p.name)
            return when {
                n == nq -> 0
                n.startsWith(nq) -> 1
                n.contains(nq) || nq.contains(n) && n.length >= 3 -> 2
                else -> 3
            }
        }
        return if (near == null) all.sortedBy(::textScore)
        else all.sortedWith(compareBy<Place> { textScore(it) }.thenBy { Geo.distance(near, it.pos) })
    }

    /** Drops duplicates of the same place coming from different sources. */
    fun merge(list: List<Place>): List<Place> {
        val out = ArrayList<Place>()
        for (p in list) {
            val dup = out.any { o ->
                val d = Geo.distance(o.pos, p.pos)
                d < 12 || (d < 150 && sameName(o.name, p.name))
            }
            if (!dup) out.add(p)
        }
        return out
    }

    private fun sameName(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || x.startsWith(y.take(5)) || y.startsWith(x.take(5))
    }

    fun norm(s: String) = s.lowercase(Locale.US).filter { it.isLetterOrDigit() }

    /** A source that's slow or down never holds up the results: give up on it after [ms]. */
    private suspend fun capped(ms: Long, block: suspend () -> List<Place>): List<Place> =
        kotlinx.coroutines.withTimeoutOrNull(ms) { safe(block) } ?: emptyList()

    private suspend fun safe(block: suspend () -> List<Place>): List<Place> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    internal fun address(house: String, street: String, city: String, state: String): String =
        listOf(listOf(house, street).filter { it.isNotBlank() }.joinToString(" "), city, state).filter { it.isNotBlank() }.joinToString(", ")
}

/** OpenStreetMap's live database: every gas station / cafe / chain location around you. Free, no key. */
object Overpass {
    private val servers = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    suspend fun nearby(intent: Search.Intent, near: LatLon): List<Place> {
        val filter = intent.tags.joinToString("") { "[$it]" }
        return run("nwr$filter(around:%d,%.6f,%.6f);", near, 25_000, intent)
    }

    /** Places whose name or brand matches what you typed (ignores spaces, apostrophes and case). */
    suspend fun named(query: String, near: LatLon): List<Place> {
        val letters = Search.norm(query)
        if (letters.length < 3 || letters.length > 30) return emptyList()
        // "mcdonalds" must match "McDonald's": allow any punctuation/space between letters.
        val rx = letters.toCharArray().joinToString("[^a-zA-Z0-9]*")
        val body = "nwr[\"name\"~\"$rx\",i](around:%d,%.6f,%.6f);nwr[\"brand\"~\"$rx\",i](around:%d,%.6f,%.6f);"
        return run(body, near, 30_000, null, twice = true)
    }

    private suspend fun run(body: String, near: LatLon, radius: Int, intent: Search.Intent?, twice: Boolean = false): List<Place> {
        val b = if (twice) String.format(Locale.US, body, radius, near.lat, near.lon, radius, near.lat, near.lon)
        else String.format(Locale.US, body, radius, near.lat, near.lon)
        val q = "[out:json][timeout:8];($b);out tags center 80;"
        // Ask both servers at once and take whichever answers first.
        val json = coroutineScope {
            val ch = kotlinx.coroutines.channels.Channel<String?>(servers.size)
            val jobs = servers.map { s -> launch { ch.send(try { Http.get("$s?data=${Http.enc(q)}") } catch (e: CancellationException) { throw e } catch (_: Exception) { null }) } }
            var got: String? = null
            for (i in servers.indices) {
                got = ch.receive()
                if (got != null) break
            }
            jobs.forEach { it.cancel() }
            got
        } ?: throw Exception("Overpass unavailable")
        return parse(json, intent)
    }

    private fun parse(json: String, intent: Search.Intent?): List<Place> {
        val els = JSONObject(json).optJSONArray("elements") ?: JSONArray()
        val out = ArrayList<Place>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val t = e.optJSONObject("tags") ?: continue
            val lat = if (e.has("lat")) e.getDouble("lat") else e.optJSONObject("center")?.optDouble("lat") ?: continue
            val lon = if (e.has("lon")) e.getDouble("lon") else e.optJSONObject("center")?.optDouble("lon") ?: continue
            val kind = listOf("amenity", "shop", "tourism", "leisure", "highway").firstNotNullOfOrNull { k -> t.optString(k).ifBlank { null } }
                ?: intent?.kind ?: ""
            val name = t.optString("name").ifBlank { t.optString("brand") }.ifBlank { intent?.label ?: "" }
            if (name.isBlank()) continue
            val detail = Search.address(t.optString("addr:housenumber"), t.optString("addr:street"), t.optString("addr:city"), t.optString("addr:state"))
            out.add(
                Place(
                    id = "${e.optString("type")}${e.optLong("id")}",
                    name = name,
                    detail = detail,
                    category = intent?.label ?: kind.replace('_', ' ').replaceFirstChar { it.titlecase() },
                    kind = if (intent != null && intent.kind != "restaurant") intent.kind else kind,
                    lat = lat,
                    lon = lon,
                ),
            )
        }
        return out
    }
}

/** TomTom's place search (same free key as live traffic): great for businesses, chains and addresses. */
object TomTomSearch {
    suspend fun search(query: String, near: LatLon?, key: String, limit: Int, radiusM: Int?): List<Place> {
        val lang = Locale.getDefault().toLanguageTag()
        val loc = if (near != null) "&lat=${near.lat}&lon=${near.lon}" + (if (radiusM != null) "&radius=$radiusM" else "") else ""
        val q = Http.enc(query).replace("+", "%20")
        return try {
            parse(Http.get("https://api.tomtom.com/search/2/search/$q.json?key=${Http.enc(key)}&limit=$limit&typeahead=true&language=$lang$loc"))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Newer keys may only allow the Orbis version of the same search.
            parse(Http.get("https://api.tomtom.com/maps/orbis/places/search/$q.json?apiVersion=1&key=${Http.enc(key)}&limit=$limit&typeahead=true&language=$lang$loc"))
        }
    }

    private fun parse(json: String): List<Place> {
        val rs = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until rs.length()) {
            val r = rs.getJSONObject(i)
            val pos = r.optJSONObject("position") ?: continue
            val a = r.optJSONObject("address") ?: JSONObject()
            val poi = r.optJSONObject("poi")
            val type = r.optString("type")
            val street = listOf(a.optString("streetNumber"), a.optString("streetName")).filter { it.isNotBlank() }.joinToString(" ")
            val city = a.optString("municipality").substringBefore(",")
            val state = a.optString("countrySubdivisionCode").ifBlank { a.optString("countrySubdivision") }
            val code = poi?.optJSONArray("classifications")?.optJSONObject(0)?.optString("code") ?: ""
            val name: String
            val detail: String
            val kind: String
            val category: String
            if (poi != null) {
                name = poi.optString("name")
                detail = listOf(street, city, state).filter { it.isNotBlank() }.joinToString(", ")
                kind = kindFor(code, poi)
                category = labelFor(kind) ?: poi.optJSONArray("categories")?.optString(0)
                    ?.replace("petrol", "gas", ignoreCase = true)?.replaceFirstChar { it.titlecase() } ?: "Place"
            } else {
                val isAddress = type == "Point Address" || type == "Address Range"
                name = if (isAddress && street.isNotBlank()) street else a.optString("freeformAddress").substringBefore(",")
                detail = listOf(city, state).filter { it.isNotBlank() && it != name }.joinToString(", ")
                kind = when (type) { "Point Address", "Address Range" -> "house"; "Geography" -> "city"; else -> "street" }
                category = when (type) { "Point Address", "Address Range" -> "Address"; "Geography" -> "Area"; else -> "Street" }
            }
            if (name.isBlank()) continue
            out.add(Place("tt:${r.optString("id")}", name, detail, category, kind, pos.getDouble("lat"), pos.getDouble("lon")))
        }
        return out
    }

    private fun labelFor(kind: String): String? = when (kind) {
        "fuel" -> "Gas station"
        "charging_station" -> "Charging station"
        "cafe" -> "Cafe"
        "fast_food" -> "Fast food"
        "restaurant" -> "Restaurant"
        "pub" -> "Bar"
        "parking" -> "Parking"
        "supermarket" -> "Grocery store"
        "pharmacy" -> "Pharmacy"
        "hospital" -> "Hospital"
        "clinic" -> "Clinic"
        "hotel" -> "Hotel"
        "bank" -> "Bank"
        "atm" -> "ATM"
        "mall" -> "Shopping center"
        "gym" -> "Gym"
        else -> null
    }

    private fun kindFor(code: String, poi: JSONObject): String = when (code) {
        "PETROL_STATION" -> "fuel"
        "ELECTRIC_VEHICLE_STATION" -> "charging_station"
        "CAFE_PUB" -> if (poi.optJSONArray("categories")?.toString()?.contains("pub") == true) "pub" else "cafe"
        "RESTAURANT" -> if (poi.optJSONArray("categories")?.toString()?.contains("fast food") == true) "fast_food" else "restaurant"
        "PARKING_GARAGE", "OPEN_PARKING_AREA" -> "parking"
        "SHOP" -> if (poi.optJSONArray("categories")?.toString()?.let { "grocer" in it || "supermarket" in it } == true) "supermarket" else "shop"
        "MARKET" -> "supermarket"
        "PHARMACY" -> "pharmacy"
        "HOSPITAL_POLYCLINIC", "EMERGENCY_MEDICAL_SERVICE" -> "hospital"
        "DOCTOR", "HEALTH_CARE_SERVICE" -> "clinic"
        "HOTEL_MOTEL" -> "hotel"
        "BANK" -> "bank"
        "CASH_DISPENSER" -> "atm"
        "SHOPPING_CENTER" -> "mall"
        "SCHOOL", "COLLEGE_UNIVERSITY" -> "school"
        "PARK_RECREATION_AREA" -> "park"
        "AIRPORT" -> "airport"
        "RAILWAY_STATION", "PUBLIC_TRANSPORT_STOP" -> "station"
        "CINEMA" -> "cinema"
        "FITNESS_CLUB_CENTER", "SPORTS_CENTER" -> "gym"
        else -> code.lowercase(Locale.US)
    }
}
