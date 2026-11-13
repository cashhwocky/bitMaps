package com.bitdroid.maps.data

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

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            body
        }
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
        val sb = StringBuilder("${Servers.photon.trimEnd('/')}/api/?q=${Http.enc(query)}&limit=12")
        if (near != null) {
            sb.append("&lat=${near.lat}&lon=${near.lon}")
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
        if (heading != null) start.put("heading", heading.toInt()).put("heading_tolerance", 60)
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
