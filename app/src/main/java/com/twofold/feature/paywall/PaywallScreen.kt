package com.twofold.feature.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revenuecat.purchases.Package
import com.twofold.core.design.LocalTwofoldColors

/**
 * The upgrade screen.
 *
 * Hand-built rather than RevenueCat's prebuilt paywall. This is the one screen where the app asks
 * for money, and it has to read like the rest of Twofold — paper and ink, no gradients, no
 * countdown timers. A stock paywall in a tool a professional uses in front of their own customer
 * would look like a different product bolted on.
 *
 * The copy names what the agent gets in their own terms, not in feature bullets.
 */
@Composable
fun PaywallScreen(
    packages: List<Package>,
    isPurchasing: Boolean,
    onPurchase: (Package) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTwofoldColors.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Twofold Pro",
            style = MaterialTheme.typography.displaySmall,
            color = colors.ink,
        )

        Text(
            text = "Clean signed documents, unlimited files, and every meeting logged.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(4.dp))

        listOf(
            "Signed copies without the Twofold watermark",
            "As many documents as you carry",
            "Private notes and talk track on every page",
            "A record of what you showed, and who hasn't signed yet",
        ).forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.seal,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (packages.isEmpty()) {
            // Reached when the store is unreachable or products are not configured yet. Say so
            // plainly rather than showing a buy button that cannot work.
            Text(
                text = "Subscription options aren't available right now. " +
                    "Check your connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
        } else {
            packages.forEach { option ->
                Button(
                    onClick = { onPurchase(option) },
                    enabled = !isPurchasing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.seal,
                        contentColor = colors.paperRaised,
                    ),
                ) {
                    Text(
                        text = "${option.product.title} · ${option.product.price.formatted}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        TextButton(onClick = onDismiss, enabled = !isPurchasing) {
            Text(
                text = "Not now",
                style = MaterialTheme.typography.labelLarge,
                color = colors.inkMuted,
            )
        }

        Text(
            text = "Billed through the Galaxy Store. Cancel any time from your Samsung account.",
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
        )
    }
}
