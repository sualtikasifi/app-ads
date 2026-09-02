package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import java.util.Locale
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.KickedUser
import com.sualtikasifi.cizimhafiza.domain.model.OnlinePlayer
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.Reaction
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.presentation.common.EmptyState
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.InviteShareUtil
import kotlinx.coroutines.delay
import com.sualtikasifi.cizimhafiza.util.UiText
import com.sualtikasifi.cizimhafiza.util.asString

@Composable
fun WaitingRoomScreen(
    onGameStarted: (roomCode: String) -> Unit,
    onLeave: () -> Unit,
    viewModel: WaitingRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val phraseUsageCounts by viewModel.phraseUsageCounts.collectAsState()
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
    val teamMode = room?.teamMode == true
    // Ready-check only applies to players actually in this round — a
    // pendingNextRound joiner (sitting out the round already in progress)
    // shouldn't block the others from starting/being "all ready".
    val activePlayers = room?.players?.filter { !it.pendingNextRound && isPresent(it) } ?: emptyList()
    // A 2v2 room additionally needs BOTH teams actually full — two ready
    // players who both picked Team A is not a match, whatever the total
    // headcount says.
    val allReady = if (teamMode) {
        activePlayers.size == GameConstants.TEAM_ROOM_SIZE &&
            activePlayers.count { it.teamId == "A" } == GameConstants.TEAM_SIZE &&
            activePlayers.count { it.teamId == "B" } == GameConstants.TEAM_SIZE &&
            activePlayers.all { it.ready }
    } else {
        activePlayers.size >= 2 && activePlayers.all { it.ready }
    }
    val amReady = me?.ready == true

    // Why a full, all-ready 2v2 room still cannot start. Computed here
    // alongside allReady so the two can never disagree.
    val teamImbalanceHint: Int? = when {
        !teamMode -> null
        activePlayers.size < GameConstants.TEAM_ROOM_SIZE -> R.string.online_team_needs_players
        activePlayers.count { it.teamId == "A" } != GameConstants.TEAM_SIZE ||
            activePlayers.count { it.teamId == "B" } != GameConstants.TEAM_SIZE ->
            R.string.online_team_unbalanced
        else -> null
    }

    var kickTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var invitePickerOpen by remember { mutableStateOf(false) }
    val friends by viewModel.friends.collectAsState()
    val inviteState by viewModel.inviteState.collectAsState()

    // Per-sender chat bubble state (see ReactionBar.rememberActiveReactionsByUid)
    // — each entry is shown anchored on that sender's own slot card below,
    // instead of one shared pop-up that didn't say who was talking.
    val activeReactionsByUid = rememberActiveReactionsByUid(uiState.reactions)

    // Sude shows the same level-badge avatar a real player would (see
    // BotRoomEngine.BOT_LEVEL) rather than dedicated bot art, so she reads as
    // another player filling a lobby slot.
    val mySlot = me?.let {
        PlayerSlotUiState(
            uid = it.uid,
            name = it.displayName,
            level = it.level,
            frame = AvatarFrame.resolve(it.frameId, it.level),
            ready = amReady,
            isYou = true,
            pending = amPending,
            onKick = null,
            teamId = it.teamId,
            // Only your own slot can switch — this is a self-service pick,
            // not something a teammate or the host can move for you.
            onSwitchTeam = if (teamMode) {
                { viewModel.switchTeam(if (it.teamId == "A") "B" else "A") }
            } else null
        )
    }
    val otherSlots = others.map { player ->
        val isBot = player.uid == BotRoomEngine.BOT_UID
        PlayerSlotUiState(
            uid = player.uid,
            name = player.displayName,
            level = if (isBot) BotRoomEngine.BOT_LEVEL else player.level,
            frame = if (isBot) {
                AvatarFrame.highestUnlockedFor(BotRoomEngine.BOT_LEVEL)
            } else {
                AvatarFrame.resolve(player.frameId, player.level)
            },
            ready = player.ready,
            isYou = false,
            pending = player.pendingNextRound,
            onKick = if (isHost) {
                { kickTarget = player.uid to player.displayName }
            } else null,
            teamId = player.teamId,
            onSwitchTeam = null
        )
    }
    val occupiedSlots = listOfNotNull(mySlot) + otherSlots
    // Padded to a fixed 8-slot grid (see GameConstants.MAX_ROOM_SIZE) so the
    // lobby reads as slots being filled in, not a list that happens to be
    // short right now. Team mode instead pads each team to exactly
    // GameConstants.TEAM_SIZE — see teamASlots/teamBSlots below.
    val playerSlots: List<PlayerSlotUiState?> =
        occupiedSlots + List((GameConstants.MAX_ROOM_SIZE - occupiedSlots.size).coerceAtLeast(0)) { null }
    val teamASlots: List<PlayerSlotUiState?> = occupiedSlots.filter { it.teamId == "A" }
        .let { it + List((GameConstants.TEAM_SIZE - it.size).coerceAtLeast(0)) { null } }
    val teamBSlots: List<PlayerSlotUiState?> = occupiedSlots.filter { it.teamId == "B" }
        .let { it + List((GameConstants.TEAM_SIZE - it.size).coerceAtLeast(0)) { null } }

    LaunchedEffect(room?.status, amPending) {
        if (room?.status == RoomStatus.PLAYING && !amPending) {
            onGameStarted(viewModel.roomCode)
        }
    }

    BackHandler {
        viewModel.leaveRoom()
        onLeave()
    }

    // No title bar: the back button floats directly on the page's own
    // background instead of sitting in a separate, differently-colored strip.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The action controls are pinned rather than living at the end of the
        // scroll: the player list is the only part that should ever grow, and
        // in a full room it used to push "Hazırım" (and the last player row)
        // off the bottom of the screen entirely.
        bottomBar = {
            WaitingRoomActions(
                amPending = amPending,
                hasOthers = others.isNotEmpty(),
                allReady = allReady,
                teamImbalanceHint = teamImbalanceHint,
                amReady = amReady,
                isHost = isHost,
                isStarting = uiState.isStarting,
                errorMessage = uiState.errorMessage,
                phraseUsageCounts = phraseUsageCounts,
                onToggleReady = viewModel::toggleReady,
                onStartGame = viewModel::startGame,
                onSendReaction = viewModel::sendReaction
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 20.dp),
            // Clears the floating back button (see ScreenTopActions).
            contentPadding = PaddingValues(top = TopActionsClearance, bottom = 12.dp),
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
                            if (teamMode) GameConstants.TEAM_ROOM_SIZE else GameConstants.MAX_ROOM_SIZE
                        )
                    )
                }
            }

            if (teamMode) {
                // Two team columns instead of one flat grid — a 2v2 room's
                // whole point is which SIDE you're on, so the lobby needs to
                // show that grouping, not just a headcount.
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TeamColumn(
                            title = stringResource(R.string.online_team_a),
                            slots = teamASlots,
                            activeReactionsByUid = activeReactionsByUid,
                            onInvite = { invitePickerOpen = true },
                            modifier = Modifier.weight(1f)
                        )
                        TeamColumn(
                            title = stringResource(R.string.online_team_b),
                            slots = teamBSlots,
                            activeReactionsByUid = activeReactionsByUid,
                            onInvite = { invitePickerOpen = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // A fixed 2-column grid of GameConstants.MAX_ROOM_SIZE slots —
                // each occupied player takes half a row instead of a whole one,
                // and the still-empty slots stay visible as placeholders so the
                // room reads as "being filled in" rather than a short list.
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        playerSlots.chunked(2).forEach { rowSlots ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowSlots.forEach { slot ->
                                    PlayerSlotCell(
                                        slot = slot,
                                        activeReaction = slot?.let { activeReactionsByUid[it.uid] },
                                        onInvite = { invitePickerOpen = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
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
        ScreenTopActions(
            onBack = {
                viewModel.leaveRoom()
                onLeave()
            },
            modifier = Modifier.align(Alignment.TopStart)
        )
        }
    }

    if (invitePickerOpen) {
        LobbyInviteSheet(
            friends = friends,
            sendingToUid = inviteState.sendingToUid,
            onInvite = viewModel::inviteFriendToRoom,
            onDismiss = { invitePickerOpen = false }
        )
    }

    // The send result is reported over the lobby rather than inside the
    // sheet: the sheet is dismissible mid-send, and an invite that has
    // actually left should still say so.
    inviteState.message?.let { message ->
        LaunchedEffect(message) {
            delay(2_500)
            viewModel.consumeInviteMessage()
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 96.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            TintedBadge(text = message.asString())
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
    // Teal, not the default white face: a plain white card read as flat
    // against the textured collage background, and online surfaces already
    // use teal as their own identity color throughout the app.
    RaisedCard(corner = 24.dp, face = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.online_room_code_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = roomCode,
                style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 8.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
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
 * The pinned bottom half of the screen: the chat/emoji dock and whichever
 * single action is available right now. Messages themselves no longer show
 * here — see [PlayerSlotCell]'s per-sender chat bubble, anchored on that
 * player's own card up in the grid instead of one shared pop-up down here.
 */
@Composable
private fun WaitingRoomActions(
    amPending: Boolean,
    hasOthers: Boolean,
    allReady: Boolean,
    @StringRes teamImbalanceHint: Int? = null,
    amReady: Boolean,
    isHost: Boolean,
    isStarting: Boolean,
    errorMessage: UiText?,
    phraseUsageCounts: Map<String, Int>,
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
            ReactionSendRow(
                onSend = onSendReaction,
                modifier = Modifier.padding(bottom = 12.dp),
                phraseUsageCounts = phraseUsageCounts
            )
        }

        errorMessage?.let { message ->
            Text(
                text = message.asString(),
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
            !allReady -> Column(modifier = Modifier.fillMaxWidth()) {
                // In a 2v2 room "everyone is ready" is not enough — the sides
                // have to be even. Without this line four ready players in a
                // 3-1 split sat looking at a ready button that would never
                // turn into a start button, with nothing saying why.
                teamImbalanceHint?.let { hint ->
                    Text(
                        text = stringResource(hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                SecondaryButton(
                    text = stringResource(if (amReady) R.string.online_ready_cancel else R.string.online_ready),
                    onClick = onToggleReady,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
            String.format(Locale.US, "%d:%02d", remainingSeconds / 60, remainingSeconds % 60)
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
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

/** One slot in the lobby's 2-column grid — a real player, or null for a still-empty seat. */
private data class PlayerSlotUiState(
    val uid: String,
    val name: String,
    val level: Int,
    val frame: AvatarFrame,
    val ready: Boolean,
    val isYou: Boolean,
    val pending: Boolean,
    val onKick: (() -> Unit)?,
    val teamId: String? = null,
    /** Non-null only on the current player's own slot, only in a team room — see WaitingRoomScreen's mySlot. */
    val onSwitchTeam: (() -> Unit)? = null
)

/** One 2v2 team's column: a header and its (padded-to-TEAM_SIZE) slots stacked vertically. */
@Composable
private fun TeamColumn(
    title: String,
    slots: List<PlayerSlotUiState?>,
    activeReactionsByUid: Map<String, Reaction>,
    onInvite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        slots.forEach { slot ->
            PlayerSlotCell(
                slot = slot,
                activeReaction = slot?.let { activeReactionsByUid[it.uid] },
                onInvite = onInvite,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** A fixed row height shared by [PlayerSlotCard] and [EmptySlotCard] so occupied and empty seats line up in the grid. */
private val SLOT_HEIGHT = 62.dp

/**
 * One grid cell: [slot]'s card (or an empty placeholder). A player's own
 * [activeReaction] — if any — renders inside their own card, in the same
 * spot the ready/pending status normally sits (see [PlayerSlotCard]),
 * instead of a bubble growing out of the top of it: a bubble there pushed
 * every card below it down the grid whenever someone spoke, breaking the
 * fixed 2-column layout it sits in.
 */
@Composable
private fun PlayerSlotCell(
    slot: PlayerSlotUiState?,
    activeReaction: Reaction?,
    onInvite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (slot == null) {
        EmptySlotCard(onInvite = onInvite, modifier = modifier)
    } else {
        PlayerSlotCard(slot = slot, activeReaction = activeReaction, modifier = modifier)
    }
}

@Composable
private fun PlayerSlotCard(slot: PlayerSlotUiState, activeReaction: Reaction?, modifier: Modifier = Modifier) {
    // Ready reads as the whole card turning a light "go" green instead of a
    // small checkmark next to the name — a glance at the grid says who's
    // ready without having to read every row.
    val face = if (slot.ready) AppTheme.tokens.successContainer else MaterialTheme.colorScheme.surface
    RaisedCard(corner = 16.dp, face = face, modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = SLOT_HEIGHT)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The level badge stands in for a profile picture — it's the
                // whole point of the ladder that opponents see it. The frame
                // is each player's own pick, synced onto the room alongside
                // their level (see OnlinePlayer.frameId), so everyone sees
                // what that player actually chose.
                LevelAvatar(level = slot.level, frame = slot.frame, size = 38.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (slot.isYou) stringResource(R.string.online_you_label, slot.name) else slot.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // A chat message takes over this exact spot instead of
                    // opening a bubble above the card. A fixed-height Box
                    // around the Crossfade (rather than letting an empty
                    // state collapse to 0dp) means this line's own presence
                    // never changes the row's height — the card stays
                    // exactly SLOT_HEIGHT tall whether or not there's
                    // anything to show here right now.
                    Box(modifier = Modifier.height(16.dp)) {
                        Crossfade(targetState = activeReaction, label = "slot-status") { reaction ->
                            if (reaction != null) {
                                val phraseTextRes = presetPhraseTextRes(reaction.messageKey)
                                Text(
                                    text = phraseTextRes?.let { stringResource(it) } ?: reaction.emoji,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (slot.pending) {
                                Text(
                                    text = stringResource(R.string.online_pending_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            if (slot.onKick != null) {
                IconButton(
                    onClick = slot.onKick,
                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp)
                ) {
                    Icon(
                        Icons.Filled.PersonRemove,
                        contentDescription = stringResource(R.string.online_kick_player),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            // Never on the same slot as onKick above (that's always someone
            // ELSE's card, this is always your own) — same corner is fine.
            if (slot.onSwitchTeam != null) {
                IconButton(
                    onClick = slot.onSwitchTeam,
                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp)
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = stringResource(R.string.online_switch_team),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/**
 * A still-unfilled lobby seat, and the way to fill it.
 *
 * It used to be a barely-there grey rectangle with a 40%-opacity silhouette
 * on it — so faint that a half-empty lobby read as a rendering glitch
 * rather than as seats waiting for people. It is now a dashed outline with
 * a legible "invite" affordance, and tapping it opens the friends list to
 * send an in-game invite: the seat itself is the most obvious place to ask
 * for someone to sit in it.
 */
@Composable
private fun EmptySlotCard(onInvite: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val shape = RoundedCornerShape(16.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SLOT_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .then(if (onInvite != null) Modifier.clickable(onClick = onInvite) else Modifier)
            .drawBehind {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
                drawRoundRect(
                    color = outline,
                    style = stroke,
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
            }
            .padding(6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            if (onInvite != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.online_invite_friend_slot),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * The friends list, opened by tapping an empty seat.
 *
 * Sends an invite into the room already open, unlike FriendsScreen's own
 * invite button which spins up a fresh one — asking from inside a lobby can
 * only sensibly mean "come to this one".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LobbyInviteSheet(
    friends: List<Friend>,
    sendingToUid: String?,
    onInvite: (Friend) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.online_invite_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (friends.isEmpty()) {
                EmptyState(
                    emoji = "🤝",
                    message = stringResource(R.string.online_invite_sheet_empty),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    items(friends, key = { it.uid }) { friend ->
                        RaisedCard(corner = 18.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = friend.nickname,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                if (sendingToUid == friend.uid) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    SecondaryButton(
                                        text = stringResource(R.string.online_invite_send),
                                        onClick = { onInvite(friend) },
                                        height = 38.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
