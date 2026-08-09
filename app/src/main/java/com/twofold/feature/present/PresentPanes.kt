package com.twofold.feature.present

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.twofold.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twofold.data.document.Clause
import androidx.compose.ui.unit.sp
import com.twofold.core.design.LocalTwofoldColors

/**
 * The client's reading size, chosen from the physical distance rather than from taste.
 *
 * 30sp is 4.8mm, about 13.5pt on the glass. Book text is 10–11pt and is set for a reading distance
 * of ~35cm; someone across a table is at 60–70cm, which needs roughly twice the angular size. 22sp
 * was the first choice here and measured 9.9pt — perfectly readable in the hand and too small for
 * the one situation this app exists for.
 *
 * It still fits: 116mm of usable width at 30sp is about 48 characters a line, and 49mm of height
 * about seven lines — roughly 340 characters, which covers a normal policy clause. The legibility
 * control doubles it again for a client without their glasses.
 *
 * There is no spotlight any more, and its absence is the point: dimming part of a page to draw the
 * eye only made sense while the client was looking at a whole page. Now that they are shown one
 * clause at a time, moving to a clause *is* pointing at it, so the feature dissolved rather than
 * needing to be ported.
 */
private val CLIENT_BODY_SIZE = 30.sp
private val CLIENT_BODY_LINE_HEIGHT = 42.sp

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
    /** The clause being read. Text, not a page image — see [ClientPane]. */
    val clause: Clause?,
    val clauseNumber: Int,
    val clauseCount: Int,
    /** 1.0–2.0. Raised by the agent for a client who is reading without their glasses. */
    val legibility: Float = 1f,
    /**
     * True while the document is still being read.
     *
     * Extraction takes a few seconds the first time, and without this the client's half sat blank
     * under a counter reading "1 / 0" — which looks like a broken app to the one person who must
     * not doubt it.
     */
    val isPreparing: Boolean = false,
)

/** The agent's view: the same page, plus the private layer. Composition, not inheritance. */
data class AgentPage(
    val page: ClientPage,
    val documentTitle: String,
    val notes: String,
    val talkTrack: List<String>,
    /** The full page as rendered. Only the agent sees this — see [AgentPane]. */
    val pageBitmap: Bitmap? = null,
    val pageNumber: Int = 1,
    val pageCount: Int = 1,
    /** Clauses came from OCR, so treat the wording as approximate. Agent's half only. */
    val textIsApproximate: Boolean = false,
    /** The client tapped to have this clause explained. */
    val wasQuestioned: Boolean = false,
    /** How many they have asked about so far — all of which reach the signed copy. */
    val questionCount: Int = 0,
    /**
     * Every clause label, for the jump strip — agent's half only.
     *
     * Labels rather than whole clauses: this is a way to *get* somewhere, not a second reader, and
     * the agent's half is the same 130 × 63 mm as the client's.
     */
    val clauseLabels: List<String> = emptyList(),
    /**
     * The clause in its original wording.
     *
     * Separate from `page.clause`, which may be translated for the client. The agent must always
     * see the document as written — they are the one who has to stand behind it, and an agent
     * reading a machine translation of their own policy back to themselves has lost the thread.
     */
    val sourceClause: Clause? = null,
)

/**
 * The far half — what the client reads.
 *
 * Rendered rotated 180° by TwofoldScaffold so it reads right-side-up to the person opposite. No
 * chrome, no toolbar, no branding: one clause and where they are in the document.
 *
 * **This shows text, not a picture of the page, and that is the whole reason the product works.**
 * This half is 130 × 63 mm. An A4 page fitted into it renders at 20% scale, which puts a 10pt
 * clause on the glass at 2pt — unreadable by anyone, at any age, at arm's length. Measured, not
 * estimated. Set the same clause as text at 22sp and it is comfortably readable across a table,
 * which is the entire point of the exercise.
 */
