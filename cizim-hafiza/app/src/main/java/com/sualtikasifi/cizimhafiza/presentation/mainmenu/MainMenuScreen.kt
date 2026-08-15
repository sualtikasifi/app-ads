package com.sualtikasifi.cizimhafiza.presentation.mainmenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R

@Composable
fun MainMenuScreen(
    onPlay: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(48.dp))

            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.menu_play))
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onStatistics, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.menu_stats))
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.menu_settings))
            }
        }
    }
}
