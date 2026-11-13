package com.bitdroid.maps.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    // ---- turn card (with music built in) ----
    val shape = RoundedCornerShape(30.dp)
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(22.dp, shape, ambientColor = C.Violet, spotColor = C.Violet)
            .clip(shape)
            .background(C.gradient),
    ) {
        if (p == null) {
            Text(
                "Getting your location…", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.padding(22.dp),
            )
        } else if (p.arrived) {
            Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(maneuverIcon(4), null, tint = Color.White, modifier = Modifier.size(52.dp))
                Column {
                    Text("🎉 You've arrived", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    vm.place?.let { Text(it.name, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        } else {
            val m = p.maneuver
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(maneuverIcon(m.type), null, tint = Color.White, modifier = Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(Units.short(p.toManeuverM), color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                    Text(
                        m.instruction.removeSuffix("."), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 23.sp,
                    )
                }
            }
            p.next?.takeIf { !m.isArrival }?.let { n ->
                Row(
                    Modifier.fillMaxWidth().background(Color(0x47070A0D)).padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Then", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Icon(maneuverIcon(n.type), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(n.street ?: n.instruction.removeSuffix("."), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (vm.settings.musicInNav) now?.let { MusicStrip(vm, it) }
    }

    // ---- side controls ----
    Column(
        Modifier.align(Alignment.CenterStart).padding(start = 16.dp, top = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (p != null) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(C.Glass).border(1.dp, C.Outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Units.speed(p.speedMps), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(Units.speedUnit, fontSize = 10.sp, color = C.Dim, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Column(
        Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassButton(
            if (vm.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            if (vm.muted) "Unmute voice" else "Mute voice",
            { vm.toggleMute() }, size = 52.dp, shape = RoundedCornerShape(26.dp),
        )
        if (vm.following) GlassButton(Icons.Rounded.Route, "Route overview", { vm.overview() }, size = 52.dp, shape = RoundedCornerShape(26.dp))
        else RecenterButton(vm)
    }

    // ---- bottom bar ----
    val barShape = RoundedCornerShape(30.dp)
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(12.dp)
            .fillMaxWidth()
            .height(92.dp)
            .shadow(20.dp, barShape)
            .clip(barShape)
            .background(Color(0xF512151C))
            .border(1.dp, C.Outline, barShape)
            .padding(start = 22.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (p != null) {
                Text(Units.duration(p.remainingS), color = C.Green, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                val arrive = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (p.remainingS * 1000).toLong()))
                Text("${Units.short(p.remainingM)} · arrive $arrive", color = C.Dim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(vm.place?.name ?: "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(C.Red).clickable(role = Role.Button, onClickLabel = "End navigation") { vm.end() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "End navigation", tint = Color.White, modifier = Modifier.size(24.dp)) }
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

/** Music inside the turn card. */
@Composable
private fun MusicStrip(vm: AppViewModel, np: NowPlaying) {
    val pos = rememberPosition(np)
    Box(Modifier.fillMaxWidth().background(Color(0x73070A0D))) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlbumArt(np, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(np.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(np.artist, np.app).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            MediaButton(Icons.Rounded.SkipPrevious, "Previous track", { vm.music.previous() })
            MediaButton(
                if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.playing) "Pause" else "Play",
                { vm.music.playPause() }, filled = true, tint = Color(0xFF3B3FD8),
            )
            MediaButton(Icons.Rounded.SkipNext, "Next track", { vm.music.next() })
        }
        if (np.durationMs > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(pos.toFloat() / np.durationMs).fillMaxHeight().background(Color.White))
            }
        }
    }
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
                Icon(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(22.dp))
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
