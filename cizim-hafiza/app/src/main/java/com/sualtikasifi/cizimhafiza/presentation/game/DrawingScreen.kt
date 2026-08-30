package com.sualtikasifi.cizimhafiza.presentation.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.PenSkin
import com.sualtikasifi.cizimhafiza.presentation.common.CircularCountdown
import com.sualtikasifi.cizimhafiza.presentation.common.DrawTool
import com.sualtikasifi.cizimhafiza.presentation.common.DrawableCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.EraserGlyph
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.StatPill
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.currentWordLanguage
import com.sualtikasifi.cizimhafiza.presentation.common.dotGridBackground
import com.sualtikasifi.cizimhafiza.presentation.common.hardEdge
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
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
    onBackClick: () -> Unit,
    onHintClick: () -> Unit = {},
    /** The player's chosen cosmetic pen (see domain.model.PenSkin); defaults keep the tutorial's call site unchanged. */
    penSkin: PenSkin = PenSkin.DEFAULT
) {
    val wordLanguage = currentWordLanguage()
    val timerColor = if (state.isWarning) TimerWarning else MaterialTheme.colorScheme.primary
    // Keyed on the word so every new turn starts on the pen again — finishing
    // one word with the eraser selected shouldn't leave the next word's blank
    // canvas in eraser mode, where the first strokes would silently do nothing.
    var tool by remember(state.word.id) { mutableStateOf(DrawTool.PEN) }

    // Guards against a double-tap firing two rewarded-ad loads for the same
    // click — resets per word, though once state.hintUsed flips true the
    // button is gone for the rest of the match anyway.
    var hintRequested by remember(state.word.id) { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // --- Chrome row: exit, branding + whole-match clock, word timer ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RaisedIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBackClick,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier.size(38.dp).background(OrangeContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.karalak_logo_mark),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }

                state.matchSecondsRemaining?.let { remaining ->
                    Spacer(modifier = Modifier.width(10.dp))
                    StatPill(text = formatMmSs(remaining), icon = Icons.Filled.Timer)
                }

                Spacer(modifier = Modifier.width(8.dp))
                TintedBadge(text = "${state.wordNumber} / ${state.totalWords}")

                Spacer(modifier = Modifier.weight(1f))

                if (state.isUntimed) {
                    Box(
                        modifier = Modifier.size(56.dp).background(OrangeContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SelfImprovement,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    CircularCountdown(
                        secondsLeft = state.secondsLeft,
                        totalSeconds = state.totalSeconds,
                        ringColor = timerColor,
                        modifier = Modifier.size(58.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- The word to draw ---
            RaisedCard(
                corner = 22.dp,
                border = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.word.text.capitalizeForWordLanguage(wordLanguage),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 180.dp)
                        .padding(bottom = AppTheme.tokens.raise)
                        .hardEdge(AppTheme.tokens.edge, AppTheme.tokens.raise, 26.dp)
                        .background(CardWhite, MaterialTheme.shapes.large)
                        .dotGridBackground(
                            dotColor = AppTheme.tokens.canvasGrid,
                            spacing = 22.dp,
                            radius = 1.2.dp
                        )
                        .border(2.5.dp, timerColor, MaterialTheme.shapes.large)
                ) {
                    DrawableCanvas(
                        liveStrokes = state.strokes,
                        onStrokeFinished = { onStrokeFinished(wordId, it) },
                        onStrokeProgress = { onStrokeProgress(wordId, it) },
                        tool = tool,
                        onEraseStroke = onEraseStroke,
                        penSkin = penSkin,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Tools ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RaisedIconButton(
                    icon = Icons.Filled.Create,
                    contentDescription = stringResource(R.string.tool_pen),
                    onClick = { tool = DrawTool.PEN },
                    selected = tool == DrawTool.PEN
                )
                RaisedIconButton(
                    onClick = { tool = DrawTool.ERASER },
                    contentDescription = stringResource(R.string.tool_eraser),
                    selected = tool == DrawTool.ERASER
                ) { tint, iconSize -> EraserGlyph(tint = tint, size = iconSize) }
                RaisedIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.tool_undo),
                    onClick = onUndoLastStroke,
                    enabled = state.strokes.isNotEmpty()
                )
                RaisedIconButton(
                    icon = Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.clear_canvas),
                    onClick = onClearCanvas,
                    enabled = state.strokes.isNotEmpty()
                )

                Spacer(modifier = Modifier.weight(1f))

                if (state.isUntimed) {
                    PrimaryButton(
                        text = stringResource(R.string.next_word),
                        onClick = onNextWord,
                        height = 46.dp
                    )
                } else if (!state.hintUsed) {
                    // One rewarded-ad "+time" hint per whole match, not per
                    // word — separate budget from the guessing screen's hint
                    // (see GameViewModel/OnlineGameViewModel.useDrawingHint).
                    SecondaryButton(
                        text = stringResource(
                            if (hintRequested) R.string.loading_hint else R.string.watch_ad_for_extra_time
                        ),
                        onClick = {
                            if (!hintRequested) {
                                hintRequested = true
                                onHintClick()
                            }
                        },
                        enabled = !hintRequested,
                        height = 46.dp
                    )
                }
            }
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(clamped / 60, clamped % 60)
}
