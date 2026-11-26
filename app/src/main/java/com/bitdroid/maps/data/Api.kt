package com.bitdroid.maps.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Free, keyless services. Change these URLs to point bitMaps at your own servers later
 * (Photon and Valhalla are both open source and self-hostable).
 */
object Servers {
    const val MAP_STYLE = "https://tiles.openfreemap.org/styles/dark"
    const val PHOTON = "https://photon.komoot.io"
    const val VALHALLA = "https://valhalla1.openstreetmap.de"

    /** Current servers (from Settings). */
    var photon = PHOTON
    var valhalla = VALHALLA
}

data class Avoid(val highways: Boolean = false, val tolls: Boolean = false, val ferries: Boolean = false)

object Http {
    private const val UA = "bitMaps/0.1 (bitDroid; https://github.com/cashhwocky/bitMaps)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", UA).build()) }
        .build()

    /** Cancellable GET: when a search is replaced or times out, the request is dropped at once. */
    suspend fun get(url: String): String = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val call = client.newCall(Request.Builder().url(url).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (cont.isActive) cont.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val result = runCatching {
                    response.use { r ->
                        val body = r.body?.string() ?: ""
                        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                        body
                    }
                }
                if (cont.isActive) cont.resumeWith(result)
            }
        })
    }

    suspend fun postJson(url: String, json: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IOException(if (msg.isNullOrBlank()) "HTTP ${r.code}" else msg)
            }
            body
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** Place search with Photon (OpenStreetMap data). */
object Photon {
    suspend fun search(
        query: String,
        near: LatLon?,
        osmTag: String? = null,
        nearbyOnly: Boolean = false,
    ): List<Place> {
        val sb = StringBuilder("${Servers.photon.trimEnd('/')}/api/?q=${Http.enc(query)}&limit=20")
        if (near != null) {
            sb.append("&lat=${near.lat}&lon=${near.lon}&zoom=12&location_bias_scale=0.3")
            if (nearbyOnly) {
                val d = 0.12
                sb.append(String.format(Locale.US, "&bbox=%.5f,%.5f,%.5f,%.5f", near.lon - d, near.lat - d, near.lon + d, near.lat + d))
            }
        }
        if (osmTag != null) sb.append("&osm_tag=${Http.enc(osmTag)}")
        return parse(Http.get(sb.toString()))
    }

    suspend fun reverse(lat: Double, lon: Double): Place? =
        parse(Http.get("${Servers.photon.trimEnd('/')}/reverse?lat=$lat&lon=$lon")).firstOrNull()

    private fun parse(json: String): List<Place> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val p = f.optJSONObject("properties") ?: JSONObject()
            val house = p.optString("housenumber")
            val street = p.optString("street")
            val city = p.optString("city").ifBlank { p.optString("district") }
            val state = p.optString("state")
            val streetLine = listOf(house, street).filter { it.isNotBlank() }.joinToString(" ")
            val name = p.optString("name").ifBlank { streetLine.ifBlank { city.ifBlank { state } } }
            if (name.isBlank()) continue
            val detail = listOf(if (streetLine == name) "" else streetLine, if (city == name) "" else city, state)
                .filter { it.isNotBlank() }.joinToString(", ")
            val key = p.optString("osm_key")
            val value = p.optString("osm_value")
            val category = when {
                p.optString("type") == "house" && p.optString("name").isBlank() -> "Address"
                value.isBlank() || value == "yes" -> key.pretty()
                else -> value.pretty()
            }
            out.add(
                Place(
                    id = "${p.optString("osm_type")}${p.optLong("osm_id")}",
                    name = name,
                    detail = detail,
                    category = category,
                    kind = value,
                    lat = c.getDouble(1),
                    lon = c.getDouble(0),
                ),
            )
        }
        return out.distinctBy { it.id }
    }

    private fun String.pretty() = replace('_', ' ').replaceFirstChar { it.titlecase() }
}

