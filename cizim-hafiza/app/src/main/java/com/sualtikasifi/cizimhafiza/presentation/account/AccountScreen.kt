package com.sualtikasifi.cizimhafiza.presentation.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import com.sualtikasifi.cizimhafiza.presentation.common.AppTextField
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.raisedSurface
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.util.AppRestarter
import com.sualtikasifi.cizimhafiza.util.asString
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * One profile, one pair of buttons: sign in, or sign out.
 *
 * Backing up is not offered as an action because it is not one — it runs
 * on its own (see util.AutoBackupPublisher) and the card below reports
 * when it last did, which is the only part a player actually needs. The
 * old "Şimdi Yedekle"/"Yedeği Geri Yükle"/"Hesap Değiştir" trio is gone
 * for the same reason: each was a way to put the device into a state where
 * the profile on screen and the account it belonged to had drifted apart.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // The account changed, so every in-memory copy of the previous one has
    // to go — see util.AppRestarter.
    LaunchedEffect(uiState.restartRequired) {
        if (uiState.restartRequired) AppRestarter.restart(context)
    }

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
                Spacer(modifier = Modifier.height(TopActionsClearance))

                when {
                    uiState.isSignedIn -> SignedInCard(uiState)
                    uiState.isGoogleSignInConfigured -> SignedOutCard(uiState)
                    else -> NotConfiguredCard()
                }

                Spacer(modifier = Modifier.height(14.dp))
                NicknameCard(nickname = uiState.nickname, onNicknameChange = viewModel::setNickname)

                Spacer(modifier = Modifier.height(18.dp))
                if (uiState.isBusy) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp))
                    }
                } else if (uiState.isSignedIn) {
                    SecondaryButton(
                        text = stringResource(R.string.account_sign_out),
                        icon = Icons.AutoMirrored.Filled.Logout,
                        onClick = viewModel::promptSignOut,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (uiState.isGoogleSignInConfigured) {
                    GoogleSignInButton(onClick = viewModel::signIn, modifier = Modifier.fillMaxWidth())
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

                // Offered whether or not a Google account is signed in: an
                // anonymous player still has a uid with a profile, a friends
                // list and a league entry under it, and Play's requirement is
                // about the data, not about how the account was created.
                Spacer(modifier = Modifier.height(28.dp))
                if (uiState.isDeleting) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
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

    if (uiState.showSignOutPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSignOutPrompt,
            title = { Text(stringResource(R.string.account_sign_out_title)) },
            text = { Text(stringResource(R.string.account_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::signOut) {
                    Text(stringResource(R.string.account_sign_out_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSignOutPrompt) {
                    Text(stringResource(R.string.account_sign_out_cancel))
                }
            }
        )
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

    // Feedback is one-shot: clear it once shown for long enough to read,
    // so navigating back to this screen later doesn't resurface a stale
    // message from a previous visit.
    LaunchedEffect(uiState.message, uiState.errorMessage) {
        if (uiState.message != null || uiState.errorMessage != null) {
            kotlinx.coroutines.delay(4_000)
            viewModel.dismissMessages()
        }
    }
}

/**
 * Who is signed in, what level they are, and whether their progress is
 * safe — the three things a player opens this screen to check.
 */
@Composable
private fun SignedInCard(uiState: AccountUiState) {
    val linked = uiState.authState as? AuthState.Linked
    RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (linked?.photoUrl != null) {
                    AsyncImage(
                        model = linked.photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                } else {
                    LevelAvatar(level = uiState.level, frame = uiState.frame, size = 56.dp)
                }
                Spacer(modifier = Modifier.size(14.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = linked?.displayName?.takeIf { it.isNotBlank() }
                            ?: linked?.email
                            ?: stringResource(R.string.account_signed_in_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    linked?.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelAvatar(level = uiState.level, frame = uiState.frame, size = 34.dp)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.account_level_format, uiState.level),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            SyncStatusRow(lastBackupAtMillis = uiState.lastBackupAtMillis)
        }
    }
}

/**
 * States the sync guarantee in the one place a player would look for it —
 * replacing the two buttons that used to imply syncing was their job.
 *
 * The two states are told apart deliberately. This row used to show the
 * green "kaydedildi" tick unconditionally, so an account whose progress had
 * never once reached the cloud was still reassured that it had — which is
 * the exact false comfort behind the account that was lost. A backup that
 * has not happened yet now looks like one that has not happened yet.
 */
@Composable
private fun SyncStatusRow(lastBackupAtMillis: Long?) {
    val backedUp = lastBackupAtMillis != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (backedUp) Icons.Filled.CloudDone else Icons.Filled.CloudSync,
            contentDescription = null,
            tint = if (backedUp) AppTheme.tokens.success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column {
            Text(
                text = stringResource(
                    if (backedUp) R.string.account_sync_on else R.string.account_sync_pending
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = lastBackupAtMillis?.let {
                    stringResource(R.string.account_last_backup_format, rememberBackupTimestamp(it))
                } ?: stringResource(R.string.account_never_backed_up),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The Google-branded entry point.
 *
 * Not a [PrimaryButton] with a label, which is what it used to be: Google's
 * Sign-In branding guidelines require their own mark on the button that
 * starts their flow, and a bare coloured pill saying "Google ile Giriş Yap"
 * meets neither the guideline nor a player's expectation of what a Google
 * sign-in looks like. Built on the app's own [raisedSurface] so it still
 * belongs to this screen — the guidelines constrain the logo and the
 * wording, not the shape around them.
 */
@Composable
private fun GoogleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .raisedSurface(
                face = GoogleButtonFace,
                edge = GoogleButtonEdge,
                corner = 29.dp,
                pressed = pressed,
                border = GoogleButtonBorder
            )
            .height(58.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.account_sign_in_google),
                style = MaterialTheme.typography.titleMedium,
                color = GoogleButtonText,
                maxLines = 1
            )
        }
    }
}

// Fixed rather than theme-derived: these are Google's own button colours,
// and the whole point of the branding is that it looks the same in every
// app a player meets it in.
private val GoogleButtonFace = Color(0xFFFFFFFF)
private val GoogleButtonEdge = Color(0xFFDADCE0)
private val GoogleButtonBorder = Color(0xFF747775)
private val GoogleButtonText = Color(0xFF1F1F1F)

@Composable
private fun SignedOutCard(uiState: AccountUiState) {
    RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LevelAvatar(level = uiState.level, frame = uiState.frame, size = 56.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.account_signed_out_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.account_signed_out_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NotConfiguredCard() {
    RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

/**
 * Shown signed in or out: an anonymous player has a nickname too (it is
 * what friends and league tables already show). Writes on every keystroke,
 * same as the Oda Kur/Koda Katıl fields — a nickname is a preference, not
 * something that needs an explicit save step.
 */
@Composable
private fun NicknameCard(nickname: String, onNicknameChange: (String) -> Unit) {
    RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
        AppTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = stringResource(R.string.account_nickname_label),
            placeholder = stringResource(R.string.account_nickname_hint),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}

/**
 * "bugün 14:32" for a backup from today, "dün 14:32" for yesterday, the
 * full date before that.
 *
 * The absolute date was technically correct and read like a receipt. The
 * question this line answers is "is my progress safe right now?", and for
 * a backup made minutes ago the answer is far clearer as "bugün".
 *
 * Remembered on the timestamp because a SimpleDateFormat is not cheap to
 * build and this recomposes with the rest of the card.
 */
@Composable
private fun rememberBackupTimestamp(millis: Long): String {
    val context = LocalContext.current
    return remember(millis) {
        val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        when (LocalDate.now().toEpochDay() - day.toEpochDay()) {
            0L -> context.getString(R.string.account_backup_today, timeOnly)
            1L -> context.getString(R.string.account_backup_yesterday, timeOnly)
            else -> SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
        }
    }
}
