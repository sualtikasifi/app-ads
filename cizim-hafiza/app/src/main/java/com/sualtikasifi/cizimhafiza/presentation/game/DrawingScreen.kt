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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.presentation.common.DrawableCanvas
import com.sualtikasifi.cizimhafiza.presentation.theme.TimerWarning

@Composable
fun DrawingScreen(
    state: GamePhase.Drawing,
    onStrokeFinished: (DrawingStroke) -> Unit,
    onClearCanvas: () -> Unit
) {
    val timerColor = if (state.isWarning) TimerWarning else MaterialTheme.colorScheme.primary

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.wordNumber}/${state.totalWords}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${state.secondsLeft}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = timerColor
                )
            }

            Text(
                text = state.word.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            DrawableCanvas(
                liveStrokes = state.strokes,
                onStrokeFinished = onStrokeFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(2.dp, timerColor)
                    .background(Color.White)
            )

            OutlinedButton(
                onClick = onClearCanvas,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text(text = stringResource(R.string.clear_canvas))
            }
        }
    }
}
