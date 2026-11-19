package com.bitdroid.maps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.maps.data.Geo
import com.bitdroid.maps.data.Place
import com.bitdroid.maps.data.Units
import java.text.DateFormat
import java.util.Date

// The "bitMaps" look: Google Maps-style layout (search card, chips, buttons, bottom sheet)
// in bitMaps' dark violet colors.

private fun dist(vm: AppViewModel, p: Place): String? = vm.here()?.let { Units.short(Geo.distance(it, p.pos)) }

@Composable
private fun Fab(label: String, size: Int = 52, bg: Color = C.Sheet, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun BoxScope.ExploreMaps(vm: AppViewModel) {
    val context = LocalContext.current
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::voiceQuery)
    }

    // ---- search card + chips ----
    Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).fillMaxWidth()) {
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(54.dp)
                .shadow(6.dp, RoundedCornerShape(27.dp))
                .clip(RoundedCornerShape(27.dp))
                .background(C.Sheet)
                .clickable(onClickLabel = "Search") { vm.openSearch() }
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Place, null, tint = C.Accent, modifier = Modifier.size(24.dp))
            Text("Search here", color = C.Dim, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Search by voice") {
                    val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    runCatching { voice.launch(i) }.onFailure {
                        android.widget.Toast.makeText(context, "No voice typing app on this phone", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Mic, "Search by voice", tint = C.Text, modifier = Modifier.size(22.dp)) }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(C.AccentDeep).clickable(role = Role.Button, onClickLabel = "Settings") { vm.openSettings() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Settings, "Settings", tint = C.OnAccentDeep, modifier = Modifier.size(20.dp)) }
        }
        Row(
            Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("🏠", "Home") { if (vm.home != null) vm.goHome() else vm.openSearch() }
            categories.forEach { c -> Chip(placeEmoji(c.query).first, c.label) { vm.searchCategory(c) } }
        }
    }

    // ---- bottom sheet + buttons above it ----
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Column(
            Modifier.align(Alignment.End).padding(end = 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Fab("Center on my location", onClick = { vm.recenter() }) {
                Icon(Icons.Rounded.MyLocation, "Center on my location", tint = C.Accent, modifier = Modifier.size(24.dp))
            }
            Box(
                Modifier
                    .size(58.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.Accent)
                    .clickable(role = Role.Button, onClickLabel = "Directions") { if (vm.home != null) vm.goHome() else vm.openSearch() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Directions, "Directions", tint = C.OnAccent, modifier = Modifier.size(28.dp)) }
        }
        if (vm.settings.musicOnMap) {
            val now by vm.music.now.collectAsState()
            now?.let { MusicBar(vm, it, Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) }
        }
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, shape)
                .clip(shape)
                .background(C.Sheet)
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(C.SurfaceHigh))
            Spacer(Modifier.height(12.dp))
            vm.update?.let { u ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(C.AccentDeep)
                        .clickable(role = Role.Button) { vm.showUpdate = true }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎉 bitMaps ${u.version} is here", color = C.OnAccentDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("Update", color = C.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Text("Recent", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            val h = vm.home
            if (h != null) {
                SheetRow("🏠", C.AccentDeep, "Home", listOfNotNull(h.name, vm.homeMinutes?.let { if (it == 0) "You're here" else "$it min" }).joinToString(" · ")) { vm.goHome() }
            }
            val list = vm.recents.filter { h == null || !vm.isHome(it) }.take(if (h != null) 2 else 3)
            if (list.isEmpty() && h == null) {
                Text(
                    if (vm.location == null) "Finding your location…" else "Search for a place, or long-press the map to drop a pin.",
                    color = C.Dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            list.forEach { p ->
                SheetRow(placeEmoji(p.kind, p.name).first, C.Surface, p.name, listOfNotNull(p.category.ifBlank { null }, dist(vm, p)).joinToString(" · ")) { vm.open(p) }
            }
        }
    }
}

