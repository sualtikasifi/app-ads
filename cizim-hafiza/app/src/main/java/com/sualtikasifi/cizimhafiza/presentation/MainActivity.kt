package com.sualtikasifi.cizimhafiza.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import com.sualtikasifi.cizimhafiza.presentation.navigation.CizimHafizaNavGraph
import com.sualtikasifi.cizimhafiza.presentation.theme.CizimHafizaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
