package com.sualtikasifi.cizimhafiza.presentation.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.BlockedUser
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.AppTextField
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.OrangeContainer
import com.sualtikasifi.cizimhafiza.util.InviteShareUtil
import com.sualtikasifi.cizimhafiza.util.UiText
import com.sualtikasifi.cizimhafiza.util.asString

@Composable
fun FriendsScreen(
    onNavigateToWaitingRoom: (roomCode: String) -> Unit,
    onBack: () -> Unit,
    onDuel: (opponentUid: String, opponentName: String) -> Unit,
    onDuelList: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.navigateToWaitingRoomCode) {
        uiState.navigateToWaitingRoomCode?.let { roomCode ->
            onNavigateToWaitingRoom(roomCode)
            viewModel.onNavigatedToWaitingRoom()
        }
    }

    uiState.confirmRemove?.let { friend ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveConfirm,
            title = { Text(stringResource(R.string.friends_remove_confirm_title)) },
            text = { Text(stringResource(R.string.friends_remove_confirm_message, friend.nickname)) },
            confirmButton = {
                TextButton(onClick = { viewModel.removeFriend(friend) }) {
                    Text(stringResource(R.string.friends_remove_confirm_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveConfirm) {
                    Text(stringResource(R.string.friends_remove_confirm_cancel))
                }
            }
        )
    }

    uiState.confirmBlock?.let { friend ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBlockConfirm,
            title = { Text(stringResource(R.string.friends_block_confirm_title)) },
            text = { Text(stringResource(R.string.friends_block_confirm_message, friend.nickname)) },
            confirmButton = {
                TextButton(onClick = { viewModel.blockFriend(friend) }) {
                    Text(stringResource(R.string.friends_block_confirm_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBlockConfirm) {
                    Text(stringResource(R.string.friends_block_confirm_cancel))
                }
            }
        )
    }

    // No title bar: the back button (and duel-list action) float directly on
    // the page's own background instead of sitting in a separate,
    // differently-colored strip.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            // Extra top inset so the first card doesn't render underneath
            // the floating back button below.
            contentPadding = PaddingValues(top = 76.dp, bottom = 16.dp)
        ) {
            item {
                MyCodeCard(
                    code = uiState.myFriendCode,
                    onShare = { code -> InviteShareUtil.shareFriendCode(context, code) }
                )
            }

            item { AddFriendSection(uiState = uiState, viewModel = viewModel) }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message.asString(),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { InfoMessageRow(message = uiState.infoMessage) }

            item {
                Text(text = stringResource(R.string.friends_list_title), style = MaterialTheme.typography.titleLarge)
            }

            if (uiState.friends.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.friends_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            } else {
                items(uiState.friends, key = { it.uid }) { friend ->
                    FriendRow(
                        friend = friend,
                        inviting = uiState.invitingFriendUid == friend.uid,
                        busy = uiState.removingFriendUid == friend.uid || uiState.blockingFriendUid == friend.uid,
                        onInvite = { viewModel.inviteFriend(friend) },
                        onRemove = { viewModel.confirmRemoveFriend(friend) },
                        onBlock = { viewModel.confirmBlockFriend(friend) },
                        onDuel = { onDuel(friend.uid, friend.nickname) }
                    )
                }
            }

            if (uiState.blockedUsers.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = stringResource(R.string.friends_blocked_section_title), style = MaterialTheme.typography.titleLarge)
                }
                items(uiState.blockedUsers, key = { it.uid }) { blocked ->
                    BlockedUserRow(
                        blocked = blocked,
                        unblocking = uiState.unblockingUid == blocked.uid,
                        onUnblock = { viewModel.unblockUser(blocked) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RaisedIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack
            )
            Spacer(modifier = Modifier.weight(1f))
            RaisedIconButton(
                icon = Icons.Filled.SportsMma,
                contentDescription = stringResource(R.string.duel_list_title),
                onClick = onDuelList
            )
        }
        }
    }
}

@Composable
private fun MyCodeCard(code: String?, onShare: (String) -> Unit) {
    // Orange, not the default white face: a plain white card read as flat
    // against the textured collage background, and this code is the
    // screen's single most important piece of content — the same "hero
    // card" treatment as the main menu's level/daily-challenge cards.
    RaisedCard(
        corner = 26.dp,
        face = OrangeContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        raise = 7.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.friends_my_code_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (code != null) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 6.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryButton(
                    text = stringResource(R.string.friends_share_code),
                    onClick = { onShare(code) },
                    icon = Icons.Filled.Share
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun AddFriendSection(uiState: FriendsUiState, viewModel: FriendsViewModel) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column {
        Text(text = stringResource(R.string.friends_add_friend_label), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                value = uiState.addFriendCodeInput,
                onValueChange = viewModel::setAddFriendCodeInput,
                placeholder = stringResource(R.string.friends_add_friend_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.weight(1f)
            )
            if (uiState.isAddingFriend) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            } else {
                PrimaryButton(
                    text = stringResource(R.string.friends_add_button),
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        viewModel.addFriend()
                    },
                    enabled = uiState.addFriendCodeInput.length == 6
                )
            }
        }
    }
}

/**
 * Fades out over ~1s once [message] clears (see FriendsViewModel.addFriend's
 * self-clearing infoMessage) instead of just vanishing — the text itself is
 * remembered across that fade so it doesn't blank out mid-animation.
 */
@Composable
private fun InfoMessageRow(message: UiText?) {
    var lastMessage by remember { mutableStateOf<UiText?>(null) }
    LaunchedEffect(message) {
        if (message != null) lastMessage = message
    }
    AnimatedVisibility(visible = message != null, exit = fadeOut(tween(800))) {
        Text(
            text = lastMessage?.asString().orEmpty(),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    inviting: Boolean,
    busy: Boolean,
    onInvite: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
    onDuel: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    RaisedCard(
        corner = 20.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = friend.nickname, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (inviting || busy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                SecondaryButton(text = stringResource(R.string.friends_invite_action), onClick = onInvite)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.friends_row_more_actions),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duel_challenge_action)) },
                            leadingIcon = { Icon(Icons.Filled.SportsMma, contentDescription = null) },
                            onClick = { menuExpanded = false; onDuel() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.friends_remove_action)) },
                            leadingIcon = { Icon(Icons.Filled.PersonRemove, contentDescription = null) },
                            onClick = { menuExpanded = false; onRemove() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.friends_block_action)) },
                            leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                            onClick = { menuExpanded = false; onBlock() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedUserRow(blocked: BlockedUser, unblocking: Boolean, onUnblock: () -> Unit) {
    RaisedCard(
        corner = 20.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = blocked.nickname, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (unblocking) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                SecondaryButton(text = stringResource(R.string.friends_unblock_action), onClick = onUnblock)
            }
        }
    }
}
