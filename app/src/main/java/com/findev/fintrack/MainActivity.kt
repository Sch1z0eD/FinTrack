package com.findev.fintrack

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.ui.FinTrackApp
import com.findev.fintrack.ui.ThemeViewModel
import com.findev.fintrack.ui.theme.FinTrackTheme
import com.findev.fintrack.ui.theme.isDark
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Set by the widget's deep-link intent; consumed once the NavHost has navigated. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = intent.routeExtra()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = themeMode.isDark()

            // System bar icons follow the app's theme, not the system's: forcing a light
            // app theme on a dark phone would otherwise leave white icons on white.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                )
                onDispose {}
            }

            FinTrackTheme(themeMode = themeMode) {
                FinTrackApp(
                    pendingRoute = pendingRoute,
                    onPendingRouteHandled = { pendingRoute = null },
                )
            }
        }
    }

    /** The activity is single-top per the launcher default; a widget tap while it is open lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.routeExtra()
    }

    private fun Intent.routeExtra(): String? = getStringExtra(EXTRA_START_ROUTE)

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
    }
}