@Composable
private fun Chip(emoji: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(C.Sheet)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 15.sp)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SheetRow(emoji: String, circle: Color, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = C.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- Navigation ----------------

private val BannerColor = Color(0xFF3E2F78)
private val BannerStrip = Color(0xFF2F2360)

@Composable
fun BoxScope.NavMaps(vm: AppViewModel) {
    val p = vm.progress
    val now by vm.music.now.collectAsState()

    Column(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- turn banner ----
        Column(Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(BannerColor)) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    if (p?.arrived == true) maneuverIcon(4) else maneuverIcon(p?.maneuver?.type ?: 1),
                    null, tint = Color.White, modifier = Modifier.size(48.dp),
                )
                Column(Modifier.weight(1f)) {
                    when {
                        p == null -> Text("Getting your location…", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        p.arrived -> {
                            Text("You've arrived", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            vm.place?.let { Text(it.name, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp) }
                        }
                        else -> {
                            Text(Units.short(p.toManeuverM), color = Color.White.copy(alpha = 0.9f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                p.maneuver.street ?: p.maneuver.instruction.removeSuffix("."),
                                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            p?.next?.takeIf { !p.arrived && !p.maneuver.isArrival }?.let { n ->
                Row(
                    Modifier.fillMaxWidth().background(BannerStrip).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Then", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Icon(maneuverIcon(n.type), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(n.street ?: "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // ---- music pill ----
        val np = now
        if (vm.settings.musicInNav && np != null) {
            Row(
                Modifier.shadow(6.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(C.Sheet).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val art = np.art
                if (art != null) {
                    androidx.compose.foundation.Image(
                        art.asImageBitmap(), "Album art",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                } else {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(C.AccentDeep), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 18.sp) }
                }
                Column(Modifier.width(150.dp)) {
                    Text(np.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(np.artist.ifBlank { np.app }, fontSize = 12.sp, color = C.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(C.Accent)
                        .clickable(role = Role.Button, onClickLabel = if (np.playing) "Pause" else "Play") { vm.music.playPause() },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.playing) "Pause" else "Play", tint = C.OnAccent, modifier = Modifier.size(22.dp)) }
            }
        }
    }

    // ---- right buttons ----
    Column(
        Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Fab(if (vm.muted) "Unmute voice" else "Mute voice", size = 48, onClick = { vm.toggleMute() }) {
            Icon(if (vm.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = C.Text, modifier = Modifier.size(22.dp))
        }
        if (vm.following) {
            Fab("Route overview", size = 48, onClick = { vm.overview() }) { Icon(Icons.Rounded.Route, null, tint = C.Text, modifier = Modifier.size(22.dp)) }
        } else {
            Fab("Recenter", size = 48, onClick = { vm.recenter() }) { Icon(Icons.Rounded.MyLocation, null, tint = C.Accent, modifier = Modifier.size(22.dp)) }
        }
    }

    // ---- speed + bottom sheet ----
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        if (p != null) {
            Box(
                Modifier.padding(start = 14.dp, bottom = 12.dp).size(60.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Units.speed(p.speedMps), color = Color(0xFF1B1A22), fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                    Text(Units.speedUnit, color = Color(0xFF5A5568), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, shape)
                .clip(shape)
                .background(C.Sheet)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(C.Surface)
                    .clickable(role = Role.Button, onClickLabel = "End navigation") { vm.end() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "End navigation", tint = C.Text, modifier = Modifier.size(26.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (p != null) {
                    // Green when traffic is light, orange when it's adding time (like Google Maps).
                    val delay = vm.routes.firstOrNull()?.trafficDelayS ?: 0.0
                    val etaColor = if (delay >= 300) Color(0xFFFCAD70) else C.Green
                    Text(Units.duration(p.remainingS), color = etaColor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    val arrive = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (p.remainingS * 1000).toLong()))
                    Text("${Units.short(p.remainingM)} · $arrive", color = C.Dim, fontSize = 14.sp)
                } else {
                    Text(vm.place?.name ?: "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.size(48.dp))
        }
    }
}

