@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.bitdroid.maps.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.maps.data.Geo
import com.bitdroid.maps.data.Mode
import com.bitdroid.maps.data.Place
import com.bitdroid.maps.data.Units
import java.text.DateFormat
import java.util.Date

// bitMaps, Material You: real Material 3 components and tonal colors from the wallpaper,
// laid out like Google Maps (search on top, sheet at the bottom, bottom navigation bar).

private val Green = Color(0xFF8CDBA0)
private val Orange = Color(0xFFFFB454)

private fun categoryIcon(label: String): ImageVector = when (label) {
    "Gas" -> Icons.Rounded.LocalGasStation
    "Coffee" -> Icons.Rounded.LocalCafe
    "Food" -> Icons.Rounded.Restaurant
    "Groceries" -> Icons.Rounded.ShoppingCart
    "Parking" -> Icons.Rounded.LocalParking
    "Charging" -> Icons.Rounded.EvStation
    else -> Icons.Rounded.Search
}

private fun arriveAt(seconds: Double): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (seconds * 1000).toLong()))

private fun away(vm: AppViewModel, p: Place): String? = vm.here()?.let { Units.short(Geo.distance(it, p.pos)) }

// ======================================================================
// Explore + bottom navigation (Explore · Saved · Drive · You)
// ======================================================================

@Composable
fun BoxScope.HomeM3(vm: AppViewModel) {
    when (vm.tab) {
        0 -> ExploreTab(vm)
        1 -> SavedTab(vm)
        2 -> DriveTab(vm)
        3 -> YouTab(vm)
    }
    Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(vm) }
    if (vm.showLayers) LayersSheet(vm)
}

