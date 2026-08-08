package com.twofold.feature.present

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.twofold.core.design.LocalTwofoldColors

/** How far the spotlight dims the rest of the page. Enough to guide, not enough to obscure. */
private const val SPOTLIGHT_DIM = 0.42f

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
            .background(colors.paper)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .background(colors.paperRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (page.bitmap != null) {
                Image(
                    bitmap = page.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(page.legibility),
                    // Fit, never Crop. Cropping a legal document silently hides text from the one
                    // person who most needs to read all of it.
                    contentScale = ContentScale.Fit,
                )
            }

            // The spotlight dims everything except one region. Drawn as a wash rather than a
            // border because a border round a clause in a contract looks like the app marking it
            // as a problem; a soft dim just moves the eye.
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

        Spacer(Modifier.height(10.dp))

        Text(
            text = "${page.pageNumber} / ${page.pageCount}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
        )
    }
}

/** The near half. Same document, plus everything the client must never see. */
@Composable
fun AgentPane(page: AgentPage, modifier: Modifier = Modifier) {
    val colors = LocalTwofoldColors.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = page.documentTitle,
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (page.notes.isNotBlank()) {
                Text(
                    text = page.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.note,
                )
            }

            page.talkTrack.forEach { line ->
                Text(
                    text = "— $line",
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
