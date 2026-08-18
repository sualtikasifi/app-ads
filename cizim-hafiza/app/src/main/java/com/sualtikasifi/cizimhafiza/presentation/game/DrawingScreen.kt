package com.sualtikasifi.cizimhafiza.presentation.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.presentation.common.CircularCountdown
import com.sualtikasifi.cizimhafiza.presentation.common.DrawableCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.currentWordLanguage
import com.sualtikasifi.cizimhafiza.presentation.common.dotGridBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.OrangeContainer
import com.sualtikasifi.cizimhafiza.presentation.theme.TimerWarning
import com.sualtikasifi.cizimhafiza.util.capitalizeForWordLanguage

@Composable
fun DrawingScreen(
    state: GamePhase.Drawing,
    onStrokeFinished: (Int, DrawingStroke) -> Unit,
    onStrokeProgress: (Int, DrawingStroke) -> Unit,
    onClearCanvas: () -> Unit,
    onNextWord: () -> Unit
) {
    val wordLanguage = currentWordLanguage()
    val timerColor = if (state.isWarning) TimerWarning else MaterialTheme.colorScheme.primary

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "${state.wordNumber}/${state.totalWords}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.word.text.capitalizeForWordLanguage(wordLanguage),
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.isUntimed) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(OrangeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SelfImprovement,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    CircularCountdown(
                        secondsLeft = state.secondsLeft,
                        totalSeconds = state.totalSeconds,
                        ringColor = timerColor
                    )
                }
            }

            // Keying on the word's id forces a brand-new canvas instance per
            // turn, but Compose doesn't guarantee the OLD instance's
            // pointerInput coroutine is cancelled the instant the key
            // changes — recomposition/disposal happens on a later frame. If
            // the user's finger is still down when the timer flips words,
            // that stale gesture detector can still fire onDragEnd after
            // the ViewModel has already moved on, silently attributing a
            // leftover drag to the WRONG (new) word. Tagging every callback
            // with the word id this specific canvas instance was created
            // for lets the ViewModel recognize and drop such stale events
            // (see GameViewModel/OnlineGameViewModel.onStrokeFinished).
            key(state.word.id) {
                val wordId = state.word.id
                DrawableCanvas(
                    liveStrokes = state.strokes,
                    onStrokeFinished = { onStrokeFinished(wordId, it) },
                    onStrokeProgress = { onStrokeProgress(wordId, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(top = 16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(CardWhite)
                        .dotGridBackground(dotColor = MaterialTheme.colorScheme.outline, spacing = 20.dp, radius = 1.dp)
                        .border(width = 2.dp, color = timerColor, shape = MaterialTheme.shapes.large)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.clear_canvas),
                    onClick = onClearCanvas,
                    modifier = if (state.isUntimed) Modifier.weight(1f) else Modifier.fillMaxWidth()
                )
                if (state.isUntimed) {
                    PrimaryButton(
                        text = stringResource(R.string.next_word),
                        onClick = onNextWord,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
