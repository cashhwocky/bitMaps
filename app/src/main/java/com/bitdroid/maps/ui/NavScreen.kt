package com.bitdroid.maps.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.maps.data.Units
import com.bitdroid.maps.nav.NowPlaying
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun BoxScope.NavScreen(vm: AppViewModel) {
    val p = vm.progress
    val now by vm.music.now.collectAsState()

    Column(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- lilac turn card ----
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(TileRadius)).background(C.Accent).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Cookie(78.dp, C.AccentDeep) {
                    Icon(
                        if (p?.arrived == true) maneuverIcon(4) else maneuverIcon(p?.maneuver?.type ?: 1),
                        null, tint = C.OnAccentDeep, modifier = Modifier.size(42.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    when {
                        p == null -> Text("Getting your location…", color = C.OnAccent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        p.arrived -> {
                            Text("🎉 You've arrived", color = C.OnAccent, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                            vm.place?.let { Text(it.name, color = C.OnAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        else -> {
                            Text(Units.short(p.toManeuverM), color = C.OnAccent, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, lineHeight = 44.sp)
                            Text(
                                p.maneuver.instruction.removeSuffix("."), color = C.OnAccent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            p?.next?.takeIf { !p.arrived && !p.maneuver.isArrival }?.let { n ->
                Row(
                    Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(C.AccentSoft).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Then", color = C.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Icon(maneuverIcon(n.type), null, tint = C.OnAccent, modifier = Modifier.size(18.dp))
                    Text(n.street ?: n.instruction.removeSuffix("."), color = C.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ---- music tile + speed cookie ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val np = now
            if (vm.settings.musicInNav && np != null) MusicTile(vm, np, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            if (p != null) {
                Cookie(76.dp, C.Accent, shape = Cookie5) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Units.speed(p.speedMps), color = C.OnAccent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
                        Text(Units.speedUnit, color = C.OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ---- side squircles ----
    Column(
        Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, top = 160.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Squircle(if (vm.muted) "🔇" else "🔊", if (vm.muted) "Unmute voice" else "Mute voice") { vm.toggleMute() }
        if (vm.following) Squircle("🗺️", "Route overview") { vm.overview() }
        else Squircle("🎯", "Recenter") { vm.recenter() }
    }

    // ---- arrival tile + End cookie ----
    Row(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).height(92.dp).clip(RoundedCornerShape(32.dp)).background(C.Glass).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (p != null) {
                Text(Units.duration(p.remainingS), color = C.Accent, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                val arrive = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (p.remainingS * 1000).toLong()))
                Text("${Units.short(p.remainingM)} · arrive $arrive", color = C.Dim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(vm.place?.name ?: "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Cookie(
            92.dp, C.Danger,
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "End navigation") { vm.end() },
        ) { Icon(Icons.Rounded.Close, "End navigation", tint = C.OnDanger, modifier = Modifier.size(40.dp)) }
    }
}

@Composable
private fun Squircle(emoji: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(C.Glass).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 22.sp) }
}

/** Music tile like the home screen's music widget. Swipe it left/right to skip. */
@Composable
private fun MusicTile(vm: AppViewModel, np: NowPlaying, modifier: Modifier) {
    var drag by remember { mutableFloatStateOf(0f) }
    Row(
        modifier
            .height(76.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(C.Glass)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag < -80) vm.music.next() else if (drag > 80) vm.music.previous()
                        drag = 0f
                    },
                    onHorizontalDrag = { _, d -> drag += d },
                )
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AlbumArt(np, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(np.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(np.artist.ifBlank { np.app }, color = C.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(C.Accent)
                .clickable(role = Role.Button, onClickLabel = if (np.playing) "Pause" else "Play") { vm.music.playPause() },
            contentAlignment = Alignment.Center,
        ) { Icon(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.playing) "Pause" else "Play", tint = C.OnAccent, modifier = Modifier.size(22.dp)) }
    }
}

/** Current playback position, ticking while the song plays. */
@Composable
private fun rememberPosition(np: NowPlaying): Long {
    var pos by remember { mutableLongStateOf(np.positionMs) }
    LaunchedEffect(np) {
        while (true) {
            pos = if (np.playing && np.measuredAt > 0) {
                np.positionMs + ((SystemClock.elapsedRealtime() - np.measuredAt) * np.speed).toLong()
            } else np.positionMs
            if (!np.playing) break
            delay(500)
        }
    }
    return if (np.durationMs > 0) pos.coerceIn(0, np.durationMs) else pos
}

@Composable
private fun AlbumArt(np: NowPlaying, size: Dp) {
    val shape = RoundedCornerShape(size / 4)
    val art = np.art
    if (art != null) {
        Image(art.asImageBitmap(), "Album art", contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(
            Modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(C.Pink, C.Violet, C.Cyan))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(size / 2)) }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit, filled: Boolean = false, tint: Color = Color.White, fill: Color = Color.White) {
    Box(
        Modifier
            .size(if (filled) 44.dp else 40.dp)
            .clip(CircleShape)
            .background(if (filled) fill else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Music bar floating over the map (Settings → Music → Show on map). */
@Composable
fun MusicBar(vm: AppViewModel, np: NowPlaying, modifier: Modifier = Modifier) {
    val pos = rememberPosition(np)
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.fillMaxWidth().shadow(14.dp, shape).clip(shape).background(C.Glass).border(1.dp, C.Outline, shape)) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlbumArt(np, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(np.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(np.artist, np.app).filter { it.isNotBlank() }.joinToString(" · "), color = C.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MediaButton(Icons.Rounded.SkipPrevious, "Previous track", { vm.music.previous() }, tint = C.Text)
            Box(Modifier.size(44.dp).clip(CircleShape).background(C.gradient).clickable(role = Role.Button) { vm.music.playPause() }, contentAlignment = Alignment.Center) {
                Icon(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.playing) "Pause" else "Play", tint = C.OnAccent, modifier = Modifier.size(22.dp))
            }
            MediaButton(Icons.Rounded.SkipNext, "Next track", { vm.music.next() }, tint = C.Text)
        }
        if (np.durationMs > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxWidth(pos.toFloat() / np.durationMs).fillMaxHeight().background(C.gradient))
            }
        }
    }
    Spacer(Modifier.width(0.dp))
}
