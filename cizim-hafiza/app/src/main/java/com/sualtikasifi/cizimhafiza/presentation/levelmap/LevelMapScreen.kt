package com.sualtikasifi.cizimhafiza.presentation.levelmap

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathBiasCycle
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.rememberBottomAlignedScrollState
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark

private val RowHeight = 108.dp
// Clears the floating back button (see ScreenTopActions/TopActionsClearance).
private val TopPadding = TopActionsClearance

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

    // No title bar: the back button floats directly on the page's own
    // background instead of sitting in a separate, differently-colored strip.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        // Open the map showing the bottom (level 1) first, not the top of the
        // scrollable content — content stays hidden until pre-scrolled there,
        // so the top never flashes for a frame first.
        val (scrollState, isReady) = rememberBottomAlignedScrollState()

        // Collage plus this world's accent wash, both painted full-window
        // with the Scaffold inset moved onto the scrolling content — see
        // WorldMapScreen for why (a wash that started below the status bar
        // left a hard seam there).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.04f))))
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(scrollState)
                .graphicsLayer(alpha = if (isReady) 1f else 0f)
        ) {
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
        ScreenTopActions(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
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
