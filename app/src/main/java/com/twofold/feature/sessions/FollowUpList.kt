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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.twofold.R
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
            text = stringResource(R.string.follow_up_heading),
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
 *
 * Uses plurals rather than string formatting. English gets away with "1 days ago" looking merely
 * sloppy; languages with more than two plural forms do not, and Hindi and Tamil are both on the
 * list of places this app is for.
 */
@Composable
private fun relativeAge(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    val days = TimeUnit.MILLISECONDS.toDays(elapsed).toInt()
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed).toInt()

    return when {
        days >= 2 -> pluralStringResource(R.plurals.age_days, days, days)
        days == 1 -> stringResource(R.string.age_yesterday)
        hours >= 1 -> pluralStringResource(R.plurals.age_hours, hours, hours)
        else -> stringResource(R.string.age_just_now)
    }
}

/** Enough to act on between meetings. A longer list is a backlog, not a prompt. */
private const val MAX_SHOWN = 5
