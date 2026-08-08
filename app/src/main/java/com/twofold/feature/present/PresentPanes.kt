package com.twofold.feature.present

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twofold.core.design.LocalTwofoldColors

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
    val title: String,
    val body: String,
    val pageNumber: Int,
    val pageCount: Int,
    val highlights: List<Rect> = emptyList(),
    val legibility: Float = 1f,
)

/** The agent's view: the same page, plus the private layer. Composition, not inheritance. */
data class AgentPage(
    val page: ClientPage,
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
            .background(colors.paperRaised)
            .padding(horizontal = 32.dp, vertical = 28.dp)
    ) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${page.pageNumber} / ${page.pageCount}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
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
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = page.page.title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )

        Text(
            text = page.notes,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.note,
        )

        page.talkTrack.forEach { line ->
            Text(
                text = "— $line",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
            )
        }
    }
}

/** Shown when the device is folded or held: single pane, private by definition. */
@Composable
fun PreparePane(hint: String, modifier: Modifier = Modifier) {
    val colors = LocalTwofoldColors.current

    Box(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}
