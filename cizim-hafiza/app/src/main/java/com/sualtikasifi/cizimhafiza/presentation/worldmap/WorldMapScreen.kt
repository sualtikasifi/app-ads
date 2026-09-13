package com.sualtikasifi.cizimhafiza.presentation.worldmap

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
import androidx.compose.ui.text.style.TextAlign
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

private val RowHeight = 184.dp
// Clears the floating back button (see ScreenTopActions/TopActionsClearance).
private val TopPadding = TopActionsClearance

// A light wash over the page's collage, warm rather than the mint green this
// used to be: the green read as a color cast on the artwork rather than as a
// backdrop of its own, and made this the one screen whose background didn't
// look like the rest of the app.
private val OverviewGradientTop = Color(0xFFFDF7EC).copy(alpha = 0.24f)
private val OverviewGradientBottom = Color(0xFFF3E6D2).copy(alpha = 0.40f)

@Composable
fun WorldMapScreen(
    onWorldClick: (worldId: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: WorldMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // World 1 at the bottom, climbing toward World 9 at the top.
    val displayWorlds = uiState.worlds.asReversed()

    // No title bar: the back button floats directly on the page's own
    // background instead of sitting in a separate, differently-colored strip.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        val (scrollState, isReady) = rememberBottomAlignedScrollState()

        // Both background layers — the collage and the wash over it — are
        // painted here, on the full window, and the Scaffold inset goes on
        // the scrolling content instead. Previously the wash was a scrolling
        // element inset below the status bar, so the strip behind the
        // notification bar showed bare collage and the boundary between the
        // two read as a hard seam that stayed put while the map scrolled.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .background(Brush.verticalGradient(listOf(OverviewGradientTop, OverviewGradientBottom)))
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(scrollState)
                .graphicsLayer(alpha = if (isReady) 1f else 0f)
        ) {
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
        ScreenTopActions(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
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
                containerColor = if (card.unlocked) accent else MaterialTheme.colorScheme.surface,
                contentColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (card.unlocked) 6.dp else 0.dp),
            modifier = Modifier.size(92.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (card.unlocked) {
                    Text(text = card.world.emoji, fontSize = 40.sp)
                } else {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.level_locked), tint = MaterialTheme.colorScheme.onSurface)
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
