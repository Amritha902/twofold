package com.twofold.feature.present

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.twofold.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twofold.core.design.LocalTwofoldColors

/**
 * How far the spotlight dims the rest of the page. Enough to guide, not enough to obscure.
 *
 * 0.42 was the first guess and it was wrong — on a real page it greyed the document out, and the
 * client lost the ability to see anything except the lit clause. Someone reading a contract needs
 * to keep the whole page in view while their eye is drawn to one part of it.
 */
private const val SPOTLIGHT_DIM = 0.26f

/**
 * Two drag points to a normalised rect, ordered and clamped.
 *
 * Ordering matters: dragging up-and-left is as natural as down-and-right, and an unordered rect
 * would have right < left and silently draw nothing.
 */
private fun normalisedRect(start: Offset, end: Offset, size: Size): Rect = Rect(
    left = (minOf(start.x, end.x) / size.width).coerceIn(0f, 1f),
    top = (minOf(start.y, end.y) / size.height).coerceIn(0f, 1f),
    right = (maxOf(start.x, end.x) / size.width).coerceIn(0f, 1f),
    bottom = (maxOf(start.y, end.y) / size.height).coerceIn(0f, 1f),
)

/**
 * Everything the client is allowed to see.
 *
 * This type is the leak guarantee. It has no field for notes, no field for a talk track, and no
 * escape hatch — so [ClientPane] cannot render private content even if someone later wires it up
 * carelessly. A styling bug can leak a colour. It must not be able to leak a note in front of a
 * paying customer.
 *
 * If you find yourself wanting to add a field here, that is the signal to stop.
 */
data class ClientPage(
    val bitmap: Bitmap?,
    val pageNumber: Int,
    val pageCount: Int,
    /** 1.0–2.0. Raised by the agent for a client who is reading without their glasses. */
    val legibility: Float = 1f,
    /** Normalised (0..1) region of the page to draw the eye to, or null. */
    val spotlight: Rect? = null,
)

/** The agent's view: the same page, plus the private layer. Composition, not inheritance. */
data class AgentPage(
    val page: ClientPage,
    val documentTitle: String,
    val notes: String,
    val talkTrack: List<String>,
)

/**
 * The far half. Rendered rotated 180° by TwofoldScaffold so it reads right-side-up to the person
 * across the table. No chrome, no toolbar, no branding — the document and nothing else.
 */
