package com.sualtikasifi.cizimhafiza.presentation.common

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.util.AppReviewLauncher

/**
 * Shown once, right after a new player's 3rd finished match (see
 * util/PostMatchPrompts.shouldShowRating) — early enough to still be a
 * first impression of the app, late enough that they've actually played
 * something worth rating.
 *
 * "Puanla" both opens the Play Store listing AND signals the caller to
 * grant the one-time 500 XP bonus in the same tap — the app has no way to
 * confirm a player actually left 5 stars once Play Store opens (that never
 * reports back), so the reward is for making the trip, not a verified
 * rating. [onRate] is expected to call
 * SettingsRepository.grantRatingBonusXpOnce, which is itself idempotent —
 * this dialog only ever shows once anyway, but the XP grant guards itself
 * independently rather than trusting that.
 */
@Composable
fun RatingPromptDialog(onRate: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    Dialog(onDismissRequest = onDismiss) {
        // A quick spring pop rather than the platform's own generic dialog
        // fade — this is a celebratory ask ("you've played 3 rounds!"), not
        // a neutral system prompt, and it should read that way immediately.
        val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(tween(150)),
            exit = fadeOut(tween(120))
        ) {
            RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(5) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = AppTheme.tokens.gold,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.rating_prompt_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.rating_prompt_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    PrimaryButton(
                        text = stringResource(R.string.rating_prompt_rate_button),
                        onClick = {
                            activity?.let(AppReviewLauncher::openStoreListing)
                            onRate()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SecondaryButton(
                        text = stringResource(R.string.rating_prompt_later_button),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
