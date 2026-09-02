package com.sualtikasifi.cizimhafiza.presentation.worldmap

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
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark

private val RowHeight = 184.dp
// Tall enough to clear the floating back button now drawn in the same Box
// as this content (see the removed CenterAlignedTopAppBar) — the button
// itself sits at (16dp, 16dp) with roughly a 50dp footprint.
private val TopPadding = 76.dp

// A soft, brand-neutral "many lands" backdrop for the world overview — not
// tied to any single world's accent color, unlike each world's own level
// map (see LevelMapScreen). Kept quite light (not the near-opaque alpha a
// screen with no other background layer would use): this sits on top of
// screenBackground()'s own cream wash over the collage, so stacking two
// strong washes here nearly erased the artwork entirely.
private val OverviewGradientTop = Color(0xFFDCEEDD).copy(alpha = 0.30f)
private val OverviewGradientBottom = Color(0xFFFBF3E7).copy(alpha = 0.45f)

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

    // No title bar: the back button floats directly on the page's own
    // background instead of sitting in a separate, differently-colored strip.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        val (scrollState, isReady) = rememberBottomAlignedScrollState()

        // screenBackground() is applied to this fixed, viewport-sized Box
        // (not the taller scrollable one below it) so the collage paints
        // once at screen size and scrolls with the page like a real sheet
        // of paper, rather than being stretched/cropped across the full,
        // much taller scroll content height.
        Box(modifier = Modifier.fillMaxSize().screenBackground().padding(padding)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .graphicsLayer(alpha = if (isReady) 1f else 0f)
        ) {
            if (uiState.worlds.isNotEmpty()) {
                // decorationCount = 0: the scattered world-emoji decorations
                // this originally drew were designed for a plain gradient
                // page with nothing else on it — stacked on the collage
                // background now behind it, they read as visual clutter
                // (a leftover doodle layer fighting the artwork) rather
                // than texture, so only the tint gradient remains.
                ThemedMapBackground(
                    emojis = uiState.worlds.map { it.world.emoji },
                    gradientColors = listOf(OverviewGradientTop, OverviewGradientBottom),
                    contentHeight = contentHeight,
                    decorationCount = 0
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