/** Turn-by-turn routing with Valhalla. */
object Valhalla {
    suspend fun route(from: LatLon, to: LatLon, mode: Mode, avoid: Avoid = Avoid(), heading: Float? = null): List<Route> {
        val units = if (Units.imperial) "miles" else "kilometers"
        val lang = Locale.getDefault().toLanguageTag()
        val start = JSONObject().put("lat", from.lat).put("lon", from.lon).put("type", "break")
        if (heading != null) start.put("heading", heading.toInt()).put("heading_tolerance", 45)
        val body = JSONObject()
            .put("locations", JSONArray().put(start).put(JSONObject().put("lat", to.lat).put("lon", to.lon).put("type", "break")))
            .put("costing", mode.costing)
            .put("alternates", if (mode == Mode.DRIVE) 2 else 1)
            .put("units", units)
            .put("language", lang)
            .put("directions_options", JSONObject().put("units", units).put("language", lang))
        if (mode != Mode.WALK) {
            val opts = JSONObject()
            if (avoid.highways) opts.put("use_highways", 0.0)
            if (avoid.tolls) opts.put("use_tolls", 0.0)
            if (avoid.ferries) opts.put("use_ferry", 0.0)
            if (opts.length() > 0) body.put("costing_options", JSONObject().put(mode.costing, opts))
        }
        val json = JSONObject(Http.postJson("${Servers.valhalla.trimEnd('/')}/route", body.toString()))
        val toMeters = if (Units.imperial) 1609.344 else 1000.0
        val out = ArrayList<Route>()
        json.optJSONObject("trip")?.let { out.add(parseTrip(it, toMeters)) }
        json.optJSONArray("alternates")?.let { alts ->
            for (i in 0 until alts.length()) alts.getJSONObject(i).optJSONObject("trip")?.let { out.add(parseTrip(it, toMeters)) }
        }
        if (out.isEmpty()) throw IOException("No route found")
        return out
    }

    /** Snaps a route shape (e.g. TomTom's traffic route) onto the road network and returns its turns. */
    suspend fun trace(points: List<LatLon>, mode: Mode): Route {
        val units = if (Units.imperial) "miles" else "kilometers"
        val lang = Locale.getDefault().toLanguageTag()
        // Keep the shape dense (a point every ~15 m, up to ~8,000 points) so the match never cuts corners.
        var total = 0.0
        for (i in 1 until points.size) total += Geo.distance(points[i - 1], points[i])
        val step = maxOf(15.0, total / 8000.0)
        val shape = JSONArray()
        var acc = 0.0
        shape.put(JSONObject().put("lat", points.first().lat).put("lon", points.first().lon))
        for (i in 1 until points.size - 1) {
            acc += Geo.distance(points[i - 1], points[i])
            if (acc >= step) {
                shape.put(JSONObject().put("lat", points[i].lat).put("lon", points[i].lon))
                acc = 0.0
            }
        }
        shape.put(JSONObject().put("lat", points.last().lat).put("lon", points.last().lon))
        val body = JSONObject()
            .put("shape", shape)
            .put("costing", mode.costing)
            .put("shape_match", "map_snap")
            // TomTom's roads can sit 10–30 m from OpenStreetMap's: look wider so the match doesn't fail.
            .put("trace_options", JSONObject().put("search_radius", 40).put("gps_accuracy", 20).put("turn_penalty_factor", 200))
            .put("units", units)
            .put("language", lang)
            .put("directions_options", JSONObject().put("units", units).put("language", lang))
        val json = JSONObject(Http.postJson("${Servers.valhalla.trimEnd('/')}/trace_route", body.toString()))
        val trip = json.optJSONObject("trip") ?: throw IOException("No match")
        return parseTrip(trip, if (Units.imperial) 1609.344 else 1000.0)
    }

    private fun parseTrip(trip: JSONObject, toMeters: Double): Route {
        val legs = trip.getJSONArray("legs")
        val points = ArrayList<LatLon>()
        val maneuvers = ArrayList<Maneuver>()
        for (l in 0 until legs.length()) {
            val leg = legs.getJSONObject(l)
            val offset = points.size
            points.addAll(Geo.decodePolyline6(leg.getString("shape")))
            val ms = leg.getJSONArray("maneuvers")
            for (i in 0 until ms.length()) {
                val m = ms.getJSONObject(i)
                val streets = m.optJSONArray("street_names")
                maneuvers.add(
                    Maneuver(
                        type = m.optInt("type"),
                        instruction = m.optString("instruction"),
                        alert = m.optString("verbal_transition_alert_instruction").ifBlank { null },
                        pre = m.optString("verbal_pre_transition_instruction").ifBlank { null },
                        post = m.optString("verbal_post_transition_instruction").ifBlank { null },
                        street = streets?.optString(0)?.ifBlank { null },
                        beginIndex = offset + m.optInt("begin_shape_index"),
                        lengthM = m.optDouble("length", 0.0) * toMeters,
                        timeS = m.optDouble("time", 0.0),
                    ),
                )
            }
        }
        val summary = trip.optJSONObject("summary") ?: JSONObject()
        val via = maneuvers.filter { it.street != null }.maxByOrNull { it.lengthM }?.street
        return Route(
            points = points,
            maneuvers = maneuvers,
            distanceM = summary.optDouble("length", 0.0) * toMeters,
            durationS = summary.optDouble("time", 0.0),
            via = via,
        )
    }
}

