package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.Chest
import com.sualtikasifi.cizimhafiza.domain.model.ChestTier

/**
 * Shown once, right after a WON Arkadaşla Yarış match found a free chest
 * slot (see OnlineResultViewModel.chestWon / SettingsRepository.
 * awardChestForOnlineWin) — never for a loss, and never for solo play,
 * Hızlı Eşleş or the daily challenge, none of which ever award a chest.
 * Same shape as RatingPromptDialog: a spring pop, not a neutral system alert.
 */
@Composable
fun ChestWonDialog(chest: Chest, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
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
                    Image(
                        painter = painterResource(chest.tier.artRes()),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.chest_won_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(chest.tier.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    PrimaryButton(
                        text = stringResource(R.string.chest_won_button),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

fun ChestTier.labelRes(): Int = when (this) {
    ChestTier.SILVER -> R.string.chest_tier_silver
    ChestTier.GOLD -> R.string.chest_tier_gold
    ChestTier.RARE -> R.string.chest_tier_rare
}

/** The chest's own illustration — see store-assets/chest-art for the originals. */
fun ChestTier.artRes(): Int = when (this) {
    ChestTier.SILVER -> R.drawable.chest_apprentice
    ChestTier.GOLD -> R.drawable.chest_artist
    ChestTier.RARE -> R.drawable.chest_surprise
}
