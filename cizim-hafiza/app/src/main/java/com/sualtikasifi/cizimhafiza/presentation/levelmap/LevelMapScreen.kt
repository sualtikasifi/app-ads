package com.sualtikasifi.cizimhafiza.presentation.levelmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathBiasCycle
import com.sualtikasifi.cizimhafiza.presentation.common.WindingPathCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.rememberBottomAlignedScrollState
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground

private val RowHeight = 158.dp
private val NodeSize = 74.dp
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

/**
 * One stop on the winding path: the numbered circle, plus a small plate
 * naming the stage and its difficulty. The bare numbered circles this
 * replaced gave no reason to prefer one level over the next — every stop
 * looked and read identically, so the map was a queue rather than a climb.
 */
@Composable
private fun LevelNode(level: LevelNodeState, accent: Color, onClick: () -> Unit) {
    val unlocked = level.unlocked
    // raisedSurface reserves `raise` at the bottom for the card's edge, so
    // the box has to be that much taller than it is wide or the "circle"
    // comes out as an ellipse.
    val raise = if (unlocked) 6.dp else 3.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RaisedCard(
            corner = NodeSize / 2,
            face = if (unlocked) accent else AppTheme.tokens.cardWarm,
            contentColor = if (unlocked) Color.White else AppTheme.tokens.textFaint,
            // The level you are meant to play next wears a gold ring and
            // stands a little taller than the rest of the path.
            border = if (level.isNext) AppTheme.tokens.gold else null,
            raise = raise,
            onClick = if (unlocked) onClick else null,
            modifier = Modifier.size(width = NodeSize, height = NodeSize + raise)
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (!unlocked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.level_locked),
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Text(text = "${level.levelIndex}", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        RaisedCard(
            corner = 14.dp,
            face = AppTheme.tokens.cardWarm,
            raise = 3.dp,
            modifier = Modifier.widthIn(min = 104.dp, max = 148.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = stringResource(level.nameRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (unlocked) {
                    if (level.stars > 0) {
                        StarRow(stars = level.stars)
                    } else {
                        DifficultyBadge(level.difficulty)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.level_locked),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.tokens.textFaint
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: Difficulty) {
    val (container, ink, labelRes) = when (difficulty) {
        Difficulty.EASY -> Triple(AppTheme.tokens.successContainer, AppTheme.tokens.success, R.string.difficulty_easy)
        Difficulty.MEDIUM -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, R.string.difficulty_medium)
        Difficulty.HARD -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, R.string.difficulty_hard)
    }
    TintedBadge(text = stringResource(labelRes), container = container, content = ink)
}

@Composable
private fun StarRow(stars: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        repeat(3) { index ->
            Icon(
                imageVector = if (index < stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (index < stars) AppTheme.tokens.gold else AppTheme.tokens.textFaint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
