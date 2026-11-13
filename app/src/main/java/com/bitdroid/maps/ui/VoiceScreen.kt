package com.bitdroid.maps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitdroid.maps.data.VoiceInfo
import com.bitdroid.maps.data.Voices
import java.util.Locale

@Composable
fun VoiceScreen(vm: AppViewModel) {
    val s = vm.settings
    val store = vm.voiceStore
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back") { vm.back() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.Text) }
            Column(Modifier.padding(start = 6.dp)) {
                Text("🗣️ Voice", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text("Pick who talks to you while you drive", color = C.Dim, fontSize = 14.sp)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨ Natural voices", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
                Text("  · free · work offline", color = C.Dim, fontSize = 12.sp)
            }
            Card {
                Voices.all.forEachIndexed { i, v ->
                    VoiceRow(vm, v, selected = s.voiceId == v.id, ready = store.isReady(v.id), progress = store.progress[v.id])
                    if (i != Voices.all.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hairline))
                }
            }
            store.error?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)) }

            Text("🎚️ Sound", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            Card(Modifier.padding(vertical = 6.dp)) {
                SliderRow("🐢 Speed", String.format(Locale.US, "%.1f×", s.voiceSpeed), s.voiceSpeed, 0.7f..1.5f) { s.voiceSpeed = it }
                SliderRow("🎵 Pitch", if (s.voicePitch in 0.97f..1.03f) "Normal" else String.format(Locale.US, "%.2f", s.voicePitch), s.voicePitch, 0.8f..1.25f) { s.voicePitch = it }
            }

            GradientButton("Test voice", { vm.testVoice() }, Modifier.fillMaxWidth().padding(top = 6.dp), emoji = "🔊", height = 56.dp)
            Text("“${vm.sampleLine}”", color = C.Dim, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

            Card(Modifier.padding(top = 6.dp)) {
                val on = s.voiceId == Voices.SYSTEM
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { vm.pickVoice(Voices.SYSTEM) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar("📱", on)
                    Column(Modifier.weight(1f)) {
                        Text("Phone voice", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Your phone's own speech engine", fontSize = 13.sp, color = C.Dim)
                    }
                    if (on) Pill("✓ In use", C.Lilac, C.Violet.copy(alpha = 0.2f))
                }
            }
            Text(
                "Voices by Piper (open source). Amy is built in; the others download once (about 60 MB) and then work offline.",
                color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(6.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(C.Sheet).border(1.dp, C.Outline, shape).padding(horizontal = 14.dp, vertical = 4.dp)) { content() }
}

@Composable
private fun Avatar(emoji: String, on: Boolean) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) Brush.linearGradient(listOf(C.Pink.copy(alpha = 0.3f), C.Violet.copy(alpha = 0.3f))) else Brush.linearGradient(listOf(C.Surface, C.Surface))),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 22.sp) }
}

@Composable
private fun VoiceRow(vm: AppViewModel, v: VoiceInfo, selected: Boolean, ready: Boolean, progress: Float?) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, C.gradient3, shape) else Modifier)
            .clickable(role = Role.RadioButton) { vm.pickVoice(v.id) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(v.emoji, selected)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (v.bundled) {
                    Spacer(Modifier.size(8.dp))
                    Pill("Default", C.Lilac, C.Violet.copy(alpha = 0.2f))
                }
            }
            val state = when {
                progress != null -> "⬇ ${(progress * 100).toInt()}%"
                ready || v.bundled -> "✅ Ready"
                else -> "⬇ 60 MB"
            }
            Text("${v.flag} ${v.description} · $state", fontSize = 13.sp, color = C.Dim)
        }
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (selected) C.gradient else Brush.linearGradient(listOf(C.SurfaceHigh, C.SurfaceHigh)))
                .clickable(role = Role.Button, onClickLabel = "Preview ${v.name}") { vm.previewVoice(v.id) },
            contentAlignment = Alignment.Center,
        ) {
            if (progress != null) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Rounded.PlayArrow, "Preview ${v.name}", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SliderRow(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Lilac)
        }
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = C.Violet, inactiveTrackColor = C.SurfaceHigh),
        )
    }
}
