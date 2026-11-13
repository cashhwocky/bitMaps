package com.bitdroid.maps.ui

import android.content.Intent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun VoiceScreen(vm: AppViewModel) {
    val s = vm.settings
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back") { vm.back() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.Text) }
            Column(Modifier.padding(start = 6.dp)) {
                Text("🗣️ Voice", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text("How directions sound", color = C.Dim, fontSize = 14.sp)
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🎚️ Sound", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            Card {
                SliderRow("🐢 Speed", String.format(Locale.US, "%.1f×", s.voiceSpeed), s.voiceSpeed, 0.6f..1.6f) { s.voiceSpeed = it }
                SliderRow("🎵 Pitch", if (s.voicePitch in 0.97f..1.03f) "Normal" else String.format(Locale.US, "%.2f", s.voicePitch), s.voicePitch, 0.7f..1.4f) { s.voicePitch = it }
            }
            GradientButton("Test voice", { vm.testVoice() }, Modifier.fillMaxWidth().padding(top = 6.dp), emoji = "🔊", height = 56.dp)
            Text("“${vm.sampleLine}”", color = C.Dim, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

            Card(Modifier.padding(top = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface), contentAlignment = Alignment.Center) {
                        Text("📱", fontSize = 22.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Phone voice settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Pick the speech engine and voice your phone uses", fontSize = 13.sp, color = C.Dim)
                    }
                    Text("›", fontSize = 24.sp, color = C.Dim)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(C.Sheet).border(1.dp, C.Outline, shape).padding(horizontal = 14.dp, vertical = 4.dp)) { content() }
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
            colors = SliderDefaults.colors(thumbColor = C.Accent, activeTrackColor = C.Violet, inactiveTrackColor = C.SurfaceHigh),
        )
    }
}
