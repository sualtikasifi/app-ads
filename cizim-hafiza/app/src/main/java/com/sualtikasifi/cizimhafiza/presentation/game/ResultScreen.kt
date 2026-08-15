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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.StrokeCanvas
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed

@Composable
fun ResultScreen(
    state: GamePhase.Result,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit
) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text(text = stringResource(R.string.game_over), style = MaterialTheme.typography.headlineLarge)
            Text(text = stringResource(R.string.total_score, state.totalScore), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.correct_count, state.correctCount))
            Text(text = stringResource(R.string.wrong_count, state.wrongCount))
            state.fastestCorrectSeconds?.let {
                Text(text = stringResource(R.string.fastest_correct, it))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items) { item ->
                    Column {
                        StrokeCanvas(
                            strokes = item.strokes,
                            strokeColor = Color.Black,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .border(2.dp, if (item.isCorrect) CorrectGreen else WrongRed)
                                .background(Color.White)
                        )
                        Text(
                            text = item.word,
                            color = if (item.isCorrect) CorrectGreen else WrongRed,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onMainMenu, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.main_menu))
                }
                Button(onClick = onPlayAgain, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.play_again))
                }
            }
        }
    }
}