/**
 * US Census address lookup: free, no key. Fills in house numbers that OpenStreetMap is missing
 * (e.g. "123 Oak St, Greensboro NC").
 */
object Census {
    /** Looks like "123 Something …" (a house number and a street). */
    fun looksLikeAddress(q: String): Boolean = Regex("""^\s*\d+[A-Za-z]?\s+\S+.*""").matches(q) && q.trim().split(Regex("\\s+")).size >= 3

    /**
     * Tries the address as typed; if the Census needs a city/state, retries with [area]
     * (where you are now, e.g. "Whitsett, NC").
     */
    suspend fun search(q: String, area: String?): List<Place> {
        val first = runCatching { lookup(q) }.getOrDefault(emptyList())
        if (first.isNotEmpty() || area.isNullOrBlank() || q.contains(',')) return first
        return runCatching { lookup("$q, $area") }.getOrDefault(emptyList())
    }

    private suspend fun lookup(q: String): List<Place> {
        val url = "https://geocoding.geo.census.gov/geocoder/locations/onelineaddress" +
            "?address=${Http.enc(q)}&benchmark=Public_AR_Current&format=json"
        val matches = JSONObject(Http.get(url)).optJSONObject("result")?.optJSONArray("addressMatches") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until matches.length()) {
            val m = matches.getJSONObject(i)
            val c = m.optJSONObject("coordinates") ?: continue
            val full = m.optString("matchedAddress")
            val parts = full.split(", ")
            val street = parts.firstOrNull()?.titleCase() ?: continue
            val rest = parts.drop(1).joinToString(", ") { if (it.length <= 2 || it.all(Char::isDigit)) it else it.titleCase() }
            out.add(
                Place(
                    id = "census:$full",
                    name = street,
                    detail = rest,
                    category = "Address",
                    kind = "house",
                    lat = c.getDouble("y"),
                    lon = c.getDouble("x"),
                ),
            )
        }
        return out
    }

    private fun String.titleCase() = lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
}

/**
 * Nominatim (OpenStreetMap's own search). Knows US house numbers from the official road
 * data even where the map is missing them. Used only for addresses, once per search.
 */
