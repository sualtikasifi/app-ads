package com.sualtikasifi.cizimhafiza.presentation.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.presentation.common.CircularCountdown
import com.sualtikasifi.cizimhafiza.presentation.common.DrawableCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.dotGridBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TimerWarning

@Composable
fun DrawingScreen(
    state: GamePhase.Drawing,
    onStrokeFinished: (DrawingStroke) -> Unit,
    onClearCanvas: () -> Unit
) {
    val timerColor = if (state.isWarning) TimerWarning else MaterialTheme.colorScheme.primary

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${state.wordNumber}/${state.totalWords}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = state.word.text, style = MaterialTheme.typography.headlineLarge)
                }
                CircularCountdown(
                    secondsLeft = state.secondsLeft,
                    totalSeconds = state.totalSeconds,
                    ringColor = timerColor
                )
            }

            DrawableCanvas(
                liveStrokes = state.strokes,
                onStrokeFinished = onStrokeFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(top = 16.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(CardWhite)
                    .dotGridBackground(dotColor = MaterialTheme.colorScheme.outline, spacing = 20.dp, radius = 1.dp)
                    .border(width = 2.dp, color = timerColor, shape = MaterialTheme.shapes.large)
            )

            SecondaryButton(
                text = stringResource(R.string.clear_canvas),
                onClick = onClearCanvas,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        }
    }
}
