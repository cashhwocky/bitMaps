package com.bitdroid.maps.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class UnitChoice(val label: String) { AUTO("Automatic"), MILES("Miles"), KILOMETERS("Kilometers") }

/** Everything on the Settings screen. Each value is Compose state, so the UI updates live. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Navigation
    var voice by bool("voice", true)
    var keepScreenOn by bool("keep_screen_on", true)
    var tilt3d by bool("tilt_3d", true)
    var avoidHighways by bool("avoid_highways", false)
    var avoidTolls by bool("avoid_tolls", false)
    var avoidFerries by bool("avoid_ferries", false)

    // Voice
    var voiceId by text("voice_id", "amy")
    var voiceSpeed by float("voice_speed", 1f)
    var voicePitch by float("voice_pitch", 1f)

    // Music
    var musicInNav by bool("music_in_nav", true)
    var musicOnMap by bool("music_on_map", false)

    // Units
    var units by choice("units", UnitChoice.AUTO)

    // Servers (self-hostable, so bitMaps never has to rely on anyone else)
    var routingServer by text("routing_server", Servers.VALHALLA)
    var searchServer by text("search_server", Servers.PHOTON)

    init {
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
