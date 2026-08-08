package com.twofold.feature.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twofold.core.design.LocalTwofoldColors
import com.twofold.data.session.Session
import java.util.concurrent.TimeUnit

/**
 * Documents presented but never signed.
 *
 * Shown on the prepare screen — the one the agent sees between meetings — because that is when
 * follow-up actually happens. Burying it behind a tab would make it a feature nobody opens.
 *
 * Renders nothing at all when the list is empty. An empty state saying "no unsigned documents"
 * would be a permanent piece of furniture for a new user with no history, which is worse than
 * silence.
 */
@Composable
fun FollowUpList(sessions: List<Session>, modifier: Modifier = Modifier) {
    if (sessions.isEmpty()) return

    val colors = LocalTwofoldColors.current

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Shown but not signed",
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
        )

        sessions.take(MAX_SHOWN).forEach { session ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.documentTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = relativeAge(session.endedAt ?: session.startedAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.seal,
                )
            }
        }
    }
}

/**
 * "3 days ago" rather than a date.
 *
 * The only thing an agent needs from this list is how stale a lead is, and days-since answers that
 * without arithmetic.
 */
private fun relativeAge(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)

    return when {
        days >= 2 -> "$days days ago"
        days == 1L -> "yesterday"
        hours >= 1 -> "$hours h ago"
        else -> "just now"
    }
}

/** Enough to act on between meetings. A longer list is a backlog, not a prompt. */
private const val MAX_SHOWN = 5
