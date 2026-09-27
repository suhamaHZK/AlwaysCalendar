package com.kmmm_engineering.alwayscalendar.ui

import android.widget.Toast
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kmmm_engineering.alwayscalendar.BuildConfig
import com.kmmm_engineering.alwayscalendar.R
import com.kmmm_engineering.alwayscalendar.data.LandscapeMode
import com.kmmm_engineering.alwayscalendar.data.SettingsRepository
import com.kmmm_engineering.alwayscalendar.data.UserSettings
import com.kmmm_engineering.alwayscalendar.data.WeekStart
import com.kmmm_engineering.alwayscalendar.discord.DiscordNotifier
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
    val context = LocalContext.current
    var webhookDraft by remember(settings.discordWebhookUrl) {
        mutableStateOf(settings.discordWebhookUrl)
    }
    val testEmptyMsg = stringResource(R.string.discord_test_empty)
    val testOkMsg = stringResource(R.string.discord_test_ok)
    val testFailMsg = stringResource(R.string.discord_test_fail)
    val testContent = stringResource(R.string.discord_test_msg)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color.Gray,
        unfocusedBorderColor = Color.DarkGray,
        cursorColor = Color.White,
        focusedLabelColor = Color.LightGray,
        unfocusedLabelColor = Color.Gray,
        focusedPlaceholderColor = Color.Gray,
        unfocusedPlaceholderColor = Color.DarkGray,
    )

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
            OutlinedTextField(
                value = webhookDraft,
                onValueChange = {
                    webhookDraft = it
                    scope.launch { repository.setDiscordWebhookUrl(it.trim()) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://discord.com/api/webhooks/…") },
                colors = fieldColors,
            )
            TextButton(
                onClick = {
                    val url = webhookDraft.trim().ifEmpty { settings.discordWebhookUrl.trim() }
                    if (url.isEmpty()) {
                        Toast.makeText(context, testEmptyMsg, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    scope.launch {
                        val ok = DiscordNotifier.send(url, testContent)
                        Toast.makeText(
                            context,
                            if (ok) testOkMsg else testFailMsg,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Text(stringResource(R.string.discord_test_send), color = Color.White)
            }

            SectionTitle(stringResource(R.string.discord_discharge))
            ThresholdDropdown(settings.discordDischargeThreshold) { v ->
                scope.launch { repository.setDiscordDischarge(v) }
            }
            SectionTitle(stringResource(R.string.discord_charge))
            ThresholdDropdown(settings.discordChargeThreshold) { v ->
                scope.launch { repository.setDiscordCharge(v) }
            }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThresholdDropdown(
    value: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val offLabel = stringResource(R.string.threshold_off)
    val options = listOf(-1) + (5..100 step 5).toList()
    val label = if (value < 0) offLabel else "$value%"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.Gray,
                unfocusedBorderColor = Color.DarkGray,
                disabledTextColor = Color.White,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { opt ->
                val text = if (opt < 0) offLabel else "$opt%"
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    },
                )
            }
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

private fun versionFooter(): String {
    return if (BuildConfig.APP_DEBUG.isNotEmpty()) {
        "${BuildConfig.VERSION_NAME} debug${BuildConfig.APP_DEBUG}"
    } else {
        "ver.${BuildConfig.VERSION_NAME}"
    }
}
