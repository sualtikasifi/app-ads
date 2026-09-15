package com.sualtikasifi.cizimhafiza.presentation.settings

import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import com.sualtikasifi.cizimhafiza.util.AppReviewLauncher
import com.sualtikasifi.cizimhafiza.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.SupportedLanguage
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.DEVELOPER_REVEAL_TAPS
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReportBugClick: () -> Unit,
    onReplayTutorialClick: () -> Unit,
    onAccountClick: () -> Unit,
    /**
     * Opens the report inbox, after [DEVELOPER_REVEAL_TAPS] taps on the
     * version line below. Hidden this way rather than as a menu row because
     * it is not a player-facing screen — see DeveloperAccess.
     */
    onDeveloperReveal: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var versionTaps by remember { mutableIntStateOf(0) }
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val musicEnabled by viewModel.musicEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val language by viewModel.language.collectAsState()
    val showAccountNudge by viewModel.showAccountNudge.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setNotificationsEnabled(granted) }

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

            // 2x2 rather than four stacked full-width rows: four on/off
            // toggles that each only ever say one short word took up as
            // much vertical space as everything else on this screen
            // combined.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SettingGridCell(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = stringResource(R.string.settings_sound),
                    checked = soundEnabled,
                    onCheckedChange = viewModel::setSoundEnabled,
                    modifier = Modifier.weight(1f)
                )
                SettingGridCell(
                    icon = Icons.Filled.MusicNote,
                    label = stringResource(R.string.settings_music),
                    checked = musicEnabled,
                    onCheckedChange = viewModel::setMusicEnabled,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SettingGridCell(
                    icon = Icons.Filled.Vibration,
                    label = stringResource(R.string.settings_vibration),
                    checked = vibrationEnabled,
                    onCheckedChange = viewModel::setVibrationEnabled,
                    modifier = Modifier.weight(1f)
                )
                SettingGridCell(
                    icon = Icons.Filled.Notifications,
                    label = stringResource(R.string.settings_notifications),
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        val needsRuntimePermission = enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        if (needsRuntimePermission) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsEnabled(enabled)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LanguageRow(selectedLanguage = language, onLanguageSelected = viewModel::setLanguage)
            Spacer(modifier = Modifier.height(10.dp))
            NavRow(
                icon = Icons.Filled.School,
                label = stringResource(R.string.settings_replay_tutorial),
                onClick = onReplayTutorialClick
            )
            Spacer(modifier = Modifier.height(10.dp))
            NavRow(
                icon = Icons.Filled.BugReport,
                label = stringResource(R.string.report_bug_title),
                onClick = onReportBugClick
            )
            Spacer(modifier = Modifier.height(10.dp))
            NavRow(
                icon = Icons.Filled.AccountCircle,
                label = stringResource(R.string.account_title),
                onClick = onAccountClick,
                showBadge = showAccountNudge
            )
            Spacer(modifier = Modifier.height(10.dp))
            NavRow(
                icon = Icons.Filled.StarRate,
                label = stringResource(R.string.settings_rate_app),
                // Straight to the store listing, not Play Core's in-app
                // review sheet — that API silently does nothing on a
                // sideloaded install or once its quota is spent, with no
                // failure callback to fall back from, so a tap here read as
                // a dead button. This is deterministic on every install.
                onClick = { activity?.let(AppReviewLauncher::openStoreListing) }
            )

            // The build actually running, printed where anyone can find it.
            // Without this there was no way to answer "is the APK on this
            // phone the new one?" — every build looked identical from the
            // inside, and a sideloaded install that silently did not replace
            // the old app was indistinguishable from one that did.
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(
                    R.string.settings_version_format,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    // No ripple and no hint that this does anything: a player
                    // who taps the version seven times should see exactly
                    // what a player who taps it once sees.
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        versionTaps++
                        if (versionTaps >= DEVELOPER_REVEAL_TAPS) {
                            versionTaps = 0
                            onDeveloperReveal()
                        }
                    }
            )
        }
        ScreenTopActions(
            onBack = onBack,
            title = stringResource(R.string.menu_settings),
            modifier = Modifier.align(Alignment.TopStart)
        )
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit, showBadge: Boolean = false) {
    RaisedCard(corner = 22.dp, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconWell(icon = icon)
                    // Same dot as MenuTile's unseen-achievement badge — a
                    // presence indicator, not a count, since there's nothing
                    // here to count: just "still anonymous and played enough
                    // to have something worth protecting".
                    if (showBadge) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A dropdown rather than a row of buttons — two languages fit side by side
 * as chips, but [SupportedLanguage] is meant to grow, and a chip row that
 * keeps adding entries either wraps awkwardly or shrinks each one down to
 * an initial. A dropdown stays exactly this wide no matter how many
 * languages the list eventually holds.
 */
@Composable
private fun LanguageRow(selectedLanguage: String, onLanguageSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = SupportedLanguage.resolve(selectedLanguage)
    RaisedCard(corner = 22.dp, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconWell(icon = Icons.Filled.Language)
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(selected.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SupportedLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(stringResource(language.labelRes)) },
                        onClick = {
                            expanded = false
                            onLanguageSelected(language.code)
                        }
                    )
                }
            }
        }
    }
}

/**
 * One cell of the 2x2 Ses/Müzik/Titreşim/Bildirimler grid — icon and switch
 * share a row, the label sits below on its own so a half-width card still
 * has room for it without wrapping or shrinking the switch.
 */
@Composable
private fun SettingGridCell(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    RaisedCard(corner = 20.dp, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconWell(icon = icon)
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
