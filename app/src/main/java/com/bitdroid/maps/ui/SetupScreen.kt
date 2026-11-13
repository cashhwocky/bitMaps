package com.bitdroid.maps.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bitdroid.maps.data.Updater

private data class Step(
    val emoji: String,
    val title: String,
    val body: String,
    val button: String,
    val note: String? = null,
)

/** First-launch setup: walks through every permission bitMaps needs, one screen each. */
@Composable
fun SetupScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var step by rememberSaveable { mutableIntStateOf(0) }

    // What's already allowed (re-checked whenever you come back from Android's settings).
    var location by remember { mutableStateOf(vm.tracker.hasPermission()) }
    var notifications by remember { mutableStateOf(notificationsAllowed(context)) }
    var music by remember { mutableStateOf(vm.music.hasAccess()) }
    var installs by remember { mutableStateOf(Updater.canInstall(context)) }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            location = vm.tracker.hasPermission()
            notifications = notificationsAllowed(context)
            music = vm.music.hasAccess()
            installs = Updater.canInstall(context)
        }
    }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        location = vm.tracker.hasPermission()
        vm.onPermissionsChanged()
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = it
    }

    val steps = listOf(
        Step("🗺️", "Welcome to bitMaps", "Maps and turn-by-turn directions for bitDroid. Free, private, no ads.\nLet's get a few things set up.", "Let's go"),
        Step("📍", "Your location", "So bitMaps can show where you are and guide you turn by turn. It never leaves your phone except to find routes.", "Allow location"),
        Step("🔔", "Notifications", "So bitMaps can tell you when there's a new update.", "Allow notifications"),
        Step(
            "🎵", "Music while you drive", "Shows the song that's playing, with play, pause and skip, right in your directions.\nbitMaps only looks at what's playing. It never reads your notifications.",
            "Allow notification access",
            "If Android says it's a restricted setting: Settings → Apps → bitMaps → ⋮ → Allow restricted settings, then come back.",
        ),
        Step("🔄", "In-app updates", "Lets bitMaps download and install its own updates right inside the app.", "Allow updates"),
        Step("🎉", "You're all set", "Search a place, tap Directions, and go.", "Start exploring"),
    )
    val granted = when (step) {
        1 -> location
        2 -> notifications
        3 -> music
        4 -> installs
        else -> false
    }
    fun next() { if (step < steps.lastIndex) step++ else vm.finishSetup() }
    // Move on by itself once the thing is allowed.
    LaunchedEffect(step, granted) { if (granted && step in 1..4) { kotlinx.coroutines.delay(500); next() } }

    Column(
        Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // progress dots
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.indices.forEach { i ->
                Box(
                    Modifier
                        .height(6.dp)
                        .width(if (i == step) 22.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (i <= step) C.gradient else Brush.linearGradient(listOf(C.SurfaceHigh, C.SurfaceHigh))),
                )
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
            modifier = Modifier.weight(1f),
            label = "setup",
        ) { i ->
            val s = steps[i]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(132.dp)
                        .shadow(30.dp, CircleShape, ambientColor = C.Violet, spotColor = C.Violet)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(C.Violet.copy(alpha = 0.35f), C.Blue.copy(alpha = 0.25f), C.Cyan.copy(alpha = 0.2f))))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(s.emoji, fontSize = 62.sp) }
                Spacer(Modifier.height(32.dp))
                Text(s.title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(s.body, color = C.Dim, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 23.sp)
                if (s.note != null && i == step && !music) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "💡 ${s.note}", color = C.Lilac, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(C.Violet.copy(alpha = 0.12f)).padding(14.dp),
                    )
                }
            }
        }

        if (granted) {
            Text("✅ Allowed", color = C.Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))
        }
        GradientButton(
            if (granted) "Next" else steps[step].button,
            {
                when {
                    granted || step == 0 || step == steps.lastIndex -> next()
                    step == 1 -> askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    step == 2 -> askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    step == 3 -> runCatching { context.startActivity(vm.music.accessIntent()) }
                        .onFailure { runCatching { context.startActivity(vm.music.fallbackAccessIntent()) } }
                    step == 4 -> runCatching { context.startActivity(Updater.installPermissionIntent(context)) }
                }
            },
            Modifier.fillMaxWidth(),
            height = 60.dp,
        )
        if (step in 1..4 && !granted) {
            Text(
                "Skip for now", color = C.Dim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { next() }.padding(12.dp),
            )
        } else {
            Spacer(Modifier.height(54.dp))
        }
    }
}

private fun notificationsAllowed(context: android.content.Context) =
    android.os.Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
