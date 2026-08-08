package com.twofold

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twofold.core.design.LocalTwofoldColors
import com.twofold.core.design.TwofoldTheme
import com.twofold.core.fold.DeviceMode
import com.twofold.core.fold.FoldState
import com.twofold.core.fold.FoldStateTracker
import com.twofold.core.fold.TwofoldScaffold
import com.revenuecat.purchases.Package
import com.twofold.data.document.DocumentRepository
import com.twofold.feature.present.AgentPage
import com.twofold.feature.present.AgentPane
import com.twofold.feature.present.ClientPage
import com.twofold.feature.present.ClientPane
import com.twofold.feature.paywall.Entitlements
import com.twofold.feature.paywall.PaywallScreen
import com.twofold.feature.present.PreparePane
import com.twofold.feature.present.PresentState
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
    val isPro by entitlements.isPro.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch { state.importAndOpen(uri) }
    }
    val openPicker = { picker.launch(arrayOf("application/pdf")) }

    // Reopen the most recent document on launch so an agent who opens the app at a client's table
    // is one tap from presenting, not four.
    LaunchedEffect(Unit) {
        if (state.document == null) {
            DocumentRepository(context).list().firstOrNull()?.let { state.open(it) }
        }
    }

    // PdfRenderer holds a file descriptor. Without this it survives the screen and leaks.
    DisposableEffect(Unit) {
        onDispose { state.closeCurrent() }
    }

    val document = state.document

    if (document == null) {
        PreparePane(
            hint = state.error ?: "Import the document you'll be walking your client through.",
            action = {
                Button(onClick = { openPicker() }) { Text("Choose a PDF") }
            },
        )
        return
    }

    // Both page fields come from `rendered`, so the number can never describe a different image.
    val clientPage = ClientPage(
        bitmap = state.rendered.bitmap,
        pageNumber = state.rendered.index + 1,
        pageCount = state.pageCount,
        legibility = state.legibility,
        spotlight = state.spotlight,
    )

    val agentPage = AgentPage(
        page = clientPage,
        documentTitle = document.title,
        notes = state.currentNotes.note,
        talkTrack = state.currentNotes.talkTrack,
    )

    // A meeting starts when the phone is put down in front of someone and ends when it is picked
    // up. Tying the log to posture rather than to app launch keeps it to real client meetings.
    LaunchedEffect(foldState.mode, document.id) {
        if (foldState.mode == DeviceMode.TWOFOLD) state.beginSession() else state.endSession()
    }

    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var padSize by remember { mutableStateOf(Size.Zero) }

    var showPaywall by remember { mutableStateOf(false) }
    var offeringPackages by remember { mutableStateOf<List<Package>>(emptyList()) }
    var isPurchasing by remember { mutableStateOf(false) }

    if (showPaywall) {
        PaywallScreen(
            packages = offeringPackages,
            isPurchasing = isPurchasing,
            onPurchase = { option ->
                val activity = context as? Activity ?: return@PaywallScreen
                scope.launch {
                    isPurchasing = true
                    entitlements.purchase(activity, option)
                    isPurchasing = false
                    showPaywall = false
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
                    ClientPane(clientPage)
                }
            },
            nearPane = {
                Column(Modifier.fillMaxWidth()) {
                    AgentPane(agentPage, Modifier.weight(1f))
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
                            onAskForSignature = { state.startSigning(document.title) },
                        )
                    }
                }
            },
        )

        // Folded or held: private by definition, so this is where notes get written.
        DeviceMode.PRESENT, DeviceMode.PREPARE -> Column(Modifier.fillMaxWidth()) {
            ClientPane(clientPage, Modifier.weight(1f))
            NoteEditor(state)
            PageControls(state, onImport = openPicker, onAskForSignature = null)
        }
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
        TextButton(onClick = onCancel) { Text("Cancel") }
        // Disabled until there is ink. Exporting an empty signature would produce a signed-looking
        // document that nobody signed.
        Button(onClick = onDone, enabled = canComplete) { Text("Done") }
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

    OutlinedTextField(
        value = state.currentNotes.note,
        onValueChange = { scope.launch { state.setNote(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        label = { Text("Private note for this page") },
        minLines = 2,
        maxLines = 4,
    )
}

@Composable
private fun PageControls(
    state: PresentState,
    onImport: () -> Unit,
    onAskForSignature: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { scope.launch { state.previousPage() } }) { Text("Back") }

        // Only offered in Twofold mode: asking for a signature when the client cannot see the
        // screen is meaningless, so the control simply isn't there.
        onAskForSignature?.let { ask ->
            TextButton(onClick = ask) { Text("Sign") }
        }

        // Raises the type size on the client's half only. The agent's view is unchanged.
        TextButton(
            onClick = { state.adjustLegibility(state.legibility + LEGIBILITY_STEP) }
        ) { Text("Larger") }
        TextButton(
            onClick = { state.adjustLegibility(state.legibility - LEGIBILITY_STEP) }
        ) { Text("Smaller") }

        TextButton(onClick = onImport) { Text("Open…") }
        TextButton(onClick = { scope.launch { state.nextPage() } }) { Text("Next") }
    }
}

private const val LEGIBILITY_STEP = 0.15f
