package com.sualtikasifi.cizimhafiza.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.ads.AdManager
import com.sualtikasifi.cizimhafiza.ads.ConsentManager
import com.sualtikasifi.cizimhafiza.data.repository.GoogleSignInLauncher
import com.sualtikasifi.cizimhafiza.presentation.navigation.CizimHafizaNavGraph
import com.sualtikasifi.cizimhafiza.presentation.splash.BrandSplash
import com.sualtikasifi.cizimhafiza.presentation.theme.CizimHafizaTheme
import com.sualtikasifi.cizimhafiza.util.MusicPlayer
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
    @Inject lateinit var musicPlayer: MusicPlayer
    @Inject lateinit var googleSignInLauncher: GoogleSignInLauncher

    private var navController: NavHostController? = null

    private companion object {
        private const val TAG = "MainActivity"

        /** Matches BrandSplash's own opening hold, so the two overlap exactly. */
        const val SPLASH_EXIT_MILLIS = 180L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate()/setContent — shows the branded
        // splash (see Theme.Karalak.Splash) until Compose draws its first
        // frame instead of a plain platform default screen.
        installSplashScreen().setOnExitAnimationListener { splash ->
            // A plain cross-fade, and deliberately nothing more. BrandSplash
            // (see presentation/splash/) paints this exact cream field with
            // this exact mark at this exact size as its own first frame, so
            // there is nothing for the hand-off to reveal — moving or
            // scaling the system splash on the way out would only break a
            // seam that is otherwise invisible. The mark rises and the
            // pencil starts writing after this fade has finished, not
            // during it.
            //
            // Note what is NOT read here: splash.iconView. On API 31+ the
            // platform owns the splash view and getIconView() is documented
            // @Nullable, so androidx's `platformView.iconView!!` can throw
            // before the app has drawn a single frame — a hand-over with no
            // icon (the one the installer gives on the very first launch
            // after install is one) took the launch down that way. The whole
            // block stays inside runCatching regardless: a cosmetic
            // transition must never be able to fail a launch.
            runCatching {
                ObjectAnimator.ofFloat(splash.view, View.ALPHA, 1f, 0f).apply {
                    duration = SPLASH_EXIT_MILLIS
                    interpolator = AccelerateDecelerateInterpolator()
                    // remove() on BOTH ends: an animation cancelled mid-flight
                    // (the activity going away under it) must still hand the
                    // window back, or the splash stays frozen over the app.
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = splash.remove()
                        override fun onAnimationCancel(animation: Animator) = splash.remove()
                    })
                    start()
                }
            }.onFailure {
                // No cross-fade, but the app is visible and usable — which
                // is the only part of this that was ever load-bearing.
                Log.w(TAG, "Splash exit animation skipped", it)
                runCatching { splash.remove() }
            }
        }
        super.onCreate(savedInstanceState)
        // Follows the sound/music switches from here on; start() itself is
        // idempotent, and onResume/onPause below keep the soundtrack tied to
        // the app actually being on screen rather than to the process.
        musicPlayer.start()
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
                    GoogleSignInLauncherHost(googleSignInLauncher)
                    // Over the app, not instead of it: the nav graph above
                    // composes and draws underneath while this plays, so the
                    // opening costs no startup time. rememberSaveable, so a
                    // rotation or a language-change recreate does not replay
                    // it — only a genuinely cold start does.
                    var brandSplashVisible by rememberSaveable { mutableStateOf(true) }
                    if (brandSplashVisible) {
                        BrandSplash(onFinished = { brandSplashVisible = false })
                    }
                }
            }
        }
    }

    // launchMode="singleTask" (see AndroidManifest.xml) means a deep-link tap
    // while the app is already running reuses this Activity instance and
    override fun onResume() {
        super.onResume()
        musicPlayer.onAppForegrounded()
    }

    override fun onPause() {
        super.onPause()
        // Not a media app: the soundtrack belongs to a game being looked at,
        // so it stops the moment the player leaves rather than playing on
        // over whatever they switched to.
        musicPlayer.onAppBackgrounded()
    }

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

/**
 * Registers this Activity instance's launcher with [googleSignInLauncher] —
 * see that class for why the bridge exists at all. `registerForActivityResult`
 * has to run before the Activity reaches STARTED, which composition already
 * guarantees here the same way it does for [RequestNotificationPermissionOnce]
 * above; unbinding on dispose stops a torn-down Activity's dead launcher
 * reference from lingering in a singleton that outlives it.
 */
@Composable
private fun GoogleSignInLauncherHost(googleSignInLauncher: GoogleSignInLauncher) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> googleSignInLauncher.onResult(result) }

    DisposableEffect(launcher) {
        googleSignInLauncher.bind(launcher)
        onDispose { googleSignInLauncher.unbind(launcher) }
    }
}
