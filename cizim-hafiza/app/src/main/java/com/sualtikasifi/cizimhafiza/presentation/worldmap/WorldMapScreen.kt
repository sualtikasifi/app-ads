package com.sualtikasifi.cizimhafiza.presentation.worldmap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.ThemedMapBackground
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathBiasCycle
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.rememberBottomAlignedScrollState
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark

private val RowHeight = 184.dp
private val TopPadding = 24.dp

// A soft, brand-neutral "many lands" backdrop for the world overview — not
// tied to any single world's accent color, unlike each world's own level
// map (see LevelMapScreen).
private val OverviewGradientTop = Color(0xFFDCEEDD)
private val OverviewGradientBottom = Color(0xFFFBF3E7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMapScreen(
    onWorldClick: (worldId: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: WorldMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // World 1 at the bottom, climbing toward World 9 at the top.
    val displayWorlds = uiState.worlds.asReversed()
    val contentHeight = RowHeight * uiState.worlds.size + TopPadding

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.world_map_title)) },
                navigationIcon = {
                    RaisedIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        onClick = onBack
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val (scrollState, isReady) = rememberBottomAlignedScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(scrollState)
                .graphicsLayer(alpha = if (isReady) 1f else 0f)
        ) {
            if (uiState.worlds.isNotEmpty()) {
                ThemedMapBackground(
                    emojis = uiState.worlds.map { it.world.emoji },
                    gradientColors = listOf(OverviewGradientTop, OverviewGradientBottom),
                    contentHeight = contentHeight,
                    decorationCount = 22
                )
            }
            WindingPathCanvas(
                itemCount = displayWorlds.size,
                rowHeight = RowHeight,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = TopPadding)
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = TopPadding)) {
                displayWorlds.forEachIndexed { renderIndex, card ->
                    Box(
                        modifier = Modifier.fillMaxWidth().height(RowHeight),
                        contentAlignment = BiasAlignment(
                            horizontalBias = WindingPathBiasCycle[renderIndex % WindingPathBiasCycle.size],
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
