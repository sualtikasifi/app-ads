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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            SettingRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = stringResource(R.string.settings_sound),
                checked = soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingRow(
                icon = Icons.Filled.Vibration,
                label = stringResource(R.string.settings_vibration),
                checked = vibrationEnabled,
                onCheckedChange = viewModel::setVibrationEnabled
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingRow(
                icon = Icons.Filled.Notifications,
                label = stringResource(R.string.settings_notifications),
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    val needsRuntimePermission = enabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needsRuntimePermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setNotificationsEnabled(enabled)
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            LanguageRow(
                selectedLanguage = language,
                onLanguageSelected = viewModel::setLanguage
            )
        }
    }
}

@Composable
private fun LanguageRow(selectedLanguage: String, onLanguageSelected: (String) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LanguageOption(
                    label = stringResource(R.string.settings_language_turkish),
                    selected = selectedLanguage == "tr",
                    onClick = { onLanguageSelected("tr") }
                )
                LanguageOption(
                    label = stringResource(R.string.settings_language_english),
                    selected = selectedLanguage == "en",
                    onClick = { onLanguageSelected("en") }
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}
