package com.twofold.core.fold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

/**
 * Splits the window at the physical crease into a far half and a near half, counter-rotating the
 * far half so it reads right-side-up to the person sitting opposite.
 *
 * The split comes from [FoldState.creaseFraction] — the real reported crease position — never from
 * a hardcoded half. Foldables do not all crease at the midpoint, and the ones that do are not
 * guaranteed to keep doing so.
 *
 * When the device is not in Twofold mode this degrades to [nearPane] filling the window, so the
 * same composable tree runs unchanged on a flat-screen phone.
 *
 * @param agentOnNearHalf which half belongs to the agent. There is no sensor for which side of the
 *        table someone is sitting on, so this is a user setting with a swap control. Guessing and
 *        being wrong is worse than asking once.
 */
@Composable
fun TwofoldScaffold(
    foldState: FoldState,
    modifier: Modifier = Modifier,
    agentOnNearHalf: Boolean = true,
    creaseColor: androidx.compose.ui.graphics.Color,
    farPane: @Composable () -> Unit,
    nearPane: @Composable () -> Unit,
) {
    val creaseFraction = foldState.creaseFraction

    if (!foldState.isTwofold || creaseFraction == null) {
        Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { nearPane() }
        return
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val totalHeight = maxHeight
        val farHeight = totalHeight * creaseFraction

        Column(Modifier.fillMaxSize()) {
            // Far half: rotated 180° so the client reads it the right way up.
            //
            // The inset padding sits *outside* the rotation and *inside* the fixed height, and both
            // halves of that matter. Inside the height, because the crease position is a fraction of
            // the whole window — inset the window first and the split lands off the physical fold.
            // Outside the rotation, because insets are in screen space: this pane's own "bottom" is
            // the top of the screen, so padding applied within the rotated content would go to the
            // wrong edge entirely. That was not theoretical. Under the edge-to-edge behaviour that
            // is now mandatory, the client's button sat underneath the status bar, which quietly ate
            // every tap on it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(farHeight)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                        .rotate(180f)
                ) {
                    if (agentOnNearHalf) farPane() else nearPane()
                }
            }

            // The crease. Not hidden, not decorated — a single hairline on the fold itself.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(CREASE_HAIRLINE)
                    .background(creaseColor)
            )

            // weight(1f), not fillMaxSize() — inside a Column the latter would take the whole
            // window height and push the near pane off the bottom of the screen.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    )
            ) {
                if (agentOnNearHalf) nearPane() else farPane()
            }
        }
    }
}

private val CREASE_HAIRLINE = 1.dp
