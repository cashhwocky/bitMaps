package com.bitdroid.maps.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bitdroid.maps.data.UnitChoice

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Re-check notification access when coming back from Android's settings screen.
    var musicAccess by remember { mutableStateOf(vm.music.hasAccess()) }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            musicAccess = vm.music.hasAccess()
            vm.refreshMusicAccess()
        }
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back") { vm.back() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.Text) }
            Text("Settings", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(start = 6.dp))
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Group("🧭", "Navigation") {
                Toggle("🔊", "Voice guidance", "Spoken turn-by-turn directions", s.voice) { s.voice = it }
                Box(Modifier.clickable(role = Role.Button) { vm.openVoice() }) {
                    Item("🗣️", "Voice", "Speed, pitch and test") {
                        Text("›", fontSize = 24.sp, color = C.Dim, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                Toggle("💡", "Keep screen on", "While you're navigating", s.keepScreenOn) { s.keepScreenOn = it }
                Toggle("🏙️", "3D view", "Tilt the map while navigating", s.tilt3d) { s.tilt3d = it }
            }

            Group("🛣️", "Route options") {
                Toggle("🚀", "Avoid highways", null, s.avoidHighways) { s.avoidHighways = it }
                Toggle("💸", "Avoid tolls", null, s.avoidTolls) { s.avoidTolls = it }
                Toggle("⛴️", "Avoid ferries", null, s.avoidFerries) { s.avoidFerries = it }
            }

            Group("🎵", "Music") {
                Toggle("🎧", "Music while navigating", "Song and controls inside the turn card", s.musicInNav) {
                    s.musicInNav = it
                    vm.refreshMusicAccess()
                }
                Toggle("🗺️", "Music on the map", "A player above the Recent list", s.musicOnMap) {
                    s.musicOnMap = it
                    vm.refreshMusicAccess()
                }
                Item(
                    "🔔", "Notification access",
                    if (musicAccess) "On: bitMaps can see what's playing" else "Needed to see what's playing. Your notifications are never read or saved.",
                ) {
                    if (musicAccess) Pill("✅ On", C.Green, C.Green.copy(alpha = 0.14f))
                    else SmallButton("Allow") {
                        runCatching { context.startActivity(vm.music.accessIntent()) }
                            .onFailure { runCatching { context.startActivity(vm.music.fallbackAccessIntent()) } }
                    }
                }
            }

            Group("📏", "Units") {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnitChoice.entries.forEach { u ->
                        val on = s.units == u
                        val shape = RoundedCornerShape(16.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(shape)
                                .background(if (on) C.gradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.Surface, C.Surface)))
                                .border(1.dp, if (on) Color.Transparent else C.Outline, shape)
                                .clickable(role = Role.RadioButton) { s.units = u },
                            contentAlignment = Alignment.Center,
                        ) { Text(u.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) C.OnAccent else C.Text) }
                    }
                }
            }

            Group("🗂️", "Your data") {
                Item("🕘", "Recent places", "${vm.recents.size} saved on this phone only") {
                    SmallButton("Clear") { vm.clearRecents() }
                }
            }

            Group("🖥️", "Servers") {
                Text(
                    "bitMaps uses free community servers. You can point it at your own (Valhalla for routes, Photon for search) at any time.",
                    color = C.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
                )
                ServerField("🛣️ Routing (Valhalla)", s.routingServer) { s.routingServer = it }
                ServerField("🔎 Search (Photon)", s.searchServer) { s.searchServer = it }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    SmallButton("Reset to defaults") { s.resetServers() }
                }
            }

            Group("🔄", "Updates") {
                Toggle("🔔", "Check automatically", "Get notified when a new version is out", s.autoUpdates) { vm.setAutoUpdates(it) }
                Item("⬆️", "bitMaps $version", vm.updateMessage ?: vm.update?.let { "Version ${it.version} is available" } ?: "Check GitHub for a newer version") {
                    if (vm.update != null) SmallButton("Update") { vm.showUpdate = true }
                    else SmallButton(if (vm.checkingUpdate) "Checking…" else "Check") { vm.checkForUpdate(manual = true) }
                }
            }

            Group("ℹ️", "About") {
                Item("🗺️", "bitMaps $version", "Made by Alex Desrosier for bitDroid") {}
                Item("🧱", "Map data", "© OpenStreetMap contributors · tiles by OpenFreeMap") {
                    SmallButton("View") { open(context, "https://www.openstreetmap.org/copyright") }
                }
                Item("🔎", "Search", "Photon by Komoot") {}
                Item("🛣️", "Routing", "Valhalla · FOSSGIS community server") {}
                Item("💻", "Source code", "github.com/cashhwocky/bitMaps") {
                    SmallButton("Open") { open(context, "https://github.com/cashhwocky/bitMaps") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun open(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun Group(emoji: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text("$emoji  $title", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(C.Sheet).border(1.dp, C.Outline, shape).padding(horizontal = 16.dp, vertical = 6.dp),
            content = content,
        )
    }
}

@Composable
private fun Item(emoji: String, title: String, subtitle: String?, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(C.Surface), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 19.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = C.Dim)
        }
        trailing()
    }
}

@Composable
private fun Toggle(emoji: String, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.clickable(role = Role.Switch) { onChange(!checked) }) {
        Item(emoji, title, subtitle) {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = C.OnAccent,
                    checkedTrackColor = C.Violet,
                    uncheckedThumbColor = C.Dim,
                    uncheckedTrackColor = C.Surface,
                    uncheckedBorderColor = C.Hairline,
                ),
            )
        }
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = C.OnAccent,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C.gradient)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun ServerField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, fontSize = 13.sp, color = C.Dim, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it; onChange(it.trim()) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Body, fontSize = 14.sp, color = C.Text),
            cursorBrush = SolidColor(C.Violet),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
