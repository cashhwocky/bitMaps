package com.bitdroid.maps.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.maps.data.Geo
import com.bitdroid.maps.data.Mode
import com.bitdroid.maps.data.Place
import com.bitdroid.maps.data.Units
import java.text.DateFormat
import java.util.Date

@Composable
fun BitMapsApp(vm: AppViewModel, controller: MapController, onExit: () -> Unit) {
    BackHandler { if (!vm.back()) onExit() }

    // Map contents follow the app state.
    LaunchedEffect(vm.screen, vm.routes, vm.selected, vm.place) {
        controller.whenReady {
            when (vm.screen) {
                Screen.ROUTE, Screen.NAV -> controller.showRoutes(vm.routes, vm.selected, vm.place)
                Screen.PLACE -> { controller.showRoutes(emptyList(), 0, null); controller.showPin(vm.place) }
                Screen.SETTINGS -> {}
                else -> { controller.showRoutes(emptyList(), 0, null); controller.showPin(null) }
            }
        }
    }
    LaunchedEffect(vm.location) {
        val loc = vm.location ?: return@LaunchedEffect
        controller.whenReady { controller.showLocation(loc) }
    }
    LaunchedEffect(vm.cameraTick) {
        controller.onTrackingDismissed = { vm.following = false }
        controller.whenReady {
            when (vm.screen) {
                Screen.EXPLORE, Screen.SEARCH -> vm.location?.let { controller.flyTo(it.latitude, it.longitude, 15.0, 260) }
                Screen.PLACE -> vm.place?.let { controller.flyTo(it.lat, it.lon, 16.0, 420) }
                Screen.ROUTE -> vm.routes.getOrNull(vm.selected)?.let { r -> controller.fit(vm.routes.flatMap { it.points }.ifEmpty { r.points }, 220, 380) }
                Screen.NAV -> {
                    if (vm.following) controller.follow(vm.settings.tilt3d)
                    else vm.routes.firstOrNull()?.let { controller.fit(it.points, 300, 200) }
                }
                Screen.SETTINGS -> {}
            }
        }
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        MapSurface(controller, onLongPress = vm::dropPin, modifier = Modifier.fillMaxSize())
        when (vm.screen) {
            Screen.EXPLORE -> Explore(vm)
            Screen.SEARCH -> SearchScreen(vm)
            Screen.PLACE -> PlaceScreen(vm)
            Screen.ROUTE -> RouteScreen(vm)
            Screen.NAV -> NavScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm)
        }
    }
}

private fun distanceTo(vm: AppViewModel, p: Place): String? = vm.here()?.let { Units.short(Geo.distance(it, p.pos)) }

// ---------------- Explore ----------------

@Composable
private fun BoxScope.Explore(vm: AppViewModel) {
    Column(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchBar(vm, Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { c ->
                val (emoji, _) = placeEmoji(c.query)
                val shape = RoundedCornerShape(20.dp)
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(shape)
                        .background(C.Glass)
                        .border(1.dp, C.Outline, shape)
                        .clickable(role = Role.Button) { vm.searchCategory(c) }
                        .padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(emoji, fontSize = 17.sp)
                    Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 12.dp), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier
                    .size(52.dp)
                    .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = C.Violet, spotColor = C.Violet)
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.gradient)
                    .clickable(role = Role.Button, onClickLabel = "Center on my location") { vm.recenter() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Navigation, "Center on my location", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        if (vm.settings.musicOnMap) {
            val now by vm.music.now.collectAsState()
            now?.let { MusicBar(vm, it, Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) }
        }
        Sheet {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                SectionTitle("🕘 Recent")
                Spacer(Modifier.height(6.dp))
                if (vm.recents.isEmpty()) {
                    Text(
                        if (vm.location == null) "Finding your location…" else "Search for a place, or long-press the map to drop a pin.",
                        color = C.Dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp),
                    )
                } else {
                    vm.recents.take(3).forEach { p -> PlaceRow(p, distanceTo(vm, p)) { vm.open(p) } }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(29.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(C.Glass)
            .border(1.dp, C.Outline, shape)
            .clickable(onClickLabel = "Search") { vm.openSearch() }
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToggleLogo()
        Text("Search bitMaps", color = C.Dim, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(C.gradient).clickable(role = Role.Button, onClickLabel = "Settings") { vm.openSettings() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

/** bitDroid's toggle-switch mark. */
@Composable
fun ToggleLogo() {
    Box(Modifier.size(width = 30.dp, height = 18.dp).clip(CircleShape).background(C.gradient), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.padding(end = 2.dp).size(14.dp).clip(CircleShape).background(Color.White))
    }
}

// ---------------- Search ----------------

@Composable
private fun SearchScreen(vm: AppViewModel) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().imePadding()) {
        val shape = RoundedCornerShape(26.dp)
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(C.Surface)
                .border(1.dp, C.Outline, shape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back") { vm.back() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.Text) }
            BasicTextField(
                value = vm.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Body, fontSize = 16.sp, color = C.Text, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(C.Violet),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (vm.query.isEmpty()) Text("Search places and addresses", color = C.Dim, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            if (vm.query.isNotEmpty()) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Clear") { vm.setQuery("") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Clear", tint = C.Dim) }
            } else {
                Icon(Icons.Rounded.Search, null, tint = C.Dim, modifier = Modifier.padding(end = 10.dp))
            }
        }
        if (vm.searching) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = C.Violet, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Searching…", color = C.Dim, fontSize = 14.sp)
            }
        }
        vm.searchError?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 14.sp, modifier = Modifier.padding(20.dp)) }
        val list = if (vm.query.isBlank()) vm.recents else vm.results
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (vm.query.isBlank() && list.isNotEmpty()) item { SectionTitle("🕘 Recent", Modifier.padding(vertical = 8.dp)) }
            items(list, key = { it.id }) { p ->
                PlaceRow(p, distanceTo(vm, p)) { keyboard?.hide(); vm.open(p) }
            }
            if (vm.query.isNotBlank() && !vm.searching && vm.results.isEmpty() && vm.searchError == null) {
                item { Text("No places found.", color = C.Dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp)) }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
}

