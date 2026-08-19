package com.sualtikasifi.cizimhafiza.presentation.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.presentation.common.CircularCountdown
import com.sualtikasifi.cizimhafiza.presentation.common.DrawTool
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
    onEraseStroke: (DrawingStroke) -> Unit,
    onUndoLastStroke: () -> Unit,
    onNextWord: () -> Unit,
    onBackClick: () -> Unit
) {
    val wordLanguage = currentWordLanguage()
    val timerColor = if (state.isWarning) TimerWarning else MaterialTheme.colorScheme.primary
    var tool by remember { mutableStateOf(DrawTool.PEN) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            // Branding + estimated time left for the whole match (not just
            // this word) — null in RELAXED mode, where there's no fixed
            // per-word duration to estimate from (see GamePhase.Drawing).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.karalak_logo_mark),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                state.matchSecondsRemaining?.let { remaining ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMmSs(remaining),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = state.word.text.capitalizeForWordLanguage(wordLanguage),
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.wordNumber}/${state.totalWords}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    tool = tool,
                    onEraseStroke = onEraseStroke,
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
            ) {
                ToolIconButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.tool_pen),
                    selected = tool == DrawTool.PEN,
                    onClick = { tool = DrawTool.PEN }
                )
                ToolIconButton(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.tool_eraser),
                    selected = tool == DrawTool.ERASER,
                    onClick = { tool = DrawTool.ERASER }
                )
                IconButton(onClick = onUndoLastStroke, enabled = state.strokes.isNotEmpty()) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.tool_undo))
                }
                Spacer(modifier = Modifier.weight(1f))
                SecondaryButton(text = stringResource(R.string.clear_canvas), onClick = onClearCanvas)
                if (state.isUntimed) {
                    Spacer(modifier = Modifier.width(8.dp))
                    PrimaryButton(text = stringResource(R.string.next_word), onClick = onNextWord)
                }
            }
        }
    }
}

@Composable
private fun ToolIconButton(icon: ImageVector, contentDescription: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) OrangeContainer else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return "%d:%02d".format(minutes, seconds)
}
