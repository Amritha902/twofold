package com.twofold

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revenuecat.purchases.Package
import com.twofold.core.design.LocalTwofoldColors
import com.twofold.core.design.TwofoldTheme
import com.twofold.core.fold.DeviceMode
import com.twofold.core.fold.FoldState
import com.twofold.core.fold.FoldStateTracker
import com.twofold.core.fold.FlexScaffold
import com.twofold.core.fold.TwofoldScaffold
import com.twofold.data.document.ClientLanguage
import com.twofold.data.document.DocumentRef
import com.twofold.data.document.ModelState
import com.twofold.data.document.SpeechReadiness
import com.twofold.data.document.DocumentRepository
import com.twofold.feature.paywall.Entitlements
import com.twofold.feature.paywall.PaywallScreen
import com.twofold.feature.present.AgentPage
import com.twofold.feature.present.AgentPane
import com.twofold.feature.present.ClientPage
import com.twofold.feature.present.ClientPane
import com.twofold.feature.present.PreparePane
import com.twofold.feature.present.PresentState
import com.twofold.data.session.MeetingKind
import com.twofold.data.session.Session
import com.twofold.feature.sessions.FollowUpList
import com.twofold.feature.sign.SignaturePad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val foldStates = FoldStateTracker(this).foldState()

        setContent {
            TwofoldTheme {
                TwofoldApp(foldStates)
            }
        }
    }
}

