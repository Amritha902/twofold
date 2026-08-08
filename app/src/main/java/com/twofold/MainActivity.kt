package com.twofold

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.Flow

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
    val colors = LocalTwofoldColors.current

    // Placeholder content until PDF import lands in week 2. The point of this slice is that the
    // posture transition and the two-sided split are real and testable now.
    val page = remember {
        ClientPage(
            title = "Term Life — Plan Summary",
            body = "Sum assured of ₹50,00,000 for a policy term of 30 years. " +
                "Premiums are payable annually and the policy lapses if a premium is not " +
                "received within the 30-day grace period.",
            pageNumber = 1,
            pageCount = 12,
        )
    }
    val agentPage = remember {
        AgentPage(
            page = page,
            notes = "Riya asked about the grace period last time. Lead with that.",
            talkTrack = listOf(
                "₹50L cover, 30 years, premium fixed for the whole term",
                "Grace period is 30 days — the policy does not die on day one",
                "Objection: \"what if I stop paying?\" → paid-up value, page 7",
            ),
        )
    }

    when (foldState.mode) {
        DeviceMode.TWOFOLD -> TwofoldScaffold(
            foldState = foldState,
            modifier = Modifier,
            creaseColor = colors.rule,
            farPane = { ClientPane(page) },
            nearPane = { AgentPane(agentPage) },
        )

        DeviceMode.PRESENT -> PreparePane(
            hint = "Present mode — lay the phone flat to show the client."
        )

        DeviceMode.PREPARE -> PreparePane(
            hint = "Open the phone and lay it flat on the table between you and your client."
        )
    }
}
