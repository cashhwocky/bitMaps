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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
                Screen.SETTINGS, Screen.VOICE, Screen.SETUP -> {}
                else -> { controller.showRoutes(emptyList(), 0, null); controller.showPin(null) }
            }
        }
    }
    LaunchedEffect(vm.location) {
        val loc = vm.location ?: return@LaunchedEffect
        controller.whenReady {
            controller.showLocation(loc)
            if (vm.screen == Screen.NAV && vm.following) {
                if (!controller.isFollowing) controller.follow(vm.settings.tilt3d) else controller.keepClose()
            }
        }
    }
    LaunchedEffect(vm.cameraTick) {
        controller.onTrackingDismissed = { vm.following = false }
        controller.whenReady {
            when (vm.screen) {
                Screen.EXPLORE, Screen.SEARCH -> vm.location?.let { controller.flyTo(it.latitude, it.longitude, 15.5, 0) }
                Screen.PLACE -> vm.place?.let { controller.flyTo(it.lat, it.lon, 16.0, 420) }
                Screen.ROUTE -> vm.routes.getOrNull(vm.selected)?.let { r -> controller.fit(vm.routes.flatMap { it.points }.ifEmpty { r.points }, 220, 380) }
                Screen.NAV -> {
                    if (vm.following) controller.follow(vm.settings.tilt3d)
                    else vm.routes.firstOrNull()?.let { controller.fit(it.points, 300, 200) }
                }
                Screen.SETTINGS, Screen.VOICE, Screen.SETUP -> {}
            }
        }
    }

    // On Explore the map lives inside a rounded Bloom tile; everywhere else it fills the screen.
    val tile = vm.mapTile
    val density = androidx.compose.ui.platform.LocalDensity.current
    val mapModifier = if (vm.screen == Screen.EXPLORE && tile != null) {
        with(density) {
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(tile.left.toInt(), tile.top.toInt()) }
                .size(tile.width.toDp(), tile.height.toDp())
                .clip(RoundedCornerShape(TileRadius))
        }
    } else Modifier.fillMaxSize()

    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(C.glowA, Color.Transparent), center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 1400f))
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(C.glowB, Color.Transparent), center = androidx.compose.ui.geometry.Offset(1400f, 3000f), radius = 1600f)),
    ) {
        MapSurface(controller, onLongPress = vm::dropPin, modifier = mapModifier)
        when (vm.screen) {
            Screen.SETUP -> SetupScreen(vm)
            Screen.EXPLORE -> Explore(vm)
            Screen.SEARCH -> SearchScreen(vm)
            Screen.PLACE -> PlaceScreen(vm)
            Screen.ROUTE -> RouteScreen(vm)
            Screen.NAV -> NavScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm)
            Screen.VOICE -> VoiceScreen(vm)
        }
        if (vm.showUpdate) vm.update?.let { UpdateSheet(vm, it) }
    }
}

private fun distanceTo(vm: AppViewModel, p: Place): String? = vm.here()?.let { Units.short(Geo.distance(it, p.pos)) }

// ---------------- Explore (Bloom) ----------------