object Nominatim {
    suspend fun address(q: String, near: LatLon?): List<Place> {
        val sb = StringBuilder("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=5&q=${Http.enc(q)}")
        if (near != null) {
            val d = 0.6
            sb.append(String.format(Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f", near.lon - d, near.lat + d, near.lon + d, near.lat - d))
        }
        val arr = JSONArray(Http.get(sb.toString()))
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val a = o.optJSONObject("address") ?: JSONObject()
            val house = a.optString("house_number")
            val road = a.optString("road")
            val street = listOf(house, road).filter { it.isNotBlank() }.joinToString(" ")
            val city = a.optString("city").ifBlank { a.optString("town").ifBlank { a.optString("village").ifBlank { a.optString("hamlet") } } }
            val name = street.ifBlank { o.optString("name").ifBlank { o.optString("display_name").substringBefore(',') } }
            out.add(
                Place(
                    id = "nom:${o.optString("osm_type")}${o.optLong("osm_id")}:$house",
                    name = name,
                    detail = listOf(city, a.optString("state"), a.optString("postcode")).filter { it.isNotBlank() }.joinToString(", "),
                    category = if (house.isNotBlank()) "Address" else o.optString("type").replace('_', ' ').replaceFirstChar { it.titlecase() },
                    kind = if (house.isNotBlank()) "house" else o.optString("type"),
                    lat = o.getString("lat").toDouble(),
                    lon = o.getString("lon").toDouble(),
                ),
            )
        }
        return out
    }
}

/**
 * Live-traffic routing with TomTom (free tier: no credit card; over the daily limit it just answers
 * 429 and bitMaps falls back to Valhalla). Returns routes with turn-by-turn instructions and the
 * traffic delay already included in the time.
 */
object TomTom {
    suspend fun route(from: LatLon, to: LatLon, mode: Mode, key: String, avoid: Avoid, heading: Float?): List<Route> {
        val travel = when (mode) { Mode.DRIVE -> "car"; Mode.BIKE -> "bicycle"; Mode.WALK -> "pedestrian" }
        val lang = Locale.getDefault().toLanguageTag()
        val sb = StringBuilder("https://api.tomtom.com/routing/1/calculateRoute/")
        sb.append("${from.lat},${from.lon}:${to.lat},${to.lon}/json?key=${Http.enc(key)}")
        sb.append("&travelMode=$travel&routeType=fastest&traffic=true&instructionsType=text")
        sb.append("&language=${Http.enc(lang)}&maxAlternatives=2&computeTravelTimeFor=all&sectionType=traffic")
        if (heading != null && mode == Mode.DRIVE) sb.append("&vehicleHeading=${heading.toInt()}")
        if (mode != Mode.WALK) {
            if (avoid.highways) sb.append("&avoid=motorways")
            if (avoid.tolls) sb.append("&avoid=tollRoads")
            if (avoid.ferries) sb.append("&avoid=ferries")
        }
        // Classic TomTom routing: traffic + turn-by-turn in one call.
        try {
            // Ask for speed limits too; if this key/version doesn't know that section, ask again without it.
            val json = JSONObject(
                try { Http.get("$sb&sectionType=speedLimit") } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Http.get(sb.toString()) },
            )
            val routes = json.optJSONArray("routes") ?: throw IOException("No route found")
            val out = ArrayList<Route>()
            for (i in 0 until routes.length()) out.add(parse(routes.getJSONObject(i)))
            if (out.isNotEmpty() && out.first().points.size > 1) return snapAll(out, mode, from, to, avoid, heading)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Newer MyTomTom keys may only allow the Orbis API: try that next.
        }
        return orbis(from, to, mode, key, avoid, heading)
    }

