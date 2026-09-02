package com.sualtikasifi.cizimhafiza.presentation.levelmap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.ThemedMapBackground
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathBiasCycle
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.rememberBottomAlignedScrollState
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark

private val RowHeight = 108.dp
// Tall enough to clear the floating back button now drawn in the same Box
// as this content (see the removed CenterAlignedTopAppBar) — the button
// itself sits at (16dp, 16dp) with roughly a 50dp footprint.
private val TopPadding = 76.dp

@Composable
fun LevelMapScreen(
    worldId: Int,
    onLevelClick: (levelIndex: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: LevelMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val world = uiState.world
    val accent = world?.accentColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    // Level 1 at the bottom, climbing toward level 9 at the top — like a
    // Candy Crush episode — so the node list is rendered back-to-front.
    val displayLevels = uiState.levels.asReversed()
    val contentHeight = RowHeight * uiState.levels.size + TopPadding

    // No title bar: the back button floats directly on the page's own
    // background instead of sitting in a separate, differently-colored strip.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        // Open the map showing the bottom (level 1) first, not the top of the
        // scrollable content — content stays hidden until pre-scrolled there,
        // so the top never flashes for a frame first.
        val (scrollState, isReady) = rememberBottomAlignedScrollState()

        // screenBackground() on this fixed, viewport-sized Box (not the
        // taller scrollable one inside it) — see WorldMapScreen's identical
        // structure for why.
        Box(modifier = Modifier.fillMaxSize().screenBackground().padding(padding)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .graphicsLayer(alpha = if (isReady) 1f else 0f)
        ) {
            if (world != null) {
                // decorationCount = 0: see WorldMapScreen's identical change
                // — the emoji scatter this drew now just clutters on top of
                // the collage background behind it.
                ThemedMapBackground(
                    emojis = listOf(world.emoji),
                    gradientColors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.05f)),
                    contentHeight = contentHeight,
                    decorationCount = 0
                )
            }
            WindingPathCanvas(
                itemCount = displayLevels.size,
                rowHeight = RowHeight,
                color = accent.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = TopPadding)
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = TopPadding)) {
                displayLevels.forEachIndexed { renderIndex, level ->
                    Box(
                        modifier = Modifier.fillMaxWidth().height(RowHeight),
                        contentAlignment = BiasAlignment(
                            horizontalBias = WindingPathBiasCycle[renderIndex % WindingPathBiasCycle.size],
                            verticalBias = 0f
                        )
                    ) {
                        LevelNode(level = level, accent = accent, onClick = { if (level.unlocked) onLevelClick(level.levelIndex) })
                    }
                }
            }
        }
        RaisedIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        )
        }
    }
}

@Composable
private fun LevelNode(level: LevelNodeState, accent: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            enabled = level.unlocked,
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (level.unlocked) accent else CardWhite,
                contentColor = if (level.unlocked) Color.White else TextDark
            ),
            border = if (level.isNext) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = if (level.unlocked) 6.dp else 0.dp),
            modifier = Modifier.size(76.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (!level.unlocked) {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.level_locked))
                } else {
                    Text(text = "${level.levelIndex}", style = MaterialTheme.typography.titleLarge, fontSize = 22.sp)
                }
            }
        }
        if (level.unlocked && level.stars > 0) {
            StarRow(stars = level.stars, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StarRow(stars: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp),
        modifier = modifier
    ) {
        repeat(3) { index ->
            Icon(
                imageVector = if (index < stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
