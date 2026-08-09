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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.twofold.R
import androidx.compose.ui.unit.dp
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.StoreProduct
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
            text = stringResource(R.string.pro_title),
            style = MaterialTheme.typography.displaySmall,
            color = colors.ink,
        )

        Text(
            text = stringResource(R.string.pro_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(4.dp))

        listOf(
            R.string.pro_benefit_watermark,
            R.string.pro_benefit_unlimited,
            R.string.pro_benefit_notes,
            R.string.pro_benefit_followup,
        ).map { stringResource(it) }.forEach { line ->
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
                text = stringResource(R.string.pro_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
        } else {
            packages.forEach { option ->
                val trial = freeTrialLabel(option.product)

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
                        text = if (trial == null) {
                            stringResource(
                                R.string.pro_package_label,
                                option.product.title,
                                option.product.price.formatted,
                            )
                        } else {
                            stringResource(
                                R.string.pro_package_label_trial,
                                option.product.title,
                                trial,
                                option.product.price.formatted,
                            )
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        TextButton(onClick = onDismiss, enabled = !isPurchasing) {
            Text(
                text = stringResource(R.string.pro_not_now),
                style = MaterialTheme.typography.labelLarge,
                color = colors.inkMuted,
            )
        }

        Text(
            text = stringResource(R.string.pro_billing_note),
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
        )
    }
}

/**
 * "7 days" when the product carries a free trial, null when it doesn't.
 *
 * A trial nobody is told about converts nobody, so this is worth having on its own. It is also a
 * submission requirement: Shipaton asks that judges be able to unlock the in-app purchase and test
 * premium features, via either a free trial or a promo code — and a trial that the paywall renders
 * as a bare price is, from a judge's side of the screen, not a trial at all.
 *
 * Every step is nullable on purpose. `defaultOption` is a Google Play Billing concept and the
 * Galaxy Store may well not populate it; when it doesn't, the button falls back to the plain price
 * rather than the paywall breaking.
 */
@Composable
private fun freeTrialLabel(product: StoreProduct): String? {
    val period = product.defaultOption?.freePhase?.billingPeriod ?: return null
    if (period.value <= 0) return null

    val plural = when (period.unit) {
        Period.Unit.DAY -> R.plurals.trial_days
        Period.Unit.WEEK -> R.plurals.trial_weeks
        Period.Unit.MONTH -> R.plurals.trial_months
        Period.Unit.YEAR -> R.plurals.trial_years
        // A period we cannot name is not one to guess at in a purchase flow.
        Period.Unit.UNKNOWN -> return null
    }
    return pluralStringResource(plural, period.value, period.value)
}
