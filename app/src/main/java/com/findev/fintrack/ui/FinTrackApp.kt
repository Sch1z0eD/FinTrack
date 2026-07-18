package com.findev.fintrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.findev.fintrack.ui.navigation.FinTrackDestination
import com.findev.fintrack.ui.navigation.FinTrackNavHost

private val BarHeight = 64.dp
private val BarSideMargin = 16.dp
private val BarBottomMargin = 12.dp
private val BarCorner = 28.dp
private val BarBlurRadius = 24.dp

/**
 * Vertical space the floating bar covers, including the gesture inset underneath it.
 * Top-level screens add this to their bottom padding so the last item can be scrolled
 * clear of the glass instead of ending up permanently behind it.
 */
@Composable
fun floatingBottomBarSpace(): Dp =
    BarHeight + BarBottomMargin +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun FinTrackApp(
    navController: NavHostController = rememberNavController(),
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {},
) {
    // A deep link from the widget arrives as a route to open once, on top of the start screen.
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.navigate(pendingRoute)
            onPendingRouteHandled()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Full-screen routes such as quick entry hide the bottom bar.
    val isTopLevelRoute = FinTrackDestination.entries.any { it.route == currentRoute }

    // The screen is drawn into this layer so the bar can re-draw a blurred slice of it.
    val backdrop = rememberGraphicsLayer()
    // Bumped on every content draw; the bar reads it so scrolling keeps its blur fresh.
    var backdropVersion by remember { mutableIntStateOf(0) }

    // Without the old root Scaffold nothing paints the window background any more.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        FinTrackNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    backdrop.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop)
                    // Signal the bar that the captured screen changed. The read has to
                    // stay unobserved, or this draw invalidates itself and the app
                    // repaints forever at full frame rate.
                    Snapshot.withoutReadObservation { backdropVersion++ }
                }
                // Must sit inside the recording node (below drawWithContent in the chain),
                // or the captured layer stays transparent wherever a screen paints nothing
                // and the glass shows bare scrim instead of a blurred background.
                .background(MaterialTheme.colorScheme.background)
                // With the bar floating, top-level screens run to the bottom edge and
                // pad themselves; the rest still need the gesture inset kept clear.
                .windowInsetsPadding(
                    if (isTopLevelRoute) WindowInsets.statusBars else WindowInsets.systemBars,
                ),
        )

        if (isTopLevelRoute) {
            GlassBottomBar(
                navController = navController,
                backdrop = backdrop,
                backdropVersion = { backdropVersion },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun GlassBottomBar(
    navController: NavHostController,
    backdrop: androidx.compose.ui.graphics.layer.GraphicsLayer,
    backdropVersion: () -> Int,
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val shape = RoundedCornerShape(BarCorner)
    val blurred = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { BarBlurRadius.toPx() }
    var barOrigin by remember { mutableStateOf(Offset.Zero) }

    // Tint over the blur: without it the glass reads as muddy rather than frosted.
    val scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
    val hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = BarSideMargin, end = BarSideMargin, bottom = BarBottomMargin)
            .fillMaxWidth()
            .height(BarHeight)
            .onGloballyPositioned { barOrigin = it.positionInRoot() }
            .shadow(elevation = 16.dp, shape = shape, clip = false)
            .clip(shape)
            .drawBehind {
                backdropVersion()
                blurred.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                blurred.record(size = backdrop.size) { drawLayer(backdrop) }
                // The layer holds the whole screen, so shift it to line the bar's
                // slice up with the clip before drawing.
                translate(-barOrigin.x, -barOrigin.y) { drawLayer(blurred) }
                drawRect(scrim)
            }
            .border(width = 1.dp, color = hairline, shape = shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        FinTrackDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true

            GlassBarItem(
                selected = selected,
                label = stringResource(destination.labelRes),
                icon = destination.icon,
                onClick = {
                    if (!selected) {
                        navController.navigate(destination.route) {
                            // Keep a single copy of each top-level screen and preserve its state.
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun RowScope.GlassBarItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "barItemTint",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}
