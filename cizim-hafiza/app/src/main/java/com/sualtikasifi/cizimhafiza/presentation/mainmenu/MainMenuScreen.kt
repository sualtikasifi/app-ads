package com.sualtikasifi.cizimhafiza.presentation.mainmenu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent
import com.sualtikasifi.cizimhafiza.presentation.theme.OrangeContainer
import com.sualtikasifi.cizimhafiza.presentation.theme.Teal
import com.sualtikasifi.cizimhafiza.presentation.theme.TealContainer

@Composable
fun MainMenuScreen(
    onPlay: () -> Unit,
    onPlayOnline: () -> Unit,
    onLevels: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
    onBotTraining: () -> Unit,
    viewModel: MainMenuViewModel = hiltViewModel()
) {
    val hasUnseenAchievement by viewModel.hasUnseenAchievement.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RaisedIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.menu_settings),
                    onClick = onSettings
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Logo medallion: the mark on a tinted disc so it reads as an
            // object on the page rather than a sticker floating on cream.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(132.dp)
                    .background(OrangeContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.karalak_logo_mark),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            PrimaryButton(
                text = stringResource(R.string.menu_play),
                onClick = onPlay,
                icon = Icons.Filled.PlayArrow,
                height = 64.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2×2 grid rather than four stacked bars: the same destinations
            // fit without scrolling on a small phone, and each tile gets a
            // color of its own so the menu isn't a wall of orange.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MenuTile(
                    icon = Icons.Filled.People,
                    label = stringResource(R.string.menu_play_online),
                    tint = Teal,
                    container = TealContainer,
                    onClick = onPlayOnline,
                    modifier = Modifier.weight(1f)
                )
                MenuTile(
                    icon = Icons.Filled.Map,
                    label = stringResource(R.string.menu_levels),
                    tint = MaterialTheme.colorScheme.primary,
                    container = OrangeContainer,
                    onClick = onLevels,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MenuTile(
                    icon = Icons.Filled.BarChart,
                    label = stringResource(R.string.menu_stats),
                    tint = GoldAccent,
                    container = Color(0xFFF8EBD0),
                    onClick = onStatistics,
                    showBadge = hasUnseenAchievement,
                    modifier = Modifier.weight(1f)
                )
                MenuTile(
                    icon = Icons.Filled.SmartToy,
                    label = stringResource(R.string.menu_bot_training),
                    tint = Color(0xFF7B68C4),
                    container = Color(0xFFE7E3F7),
                    onClick = onBotTraining,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MenuTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false
) {
    Box(modifier = modifier) {
        RaisedCard(
            onClick = onClick,
            corner = 24.dp,
            face = CardWhite,
            edge = AppTheme.tokens.edge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconWell(icon = icon, tint = tint, container = container, size = 48.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
        // A new, not-yet-viewed achievement (see MainMenuViewModel) —
        // cleared the next time StatisticsScreen opens.
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
        }
    }
}