@Composable
private fun BoxScope.Explore(vm: AppViewModel) {
    val context = LocalContext.current
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::voiceQuery)
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Search pill
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(31.dp))
                .background(C.Glass)
                .clickable(onClickLabel = "Search") { vm.openSearch() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(C.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Search, null, tint = C.OnAccent, modifier = Modifier.size(22.dp))
            }
            Text("Search bitMaps", color = C.Dim, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Search by voice") {
                    val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Where to?")
                    runCatching { voice.launch(i) }.onFailure {
                        android.widget.Toast.makeText(context, "No voice typing app on this phone", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Mic, "Search by voice", tint = C.Accent, modifier = Modifier.size(24.dp)) }
            Cookie(
                46.dp, C.AccentDeep, shape = Cookie12,
                modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Settings") { vm.openSettings() },
            ) { Text("⚙️", fontSize = 20.sp) }
        }

        // Map tile (the map itself is drawn behind, clipped to this spot)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { vm.mapTile = it.boundsInRoot() }
                .clip(RoundedCornerShape(TileRadius)),
        ) {
            Text(
                "📍 ${vm.hereName ?: if (vm.location == null) "Finding you…" else "You're here"}",
                color = C.OnAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(14.dp).clip(RoundedCornerShape(18.dp)).background(C.Accent).padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Cookie(
                64.dp, C.Accent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    .clickable(role = Role.Button, onClickLabel = "Center on my location") { vm.recenter() },
            ) {
                Icon(Icons.Rounded.Navigation, "Center on my location", tint = C.OnAccent, modifier = Modifier.size(28.dp).rotate(45f))
            }
            vm.update?.let { u ->
                Text(
                    "🎉 Update ${u.version}",
                    color = C.OnAccentDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(18.dp)).background(C.AccentDeep)
                        .clickable(role = Role.Button) { vm.showUpdate = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // Tiles
        Row(Modifier.fillMaxWidth().height(178.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RecentTile(vm, vm.recents.firstOrNull(), Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeTile(vm, Modifier.fillMaxWidth().weight(1f))
                SecondTile(vm, vm.recents.getOrNull(1), Modifier.fillMaxWidth().weight(1f))
            }
        }

        if (vm.settings.musicOnMap) {
            val now by vm.music.now.collectAsState()
            now?.let { MusicBar(vm, it) }
        }

        // Category squircles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            categories.take(4).forEachIndexed { i, c ->
                val (emoji, _) = placeEmoji(c.query)
                val light = i % 2 == 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (light) C.Accent else C.AccentDeep)
                            .clickable(role = Role.Button, onClickLabel = c.label) { vm.searchCategory(c) },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 34.sp) }
                    Text(c.label, color = C.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun RecentTile(vm: AppViewModel, p: Place?, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(TileRadius))
            .background(C.Glass)
            .clickable(enabled = p != null) { p?.let(vm::open) }
            .padding(18.dp),
    ) {
        Text("🕘 Recent", color = C.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Cookie(74.dp, C.Accent, shape = Cookie5, modifier = Modifier.align(Alignment.TopEnd).padding(top = 0.dp)) {
            Text(if (p != null) placeEmoji(p.kind, p.name).first else "🔍", fontSize = 34.sp)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                p?.name ?: "Nothing yet", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (p != null) listOfNotNull(p.category.ifBlank { null }, distanceTo(vm, p)).joinToString(" · ") else "Search a place to start",
                color = C.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HomeTile(vm: AppViewModel, modifier: Modifier) {
    val h = vm.home
    Row(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(C.Accent)
            .clickable(role = Role.Button) { if (h != null) vm.goHome() else vm.openSearch() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Cookie(46.dp, C.AccentDeep, shape = Cookie12) { Text("🏠", fontSize = 21.sp) }
        Column {
            Text("Home", color = C.OnAccent.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    h == null -> "Set home"
                    vm.homeMinutes == 0 -> "You're home"
                    vm.homeMinutes != null -> "${vm.homeMinutes} min"
                    else -> distanceTo(vm, h) ?: "Go"
                },
                color = C.OnAccent, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            )
        }
    }
}

@Composable
private fun SecondTile(vm: AppViewModel, p: Place?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(C.Glass)
            .clickable(enabled = p != null) { p?.let(vm::open) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (p != null) placeEmoji(p.kind, p.name).first else "🧭", fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Text(p?.let { distanceTo(vm, it) } ?: "", color = C.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Wave(Modifier.fillMaxWidth().height(14.dp))
        Text(p?.name ?: "Explore nearby", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The squiggle from the Bloom widgets. */
@Composable
private fun Wave(modifier: Modifier) {
    val color = C.Accent
    androidx.compose.foundation.Canvas(modifier) {
        val path = androidx.compose.ui.graphics.Path()
        val w = size.width
        val h = size.height
        val mid = h / 2
        path.moveTo(0f, mid)
        val waves = 2.5f
        val steps = 60
        for (i in 1..steps) {
            val x = w * i / steps
            val y = mid + (h / 2.6f) * kotlin.math.sin(2 * Math.PI * waves * i / steps).toFloat()
            path.lineTo(x, y)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
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
                onValueChange = vm::updateQuery,
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
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Clear") { vm.updateQuery("") },
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
                PlaceIcon(p.kind, 52.dp, name = p.name)
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(p.category.ifBlank { null }, distanceTo(vm, p)).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = C.Dim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (p.detail.isNotBlank()) Text(p.detail, color = C.Text, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GradientButton("Directions", { vm.directions() }, Modifier.weight(1f), emoji = "🧭")
                val isHome = vm.isHome(p)
                Box(
                    Modifier
                        .height(58.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isHome) C.AccentDeep else C.Surface)
                        .clickable(role = Role.Button, onClickLabel = if (isHome) "Remove home" else "Set as home") { vm.setHome(if (isHome) null else p) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (isHome) "🏠 Home ✓" else "🏠 Set home", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isHome) C.OnAccentDeep else C.Text) }
            }
            Text(
                "✏️  Wrong name or missing? Fix the map",
                color = C.Lilac, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) {
                        // Opens an OpenStreetMap note at this spot; volunteers (or you) fix the map for everyone.
                        val url = "https://www.openstreetmap.org/note/new?lat=${p.lat}&lon=${p.lon}#map=19/${p.lat}/${p.lon}"
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
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
                    Text(m.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) C.OnAccent else C.Text)
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

/** "New version" sheet: notes, then download + install inside the app. */
@Composable
private fun UpdateSheet(vm: AppViewModel, u: com.bitdroid.maps.data.Update) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(role = Role.Button, onClickLabel = "Close") { if (vm.updateProgress == null) vm.showUpdate = false },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(enabled = false) {}) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("🎉 bitMaps ${u.version}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                if (u.notes.isNotBlank()) {
                    Text(
                        u.notes.lines().filter { it.isNotBlank() && !it.startsWith("**Full Changelog") }.take(12).joinToString("\n"),
                        color = C.Dim, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                }
                val p = vm.updateProgress
                if (p != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(C.Surface)) {
                            Box(Modifier.fillMaxWidth(p).height(10.dp).clip(CircleShape).background(C.gradient3))
                        }
                        Text("Downloading… ${(p * 100).toInt()}%", color = C.Dim, fontSize = 14.sp)
                    }
                } else {
                    GradientButton("Update now", { vm.installUpdate() }, Modifier.fillMaxWidth(), emoji = "⬇️")
                }
                vm.updateMessage?.let { Text(it, color = C.Lilac, fontSize = 14.sp) }
                if (u.sizeBytes > 0) Text("${u.sizeBytes / 1_000_000} MB · downloads and installs right here", color = C.Dim, fontSize = 12.sp)
            }
        }
    }
}
