package com.sualtikasifi.cizimhafiza.presentation.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import com.sualtikasifi.cizimhafiza.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenHeader
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReportBugClick: () -> Unit,
    onReplayTutorialClick: () -> Unit,
    onAccountClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val language by viewModel.language.collectAsState()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setNotificationsEnabled(granted) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ScreenHeader(title = stringResource(R.string.menu_settings), onBack = onBack)

            Spacer(modifier = Modifier.height(18.dp))

            SettingRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = stringResource(R.string.settings_sound),
                checked = soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingRow(
                icon = Icons.Filled.Vibration,
                label = stringResource(R.string.settings_vibration),
                checked = vibrationEnabled,
                onCheckedChange = viewModel::setVibrationEnabled
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingRow(
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
                }
            )
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
                onClick = onAccountClick
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    RaisedCard(corner = 22.dp, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconWell(icon = icon)
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

@Composable
private fun LanguageRow(selectedLanguage: String, onLanguageSelected: (String) -> Unit) {
    RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconWell(icon = Icons.Filled.Language)
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SelectableChip(
                    label = stringResource(R.string.settings_language_turkish),
                    selected = selectedLanguage == "tr",
                    onClick = { onLanguageSelected("tr") },
                    modifier = Modifier.weight(1f),
                    verticalPadding = 10.dp,
                    style = MaterialTheme.typography.bodyMedium,
                    fillWidth = true
                )
                SelectableChip(
                    label = stringResource(R.string.settings_language_english),
                    selected = selectedLanguage == "en",
                    onClick = { onLanguageSelected("en") },
                    modifier = Modifier.weight(1f),
                    verticalPadding = 10.dp,
                    style = MaterialTheme.typography.bodyMedium,
                    fillWidth = true
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    RaisedCard(corner = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconWell(icon = icon)
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CardWhite,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}