    /**
     * TomTom's road geometry is a different map than the OpenStreetMap tiles we draw, so its line can
     * sit beside the road. Match each route onto the drawn roads (and their names/turns) with Valhalla,
     * keeping TomTom's traffic-aware time. If matching fails, the TomTom route is used as-is.
     */
    private suspend fun snapAll(routes: List<Route>, mode: Mode, from: LatLon, to: LatLon, avoid: Avoid, heading: Float?): List<Route> = kotlinx.coroutines.coroutineScope {
        val osm = async { try { Valhalla.route(from, to, mode, avoid, heading) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() } }
        val snapped = routes.map { r ->
            async {
                val m = try { Valhalla.trace(r.points, mode) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                // A good match covers about the same distance as TomTom's route; otherwise it's unusable.
                if (m == null || m.points.size < 2 || kotlin.math.abs(m.distanceM - r.distanceM) > r.distanceM * 0.08) null
                else r.withShape(m.points, m.maneuvers, m.via)
            }
        }.awaitAll()
        val plain = osm.await()
        val out = snapped.mapIndexedNotNull { i, m ->
            m ?: run {
                // Never draw TomTom's own line (it can sit beside the road): use the closest OpenStreetMap
                // route of similar length, still with TomTom's traffic time.
                val r = routes[i]
                plain.minByOrNull { kotlin.math.abs(it.distanceM - r.distanceM) }
                    ?.takeIf { kotlin.math.abs(it.distanceM - r.distanceM) < r.distanceM * 0.1 }
                    ?.let { v -> r.withShape(v.points, v.maneuvers, v.via) }
            }
        }.distinctBy { it.points.size to it.distanceM }
        out.ifEmpty { plain.ifEmpty { routes } }
    }

    /**
     * Orbis Routing v2 gives the traffic-aware route + ETA but no turn instructions, so the exact
     * route is then matched on the free Valhalla server to get the turns and voice prompts.
     */
    private suspend fun orbis(from: LatLon, to: LatLon, mode: Mode, key: String, avoid: Avoid, heading: Float?): List<Route> {
        val travel = when (mode) { Mode.DRIVE -> "car"; Mode.BIKE -> "bicycle"; Mode.WALK -> "pedestrian" }
        val sb = StringBuilder("https://api.tomtom.com/maps/orbis/routing/calculateRoute/")
        sb.append("${from.lat},${from.lon}:${to.lat},${to.lon}/json?key=${Http.enc(key)}&apiVersion=2")
        sb.append("&travelMode=$travel&traffic=live&maxAlternatives=2")
        if (heading != null && mode == Mode.DRIVE) sb.append("&vehicleHeading=${heading.toInt()}")
        if (mode != Mode.WALK) {
            if (avoid.highways) sb.append("&avoid=motorways")
            if (avoid.tolls) sb.append("&avoid=tollRoads")
            if (avoid.ferries) sb.append("&avoid=ferries")
        }
        val json = JSONObject(Http.get(sb.toString()))
        val routes = json.optJSONArray("routes") ?: throw IOException("No route found")
        val out = ArrayList<Route>()
        for (i in 0 until routes.length()) {
            val r = routes.getJSONObject(i)
            val pts = points(r)
            if (pts.size < 2) continue
            val sum = r.optJSONObject("summary") ?: JSONObject()
            val time = sum.optDouble("travelTimeInSeconds", sum.optDouble("travelDurationInSeconds", 0.0))
            val delay = sum.optDouble("trafficDelayInSeconds", sum.optDouble("trafficDelayDurationInSeconds", 0.0))
            val len = sum.optDouble("lengthInMeters", 0.0)
            val matched = runCatching { Valhalla.trace(pts, mode) }.getOrNull()
                ?.takeIf { len <= 0 || kotlin.math.abs(it.distanceM - len) < len * 0.08 } ?: continue
            out.add(
                Route(
                    points = matched.points,
                    maneuvers = matched.maneuvers,
                    distanceM = sum.optDouble("lengthInMeters", matched.distanceM),
                    durationS = if (time > 0) time else matched.durationS,
                    via = matched.via,
                    trafficDelayS = delay,
                ),
            )
        }
        if (out.isEmpty()) throw IOException("No route found")
        return out
    }

    /** Route geometry from either the classic (legs[].points) or GeoJSON (path.coordinates) shape. */
    private fun points(r: JSONObject): List<LatLon> {
        val out = ArrayList<LatLon>()
        val legs = r.optJSONArray("legs")
        if (legs != null) {
            for (l in 0 until legs.length()) {
                val pts = legs.getJSONObject(l).optJSONArray("points") ?: continue
                for (p in 0 until pts.length()) {
                    val o = pts.getJSONObject(p)
                    out.add(LatLon(o.getDouble("latitude"), o.getDouble("longitude")))
                }
            }
        }
        if (out.isEmpty()) {
            val coords = r.optJSONObject("path")?.optJSONArray("coordinates")
            if (coords != null) for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                out.add(LatLon(c.getDouble(1), c.getDouble(0)))
            }
        }
        return out
    }

