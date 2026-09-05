package com.sualtikasifi.cizimhafiza.presentation.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableCountCard
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.util.GameConstants
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsMma

/**
 * Picks how many words the challenge round should be, then hands off to the
 * normal Game destination (see Screen.duelChallengeRoute) — the challenger
 * plays an ordinary solo round exactly like free play; only the word list
 * being random-mix (no category/difficulty picker here, unlike CreateRoomScreen)
 * and the fact that its result also becomes a challenge for [opponentName]
 * make this a duel. No ViewModel: nothing here needs a repository, the
 * actual challenge is created once the round finishes (see GameViewModel).
 */
@Composable
fun CreateDuelScreen(
    opponentName: String,
    onBack: () -> Unit,
    onChallengeStarted: (wordCount: Int) -> Unit
) {
    var selectedCount by remember { mutableIntStateOf(GameConstants.WORD_COUNT_OPTIONS.first()) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Clears the floating back button (see ScreenTopActions).
            Spacer(modifier = Modifier.height(TopActionsClearance))
            Spacer(modifier = Modifier.height(18.dp))

            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconWell(icon = Icons.Filled.SportsMma)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.duel_create_opponent, opponentName),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.duel_create_explainer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.select_word_count),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GameConstants.WORD_COUNT_OPTIONS.forEach { count ->
                        SelectableCountCard(
                            count = count,
                            selected = count == selectedCount,
                            onClick = { selectedCount = count },
                            modifier = Modifier.weight(1f),
                            verticalPadding = 10.dp,
                            textStyle = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }

            PrimaryButton(
                text = stringResource(R.string.duel_create_start),
                onClick = { onChallengeStarted(selectedCount) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
        ScreenTopActions(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}
