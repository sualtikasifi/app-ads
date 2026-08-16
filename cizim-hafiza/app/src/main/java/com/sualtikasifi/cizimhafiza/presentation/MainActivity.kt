package com.sualtikasifi.cizimhafiza.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import com.sualtikasifi.cizimhafiza.presentation.navigation.CizimHafizaNavGraph
import com.sualtikasifi.cizimhafiza.presentation.theme.CizimHafizaTheme
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate()/setContent — shows the branded
        // splash (see Theme.Karalak.Splash) until Compose draws its first
        // frame instead of a plain platform default screen.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CizimHafizaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CizimHafizaNavGraph(onNavControllerReady = { navController = it })
                    RequestNotificationPermissionOnce(settingsRepository)
                }
            }
        }
    }

    // launchMode="singleTask" (see AndroidManifest.xml) means a deep-link tap
    // while the app is already running reuses this Activity instance and
    // arrives here instead of a fresh onCreate — so the new URI has to be
    // forwarded to the existing NavController by hand.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navController?.handleDeepLink(intent)
    }
}

/**
 * Asks for the POST_NOTIFICATIONS runtime permission (API 33+) exactly once,
 * on whichever launch is this device's first — never nags again afterward.
 * The Settings screen's "Bildirimler" toggle offers a way to (re-)request it
 * later for anyone who dismissed this or wants to turn notifications on
 * after having turned them off.
 */
@Composable
private fun RequestNotificationPermissionOnce(settingsRepository: SettingsRepository) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> settingsRepository.setNotificationsEnabled(granted) }

    LaunchedEffect(Unit) {
        if (settingsRepository.notificationPermissionRequested) return@LaunchedEffect
        settingsRepository.notificationPermissionRequested = true
        val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
