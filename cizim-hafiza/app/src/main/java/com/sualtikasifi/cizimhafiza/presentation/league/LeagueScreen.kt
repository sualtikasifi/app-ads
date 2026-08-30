package com.sualtikasifi.cizimhafiza.presentation.league

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.LeagueEntry
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent

/**
 * A friends-only leaderboard that resets every Monday — see domain.model.WeeklyLeague
 * for why weekly, not lifetime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueScreen(
    onBack: () -> Unit,
    viewModel: LeagueViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val table = uiState.table

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.league_title)) },
                navigationIcon = {
                    RaisedIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                table?.let {
                    TintedBadge(
                        text = if (it.daysRemaining <= 0) {
                            stringResource(R.string.league_resets_today)
                        } else {
                            stringResource(R.string.league_resets_in, it.daysRemaining)
                        }
                    )
                }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                table == null || table.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.league_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(table.entries, key = { it.uid }) { entry ->
                        val rank = table.entries.indexOf(entry) + 1
                        LeagueRow(rank = rank, entry = entry)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LeagueRow(rank: Int, entry: LeagueEntry) {
    // The top 3 get a gold/silver/bronze rank chip; everyone else just gets
    // a plain number — the podium is the part worth celebrating visually,
    // rank 8 doesn't need its own color.
    val rankColor = when (rank) {
        1 -> GoldAccent
        2 -> MaterialTheme.colorScheme.onSurfaceVariant
        3 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    RaisedCard(
        corner = 18.dp,
        border = if (entry.isMe) MaterialTheme.colorScheme.primary else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.league_rank_format, rank),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (rank <= 3) FontWeight.ExtraBold else FontWeight.Normal,
                    color = rankColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            LevelAvatar(level = entry.level, frame = AvatarFrame.resolve(entry.frameId, entry.level), size = 40.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (entry.isMe) stringResource(R.string.online_you_label, entry.nickname) else entry.nickname,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.league_xp_format, entry.weeklyXp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
