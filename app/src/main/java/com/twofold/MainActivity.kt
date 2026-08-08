package com.twofold

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twofold.core.design.LocalTwofoldColors
import com.twofold.core.design.TwofoldTheme
import com.twofold.core.fold.DeviceMode
import com.twofold.core.fold.FoldState
import com.twofold.core.fold.FoldStateTracker
import com.twofold.core.fold.TwofoldScaffold
import com.twofold.feature.present.AgentPage
import com.twofold.feature.present.AgentPane
import com.twofold.feature.present.ClientPage
import com.twofold.feature.present.ClientPane
import com.twofold.feature.present.PreparePane
import com.twofold.feature.present.PresentState
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

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch { state.importAndOpen(uri) }
    }

    // Reopen the most recent document on launch so an agent who opens the app at a client's table
    // is one tap from presenting, not four.
    LaunchedEffect(Unit) {
        if (state.document == null) {
            com.twofold.data.document.DocumentRepository(context).list().firstOrNull()
                ?.let { state.open(it) }
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
                Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                    Text("Choose a PDF")
                }
            },
        )
        return
    }

    // Both fields come from `rendered`, so the page number can never describe a different image.
    val clientPage = ClientPage(
        bitmap = state.rendered.bitmap,
        pageNumber = state.rendered.index + 1,
        pageCount = state.pageCount,
    )

    val agentPage = AgentPage(
        page = clientPage,
        documentTitle = document.title,
        // Notes and talk track arrive next; the private layer exists, it is simply empty for now.
        notes = "",
        talkTrack = emptyList(),
    )

    when (foldState.mode) {
        DeviceMode.TWOFOLD -> TwofoldScaffold(
            foldState = foldState,
            creaseColor = LocalTwofoldColors.current.rule,
            farPane = { ClientPane(clientPage) },
            nearPane = {
                Column(Modifier.fillMaxWidth()) {
                    AgentPane(agentPage, Modifier.weight(1f))
                    PageControls(state, onImport = { picker.launch(arrayOf("application/pdf")) })
                }
            },
        )

        DeviceMode.PRESENT, DeviceMode.PREPARE -> Column(Modifier.fillMaxWidth()) {
            ClientPane(clientPage, Modifier.weight(1f))
            PageControls(state, onImport = { picker.launch(arrayOf("application/pdf")) })
        }
    }
}

@Composable
private fun PageControls(state: PresentState, onImport: () -> Unit) {
    val scope = rememberCoroutineScope()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { scope.launch { state.previousPage() } }) { Text("Back") }
        TextButton(onClick = onImport) { Text("Open…") }
        TextButton(onClick = { scope.launch { state.nextPage() } }) { Text("Next") }
    }
}
