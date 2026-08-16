package com.sualtikasifi.cizimhafiza.presentation.worldmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathBiasCycle
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathCanvas
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark

private val RowHeight = 184.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMapScreen(
    onWorldClick: (worldId: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: WorldMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.world_map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            WindingPathCanvas(
                itemCount = uiState.worlds.size,
                rowHeight = RowHeight,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 24.dp)
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                uiState.worlds.forEachIndexed { index, card ->
                    Box(
                        modifier = Modifier.fillMaxWidth().height(RowHeight),
                        contentAlignment = BiasAlignment(
                            horizontalBias = WindingPathBiasCycle[index % WindingPathBiasCycle.size],
                            verticalBias = 0f
                        )
                    ) {
                        WorldNode(card = card, onClick = { if (card.unlocked) onWorldClick(card.world.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldNode(card: WorldCardState, onClick: () -> Unit) {
    val accent = Color(card.world.accentColor)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp)) {
        Card(
            onClick = onClick,
            enabled = card.unlocked,
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (card.unlocked) accent else CardWhite,
                contentColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (card.unlocked) 6.dp else 0.dp),
            modifier = Modifier.size(92.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (card.unlocked) {
                    Text(text = card.world.emoji, fontSize = 40.sp)
                } else {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.level_locked), tint = TextDark)
                }
            }
        }
        Text(
            text = stringResource(card.world.displayNameRes),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (card.unlocked) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = if (card.unlocked) {
                "${stringResource(R.string.world_progress_format, card.completedLevels)} · ⭐${card.totalStars}"
            } else {
                stringResource(R.string.world_locked_message)
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