@Composable
fun ClientPane(page: ClientPage, modifier: Modifier = Modifier) {
    val colors = LocalTwofoldColors.current

    Column(
        modifier
            .fillMaxSize()
            // The warm ground, not white. The page below sits ON this, so it reads as a sheet of
            // paper on a desk rather than a small image floating in a large empty box.
            .background(colors.paper)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            page.bitmap?.let { bitmap ->
                // The sheet is exactly the page's shape. Sizing it by aspect ratio rather than
                // letting ContentScale.Fit letterbox inside a full-width box is what makes the
                // spotlight land on the page instead of on the empty margins beside it.
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                        .scale(page.legibility)
                        .background(colors.paperRaised),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        // The page IS the content of this half, so it gets the description and the
                        // page counter below is hidden from screen readers to avoid saying "2 of 3"
                        // twice. The text of a scanned page can't be read out, but knowing where
                        // you are in the document is most of what a client needs.
                        contentDescription = stringResource(
                            R.string.page_description, page.pageNumber, page.pageCount,
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            // Turning a page otherwise announces nothing: focus stays on the
                            // button and the reader has no idea the document moved.
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        // Fit, never Crop. Cropping a legal document silently hides text from the
                        // one person who most needs to read all of it.
                        contentScale = ContentScale.Fit,
                    )

                    // The spotlight dims everything except one region. A wash rather than a border,
                    // because a box drawn round a clause in a contract looks like the app flagging
                    // a problem; a soft dim just moves the eye.
                    page.spotlight?.let { region ->
                        Canvas(Modifier.fillMaxSize()) {
                            val focus = Rect(
                                left = region.left * size.width,
                                top = region.top * size.height,
                                right = region.right * size.width,
                                bottom = region.bottom * size.height,
                            )
                            val dim = Color.Black.copy(alpha = SPOTLIGHT_DIM)

                            drawRect(dim, size = Size(size.width, focus.top))
                            drawRect(
                                dim,
                                topLeft = Offset(0f, focus.bottom),
                                size = Size(size.width, size.height - focus.bottom),
                            )
                            drawRect(
                                dim,
                                topLeft = Offset(0f, focus.top),
                                size = Size(focus.left, focus.height),
                            )
                            drawRect(
                                dim,
                                topLeft = Offset(focus.right, focus.top),
                                size = Size(size.width - focus.right, focus.height),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.page_counter, page.pageNumber, page.pageCount),
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
            // Already announced by the page image above.
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * The near half. Same document, plus everything the client must never see.
 *
 * @param onSpotlight receives a normalised region when the agent drags across their copy of the
 *        page. Dragging on your own half to light something up on theirs is the closest thing the
 *        product has to pointing at a page across a table, which is what it replaces.
 */
@Composable
fun AgentPane(
    page: AgentPage,
    modifier: Modifier = Modifier,
    onSpotlight: (Rect?) -> Unit = {},
) {
    val colors = LocalTwofoldColors.current

    // A Row, not a Column. This half of a flat foldable is a wide landscape strip; stacking the
    // page above the notes left most of the width empty and squeezed both. Side by side, the page
    // gets its full height and the notes get the space they need to be read at a glance.
    Row(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // The agent's own copy of the page. Without it there is nothing to point at, and "cast a
        // spotlight" becomes a control with no target.
        page.page.bitmap?.let { bitmap ->
            var paneSize by remember { mutableStateOf(Size.Zero) }
            var dragOrigin by remember { mutableStateOf<Offset?>(null) }

            // Resolved out here: semantics {} is not a composable scope.
            val clearHighlightLabel = stringResource(R.string.clear_highlight)

            Image(
                bitmap = bitmap.asImageBitmap(),
                // This copy is a control, not just content — you drag on it to light a region on
                // the client's half. Say what it does, since a drag gesture is invisible.
                contentDescription = stringResource(
                    R.string.agent_page_description, page.page.pageNumber, page.page.pageCount,
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    // Same shape as the client's sheet, so a drag here maps to what they see.
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .background(colors.paperRaised)
                    // A drag cannot be performed with a screen reader, so the one part of this that
                    // can be exposed as a discrete action is exposed. Casting a highlight still
                    // needs sight; clearing one should not.
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(clearHighlightLabel) {
                                onSpotlight(null)
                                true
                            }
                        )
                    }
                    .onSizeChanged { paneSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(bitmap) {
                        detectDragGestures(
                            onDragStart = { start -> dragOrigin = start },
                            onDrag = { change, _ ->
                                change.consume()
                                if (paneSize.width <= 0f || paneSize.height <= 0f) return@detectDragGestures
                                val origin = dragOrigin ?: return@detectDragGestures
                                onSpotlight(
                                    normalisedRect(origin, change.position, paneSize)
                                )
                            },
                            // Deliberately no onDragEnd clear: the spotlight stays lit until the
                            // page turns. An agent lets go of the screen to gesture with their
                            // hand, and the light going out at that moment would be exactly wrong.
                            onDragEnd = { dragOrigin = null },
                        )
                    },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = page.documentTitle,
                style = MaterialTheme.typography.labelLarge,
                color = colors.inkMuted,
            )

            if (page.notes.isNotBlank()) {
                Text(
                    text = page.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.note,
                )
            }

            page.talkTrack.forEach { line ->
                Text(
                    text = stringResource(R.string.talk_track_line, line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                )
            }
        }
    }
}

/** Shown when the device is folded or held: single pane, private by definition. */
@Composable
fun PreparePane(
    hint: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalTwofoldColors.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