// ---------------- Place ----------------

@Composable
private fun BoxScope.PlaceScreen(vm: AppViewModel) {
    val p = vm.place ?: return
    val context = LocalContext.current
    Row(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlassButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { vm.back() }, shape = RoundedCornerShape(25.dp))
        GlassButton(Icons.Rounded.Share, "Share", {
            val link = "https://www.openstreetmap.org/?mlat=${p.lat}&mlon=${p.lon}#map=17/${p.lat}/${p.lon}"
            val text = listOf(p.name, p.detail, link).filter { it.isNotBlank() }.joinToString("\n")
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share place")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }, shape = RoundedCornerShape(25.dp))
    }
    Sheet(Modifier.align(Alignment.BottomCenter)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlaceIcon(p.kind, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(p.category.ifBlank { null }, distanceTo(vm, p)).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = C.Dim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (p.detail.isNotBlank()) Text(p.detail, color = C.Text, fontSize = 15.sp)
            GradientButton("Directions", { vm.directions() }, Modifier.fillMaxWidth(), emoji = "🧭")
        }
    }
}

// ---------------- Route preview ----------------

@Composable
private fun BoxScope.RouteScreen(vm: AppViewModel) {
    val dest = vm.place ?: return
    Column(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val shape = RoundedCornerShape(26.dp)
        Row(
            Modifier.fillMaxWidth().shadow(16.dp, shape).clip(shape).background(C.Glass).border(1.dp, C.Outline, shape).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back") { vm.back() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.Text) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Your location", C.Dim)
                Field(dest.name, C.Text)
            }
            Icon(Icons.Rounded.SwapVert, null, tint = C.Dim, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mode.entries.forEach { m ->
                val on = m == vm.mode
                val s = RoundedCornerShape(20.dp)
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(s)
                        .background(if (on) C.gradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.Glass, C.Glass)))
                        .border(1.dp, if (on) Color.Transparent else C.Outline, s)
                        .clickable(role = Role.Tab) { vm.setTravelMode(m) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(m.emoji, fontSize = 16.sp)
                    Text(m.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else C.Text)
                }
            }
        }
    }

    Sheet(Modifier.align(Alignment.BottomCenter)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val r = vm.routes.getOrNull(vm.selected)
            when {
                vm.routing -> Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = C.Violet, strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Finding the best route…", color = C.Dim, fontSize = 15.sp)
                }
                r == null -> Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.routeError ?: "No route yet.", color = C.Dim, fontSize = 15.sp)
                    if (vm.location != null) GradientButton("Try again", { vm.computeRoutes() }, Modifier.fillMaxWidth(), height = 50.dp)
                }
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(Units.duration(r.durationS), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                            val arrive = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (r.durationS * 1000).toLong()))
                            Text(
                                listOfNotNull(Units.short(r.distanceM), r.via?.let { "via $it" }, "arrive $arrive").joinToString(" · "),
                                color = C.Dim, fontSize = 14.sp, maxLines = 2,
                            )
                        }
                        if (vm.routes.size > 1 && vm.selected == 0) Pill("⚡ Fastest", C.Lilac, C.Violet.copy(alpha = 0.18f))
                    }
                    vm.routes.forEachIndexed { i, alt ->
                        if (i == vm.selected) return@forEachIndexed
                        val s = RoundedCornerShape(18.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(s).background(C.Surface).border(1.dp, C.Outline, s)
                                .clickable { vm.selectRoute(i) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(width = 6.dp, height = 32.dp).clip(CircleShape).background(Color(0xFF4A5263)))
                            Column(Modifier.weight(1f)) {
                                Text("${Units.duration(alt.durationS)} · ${Units.short(alt.distanceM)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                alt.via?.let { Text("via $it", fontSize = 13.sp, color = C.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                    GradientButton("Let's go", { vm.start() }, Modifier.fillMaxWidth(), emoji = "🚀", height = 60.dp)
                }
            }
        }
    }
}

@Composable
private fun Field(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
fun RecenterButton(vm: AppViewModel, modifier: Modifier = Modifier) {
    GlassButton(Icons.Rounded.MyLocation, "Recenter", { vm.recenter() }, modifier, size = 52.dp, shape = RoundedCornerShape(26.dp))
}

@Composable
fun SlideIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) { content() }
}