    private fun parse(r: JSONObject): Route {
        val points = ArrayList<LatLon>()
        val legs = r.optJSONArray("legs") ?: JSONArray()
        for (l in 0 until legs.length()) {
            val pts = legs.getJSONObject(l).optJSONArray("points") ?: continue
            for (p in 0 until pts.length()) {
                val o = pts.getJSONObject(p)
                points.add(LatLon(o.getDouble("latitude"), o.getDouble("longitude")))
            }
        }
        val summary = r.optJSONObject("summary") ?: JSONObject()
        val total = summary.optDouble("lengthInMeters", 0.0)
        val ins = r.optJSONObject("guidance")?.optJSONArray("instructions") ?: JSONArray()
        val maneuvers = ArrayList<Maneuver>()
        for (i in 0 until ins.length()) {
            val o = ins.getJSONObject(i)
            val next = if (i + 1 < ins.length()) ins.getJSONObject(i + 1) else null
            val offset = o.optDouble("routeOffsetInMeters", 0.0)
            val nextOffset = next?.optDouble("routeOffsetInMeters", total) ?: total
            val msg = o.optString("message").ifBlank { o.optString("street") }
            maneuvers.add(
                Maneuver(
                    type = typeOf(o.optString("maneuver")),
                    instruction = msg,
                    alert = msg,
                    pre = msg,
                    post = null,
                    street = o.optString("street").ifBlank { null },
                    beginIndex = o.optInt("pointIndex", 0).coerceIn(0, (points.size - 1).coerceAtLeast(0)),
                    lengthM = (nextOffset - offset).coerceAtLeast(0.0),
                    timeS = 0.0,
                ),
            )
        }
        if (maneuvers.isEmpty() || !maneuvers.last().isArrival) {
            maneuvers.add(Maneuver(4, "You have arrived", null, "You have arrived.", null, null, (points.size - 1).coerceAtLeast(0), 0.0, 0.0))
        }
        val via = maneuvers.filter { it.street != null }.maxByOrNull { it.lengthM }?.street
        val traffic = ArrayList<Span>()
        val limits = ArrayList<Span>()
        val sections = r.optJSONArray("sections") ?: JSONArray()
        for (i in 0 until sections.length()) {
            val sec = sections.getJSONObject(i)
            val a = sec.optInt("startPointIndex", -1)
            val b = sec.optInt("endPointIndex", -1)
            if (a < 0 || b <= a) continue
            when (sec.optString("sectionType")) {
                "TRAFFIC" -> {
                    val mag = sec.optInt("magnitudeOfDelay", 1)
                    val level = when {
                        sec.optString("simpleCategory") == "ROAD_CLOSURE" || mag >= 3 -> 3
                        sec.optString("simpleCategory") == "JAM" || mag == 2 -> 2
                        else -> 1
                    }
                    traffic.add(Span(a, b, level))
                }
                "SPEED_LIMIT" -> {
                    val kmh = sec.optDouble("maxSpeedLimitInKmh", 0.0)
                    if (kmh > 0) limits.add(Span(a, b, Math.round(kmh).toInt()))
                }
            }
        }
        return Route(
            points = points,
            maneuvers = maneuvers,
            distanceM = total,
            durationS = summary.optDouble("travelTimeInSeconds", 0.0),
            via = via,
            trafficDelayS = summary.optDouble("trafficDelayInSeconds", 0.0),
            traffic = traffic,
            limits = limits,
        )
    }

    /** TomTom maneuver names → the turn types the rest of bitMaps uses (Valhalla numbering). */
    private fun typeOf(m: String): Int = when (m) {
        "DEPART" -> 1
        "ARRIVE" -> 4
        "ARRIVE_RIGHT" -> 5
        "ARRIVE_LEFT" -> 6
        "STRAIGHT", "FOLLOW", "SWITCH_MAIN_ROAD", "SWITCH_PARALLEL_ROAD" -> 8
        "BEAR_RIGHT" -> 9
        "TURN_RIGHT" -> 10
        "SHARP_RIGHT" -> 11
        "MAKE_UTURN", "TRY_MAKE_UTURN" -> 13
        "SHARP_LEFT" -> 14
        "TURN_LEFT" -> 15
        "BEAR_LEFT" -> 16
        "KEEP_RIGHT" -> 23
        "KEEP_LEFT" -> 24
        "TAKE_EXIT", "MOTORWAY_EXIT_RIGHT" -> 20
        "MOTORWAY_EXIT_LEFT" -> 21
        "ENTER_MOTORWAY", "ENTER_FREEWAY", "ENTER_HIGHWAY", "ENTRANCE_RAMP" -> 25
        "ROUNDABOUT_CROSS", "ROUNDABOUT_RIGHT", "ROUNDABOUT_LEFT", "ROUNDABOUT_BACK" -> 26
        "TAKE_FERRY" -> 28
        else -> 8
    }
}

/** Picks live-traffic TomTom when a key is set, otherwise (or if TomTom fails) the free Valhalla server. */
object Router {
    suspend fun route(from: LatLon, to: LatLon, mode: Mode, avoid: Avoid, heading: Float?, tomtomKey: String?): List<Route> {
        if (!tomtomKey.isNullOrBlank()) {
            try {
                return TomTom.route(from, to, mode, tomtomKey, avoid, heading)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Over the free limit (429), offline, or no TomTom route: fall back to Valhalla.
            }
        }
        return Valhalla.route(from, to, mode, avoid, heading)
    }
}