@Composable
private fun TwofoldApp(foldStates: Flow<FoldState>) {
    val foldState by foldStates.collectAsStateWithLifecycle(initialValue = FoldState())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { PresentState(context, scope) }
    val entitlements = remember { Entitlements.create(context) }
    val nudge = remember { (context.applicationContext as TwofoldApplication).nudge }
    val isPro by entitlements.isPro.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch { state.importAndOpen(uri) }
    }
    val openPicker = { picker.launch(arrayOf("application/pdf")) }

    var followUps by remember { mutableStateOf<List<Session>>(emptyList()) }

    // Reopen the most recent document on launch so an agent who opens the app at a client's table
    // is one tap from presenting, not four.
    LaunchedEffect(Unit) {
        if (state.document == null) {
            DocumentRepository(context).list().firstOrNull()?.let { state.open(it) }
        }
        followUps = state.unsignedSessions()
    }

    // PdfRenderer holds a file descriptor. Without this it survives the screen and leaks.
    DisposableEffect(Unit) {
        onDispose { state.closeCurrent() }
    }

    val document = state.document

    if (document == null) {
        PreparePane(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            hint = state.error ?: stringResource(R.string.import_prompt),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openPicker() }) { Text(stringResource(R.string.choose_pdf)) }
                    // Offered, not forced. Silently importing a sample into someone's document
                    // library is the app deciding it knows better than they do what is in there.
                    TextButton(onClick = { scope.launch { state.openSample() } }) {
                        Text(stringResource(R.string.see_how_it_works))
                    }
                }
            },
        )
        return
    }

    // The client reads a clause; the agent sees the page it is printed on. One index drives both,
    // so the two halves cannot drift apart.
    val clientPage = ClientPage(
        clause = state.currentClause,
        clauseNumber = state.clauseIndex + 1,
        clauseCount = state.clauses.size,
        legibility = state.legibility,
        isPreparing = state.isLoading,
    )

    val agentPage = AgentPage(
        page = clientPage,
        documentTitle = document.title,
        notes = state.currentNotes.note,
        talkTrack = state.currentNotes.talkTrack,
        pageBitmap = state.rendered.bitmap,
        pageNumber = state.rendered.index + 1,
        pageCount = state.pageCount,
        textIsApproximate = state.textIsApproximate,
        sourceClause = state.sourceClause,
        clauseLabels = state.clauses.map { it.label },
        wasQuestioned = state.currentClauseWasQuestioned,
        questionCount = state.questionedClauses.size,
    )

    // A meeting starts when the phone is put down in front of someone and ends when it is picked
    // up. Tying the log to posture rather than to app launch keeps it to real client meetings.
    LaunchedEffect(foldState.mode, document.id) {
        if (foldState.mode == DeviceMode.TWOFOLD) {
            state.beginSession()
        } else {
            state.endSession()

            // The meeting just ended, so this is the moment the follow-up list changes and the only
            // moment asking to be reminded about it explains itself. Never while presenting — see
            // NudgePolicy.
            followUps = state.unsignedSessions()
            nudge.publish(followUps.size)
            nudge.askIfItIsTheRightMoment(unsignedCount = followUps.size, isPresenting = false)
        }
    }

    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var padSize by remember { mutableStateOf(Size.Zero) }

    var showPaywall by remember { mutableStateOf(false) }
    var offeringPackages by remember { mutableStateOf<List<Package>>(emptyList()) }
    var isPurchasing by remember { mutableStateOf(false) }

    // `&& !isPro` so the paywall closes the instant the entitlement lands, from whichever source
    // reports it first — the purchase call returning, or the updated-customer-info listener. Waiting
    // only on the purchase result is what left it standing over a completed sale.
    if (showPaywall && !isPro) {
        PaywallScreen(
            packages = offeringPackages,
            isPurchasing = isPurchasing,
            onPurchase = { option ->
                val activity = context as? Activity ?: return@PaywallScreen
                scope.launch {
                    isPurchasing = true
                    val bought = entitlements.purchase(activity, option)
                    isPurchasing = false
                    // Only on success. Dismissing after a failed or cancelled purchase would take
                    // away the one screen they could retry from, which reads as the app having
                    // taken the money and moved on.
                    if (bought) showPaywall = false
                }
            },
            onDismiss = { showPaywall = false },
        )
        return
    }

    when (foldState.mode) {
        // Flat on a table between two people: the far half is theirs, the near half is yours.
        DeviceMode.TWOFOLD -> TwofoldScaffold(
            foldState = foldState,
            creaseColor = LocalTwofoldColors.current.rule,
            farPane = {
                if (state.isSigning) {
                    SignaturePad(
                        signerName = state.signerName,
                        onSignatureChanged = { captured, size ->
                            strokes = captured
                            padSize = size
                        },
                    )
                } else {
                    ClientPane(
                        page = clientPage,
                        explainLabel = state.explainLabel,
                        explainAcknowledged = state.currentClauseWasQuestioned,
                        onExplain = { state.markCurrentClauseQuestioned() },
                    )
                }
            },
            nearPane = {
                Column(Modifier.fillMaxWidth()) {
                    AgentPane(
                        page = agentPage,
                        modifier = Modifier.weight(1f),
                        onSelectClause = { scope.launch { state.goToClause(it) } },
                    )
                    if (state.isSigning) {
                        SigningControls(
                            canComplete = strokes.isNotEmpty(),
                            onCancel = { state.cancelSigning() },
                            onDone = {
                                scope.launch {
                                    state.completeSigning(
                                        strokes = strokes,
                                        padWidth = padSize.width,
                                        padHeight = padSize.height,
                                        isPro = isPro,
                                    )
                                    strokes = emptyList()

                                    // Ask here and nowhere else. The agent has just closed, the
                                    // client is signed, and the watermark they are about to send
                                    // is the only argument the paywall needs to make.
                                    if (!isPro) {
                                        offeringPackages =
                                            entitlements.currentOffering()?.availablePackages.orEmpty()
                                        showPaywall = true
                                    }
                                }
                            },
                        )
                    } else {
                        PageControls(
                            state = state,
                            onImport = openPicker,
                            onAskForSignature = { state.startSigning() },
                        )
                    }
                }
            },
        )

        // Half-opened and propped on a desk. Both halves are yours — nobody is opposite — so
        // neither is rotated: the document sits on the raised half where you are looking, and
        // everything you type or press sits on the flat half where your hands already are.
        //
        // This branch used to fall through to the one below, which meant Flex Mode was a mode name
        // with no behaviour behind it while the write-up claimed a presenter view. Either make the
        // claim true or stop making it.
        DeviceMode.PRESENT -> TwofoldScaffoldFlex(
            foldState = foldState,
            agentPage = agentPage,
            state = state,
            followUps = followUps,
            onImport = openPicker,
            onSelectClause = { scope.launch { state.goToClause(it) } },
        )

        // Folded, or held open in the hand: one pane, private by definition.
        DeviceMode.PREPARE -> Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            ClientPane(clientPage, Modifier.weight(1f))
            DocumentShelf(state) { ref -> scope.launch { state.open(ref) } }
            FollowUpList(followUps)
            NoteEditor(state)
            PageControls(state, onImport = openPicker, onAskForSignature = null)
        }
    }
}

/**
 * Which of these meetings this is. Wording only — see [MeetingKind].
 *
 * Scrolls horizontally. Six labels do not fit across a folded phone, and a row that silently clips
 * its last two options is worse than one that admits it has more.
 */
@Composable
private fun MeetingKindPicker(state: PresentState) {
    val colors = LocalTwofoldColors.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.kind_heading),
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            MeetingKind.entries.forEach { kind ->
                val selected = state.meetingKind == kind
                TextButton(onClick = { state.chooseMeetingKind(kind) }) {
                    Text(
                        text = stringResource(kind.label),
                        color = if (selected) colors.seal else colors.inkMuted,
                    )
                }
            }
        }
    }
}

