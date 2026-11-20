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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.maps.data.Geo
import com.bitdroid.maps.data.Place
import com.bitdroid.maps.data.Units
import dev.chrisbanes.haze.HazeState
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
fun BoxScope.ExploreMaps(vm: AppViewModel, haze: HazeState) {
    val context = LocalContext.current
    var savedOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val tab = when { vm.driveMode -> 2; savedOpen -> 1; else -> 0 }
    androidx.activity.compose.BackHandler(savedOpen || vm.driveMode) { savedOpen = false; vm.stopDrive() }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::voiceQuery)
    }

    // ---- glass search card + chips ----
    Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).fillMaxWidth()) {
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(56.dp)
                .squish { vm.openSearch() }
                .frosted(haze, CircleShape)
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Place, null, tint = GlassSelected, modifier = Modifier.size(24.dp))
            Text("Search here", color = Color(0xFFD0D0D8), fontSize = 16.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Search by voice") {
                    val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    runCatching { voice.launch(i) }.onFailure {
                        android.widget.Toast.makeText(context, "No voice typing app on this phone", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Mic, "Search by voice", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        if (!vm.driveMode) Row(
            Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(haze, "🏠", vm.homeMinutes?.let { if (it == 0) "Home" else "Home · $it min" } ?: "Home") { if (vm.home != null) vm.goHome() else vm.openSearch() }
            categories.forEach { c -> Chip(haze, placeEmoji(c.query).first, c.label) { vm.searchCategory(c) } }
        }
    }

    // ---- glass buttons, music, and bitDialer's dock ----
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        if (tab == 0) Column(
            Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassFab(haze, "Center on my location", onClick = { vm.recenter() }) {
                Icon(Icons.Rounded.MyLocation, null, tint = GlassSelected, modifier = Modifier.size(24.dp))
            }
            // Directions: violet-tinted glass.
            Box(
                Modifier
                    .size(58.dp)
                    .squish { if (vm.home != null) vm.goHome() else vm.openSearch() }
                    .frosted(haze, RoundedCornerShape(20.dp), tint = C.Accent, alpha = 0.6f),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Directions, "Directions", tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
        androidx.compose.animation.AnimatedVisibility(
            tab == 1,
            enter = androidx.compose.animation.slideInVertically { it / 3 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it / 3 } + androidx.compose.animation.fadeOut(),
        ) { SavedPanel(vm, haze) { savedOpen = false } }
        androidx.compose.animation.AnimatedVisibility(
            tab == 2,
            enter = androidx.compose.animation.slideInVertically { it / 3 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it / 3 } + androidx.compose.animation.fadeOut(),
        ) { DrivePanel(vm, haze) }
        if (tab == 0) vm.update?.let { u ->
            Row(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth().height(48.dp)
                    .squish { vm.showUpdate = true }
                    .frosted(haze, CircleShape, tint = C.Accent, alpha = 0.45f)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🎉 bitMaps ${u.version} is here", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Update", color = GlassSelected, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        if (vm.settings.musicOnMap) {
            val now by vm.music.now.collectAsState()
            now?.let { MusicBar(vm, it, Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), haze) }
        }
        GlassDock(
            haze = haze,
            tabs = listOf(
                TabItem(Icons.Rounded.Explore, "Explore"),
                TabItem(Icons.Rounded.Bookmark, "Saved"),
                TabItem(Icons.Rounded.DirectionsCar, "Drive"),
                TabItem(Icons.Rounded.Settings, "Settings"),
            ),
            selected = tab,
            onSelect = { i ->
                when (i) {
                    0 -> { savedOpen = false; vm.stopDrive() }
                    1 -> { vm.stopDrive(); savedOpen = true }
                    2 -> { savedOpen = false; vm.startDrive() }
                    3 -> { savedOpen = false; vm.stopDrive(); vm.openSettings() }
                }
            },
            onSearch = { vm.openSearch() },
        )
    }
}

@Composable
private fun Chip(haze: HazeState, emoji: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(38.dp)
            .squish(onClick = onClick)
            .frosted(haze, CircleShape)
            .semantics { contentDescription = label; role = Role.Button }
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 15.sp)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
    }
}

/** Saved tab: home with a live drive time, then your recent places with how far away they are. */
@Composable
private fun SavedPanel(vm: AppViewModel, haze: HazeState, close: () -> Unit) {
    val me = vm.here()
    Column(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).fillMaxWidth()
            .frosted(haze, RoundedCornerShape(30.dp), tint = Color(0xFF2E2842), alpha = 0.82f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Saved", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        val h = vm.home
        SheetRow(
            "🏠", Color(0xFFCBC2EB),
            if (h != null && me != null && Geo.distance(me, h.pos) < 60) "You're home" else "Home",
            when {
                h == null -> "Tap to set your home"
                vm.homeMinutes == 0 -> h.detail.ifBlank { h.name }
                vm.homeMinutes != null -> "${vm.homeMinutes} min drive · ${h.detail.ifBlank { h.name }}"
                else -> h.detail.ifBlank { h.name }
            },
        ) { close(); if (h != null) vm.goHome() else vm.openSearch() }
        val recent = vm.recents.filter { r -> h == null || Geo.distance(r.pos, h.pos) > 25 }.take(5)
        if (recent.isNotEmpty()) {
            Text("Recent", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBC2EB), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            recent.forEach { r ->
                val (emoji, circle) = placeEmoji(r.kind, r.name)
                val away = me?.let { Units.short(Geo.distance(it, r.pos)) }
                SheetRow(emoji, circle, r.name, listOfNotNull(away, r.detail.ifBlank { null }).joinToString(" · ")) { close(); vm.open(r) }
            }
        } else {
            Text("Places you look up show up here.", fontSize = 13.sp, color = Color(0xFFB8B0D0), modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Drive tab: no destination, just drive. Camera follows you tilted; big speed, the street you're on, your heading. */
@Composable
private fun DrivePanel(vm: AppViewModel, haze: HazeState) {
    val loc = vm.location
    val mps = if (loc != null && loc.hasSpeed()) loc.speed.toDouble() else 0.0
    val speed = Math.round(if (Units.imperial) mps * 2.23694 else mps * 3.6).toInt()
    val heading = if (loc != null && loc.hasBearing() && mps > 1.5) listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((loc.bearing + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)] else null
    Column(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).fillMaxWidth()
            .frosted(haze, RoundedCornerShape(30.dp), tint = Color(0xFF2E2842), alpha = 0.82f)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$speed", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFF241C4A), lineHeight = 30.sp)
                    Text(if (Units.imperial) "mph" else "km/h", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5280))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("🚗 Driving on", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBC2EB))
                Text(vm.driveStreet ?: "Finding your road…", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(heading?.let { "Heading $it" }, vm.hereName).joinToString(" · ").ifBlank { "Just drive — no destination needed" }, fontSize = 13.sp, color = Color(0xFFB8B0D0), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFE5484D).copy(alpha = 0.85f)).squish { vm.stopDrive() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Stop driving", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        if (vm.home != null && vm.homeMinutes != 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(Color(0xFFCBC2EB))
                    .squish { vm.stopDrive(); vm.goHome() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🏠  Take me home" + (vm.homeMinutes?.let { " · $it min" } ?: ""), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF241C4A))
            }
        }
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
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = Color(0xFFB8B0D0), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- Navigation ----------------

private val BannerColor = Color(0xFF3E2F78)
private val BannerStrip = Color(0xFF2F2360)

@Composable
fun BoxScope.NavMaps(vm: AppViewModel, haze: HazeState) {
    val p = vm.progress
    val now by vm.music.now.collectAsState()

    Column(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- turn banner ----
        // Violet-tinted glass turn banner.
        Column(Modifier.fillMaxWidth().frosted(haze, RoundedCornerShape(24.dp), tint = BannerColor, alpha = 0.78f)) {
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
                    Modifier.fillMaxWidth().background(BannerStrip.copy(alpha = 0.45f)).padding(horizontal = 18.dp, vertical = 10.dp),
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
                Modifier.frosted(haze, RoundedCornerShape(28.dp)).padding(6.dp),
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
        GlassFab(haze, if (vm.muted) "Unmute voice" else "Mute voice", size = 50, onClick = { vm.toggleMute() }) {
            Icon(if (vm.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        if (vm.following) {
            GlassFab(haze, "Route overview", size = 50, onClick = { vm.overview() }) { Icon(Icons.Rounded.Route, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        } else {
            GlassFab(haze, "Recenter", size = 50, onClick = { vm.recenter() }) { Icon(Icons.Rounded.MyLocation, null, tint = GlassSelected, modifier = Modifier.size(22.dp)) }
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
        // Floating glass pill (same glass as the dock).
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
                .frosted(haze, RoundedCornerShape(36.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFE5484D).copy(alpha = 0.85f))
                    .squish { vm.end() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "End navigation", tint = Color.White, modifier = Modifier.size(26.dp)) }
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
            Spacer(Modifier.size(52.dp))
        }
    }
}