@Composable
fun ClientPane(
    page: ClientPage,
    modifier: Modifier = Modifier,
    /**
     * The client's own button, already in the client's language.
     *
     * Passed in rather than added to [ClientPage] on purpose. A callback carries no data, so the
     * leak guarantee is untouched — the type still has nowhere to put a private note. The moment
     * this becomes a *field*, that stops being true.
     */
    explainLabel: String = "",
    explainAcknowledged: Boolean = false,
    onExplain: (() -> Unit)? = null,
) {
    val colors = LocalTwofoldColors.current
    val clause = page.clause

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        if (page.isPreparing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.preparing_document),
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = CLIENT_BODY_SIZE * page.legibility,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // A long clause scrolls rather than shrinking. Truncating a contract clause to make
                // it fit would be worse than asking someone to scroll.
                .verticalScroll(rememberScrollState())
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                clause?.heading?.takeIf { it.isNotBlank() }?.let { heading ->
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * page.legibility,
                        color = colors.ink,
                    )
                }
                Text(
                    text = clause?.body.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    // Bigger than any phone UI would normally use, and the agent can raise it
                    // further for a client who left their glasses at home.
                    fontSize = CLIENT_BODY_SIZE * page.legibility,
                    lineHeight = CLIENT_BODY_LINE_HEIGHT * page.legibility,
                    color = colors.ink,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Understated on purpose. A prominent button would read as the app inviting the client
            // to doubt the person sitting opposite; a quiet one reads as permission to ask, which
            // is the thing that is actually missing from the conversation.
            if (onExplain != null && explainLabel.isNotBlank()) {
                val spoken = stringResource(R.string.client_explain_spoken)
                TextButton(
                    onClick = onExplain,
                    modifier = Modifier.semantics { contentDescription = spoken },
                ) {
                    Text(
                        text = explainLabel,
                        style = MaterialTheme.typography.labelLarge,
                        // Once asked, it stays marked. Tapping again and seeing nothing change is
                        // how someone concludes the button did nothing the first time.
                        color = if (explainAcknowledged) colors.seal else colors.inkMuted,
                    )
                }
            } else {
                Spacer(Modifier.height(0.dp))
            }

            Text(
                text = stringResource(R.string.clause_counter, page.clauseNumber, page.clauseCount),
                style = MaterialTheme.typography.labelLarge,
                color = colors.inkMuted,
                // Announced by the live region above.
                modifier = Modifier.clearAndSetSemantics {},
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The near half. The whole page, plus everything the client must never see.
 *
 * The asymmetry is sharper than it was. The client sees one clause set large; the agent sees the
 * entire page as rendered, keeping the context and the layout — which is exactly what a client
 * cannot be given, because at this physical size a full page is readable to nobody. Each half now
 * shows the form of the document that suits the person looking at it.
 */
@Composable
fun AgentPane(
    page: AgentPage,
    modifier: Modifier = Modifier,
    onSelectClause: (Int) -> Unit = {},
) {
    val colors = LocalTwofoldColors.current

    // A Row, not a Column. This half of a flat foldable is a wide landscape strip; stacking the
    // page above the notes left most of the width empty and squeezed both.
    Row(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        page.pageBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(
                    R.string.agent_page_description, page.pageNumber, page.pageCount,
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .background(colors.paperRaised),
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

            if (page.textIsApproximate) {
                Text(
                    text = stringResource(R.string.text_is_approximate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.seal,
                )
            }

            // What the client is reading right now — so the agent knows what is in front of them
            // without looking up from their own half.
            (page.sourceClause ?: page.page.clause)?.let { clause ->
                Text(
                    text = stringResource(
                        R.string.showing_clause, clause.label, clause.heading.orEmpty(),
                    ).trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.seal,
                )
            }

            // The whole point of the client's button: it has to land somewhere the agent will see
            // it mid-sentence, without looking up.
            if (page.wasQuestioned) {
                Text(
                    text = stringResource(R.string.they_asked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.seal,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }

            if (page.questionCount > 0) {
                Text(
                    text = stringResource(R.string.questions_counted, page.questionCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                )
            }

            // The reason this exists: an agent's own note routinely says "lead with clause 3, not
            // the benefits table" — and until now the only way there was tapping Next nine times
            // while a client watched. A meeting does not run in document order.
            if (page.clauseLabels.size > 1) {
                ClauseStrip(
                    labels = page.clauseLabels,
                    currentIndex = page.page.clauseNumber - 1,
                    onSelect = onSelectClause,
                )
            }

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

/**
 * Jump straight to a clause.
 *
 * Numbers only, and horizontally scrollable. This half is no bigger than the client's, so a list
 * with headings would either take the space the page needs or be unreadable — and the agent already
 * knows what clause 3 is, which is precisely why they want to go there.
 *
 * Never rendered on the client's half. Not because it would leak anything, but because a client
 * watching someone skip past clauses is being shown the shape of the sales pitch rather than the
 * document.
 */
@Composable
private fun ClauseStrip(
    labels: List<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalTwofoldColors.current

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val isCurrent = index == currentIndex
            // Spoken by position, not by label. A document can leave several stretches unnumbered,
            // and "go to clause —" three times over is no use to anyone listening. The position is
            // the one thing guaranteed unique.
            val spoken = stringResource(R.string.jump_to_clause, index + 1, labels.size)

            TextButton(
                onClick = { onSelect(index) },
                modifier = Modifier.semantics { contentDescription = spoken },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCurrent) colors.seal else colors.inkMuted,
                )
            }
        }
    }
}
