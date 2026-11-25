package com.karelisio.patoune

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.karelisio.patoune.ui.PatouneRoot
import com.karelisio.patoune.ui.theme.PatouneTheme

class MainActivity : ComponentActivity() {
    /** Action demandée par un raccourci, la tuile ou le widget. */
    private val launchAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        launchAction.value = intent?.getStringExtra(EXTRA_ACTION) ?: intent?.data?.host
        setContent {
            PatouneTheme {
                PatouneRoot(
                    launchAction = launchAction.value,
                    onActionConsumed = { launchAction.value = null },
                    onReady = { ready = true },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchAction.value = intent.getStringExtra(EXTRA_ACTION) ?: intent.data?.host
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_BREATH = "breath"
        const val ACTION_EMERGENCY = "emergency"
        const val ACTION_CRISIS = "crisis"
    }
}
