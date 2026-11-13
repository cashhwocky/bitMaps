package com.bitdroid.maps

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.bitdroid.maps.ui.AppViewModel
import com.bitdroid.maps.ui.BitMapsApp
import com.bitdroid.maps.ui.BitTheme
import com.bitdroid.maps.ui.MapController
import com.bitdroid.maps.ui.Screen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) vm.handleGeo(intent?.data)

        setContent {
            val controller = remember { MapController(this) }
            val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                vm.onPermissionsChanged()
            }
            LaunchedEffect(Unit) {
                if (vm.tracker.hasPermission()) vm.onPermissionsChanged()
                else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            // Keep the screen awake while navigating (Settings → Keep screen on).
            val awake = vm.screen == Screen.NAV && vm.settings.keepScreenOn
            LaunchedEffect(awake) {
                if (awake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            BitTheme { BitMapsApp(vm, controller, onExit = { finish() }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handleGeo(intent.data)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshMusicAccess()
    }
}