/**
 * Flex Mode: the document above the crease, the console below it.
 *
 * Deliberately not the client's clause on top. Nobody is sitting opposite in this posture — the
 * agent is at a desk with the phone propped up, checking the page as printed against what they are
 * about to say. So the raised half shows their own view, and the flat half carries the language and
 * meeting settings, the notes, and the controls.
 */
@Composable
private fun TwofoldScaffoldFlex(
    foldState: FoldState,
    agentPage: AgentPage,
    state: PresentState,
    followUps: List<Session>,
    onImport: () -> Unit,
    onSelectClause: (Int) -> Unit,
) {
    FlexScaffold(
        foldState = foldState,
        creaseColor = LocalTwofoldColors.current.rule,
        upper = { AgentPane(page = agentPage, onSelectClause = onSelectClause) },
        lower = {
            Column(Modifier.fillMaxSize()) {
                // Notes scroll; the controls do not. This half is the console, and a console whose
                // buttons move when you scroll something else is a console you have to look at.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    FollowUpList(followUps)
                    NoteEditor(state)
                }
                // No signature control: asking someone to sign while the phone is propped facing
                // away from them is not a thing anyone does.
                PageControls(state, onImport = onImport, onAskForSignature = null)
            }
        },
    )
}

/**
 * Which document is open, and every other one you are carrying.
 *
 * Only on the prepare screen. A client watching someone scroll a list of other people's policies is
 * being shown the agent's whole book of business, which is exactly the thing the two halves exist
 * to keep apart.
 */
@Composable
private fun DocumentShelf(state: PresentState, onOpen: (DocumentRef) -> Unit) {
    val colors = LocalTwofoldColors.current
    if (state.documents.size < 2) return

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.documents_heading),
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            state.documents.forEach { ref ->
                val isOpen = ref.id == state.document?.id
                TextButton(onClick = { onOpen(ref) }) {
                    Text(
                        text = ref.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOpen) colors.seal else colors.inkMuted,
                    )
                }
            }
        }
    }
}

/**
 * Removing the open document, behind a second tap.
 *
 * Not a dialog: a dialog on a folded phone covers the screen and needs dismissing, and this is a
 * one-line action. Arming the button and letting it disarm is enough friction to stop a misplaced
 * tap deleting a client's policy, and cheap enough that the honest action stays available.
 */
@Composable
private fun RemoveDocument(state: PresentState) {
    val scope = rememberCoroutineScope()
    val colors = LocalTwofoldColors.current
    var armed by remember(state.document?.id) { mutableStateOf(false) }

    TextButton(onClick = {
        if (armed) scope.launch { state.deleteCurrent(); armed = false } else armed = true
    }) {
        Text(
            text = stringResource(if (armed) R.string.remove_confirm else R.string.remove_document),
            style = MaterialTheme.typography.labelLarge,
            color = if (armed) colors.seal else colors.inkMuted,
        )
    }
}

@Composable
private fun SigningControls(canComplete: Boolean, onCancel: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        // Disabled until there is ink. Exporting an empty signature would produce a signed-looking
        // document that nobody signed.
        Button(onClick = onDone, enabled = canComplete) { Text(stringResource(R.string.action_done)) }
    }
}

/**
 * Only reachable when the device is folded or held — never in Twofold mode.
 *
 * That is a deliberate constraint rather than an omission: there is no way to open the notes
 * editor while the phone is lying flat in front of a client, because there is no situation in
 * which you would want to be typing your private notes across the table from them.
 */
@Composable
private fun NoteEditor(state: PresentState) {
    val scope = rememberCoroutineScope()
    val colors = LocalTwofoldColors.current
    var draftLine by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.clientLabel,
            onValueChange = { state.clientLabel = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(state.meetingKind.partyPrompt)) },
            singleLine = true,
        )

        MeetingKindPicker(state)

        LanguagePicker(state)

        OutlinedTextField(
            value = state.currentNotes.note,
            onValueChange = { scope.launch { state.setNote(it) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.private_note_label)) },
            minLines = 2,
            maxLines = 4,
        )

        // Talk track: the three things to say on this page, and the objection that always comes up.
        // Kept as separate lines rather than a paragraph because they are read at a glance,
        // mid-sentence, by someone who is also talking.
        state.currentNotes.talkTrack.forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.talk_track_line, line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { scope.launch { state.removeTalkTrackLine(index) } }) {
                    Text(stringResource(R.string.action_remove))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoveDocument(state)

            OutlinedTextField(
                value = draftLine,
                onValueChange = { draftLine = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.talk_track_add_label)) },
                singleLine = true,
            )
            TextButton(
                enabled = draftLine.isNotBlank(),
                onClick = {
                    scope.launch {
                        state.addTalkTrackLine(draftLine)
                        draftLine = ""
                    }
                },
            ) { Text(stringResource(R.string.action_add)) }
        }
    }
}

