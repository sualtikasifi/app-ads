package com.sualtikasifi.cizimhafiza.presentation.levelmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark

// Cycled per row to give the node column a gentle S-curve, like a level path.
private val HorizontalBiasCycle = listOf(-0.6f, 0f, 0.6f, 0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelMapScreen(
    worldId: Int,
    onLevelClick: (levelIndex: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: LevelMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val world = uiState.world

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (world != null) stringResource(R.string.level_map_title_format, stringResource(world.displayNameRes))
                        else stringResource(R.string.world_map_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            items(uiState.levels, key = { it.levelIndex }) { level ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = BiasAlignment(
                        horizontalBias = HorizontalBiasCycle[(level.levelIndex - 1) % HorizontalBiasCycle.size],
                        verticalBias = 0f
                    )
                ) {
                    LevelNode(
                        level = level,
                        accent = world?.accentColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                        onClick = { if (level.unlocked) onLevelClick(level.levelIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelNode(level: LevelNodeState, accent: Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            enabled = level.unlocked,
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (level.unlocked) accent else CardWhite,
                contentColor = if (level.unlocked) Color.White else TextDark
            ),
            border = if (level.isNext) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = if (level.unlocked) 4.dp else 0.dp),
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
        horizontalArrangement = Arrangement.spacedBy(1.dp),
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
