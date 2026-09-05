package com.sualtikasifi.cizimhafiza.presentation.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.util.asString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Clears the floating back button (see ScreenTopActions).
            Spacer(modifier = Modifier.height(TopActionsClearance))
            Spacer(modifier = Modifier.height(18.dp))

            when {
                uiState.isLinked -> LinkedSection(uiState = uiState, onBackupNow = viewModel::backupNow, onRestore = viewModel::restoreBackup)
                uiState.isGoogleSignInConfigured -> UnlinkedSection(
                    isLinking = uiState.isLinking,
                    onLinkClick = viewModel::linkGoogleAccount
                )
                else -> NotConfiguredCard()
            }

            uiState.message?.let { message ->
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = message.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.tokens.success,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = message.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Offered whether or not a Google account is linked: an
            // anonymous player still has a uid with a profile, a friends
            // list and a league entry under it, and Play's requirement is
            // about the data, not about how the account was created.
            Spacer(modifier = Modifier.height(28.dp))
            if (uiState.isDeleting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                Text(
                    text = stringResource(R.string.account_delete_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::promptDeleteAccount)
                        .padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        ScreenTopActions(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
        }
    }

    if (uiState.showDeletePrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeletePrompt,
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::deleteAccount) {
                    Text(
                        text = stringResource(R.string.account_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeletePrompt) {
                    Text(stringResource(R.string.account_delete_cancel))
                }
            }
        )
    }

    // Nothing on this screen refers to a player any more once the account is
    // gone, so it does not stay open on a deleted one.
    if (uiState.accountDeleted) {
        LaunchedEffect(Unit) { onBack() }
    }

    if (uiState.showAlreadyLinkedPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAlreadyLinkedPrompt,
            title = { Text(stringResource(R.string.account_already_linked_title)) },
            text = { Text(stringResource(R.string.account_already_linked_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::switchToExistingAccount) {
                    Text(stringResource(R.string.account_already_linked_switch))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAlreadyLinkedPrompt) {
                    Text(stringResource(R.string.account_already_linked_cancel))
                }
            }
        )
    }

    // Feedback is one-shot: clear it once shown for long enough to read,
    // so navigating back to this screen later doesn't resurface a stale
    // "backup successful" from a previous visit.
    LaunchedEffect(uiState.message, uiState.errorMessage) {
        if (uiState.message != null || uiState.errorMessage != null) {
            kotlinx.coroutines.delay(4_000)
            viewModel.dismissMessages()
        }
    }
}

@Composable
private fun UnlinkedSection(isLinking: Boolean, onLinkClick: () -> Unit) {
    RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconWell(icon = Icons.Filled.CloudOff)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.account_unlinked_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.account_unlinked_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (isLinking) {
                CircularProgressIndicator()
            } else {
                PrimaryButton(
                    text = stringResource(R.string.account_link_google),
                    onClick = onLinkClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NotConfiguredCard() {
    RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconWell(icon = Icons.Filled.CloudOff)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.account_not_configured_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LinkedSection(uiState: AccountUiState, onBackupNow: () -> Unit, onRestore: () -> Unit) {
    val linked = uiState.authState as? AuthState.Linked
    RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconWell(icon = Icons.Filled.CloudDone, tint = AppTheme.tokens.success)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.account_linked_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            (linked?.email ?: linked?.displayName)?.let { identity ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = identity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = uiState.lastBackupAtMillis?.let { millis ->
                    stringResource(R.string.account_last_backup_format, formatBackupDate(millis))
                } ?: stringResource(R.string.account_never_backed_up),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (uiState.isBackingUp) {
            CircularProgressIndicator(modifier = Modifier.weight(1f))
        } else {
            PrimaryButton(
                text = stringResource(R.string.account_backup_now),
                icon = Icons.Filled.CloudUpload,
                onClick = onBackupNow,
                modifier = Modifier.weight(1f)
            )
        }
        if (uiState.isRestoring) {
            CircularProgressIndicator(modifier = Modifier.weight(1f))
        } else {
            SecondaryButton(
                text = stringResource(R.string.account_restore_now),
                icon = Icons.Filled.Restore,
                onClick = onRestore,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatBackupDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
