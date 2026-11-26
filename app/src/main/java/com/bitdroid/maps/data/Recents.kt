package com.bitdroid.maps.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Recently opened places, newest first, kept on the phone only. */
class Recents(context: Context) {
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun load(): List<Place> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Place(
                id = o.getString("id"),
                name = o.getString("name"),
                detail = o.optString("detail"),
                category = o.optString("category"),
                kind = o.optString("kind"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
            )
        }
    }.getOrDefault(emptyList())

    /** Saved Home place (or null). */
    fun home(): Place? = runCatching { prefs.getString("home", null)?.let { fromJson(JSONObject(it)) } }.getOrNull()

    fun setHome(p: Place?) {
        prefs.edit().apply { if (p == null) remove("home") else putString("home", toJson(p).toString()) }.apply()
    }

    /** Saved Work place (or null). */
    fun work(): Place? = runCatching { prefs.getString("work", null)?.let { fromJson(JSONObject(it)) } }.getOrNull()

    fun setWork(p: Place?) {
        prefs.edit().apply { if (p == null) remove("work") else putString("work", toJson(p).toString()) }.apply()
    }

    /** Starred places, newest first. */
    fun saved(): List<Place> = runCatching {
        val arr = JSONArray(prefs.getString("saved", "[]"))
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun setSaved(list: List<Place>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("saved", arr.toString()).apply()
    }

    /** Lifetime driving stats shown on the You tab. */
    fun stats(): Triple<Int, Double, Double> =
        Triple(prefs.getInt("trips", 0), prefs.getFloat("meters", 0f).toDouble(), prefs.getFloat("seconds", 0f).toDouble())

    fun addTrip(meters: Double, seconds: Double) {
        val (t, m, s) = stats()
        prefs.edit().putInt("trips", t + 1).putFloat("meters", (m + meters).toFloat()).putFloat("seconds", (s + seconds).toFloat()).apply()
    }

    private fun toJson(it: Place) = JSONObject().put("id", it.id).put("name", it.name).put("detail", it.detail)
        .put("category", it.category).put("kind", it.kind).put("lat", it.lat).put("lon", it.lon)

    private fun fromJson(o: JSONObject) = Place(
        id = o.getString("id"), name = o.getString("name"), detail = o.optString("detail"),
        category = o.optString("category"), kind = o.optString("kind"), lat = o.getDouble("lat"), lon = o.getDouble("lon"),
    )

    fun add(place: Place): List<Place> {
        val list = (listOf(place) + load().filter { it.id != place.id }).take(15)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("detail", it.detail)
                    .put("category", it.category).put("kind", it.kind).put("lat", it.lat).put("lon", it.lon),
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
        return list
    }
}
