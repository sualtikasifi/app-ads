package com.sualtikasifi.cizimhafiza.presentation.chests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.Chest
import com.sualtikasifi.cizimhafiza.domain.model.ChestReward
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.labelRes
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme

/**
 * "Kasalarım" — the 4 chest slots (see SettingsRepository.chestSlots),
 * earned only from a WON Arkadaşla Yarış match (see OnlineResultViewModel).
 * No kasa here ever comes from solo play, Hızlı Eşleş or the daily challenge.
 */
@Composable
fun ChestsScreen(
    onBack: () -> Unit,
    viewModel: ChestsViewModel = hiltViewModel()
) {
    val slots by viewModel.chestSlots.collectAsState()
    val gold by viewModel.goldBalance.collectAsState()
    val now by viewModel.nowMillis.collectAsState()
    val lastReward by viewModel.lastReward.collectAsState()
    val anyUnlocking = slots.any { it?.unlockStartedAtMillis != null && !it.isReady(now) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .screenBackground()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = TopActionsClearance, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.chests_gold_balance, gold),
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.tokens.gold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.chests_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                items(slots.size) { index ->
                    ChestSlotCard(
                        chest = slots[index],
                        nowMillis = now,
                        startDisabled = anyUnlocking,
                        onStart = { slots[index]?.let { viewModel.startUnlocking(it.id) } },
                        onOpen = { slots[index]?.let { viewModel.open(it.id) } }
                    )
                }
            }

            ScreenTopActions(
                onBack = onBack,
                title = stringResource(R.string.chests_title),
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }

    lastReward?.let { reward ->
        ChestRewardDialog(reward = reward, onDismiss = viewModel::consumeLastReward)
    }
}

@Composable
private fun ChestSlotCard(
    chest: Chest?,
    nowMillis: Long,
    startDisabled: Boolean,
    onStart: () -> Unit,
    onOpen: () -> Unit
) {
    RaisedCard(corner = 18.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "📦", style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(chest?.tier?.labelRes() ?: R.string.chests_slot_empty),
                    style = MaterialTheme.typography.titleMedium
                )
                if (chest != null && chest.unlockStartedAtMillis != null && !chest.isReady(nowMillis)) {
                    Text(
                        text = formatRemaining(chest.unlockStartedAtMillis + chest.tier.unlockDurationMillis - nowMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                chest == null -> Unit
                chest.unlockStartedAtMillis == null -> PrimaryButton(
                    text = stringResource(R.string.chests_start_button),
                    onClick = onStart,
                    enabled = !startDisabled
                )
                chest.isReady(nowMillis) -> PrimaryButton(
                    text = stringResource(R.string.chests_open_button),
                    onClick = onOpen
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun ChestRewardDialog(reward: ChestReward, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "✨", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.chests_reward_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.chests_reward_gold, reward.gold),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppTheme.tokens.gold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                PrimaryButton(
                    text = stringResource(R.string.chests_reward_button),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
