package com.bitdroid.maps.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class UnitChoice(val label: String) { AUTO("Automatic"), MILES("Miles"), KILOMETERS("Kilometers") }

/** Look of the whole app. */
enum class AppStyle(val label: String, val emoji: String, val description: String) {
    MATERIAL_YOU("Wallpaper colors", "🎨", "Material You: colors from your wallpaper (default)"),
    MAPS("bitDroid lilac", "💜", "Material You in bitDroid's lilac"),
    CLASSIC("Ocean blue", "🌊", "Material You in a deep blue"),
}

/** What marks your position while navigating. */
enum class Vehicle(val label: String, val emoji: String, val drawable: Int) {
    ARROW("Arrow", "➤", com.bitdroid.maps.R.drawable.ic_puck_google),
    COOKIE("Cookie arrow", "🍪", com.bitdroid.maps.R.drawable.ic_puck_gps),
    COUPE_ORANGE("Z coupe · orange", "🧡", com.bitdroid.maps.R.drawable.ic_car_coupe_orange),
    COUPE_PURPLE("Z coupe · purple", "💜", com.bitdroid.maps.R.drawable.ic_car_coupe_purple),
    COUPE_WHITE("Z coupe · white", "🤍", com.bitdroid.maps.R.drawable.ic_car_coupe_white),
    COUPE_BLACK("Z coupe · black", "🖤", com.bitdroid.maps.R.drawable.ic_car_coupe_black),
}

/** Everything on the Settings screen. Each value is Compose state, so the UI updates live. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // First launch + updates
    var setupDone by bool("setup_done", false)
    var autoUpdates by bool("auto_updates", true)

    // Navigation
    var voice by bool("voice", true)
    var keepScreenOn by bool("keep_screen_on", true)
    var tilt3d by bool("tilt_3d", true)
    var avoidHighways by bool("avoid_highways", false)
    var avoidTolls by bool("avoid_tolls", false)
    var avoidFerries by bool("avoid_ferries", false)

    // Voice
    var voiceSpeed by float("voice_speed", 1f)
    var voicePitch by float("voice_pitch", 1f)

    // Music
    var musicInNav by bool("music_in_nav", true)
    var musicOnMap by bool("music_on_map", false)

    // Units
    var units by choice("units", UnitChoice.AUTO)

    // Look
    var appStyle by enumPref("app_style", AppStyle.MAPS)
    var vehicle by enumPref("vehicle", Vehicle.ARROW)

    // Servers (self-hostable, so bitMaps never has to rely on anyone else)
    var routingServer by text("routing_server", Servers.VALHALLA)
    var searchServer by text("search_server", Servers.PHOTON)
    /** Optional personal TomTom key for live traffic (overrides the built-in one). */
    var tomtomKey by text("tomtom_key", "")
    var liveTraffic by bool("live_traffic", true)

    // Map layers
    var satellite by bool("layer_satellite", false)
    var trafficLayer by bool("layer_traffic", false)
    var buildings3d by bool("layer_3d", true)

    init {
        // 0.5.0: switch everyone once to the new Google Maps-style look and the Google-style arrow.
        if (!prefs.getBoolean("v050_look", false)) {
            prefs.edit().putBoolean("v050_look", true).putString("app_style", AppStyle.MAPS.name).putString("vehicle", Vehicle.ARROW.name).apply()
            appStyle = AppStyle.MAPS
            vehicle = Vehicle.ARROW
        }
        // 0.7.0: the Material You redo is the look for everyone (wallpaper colors).
        if (!prefs.getBoolean("v070_look", false)) {
            prefs.edit().putBoolean("v070_look", true).putString("app_style", AppStyle.MATERIAL_YOU.name).apply()
            appStyle = AppStyle.MATERIAL_YOU
        }
        applyUnits()
        applyServers()
    }

    fun applyServers() {
        Servers.valhalla = routingServer.ifBlank { Servers.VALHALLA }
        Servers.photon = searchServer.ifBlank { Servers.PHOTON }
    }

    val avoid get() = Avoid(avoidHighways, avoidTolls, avoidFerries)

    fun applyUnits() {
        Units.imperial = when (units) {
            UnitChoice.MILES -> true
            UnitChoice.KILOMETERS -> false
            UnitChoice.AUTO -> Locale.getDefault().country.uppercase() in setOf("US", "GB", "LR", "MM")
        }
    }

    fun resetServers() {
        routingServer = Servers.VALHALLA
        searchServer = Servers.PHOTON
    }

    private fun bool(key: String, def: Boolean) = Pref(prefs.getBoolean(key, def)) { prefs.edit().putBoolean(key, it).apply() }
    private fun text(key: String, def: String) = Pref(prefs.getString(key, def) ?: def) {
        prefs.edit().putString(key, it).apply()
        applyServers()
    }
    private inline fun <reified E : Enum<E>> enumPref(key: String, def: E) = Pref(
        runCatching { enumValueOf<E>(prefs.getString(key, def.name) ?: def.name) }.getOrDefault(def),
    ) { prefs.edit().putString(key, it.name).apply() }

    private fun float(key: String, def: Float) = Pref(prefs.getFloat(key, def)) { prefs.edit().putFloat(key, it).apply() }
    private fun choice(key: String, def: UnitChoice) = Pref(
        runCatching { UnitChoice.valueOf(prefs.getString(key, def.name) ?: def.name) }.getOrDefault(def),
    ) { prefs.edit().putString(key, it.name).apply(); applyUnits() }

    /** A saved setting backed by Compose state. */
    class Pref<T>(initial: T, private val save: (T) -> Unit) {
        private var state by mutableStateOf(initial)
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
            state = value
            save(value)
        }
    }
}
