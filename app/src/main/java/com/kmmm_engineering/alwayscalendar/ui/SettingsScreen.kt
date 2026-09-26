package com.kmmm_engineering.alwayscalendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kmmm_engineering.alwayscalendar.BuildConfig
import com.kmmm_engineering.alwayscalendar.R
import com.kmmm_engineering.alwayscalendar.data.LandscapeMode
import com.kmmm_engineering.alwayscalendar.data.SettingsRepository
import com.kmmm_engineering.alwayscalendar.data.UserSettings
import com.kmmm_engineering.alwayscalendar.data.WeekStart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    repository: SettingsRepository,
    onBack: () -> Unit,
    onIdleBrightnessPreview: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.idle_brightness))
            Text(
                text = "${(settings.idleBrightness * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = settings.idleBrightness,
                onValueChange = { v ->
                    onIdleBrightnessPreview(v)
                    scope.launch { repository.setIdleBrightness(v) }
                },
                onValueChangeFinished = { onIdleBrightnessPreview(null) },
                valueRange = UserSettings.IDLE_BRIGHTNESS_MIN..UserSettings.IDLE_BRIGHTNESS_MAX,
            )

            SectionTitle(stringResource(R.string.force_landscape))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LandscapeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        LandscapeMode.OFF -> stringResource(R.string.landscape_off)
                        LandscapeMode.RIGHT -> stringResource(R.string.landscape_right)
                        LandscapeMode.LEFT -> stringResource(R.string.landscape_left)
                    }
                    TextButton(
                        onClick = { scope.launch { repository.setLandscape(mode) } },
                    ) {
                        Text(
                            text = label,
                            color = if (settings.landscapeMode == mode) Color.White else Color.Gray,
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.week_start))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeekStart.entries.forEach { start ->
                    val label = when (start) {
                        WeekStart.SUNDAY -> stringResource(R.string.week_start_sunday)
                        WeekStart.MONDAY -> stringResource(R.string.week_start_monday)
                    }
                    TextButton(
                        onClick = { scope.launch { repository.setWeekStart(start) } },
                    ) {
                        Text(
                            text = label,
                            color = if (settings.weekStart == start) Color.White else Color.Gray,
                        )
                    }
                }
            }

            ToggleRow(
                label = stringResource(R.string.misoperation_lock),
                checked = settings.misoperationLock,
                onCheckedChange = { v -> scope.launch { repository.setMisoperationLock(v) } },
            )
            ToggleRow(
                label = stringResource(R.string.exit_on_unplug),
                checked = settings.exitOnUnplug,
                onCheckedChange = { v -> scope.launch { repository.setExitOnUnplug(v) } },
            )
            ToggleRow(
                label = stringResource(R.string.allow_auto_sleep),
                checked = settings.allowAutoSleep,
                onCheckedChange = { v -> scope.launch { repository.setAllowAutoSleep(v) } },
            )

            SectionTitle(stringResource(R.string.discord_webhook))
            Text(
                text = if (settings.discordWebhookUrl.isEmpty()) {
                    stringResource(R.string.discord_webhook_empty)
                } else {
                    stringResource(R.string.discord_webhook_set)
                },
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.discord_thresholds_stub,
                    thresholdLabel(settings.discordDischargeThreshold),
                    thresholdLabel(settings.discordChargeThreshold),
                ),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle(stringResource(R.string.credits_oss))
            Text(
                text = stringResource(R.string.credits_holidays_jp),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = versionFooter(),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun thresholdLabel(value: Int): String =
    if (value < 0) "OFF" else "$value%"

private fun versionFooter(): String {
    return if (BuildConfig.APP_DEBUG.isNotEmpty()) {
        "${BuildConfig.VERSION_NAME} debug${BuildConfig.APP_DEBUG}"
    } else {
        "ver.${BuildConfig.VERSION_NAME}"
    }
}