@Composable
private fun BottomBar(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    NavigationBar(containerColor = cs.surfaceContainer, tonalElevation = 0.dp) {
        val items = listOf(
            Triple("Explore", Icons.Rounded.Explore, Icons.Rounded.Explore),
            Triple("Saved", Icons.Rounded.Bookmark, Icons.Rounded.BookmarkBorder),
            Triple("Drive", Icons.Rounded.DirectionsCar, Icons.Rounded.DirectionsCar),
            Triple("You", Icons.Rounded.Person, Icons.Rounded.Person),
        )
        items.forEachIndexed { i, (label, on, off) ->
            NavigationBarItem(
                selected = vm.tab == i,
                onClick = { vm.selectTab(i) },
                icon = { Icon(if (vm.tab == i) on else off, label) },
                label = { Text(label, fontWeight = if (vm.tab == i) FontWeight.Bold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = cs.onPrimary,
                    indicatorColor = cs.primary,
                    selectedTextColor = cs.onSurface,
                    unselectedIconColor = cs.onSurfaceVariant,
                    unselectedTextColor = cs.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun SearchPill(vm: AppViewModel, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::voiceQuery)
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .clickable(onClickLabel = "Search") { vm.openSearch() }
            .padding(start = 9.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Navigation, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp))
        }
        Text("Search bitMaps", color = cs.onSurfaceVariant, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(42.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Search by voice") {
                val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Where to?")
                runCatching { voice.launch(i) }.onFailure {
                    android.widget.Toast.makeText(context, "No voice typing app on this phone", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Mic, "Search by voice", tint = cs.onSurfaceVariant) }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(cs.tertiaryContainer).clickable(role = Role.Button, onClickLabel = "Map layers") { vm.showLayers = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Layers, "Map layers", tint = cs.onTertiaryContainer, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun CategoryChips(vm: AppViewModel, onPick: (Category) -> Unit = { vm.searchCategory(it) }) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { c ->
            Row(
                Modifier
                    .height(38.dp)
                    .shadow(3.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceContainerHighest)
                    .clickable(role = Role.Button, onClickLabel = c.label) { onPick(c) }
                    .padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(categoryIcon(c.label), null, tint = cs.onSecondaryContainer, modifier = Modifier.size(18.dp))
                Text(c.label, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BoxScope.ExploreTab(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val sheetH by animateDpAsState(if (expanded) screenH * 0.78f else 330.dp, spring(dampingRatio = 0.85f, stiffness = 400f), label = "sheet")
    BackHandler(expanded) { expanded = false }

    Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchPill(vm, Modifier.padding(horizontal = 14.dp))
        CategoryChips(vm)
    }

    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        AnimatedVisibility(!expanded, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.End)) {
            Box(
                Modifier
                    .padding(end = 16.dp, bottom = 14.dp)
                    .size(58.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(cs.primaryContainer)
                    .clickable(role = Role.Button, onClickLabel = "Center on my location") { vm.recenter() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MyLocation, "Center on my location", tint = cs.onPrimaryContainer) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(sheetH)
                .shadow(16.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(cs.surfaceContainerLow),
        ) {
            // Drag handle: swipe up/down or tap to expand.
            Box(
                Modifier.fillMaxWidth().height(26.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dy -> if (dy < -6) expanded = true else if (dy > 6) expanded = false }
                    }
                    .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(cs.outlineVariant)) }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ExploreSheetContent(vm)
            }
        }
    }
}

@Composable
private fun ColumnScope.ExploreSheetContent(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(vm.greeting(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Text(
            listOfNotNull(vm.hereName ?: if (vm.location == null) "Finding you…" else null, vm.homeMinutes?.let { if (it == 0) "you're home" else "home in $it min" }).joinToString(" · "),
            fontSize = 14.sp, color = cs.onSurfaceVariant,
        )
    }
    Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val h = vm.home
        val w = vm.work
        Shortcut(
            Icons.Rounded.Home, if (h != null && vm.homeMinutes == 0) "You're home" else "Home",
            when { h == null -> "Tap to set"; vm.homeMinutes == 0 -> "Welcome back"; vm.homeMinutes != null -> "${vm.homeMinutes} min"; else -> away(vm, h) ?: "" },
            cs.primary, cs.onPrimary, Modifier.weight(1.2f),
        ) { if (h != null) vm.goHome() else vm.openSearch() }
        Shortcut(
            Icons.Rounded.Work, "Work",
            when { w == null -> "Tap to set"; vm.workMinutes == 0 -> "You're here"; vm.workMinutes != null -> "${vm.workMinutes} min"; else -> away(vm, w) ?: "" },
            cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f),
        ) { if (w != null) vm.goWork() else vm.openSearch() }
    }
    vm.update?.let { u ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.tertiaryContainer)
                .clickable { vm.showUpdate = true }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = cs.onTertiaryContainer)
            Text("bitMaps ${u.version} is ready", color = cs.onTertiaryContainer, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Update", color = cs.onTertiaryContainer, fontWeight = FontWeight.Bold)
        }
    }
    Text("Nearby", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val tones = listOf(
            cs.primaryContainer to cs.onPrimaryContainer,
            cs.tertiaryContainer to cs.onTertiaryContainer,
            cs.secondaryContainer to cs.onSecondaryContainer,
        )
        categories.forEachIndexed { i, c ->
            val (bg, fg) = tones[i % tones.size]
            Column(
                Modifier.size(width = 118.dp, height = 104.dp).clip(RoundedCornerShape(24.dp)).background(bg)
                    .clickable(role = Role.Button, onClickLabel = c.label) { vm.searchCategory(c) }.padding(14.dp),
            ) {
                Icon(categoryIcon(c.label), null, tint = fg, modifier = Modifier.size(28.dp))
                Spacer(Modifier.weight(1f))
                Text(c.label, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Near you", color = fg.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }
    }
    if (vm.settings.musicOnMap) {
        val now by vm.music.now.collectAsState()
        now?.let { MusicBar(vm, it) }
    }
    if (vm.recents.isNotEmpty()) {
        Text("Recent", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Column {
            vm.recents.take(8).forEach { p -> PlaceListRow(vm, p) { vm.open(p) } }
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, title: String, sub: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(bg).clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(28.dp))
        Column {
            Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            if (sub.isNotBlank()) Text(sub, color = fg.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaceListRow(vm: AppViewModel, p: Place, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val (emoji, _) = placeEmoji(p.kind, p.name)
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
        Column(Modifier.weight(1f)) {
            Text(p.name, color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(away(vm, p), p.detail.ifBlank { p.category.ifBlank { null } }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = cs.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

// ---------------- Saved ----------------

@Composable
private fun SavedTab(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Saved", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SavedSpot(vm, "Home", Icons.Rounded.Home, vm.home, vm.homeMinutes, cs.primary, cs.onPrimary, Modifier.weight(1f), onClear = { vm.saveHome(null) }) { vm.goHome() }
            SavedSpot(vm, "Work", Icons.Rounded.Work, vm.work, vm.workMinutes, cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f), onClear = { vm.saveWork(null) }) { vm.goWork() }
        }
        Text("Starred", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        if (vm.saved.isEmpty()) {
            EmptyCard(Icons.Rounded.BookmarkBorder, "Nothing starred yet", "Open any place and tap Save to keep it here.")
        } else {
            vm.saved.forEach { p ->
                PlaceListRow(vm, p, trailing = {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Remove") { vm.toggleSaved(p) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Bookmark, "Remove", tint = cs.primary) }
                }) { vm.open(p) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Recent", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (vm.recents.isNotEmpty()) TextButton(onClick = { vm.clearRecents() }) { Text("Clear") }
        }
        if (vm.recents.isEmpty()) EmptyCard(Icons.Rounded.History, "No history", "Places you look up show up here.")
        vm.recents.forEach { p -> PlaceListRow(vm, p) { vm.open(p) } }
    }
}

@Composable
private fun SavedSpot(
    vm: AppViewModel, label: String, icon: ImageVector, p: Place?, minutes: Int?, bg: Color, fg: Color, modifier: Modifier,
    onClear: () -> Unit, onGo: () -> Unit,
) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(bg)
            .clickable(role = Role.Button, onClickLabel = label) { if (p != null) onGo() else vm.openSearch() }.padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = fg)
            Spacer(Modifier.weight(1f))
            if (p != null) Icon(Icons.Rounded.Close, "Clear $label", tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClear))
        }
        Spacer(Modifier.weight(1f))
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            when { p == null -> "Search it, then tap Set as $label"; minutes == 0 -> "You're here"; minutes != null -> "$minutes min drive"; else -> p.detail.ifBlank { p.name } },
            color = fg.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyCard(icon: ImageVector, title: String, sub: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainer).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = cs.onSurfaceVariant)
        Column {
            Text(title, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
            Text(sub, color = cs.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

// ---------------- Drive (no destination) ----------------

@Composable
private fun BoxScope.DriveTab(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val loc = vm.location
    val mps = if (loc != null && loc.hasSpeed()) loc.speed.toDouble() else 0.0
    val speed = Math.round(if (Units.imperial) mps * 2.23694 else mps * 3.6).toInt()
    val heading = if (loc != null && loc.hasBearing() && mps > 1.5) listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((loc.bearing + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)] else null

    Column(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(10.dp).fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(cs.primary).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("You're on", color = cs.onPrimary.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(vm.driveStreet ?: "Finding your road…", color = cs.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(heading?.let { "Heading $it" }, vm.hereName).joinToString(" · ").ifBlank { "Just drive — no destination needed" },
            color = cs.onPrimary.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }

    Column(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp).navigationBarsPadding().padding(horizontal = 12.dp).fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(cs.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SpeedBubble(speed, 76.dp)
            Column(Modifier.weight(1f)) {
                Text("Free drive", color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("The map follows you. Pick a shortcut anytime.", color = cs.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            vm.home?.let { DriveAction(Icons.Rounded.Home, "Home" + (vm.homeMinutes?.takeIf { it > 0 }?.let { " · $it min" } ?: ""), true) { vm.goHome() } }
            vm.work?.let { DriveAction(Icons.Rounded.Work, "Work" + (vm.workMinutes?.takeIf { it > 0 }?.let { " · $it min" } ?: ""), vm.home == null) { vm.goWork() } }
            categories.firstOrNull { it.label == "Gas" }?.let { c -> DriveAction(Icons.Rounded.LocalGasStation, "Gas nearby", false) { vm.selectTab(0); vm.searchCategory(c) } }
            categories.firstOrNull { it.label == "Coffee" }?.let { c -> DriveAction(Icons.Rounded.LocalCafe, "Coffee", false) { vm.selectTab(0); vm.searchCategory(c) } }
        }
    }
}

@Composable
private fun DriveAction(icon: ImageVector, label: String, primary: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(46.dp).clip(CircleShape).background(if (primary) cs.primary else cs.secondaryContainer)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (primary) cs.onPrimary else cs.onSecondaryContainer, modifier = Modifier.size(20.dp))
        Text(label, color = if (primary) cs.onPrimary else cs.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun SpeedBubble(speed: Int, size: Dp) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(cs.surfaceContainerHighest),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$speed", color = cs.onSurface, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold, lineHeight = (size.value * 0.38f).sp)
        Text(if (Units.imperial) "mph" else "km/h", color = cs.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------- You ----------------

@Composable
private fun YouTab(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    val (trips, meters, seconds) = vm.stats
    Column(
        Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("You", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Text("Your driving, kept on this phone only.", color = cs.onSurfaceVariant, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("$trips", if (trips == 1) "trip" else "trips", cs.primaryContainer, cs.onPrimaryContainer, Modifier.weight(1f))
            Stat(Units.short(meters).substringBefore(' '), Units.short(meters).substringAfter(' ', "mi") + " driven", cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(if (seconds >= 3600) String.format(java.util.Locale.US, "%.1f", seconds / 3600) else "${(seconds / 60).toInt()}", if (seconds >= 3600) "hours on the road" else "minutes on the road", cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f))
            Stat("${vm.saved.size}", "saved places", cs.surfaceContainerHighest, cs.onSurface, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Group {
            SettingRow(Icons.Rounded.Settings, "Settings", "Route options, music, units, look") { vm.openSettings() }
            SettingRow(Icons.Rounded.RecordVoiceOver, "Voice guidance", "Speed and pitch of the voice") { vm.openVoice() }
            SettingRow(Icons.Rounded.Layers, "Map layers", "Satellite, live traffic, 3D buildings") { vm.showLayers = true }
        }
        Group {
            SettingRow(
                Icons.Rounded.SystemUpdate,
                if (vm.update != null) "Update to ${vm.update?.version}" else "Check for updates",
                when { vm.checkingUpdate -> "Checking…"; vm.updateMessage != null -> vm.updateMessage!!; else -> "You're on $version" },
            ) { if (vm.update != null) vm.showUpdate = true else vm.checkForUpdate(true) }
            SettingRow(Icons.Rounded.Info, "About bitMaps", "Free maps for bitDroid · OpenStreetMap, TomTom traffic") {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/cashhwocky/bitMaps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.height(104.dp).clip(RoundedCornerShape(26.dp)).background(bg).padding(16.dp), verticalArrangement = Arrangement.Bottom) {
        Text(value, color = fg, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = fg.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer), content = content)
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(sub, color = cs.onSurfaceVariant, fontSize = 13.sp, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
    }
}

// ---------------- Map layers ----------------

@Composable
private fun LayersSheet(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val hasKey = vm.searchKey() != null
    ModalBottomSheet(
        onDismissRequest = { vm.showLayers = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Map type", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapTypeCard("Default", Icons.Rounded.Map, !vm.settings.satellite, Modifier.weight(1f)) { vm.setLayer(satellite = false) }
                MapTypeCard("Satellite", Icons.Rounded.SatelliteAlt, vm.settings.satellite, Modifier.weight(1f), enabled = hasKey) { vm.setLayer(satellite = true) }
            }
            Text("Details", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface, modifier = Modifier.padding(top = 6.dp))
            ToggleRow(Icons.Rounded.Traffic, "Live traffic", if (hasKey) "Color every road by how fast it's moving" else "Needs the TomTom key", vm.settings.trafficLayer, hasKey) { vm.setLayer(traffic = it) }
            ToggleRow(Icons.Rounded.ViewInAr, "3D buildings", "When you zoom in close", vm.settings.buildings3d, true) { vm.setLayer(buildings = it) }
        }
    }
}

@Composable
private fun MapTypeCard(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(22.dp))
            .background(if (on) cs.primaryContainer else cs.surfaceContainerHigh)
            .border(2.dp, if (on) cs.primary else Color.Transparent, RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(32.dp))
        Text(label, color = if (on) cs.onPrimaryContainer else cs.onSurface.copy(alpha = if (enabled) 1f else 0.4f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, sub: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = cs.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(sub, color = cs.onSurfaceVariant, fontSize = 13.sp)
        }
        Switch(checked = on && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}

// ======================================================================
// Place
// ======================================================================

@Composable
fun BoxScope.PlaceM3(vm: AppViewModel) {
    val p = vm.place ?: return
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(14.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { vm.back() }
        RoundIcon(Icons.Rounded.Share, "Share") {
            val link = "https://www.openstreetmap.org/?mlat=${p.lat}&mlon=${p.lon}#map=17/${p.lat}/${p.lon}"
            val text = listOf(p.name, p.detail, link).filter { it.isNotBlank() }.joinToString("\n")
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share place").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(cs.surfaceContainerLow)
            .navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(cs.outlineVariant))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val (emoji, _) = placeEmoji(p.kind, p.name)
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 26.sp) }
            Column(Modifier.weight(1f)) {
                Text(p.name, color = cs.onSurface, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 28.sp)
                val sub = listOfNotNull(p.category.ifBlank { null }, away(vm, p)).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = cs.onSurfaceVariant, fontSize = 14.sp)
            }
        }
        if (p.detail.isNotBlank()) Text(p.detail, color = cs.onSurface, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.directions() },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
            ) {
                Icon(Icons.Rounded.Navigation, null, modifier = Modifier.size(20.dp).rotate(45f))
                Spacer(Modifier.width(8.dp))
                Text("Directions", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            val saved = vm.isSaved(p)
            FilledTonalButton(onClick = { vm.toggleSaved(p) }, modifier = Modifier.height(56.dp)) {
                Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (saved) "Saved" else "Save", fontWeight = FontWeight.SemiBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            val isHome = vm.isHome(p)
            val isWork = vm.isWork(p)
            FilterChip(
                selected = isHome, onClick = { vm.saveHome(if (isHome) null else p) },
                label = { Text(if (isHome) "Home" else "Set as Home") },
                leadingIcon = { Icon(Icons.Rounded.Home, null, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cs.primaryContainer),
            )
            FilterChip(
                selected = isWork, onClick = { vm.saveWork(if (isWork) null else p) },
                label = { Text(if (isWork) "Work" else "Set as Work") },
                leadingIcon = { Icon(Icons.Rounded.Work, null, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cs.primaryContainer),
            )
            FilterChip(
                selected = false,
                onClick = {
                    val url = "https://www.openstreetmap.org/note/new?lat=${p.lat}&lon=${p.lon}#map=19/${p.lat}/${p.lon}"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                label = { Text("Fix the map") },
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, bg: Color? = null, tint: Color? = null, size: Dp = 50.dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).shadow(6.dp, CircleShape).clip(CircleShape).background(bg ?: cs.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint ?: cs.onSurface) }
}

// ======================================================================
// Route preview
// ======================================================================

@Composable
fun BoxScope.RouteM3(vm: AppViewModel) {
    val dest = vm.place ?: return
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back") { vm.back() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = cs.onSurface)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteField(cs.primary, "Your location", CircleShape)
                RouteField(cs.tertiary, dest.name, RoundedCornerShape(3.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Mode.entries.forEach { m ->
                val on = m == vm.mode
                val icon = when (m) { Mode.DRIVE -> Icons.Rounded.DirectionsCar; Mode.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk; Mode.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike }
                Row(
                    Modifier.height(40.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(if (on) cs.primary else cs.surfaceContainerHigh)
                        .clickable(role = Role.Tab, onClickLabel = m.label) { vm.setTravelMode(m) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icon, null, tint = if (on) cs.onPrimary else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(
                        if (on && vm.routes.isNotEmpty()) Units.duration(vm.routes[vm.selected.coerceIn(0, vm.routes.lastIndex)].durationS) else m.label,
                        color = if (on) cs.onPrimary else cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                }
            }
        }
    }

    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(cs.surfaceContainerLow)
            .navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(cs.outlineVariant))
        val r = vm.routes.getOrNull(vm.selected)
        when {
            vm.routing -> Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = cs.primary, strokeWidth = 2.5.dp)
                Spacer(Modifier.width(14.dp))
                Text("Finding the best route…", color = cs.onSurfaceVariant, fontSize = 15.sp)
            }
            r == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(vm.routeError ?: "No route yet.", color = cs.onSurfaceVariant, fontSize = 15.sp)
                if (vm.location != null) FilledTonalButton(onClick = { vm.computeRoutes() }, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            }
            else -> {
                val delayMin = r.trafficDelayS?.let { Math.round(it / 60.0).toInt() }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Units.duration(r.durationS), color = if ((delayMin ?: 0) >= 5) Orange else Green, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("(${Units.short(r.distanceM)})", color = cs.onSurfaceVariant, fontSize = 17.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    buildString {
                        append(if (vm.selected == 0 && vm.routes.size > 1) "Fastest route" else "Route")
                        r.via?.let { append(" via $it") }
                        append(" · arrive ${arriveAt(r.durationS)}")
                        when {
                            delayMin == null -> {}
                            delayMin >= 1 -> append(" · +$delayMin min traffic")
                            else -> append(" · traffic is light")
                        }
                    },
                    color = cs.onSurfaceVariant, fontSize = 14.sp,
                )
                if (vm.routes.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        vm.routes.forEachIndexed { i, alt ->
                            FilterChip(
                                selected = i == vm.selected,
                                onClick = { vm.selectRoute(i) },
                                label = { Text("${Units.duration(alt.durationS)}" + (alt.via?.let { " · $it" } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cs.primaryContainer),
                            )
                        }
                    }
                }
                if (vm.mode != Mode.WALK) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AvoidChip("Avoid highways", vm.settings.avoidHighways) { vm.settings.avoidHighways = it; vm.computeRoutes() }
                        AvoidChip("Avoid tolls", vm.settings.avoidTolls) { vm.settings.avoidTolls = it; vm.computeRoutes() }
                        AvoidChip("Avoid ferries", vm.settings.avoidFerries) { vm.settings.avoidFerries = it; vm.computeRoutes() }
                    }
                }
                Button(
                    onClick = { vm.start() },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                ) {
                    Icon(Icons.Rounded.Navigation, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Start", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RouteField(dot: Color, text: String, shape: androidx.compose.ui.graphics.Shape) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHighest).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).clip(shape).background(dot))
        Text(text, color = cs.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AvoidChip(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = on, onClick = { onChange(!on) }, label = { Text(label) })
}

// ======================================================================
// Navigation
// ======================================================================

@Composable
fun BoxScope.NavM3(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val p = vm.progress
    val now by vm.music.now.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded || vm.alongLabel != null) { if (vm.alongLabel != null) vm.closeAlong() else expanded = false }

    // ---- turn card ----
    Column(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp))) {
            Row(
                Modifier.fillMaxWidth().background(cs.primary).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(if (p?.arrived == true) maneuverIcon(4) else maneuverIcon(p?.maneuver?.type ?: 1), null, tint = cs.onPrimaryContainer, modifier = Modifier.size(42.dp))
                }
                Column(Modifier.weight(1f)) {
                    when {
                        p == null -> Text("Getting your location…", color = cs.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        p.arrived -> {
                            Text("You've arrived", color = cs.onPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            vm.place?.let { Text(it.name, color = cs.onPrimary.copy(alpha = 0.85f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        else -> {
                            Text(Units.short(p.toManeuverM), color = cs.onPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                            Text(
                                p.maneuver.street ?: p.maneuver.instruction.removeSuffix("."),
                                color = cs.onPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            p?.next?.takeIf { !p.arrived && !p.maneuver.isArrival }?.let { n ->
                Row(
                    Modifier.fillMaxWidth().background(cs.primaryContainer).padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Then", color = cs.onPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Icon(maneuverIcon(n.type), null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    Text(n.street ?: "", color = cs.onPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // ---- music pill ----
        val np = now
        if (vm.settings.musicInNav && np != null) {
            Row(
                Modifier.align(Alignment.End).shadow(6.dp, CircleShape).clip(CircleShape).background(cs.surfaceContainerHigh).padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val art = np.art
                if (art != null) Image(art.asImageBitmap(), "Album art", contentScale = ContentScale.Crop, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)))
                else Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(cs.tertiaryContainer), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 17.sp) }
                Column(Modifier.widthIn(max = 150.dp)) {
                    Text(np.title, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(np.artist.ifBlank { np.app }, color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(cs.primary)
                        .clickable(role = Role.Button, onClickLabel = if (np.playing) "Pause" else "Play") { vm.music.playPause() },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp)) }
            }
        }
    }

    // ---- right buttons ----
    if (!expanded) Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 160.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIcon(if (vm.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (vm.muted) "Unmute" else "Mute", size = 52.dp) { vm.toggleMute() }
        RoundIcon(Icons.Rounded.Search, "Search along the route", size = 52.dp) { expanded = true }
    }

    // ---- speed + limit, recenter ----
    Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 14.dp, bottom = if (expanded) 0.dp else 124.dp)) {
        if (!expanded) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.speedLimit()?.let { SpeedLimitSign(it) }
            val mps = p?.speedMps ?: 0f
            SpeedBubble(Math.round(if (Units.imperial) mps * 2.23694 else mps * 3.6).toInt(), 66.dp)
        }
    }
    if (!vm.following && !expanded) {
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 124.dp)
                .height(52.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(cs.primaryContainer)
                .clickable(role = Role.Button, onClickLabel = "Re-center") { vm.recenter() }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Navigation, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Text("Re-center", color = cs.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
    }

    // ---- bottom bar (tap to expand: stops along the way, share ETA, overview) ----
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp)
            .shadow(12.dp, RoundedCornerShape(32.dp)).clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerHigh),
    ) {
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            NavExtras(vm, onShare = {
                vm.etaShareText()?.let { text ->
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share your ETA").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, onClose = { expanded = false })
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(22.dp)).background(cs.errorContainer)
                    .clickable(role = Role.Button, onClickLabel = "End navigation") { vm.end() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "End navigation", tint = cs.onErrorContainer, modifier = Modifier.size(26.dp)) }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = "More") { expanded = !expanded }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (p != null && !p.arrived) {
                    val delay = vm.routes.firstOrNull()?.trafficDelayS ?: 0.0
                    Text(Units.duration(p.remainingS), color = if (delay >= 300) Orange else Green, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${Units.short(p.remainingM)} · ${arriveAt(p.remainingS)}", color = cs.onSurfaceVariant, fontSize = 14.sp)
                } else if (p?.arrived == true) {
                    Text("Arrived", color = Green, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Tap ✕ to finish", color = cs.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    Text("Starting…", color = cs.onSurfaceVariant, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                vm.finalStop?.let { Text("then ${it.name}", color = cs.primary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(22.dp)).background(cs.secondaryContainer)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Less" else "More") { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) { Icon(if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun NavExtras(vm: AppViewModel, onShare: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add a stop on the way", color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            categories.filter { it.label in setOf("Gas", "Coffee", "Food", "Charging", "Groceries") }.forEach { c ->
                FilterChip(
                    selected = vm.alongLabel == c.label,
                    onClick = { vm.searchAlong(c) },
                    label = { Text(c.label) },
                    leadingIcon = { Icon(categoryIcon(c.label), null, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cs.primaryContainer),
                )
            }
        }
        if (vm.alongLabel != null) {
            when {
                vm.alongLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = cs.primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Looking ahead on your route…", color = cs.onSurfaceVariant, fontSize = 14.sp)
                }
                vm.alongResults.isEmpty() -> Text("Nothing close to your route. Try another kind of stop.", color = cs.onSurfaceVariant, fontSize = 14.sp)
                else -> Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    vm.alongResults.forEach { s ->
                        PlaceListRow(vm, s, trailing = {
                            Text("Add", color = cs.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                        }) { vm.addStop(s); onClose() }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share ETA", fontWeight = FontWeight.SemiBold)
            }
            FilledTonalButton(onClick = { if (vm.following) vm.overview() else vm.recenter(); onClose() }, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(if (vm.following) Icons.Rounded.Map else Icons.Rounded.Navigation, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (vm.following) "Overview" else "Re-center", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SpeedLimitSign(limit: Int) {
    if (Units.imperial) {
        // US style: white sign, black border.
        Column(
            Modifier.size(width = 56.dp, height = 68.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(3.dp)
                .border(2.5.dp, Color.Black, RoundedCornerShape(8.dp)).padding(top = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("SPEED\nLIMIT", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 8.5.sp, textAlign = TextAlign.Center)
            Text("$limit", color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
    } else {
        Box(
            Modifier.size(62.dp).clip(CircleShape).background(Color.White).padding(5.dp).border(6.dp, Color(0xFFD32F2F), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$limit", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
    }
}