/**
 * Which language the client reads. Lives on the prepare screen on purpose: a language pack is tens
 * of megabytes, and no client should sit watching a progress bar while it arrives.
 */
@Composable
private fun LanguagePicker(state: PresentState) {
    val scope = rememberCoroutineScope()
    val colors = LocalTwofoldColors.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.lang_heading),
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(
                ClientLanguage.ORIGINAL to R.string.lang_original,
                ClientLanguage.HINDI to R.string.lang_hindi,
                ClientLanguage.BENGALI to R.string.lang_bengali,
                ClientLanguage.TELUGU to R.string.lang_telugu,
                ClientLanguage.MARATHI to R.string.lang_marathi,
                ClientLanguage.TAMIL to R.string.lang_tamil,
                ClientLanguage.GUJARATI to R.string.lang_gujarati,
                ClientLanguage.KANNADA to R.string.lang_kannada,
                ClientLanguage.URDU to R.string.lang_urdu,
            ).forEach { (language, label) ->
                val selected = state.clientLanguage == language
                TextButton(onClick = { scope.launch { state.setClientLanguage(language) } }) {
                    Text(
                        text = stringResource(label),
                        color = if (selected) colors.seal else colors.inkMuted,
                    )
                }
            }
        }
        when (state.modelState) {
            ModelState.DOWNLOADING -> StatusLine(stringResource(R.string.lang_downloading), colors.inkMuted)
            ModelState.FAILED -> StatusLine(stringResource(R.string.lang_failed), colors.seal)
            ModelState.READY -> StatusLine(stringResource(R.string.lang_ready), colors.note)
            else -> Unit
        }

        // Only when the engine has actually said no. While it is still binding the answer is
        // unknown, and announcing a missing feature that is merely slow is its own kind of wrong.
        if (state.speech == SpeechReadiness.NO_VOICE) {
            StatusLine(stringResource(R.string.speech_unavailable), colors.inkMuted)
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun PageControls(
    state: PresentState,
    onImport: () -> Unit,
    onAskForSignature: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()

    val inMeeting = onAskForSignature != null

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Control(R.string.action_back, R.string.action_back_spoken) { scope.launch { state.previousClause() } }

        // The clause, out loud, in the client's language — for the larger group who cannot read it
        // in any language. Hidden rather than disabled when the phone has no such voice: a control
        // that is permanently greyed out is just clutter on a half this size.
        if (inMeeting && state.canSpeak) {
            if (state.isSpeaking) {
                Control(R.string.action_stop, R.string.action_stop_spoken) { state.stopSpeaking() }
            } else {
                Control(R.string.action_read, R.string.action_read_spoken) {
                    scope.launch { state.speakCurrentClause() }
                }
            }
        }

        // Only offered in Twofold mode: asking for a signature when the client cannot see the
        // screen is meaningless, so the control simply isn't there.
        onAskForSignature?.let { ask ->
            Control(R.string.action_sign, R.string.action_sign_spoken, ask)
        }

        // Raises the type size on the client's half only. The agent's view is unchanged.
        Control(R.string.action_larger, R.string.action_larger_spoken) {
            state.adjustLegibility(state.legibility + LEGIBILITY_STEP)
        }
        Control(R.string.action_smaller, R.string.action_smaller_spoken) {
            state.adjustLegibility(state.legibility - LEGIBILITY_STEP)
        }

        // Not offered mid-meeting. Opening a file picker while someone is reading a contract in
        // front of you shows them your document library, which is every other client you have.
        if (!inMeeting) {
            Control(R.string.action_open, R.string.action_open_spoken, onImport)
        }

        Control(R.string.action_next, R.string.action_next_spoken) { scope.launch { state.nextClause() } }
    }
}

/**
 * A control whose visible label is short and whose spoken label is not.
 *
 * Six controls have to fit across one half of a folded screen, so the visible text stays terse —
 * but "Next" and "Larger" announced on their own tell a screen-reader user nothing about what
 * they'd do. Larger than what, on whose half?
 */
@Composable
private fun Control(
    @StringRes label: Int,
    @StringRes spoken: Int,
    onClick: () -> Unit,
) {
    val spokenText = stringResource(spoken)
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = spokenText },
    ) {
        Text(stringResource(label))
    }
}

private const val LEGIBILITY_STEP = 0.15f
