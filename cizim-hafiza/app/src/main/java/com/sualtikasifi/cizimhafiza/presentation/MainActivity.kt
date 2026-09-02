package com.sualtikasifi.cizimhafiza.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.ads.AdManager
import com.sualtikasifi.cizimhafiza.ads.ConsentManager
import com.sualtikasifi.cizimhafiza.presentation.navigation.CizimHafizaNavGraph
import com.sualtikasifi.cizimhafiza.presentation.theme.CizimHafizaTheme
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Extends AppCompatActivity (not plain ComponentActivity) solely so
// AppCompatDelegate.setApplicationLocales() — the per-app language switch
// used by the Settings screen's language toggle — actually works; Compose
// still owns 100% of the visible UI/theming (see themes.xml's comment).
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var adManager: AdManager
    @Inject lateinit var consentManager: ConsentManager

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate()/setContent — shows the branded
        // splash (see Theme.Karalak.Splash) until Compose draws its first
        // frame instead of a plain platform default screen.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // AppCompatDelegate.setApplicationLocales() (Settings screen's
        // language toggle) recreates this Activity on API < 33 to apply the
        // new locale — some OEM skins (notably MIUI) paint the bare window
        // black for a frame or two during that recreate before the theme's
        // windowBackground actually takes effect, producing a visible
        // black flash. Setting the background explicitly at the Window
        // level, this early, is the standard mitigation — it doesn't wait
        // on theme attribute resolution the way XML-declared
        // android:windowBackground can on a fast recreate.
        window.setBackgroundDrawableResource(R.color.splash_background)
        enableEdgeToEdge()

        // Consent first, ads second — always in that order, and from an
        // Activity because UMP needs one to present its form. This used to
        // run unconditionally in the Application class, which meant ad
        // requests went out in the EEA before anyone had been asked, in
        // breach of both GDPR and AdMob's own policy. ensureConsent resolves
        // silently for players in regions with no form requirement.
        consentManager.ensureConsent(this) {
            adManager.initializeIfConsented(consentManager)
        }

        // Read once, here, rather than observed: the start destination is
        // fixed for the lifetime of this NavHost, and completing the
        // tutorial navigates away explicitly instead of re-deciding it.
        val tutorialCompleted = settingsRepository.tutorialCompleted
        setContent {
            CizimHafizaTheme {
                // The app's cream page color, not Surface's default white:
                // this is what shows through anywhere a screen's own
                // background doesn't reach (behind the status bar during a
                // transition, for a frame on first draw), and white there
                // read as a seam against every page.
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CizimHafizaNavGraph(
                        onNavControllerReady = { navController = it },
                        tutorialCompleted = tutorialCompleted
                    )
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
