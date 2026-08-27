package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.KickedUser
import com.sualtikasifi.cizimhafiza.domain.model.OnlinePlayer
import com.sualtikasifi.cizimhafiza.domain.model.Reaction
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.presentation.common.BotMascot
import com.sualtikasifi.cizimhafiza.presentation.common.BotMascotPose
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.domain.model.LevelTier
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.InviteShareUtil
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingRoomScreen(
    onGameStarted: (roomCode: String) -> Unit,
    onLeave: () -> Unit,
    viewModel: WaitingRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val room = uiState.room
    val myUid = viewModel.myUid
    val isHost = room != null && room.hostUid == myUid
    val me = room?.players?.find { it.uid == myUid }
    // Only players actually still here. leaveRoom covers a clean exit, but a
    // force-closed app never sets it — that entry would otherwise linger as a
    // phantom player forever, and being permanently un-ready it would also
    // stop the room from ever starting a match. The bot is exempt: it has no
    // device to check in from (see OnlinePlayer.isPresent).
    val isPresent: (OnlinePlayer) -> Boolean = { it.uid == BotRoomEngine.BOT_UID || it.isPresent() }
    val others = room?.players?.filter { it.uid != myUid && isPresent(it) } ?: emptyList()
    val presentPlayerCount = room?.players?.count(isPresent) ?: 1
    val amPending = me?.pendingNextRound == true
    // Ready-check only applies to players actually in this round — a
    // pendingNextRound joiner (sitting out the round already in progress)
    // shouldn't block the others from starting/being "all ready".
    val activePlayers = room?.players?.filter { !it.pendingNextRound && isPresent(it) } ?: emptyList()
    val allReady = activePlayers.size >= 2 && activePlayers.all { it.ready }
    val amReady = me?.ready == true

    var kickTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Sude waves back briefly when her own "Selam" reaction lands (see
    // BotRoomEngine.replyToGreeting) — same lifetime as the reaction bubble
    // itself (ReactionOverlay) so the wave and the bubble disappear together.
    var botWaving by remember { mutableStateOf(false) }
    val latestReactionKey = uiState.reactions.lastOrNull()?.let { it.uid to it.sentAtMillis }
    LaunchedEffect(latestReactionKey) {
        val latest = uiState.reactions.lastOrNull() ?: return@LaunchedEffect
        if (latest.uid == BotRoomEngine.BOT_UID && latest.messageKey == "hi") {
            botWaving = true
            delay(2500)
            botWaving = false
        }
    }

    LaunchedEffect(room?.status, amPending) {
        if (room?.status == RoomStatus.PLAYING && !amPending) {
            onGameStarted(viewModel.roomCode)
        }
    }

    BackHandler {
        viewModel.leaveRoom()
        onLeave()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.online_waiting_title)) },
                navigationIcon = {
                    RaisedIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        onClick = {
                            viewModel.leaveRoom()
                            onLeave()
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        // The action controls are pinned rather than living at the end of the
        // scroll: the player list is the only part that should ever grow, and
        // in a full room it used to push "Hazırım" (and the last player row)
        // off the bottom of the screen entirely.
        bottomBar = {
            WaitingRoomActions(
                amPending = amPending,
                hasOthers = others.isNotEmpty(),
                allReady = allReady,
                amReady = amReady,
                isHost = isHost,
                isStarting = uiState.isStarting,
                errorMessage = uiState.errorMessage,
                reactions = uiState.reactions,
                myUid = myUid,
                players = room?.players.orEmpty(),
                onToggleReady = viewModel::toggleReady,
                onStartGame = viewModel::startGame,
                onSendReaction = viewModel::sendReaction
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                RoomCodeCard(
                    roomCode = viewModel.roomCode,
                    onInvite = { InviteShareUtil.shareRoomInvite(context, viewModel.roomCode) }
                )
            }

            if (amPending) {
                item {
                    PendingNextRoundNotice(
                        startedAtMillis = room?.startedAt,
                        estimatedRoundSeconds = uiState.estimatedRoundSeconds
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.online_players_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TintedBadge(
                        text = stringResource(
                            R.string.online_room_occupancy,
                            presentPlayerCount,
                            GameConstants.MAX_ROOM_SIZE
                        )
                    )
                }
            }

            item {
                PlayerRow(
                    name = me?.displayName ?: "…",
                    level = me?.level ?: 1,
                    ready = amReady,
                    isYou = true,
                    pending = amPending
                )
            }

            items(others, key = { it.uid }) { player ->
                val isBot = player.uid == BotRoomEngine.BOT_UID
                PlayerRow(
                    name = player.displayName,
                    level = player.level,
                    ready = player.ready,
                    isYou = false,
                    pending = player.pendingNextRound,
                    mascotPose = when {
                        !isBot -> null
                        botWaving -> BotMascotPose.WAVE
                        player.ready -> BotMascotPose.HAPPY
                        else -> BotMascotPose.THINKING
                    },
                    onKick = if (isHost) {
                        { kickTarget = player.uid to player.displayName }
                    } else null
                )
            }

            if (others.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.online_waiting_for_friend),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                }
            }

            if (isHost && room?.kickedUsers?.isNotEmpty() == true) {
                item {
                    KickedUsersSection(kickedUsers = room.kickedUsers, onUnban = viewModel::unbanPlayer)
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }
    }

    kickTarget?.let { (targetUid, targetName) ->
        AlertDialog(
            onDismissRequest = { kickTarget = null },
            title = { Text(stringResource(R.string.online_kick_confirm_title)) },
            text = { Text(stringResource(R.string.online_kick_confirm_message, targetName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.kickPlayer(targetUid, targetName)
                    kickTarget = null
                }) {
                    Text(stringResource(R.string.online_kick_confirm_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { kickTarget = null }) {
                    Text(stringResource(R.string.online_kick_confirm_cancel))
                }
            }
        )
    }
}

/** The room code plus its one action — the thing you actually came to this screen to hand someone. */
@Composable
private fun RoomCodeCard(roomCode: String, onInvite: () -> Unit) {
    RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.online_room_code_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = roomCode,
                style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 8.sp),
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                text = stringResource(R.string.online_invite_friend),
                onClick = onInvite,
                icon = Icons.Filled.Share,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Shown only to someone who joined mid-round and is sitting the current one out. */
@Composable
private fun PendingNextRoundNotice(startedAtMillis: Long?, estimatedRoundSeconds: Int?) {
    RaisedCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.online_pending_next_round_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            RoundCountdown(startedAtMillis = startedAtMillis, estimatedRoundSeconds = estimatedRoundSeconds)
        }
    }
}

/**
 * The pinned bottom half of the screen: the reaction bubble slot, the chat
 * controls, and whichever single action is available right now.
 */
@Composable
private fun WaitingRoomActions(
    amPending: Boolean,
    hasOthers: Boolean,
    allReady: Boolean,
    amReady: Boolean,
    isHost: Boolean,
    isStarting: Boolean,
    errorMessage: String?,
    reactions: List<Reaction>,
    myUid: String?,
    players: List<OnlinePlayer>,
    onToggleReady: () -> Unit,
    onStartGame: () -> Unit,
    onSendReaction: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            // A custom bottomBar isn't auto-inset like Scaffold's own content
            // slot is — without this, the ready/start button and the reaction
            // row sat behind the phone's own on-screen back/home/recents bar
            // on edge-to-edge devices (see MainActivity.enableEdgeToEdge).
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasOthers) {
            // Tall enough for the whole bubble (sender name + message), not
            // just the emoji — a short box let the bubble's bottom half
            // render underneath the controls below it.
            Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                ReactionOverlay(reactions = reactions, myUid = myUid, players = players)
            }
            ReactionSendRow(onSend = onSendReaction, modifier = Modifier.padding(bottom = 12.dp))
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        when {
            // A sitting-out joiner has no controls — the notice above the
            // player list already explains what they're waiting for.
            amPending -> Unit
            !hasOthers -> Unit
            !allReady -> SecondaryButton(
                text = stringResource(if (amReady) R.string.online_ready_cancel else R.string.online_ready),
                onClick = onToggleReady,
                modifier = Modifier.fillMaxWidth()
            )
            isHost -> if (isStarting) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            } else {
                PrimaryButton(
                    text = stringResource(R.string.online_start_game),
                    onClick = onStartGame,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> Text(
                text = stringResource(R.string.online_waiting_for_host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A rough, ticking "time left in the round" estimate for a pendingNextRound
 * joiner sitting in the lobby — see WaitingRoomViewModel.estimatedRoundSeconds
 * and OnlineRoom.startedAt. Shows nothing while either piece is unavailable
 * (e.g. the shared word list hasn't resolved locally yet) rather than a
 * misleading number.
 */
@Composable
private fun RoundCountdown(startedAtMillis: Long?, estimatedRoundSeconds: Int?) {
    if (startedAtMillis == null || estimatedRoundSeconds == null) return
    var remainingSeconds by remember(startedAtMillis, estimatedRoundSeconds) {
        val elapsed = (System.currentTimeMillis() - startedAtMillis) / 1000
        mutableStateOf((estimatedRoundSeconds - elapsed).coerceAtLeast(0))
    }
    LaunchedEffect(startedAtMillis, estimatedRoundSeconds) {
        while (remainingSeconds > 0) {
            delay(1_000)
            val elapsed = (System.currentTimeMillis() - startedAtMillis) / 1000
            remainingSeconds = (estimatedRoundSeconds - elapsed).coerceAtLeast(0)
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.online_round_time_remaining,
            String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun KickedUsersSection(kickedUsers: List<KickedUser>, onUnban: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.online_kicked_users_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 140.dp)) {
            kickedUsers.forEach { kicked ->
                val remainingMinutes = ((kicked.untilMillis - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(0)
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.online_kicked_user_remaining_format, kicked.displayName, remainingMinutes),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { onUnban(kicked.uid) }) {
                            Text(stringResource(R.string.online_unban_player))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    name: String,
    level: Int,
    ready: Boolean,
    isYou: Boolean,
    pending: Boolean = false,
    mascotPose: BotMascotPose? = null,
    onKick: (() -> Unit)? = null
) {
    RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mascotPose != null) {
                    BotMascot(pose = mascotPose)
                } else {
                    // The level badge stands in for a profile picture — it's
                    // the whole point of the ladder that opponents see it.
                    LevelAvatar(level = level, tier = LevelTier.forLevel(level), size = 38.dp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isYou) stringResource(R.string.online_you_label, name) else name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (pending) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TintedBadge(text = stringResource(R.string.online_pending_badge))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ready) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = CorrectGreen)
                }
                if (onKick != null) {
                    IconButton(onClick = onKick) {
                        Icon(
                            Icons.Filled.PersonRemove,
                            contentDescription = stringResource(R.string.online_kick_player),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
