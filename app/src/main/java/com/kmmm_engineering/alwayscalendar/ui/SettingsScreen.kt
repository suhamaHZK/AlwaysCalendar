package com.kmmm_engineering.alwayscalendar.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.kmmm_engineering.alwayscalendar.util.DiscordIconSaver
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
    val context = LocalContext.current
    var webhookDraft by remember(settings.discordWebhookUrl) {
        mutableStateOf(settings.discordWebhookUrl)
    }
    var testSending by remember { mutableStateOf(false) }
    val testEmptyMsg = stringResource(R.string.discord_test_empty)
    val testOkMsg = stringResource(R.string.discord_test_ok)
    val testFailMsg = stringResource(R.string.discord_test_fail)
    val testContent = stringResource(R.string.discord_test_msg)
    val iconOkMsg = stringResource(R.string.discord_icon_save_ok)
    val iconFailMsg = stringResource(R.string.discord_icon_save_fail)

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
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Usable W/H (not Configuration.orientation): W>H → landscape column width.
            val isLandscape = maxWidth > maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionTitle(stringResource(R.string.idle_brightness))
                    Text(
                        text = "%.0f%%".format(settings.idleBrightness * 100),
                        color = Color(0xFFAAAAAA),
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
                    ChipRow {
                        SettingChip(
                            stringResource(R.string.landscape_off),
                            settings.landscapeMode == LandscapeMode.OFF,
                        ) { scope.launch { repository.setLandscape(LandscapeMode.OFF) } }
                        SettingChip(
                            stringResource(R.string.landscape_right),
                            settings.landscapeMode == LandscapeMode.RIGHT,
                        ) { scope.launch { repository.setLandscape(LandscapeMode.RIGHT) } }
                        SettingChip(
                            stringResource(R.string.landscape_left),
                            settings.landscapeMode == LandscapeMode.LEFT,
                        ) { scope.launch { repository.setLandscape(LandscapeMode.LEFT) } }
                    }

                    SectionTitle(stringResource(R.string.week_start))
                    ChipRow {
                        SettingChip(
                            stringResource(R.string.week_start_sunday),
                            settings.weekStart == WeekStart.SUNDAY,
                        ) { scope.launch { repository.setWeekStart(WeekStart.SUNDAY) } }
                        SettingChip(
                            stringResource(R.string.week_start_monday),
                            settings.weekStart == WeekStart.MONDAY,
                        ) { scope.launch { repository.setWeekStart(WeekStart.MONDAY) } }
                    }

                    SwitchRow(
                        label = stringResource(R.string.holidays_as_white),
                        checked = settings.holidaysAsWhite,
                        onCheckedChange = { v -> scope.launch { repository.setHolidaysAsWhite(v) } },
                    )
                    SwitchRow(
                        label = stringResource(R.string.saturday_as_white),
                        checked = settings.saturdayAsWhite,
                        onCheckedChange = { v -> scope.launch { repository.setSaturdayAsWhite(v) } },
                    )
                    SwitchRow(
                        label = stringResource(R.string.sunday_as_white),
                        checked = settings.sundayAsWhite,
                        onCheckedChange = { v -> scope.launch { repository.setSundayAsWhite(v) } },
                    )

                    CustomDayRuleSection(
                        title = stringResource(R.string.custom_holidays),
                        rules = settings.customHolidays,
                        onAdd = { rule -> scope.launch { repository.addCustomHoliday(rule) } },
                        onRemove = { id -> scope.launch { repository.removeCustomHoliday(id) } },
                    )
                    CustomDayRuleSection(
                        title = stringResource(R.string.custom_weekdays),
                        rules = settings.customWeekdays,
                        onAdd = { rule -> scope.launch { repository.addCustomWeekday(rule) } },
                        onRemove = { id -> scope.launch { repository.removeCustomWeekday(id) } },
                    )
                    CustomDayTransferSection(
                        holidays = settings.customHolidays,
                        weekdays = settings.customWeekdays,
                        repository = repository,
                    )

                    SwitchRow(
                        label = stringResource(R.string.fullscreen_enabled),
                        checked = settings.fullscreenEnabled,
                        onCheckedChange = { v -> scope.launch { repository.setFullscreenEnabled(v) } },
                    )

                    SwitchRow(
                        label = stringResource(R.string.misoperation_lock),
                        checked = settings.misoperationLock,
                        onCheckedChange = { v -> scope.launch { repository.setMisoperationLock(v) } },
                    )
                    SwitchRow(
                        label = stringResource(R.string.exit_on_unplug),
                        checked = settings.exitOnUnplug,
                        onCheckedChange = { v -> scope.launch { repository.setExitOnUnplug(v) } },
                    )
                    SwitchRow(
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
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    )
                    Button(
                        onClick = {
                            val url = webhookDraft.trim().ifEmpty { settings.discordWebhookUrl.trim() }
                            if (url.isEmpty()) {
                                Toast.makeText(context, testEmptyMsg, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (testSending) return@Button
                            testSending = true
                            scope.launch {
                                val ok = DiscordNotifier.send(url, testContent)
                                Toast.makeText(
                                    context,
                                    if (ok) testOkMsg else testFailMsg,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                testSending = false
                            }
                        },
                        enabled = !testSending,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.discord_test_send))
                    }
                    Button(
                        onClick = {
                            val ok = DiscordIconSaver.saveToGallery(context)
                            Toast.makeText(
                                context,
                                if (ok) iconOkMsg else iconFailMsg,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.discord_icon_get))
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
                        color = Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = versionFooter(),
                        color = Color(0xFF888888),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThresholdDropdown(
    value: Int,
    onSelect: (Int) -> Unit,
) {
    val offLabel = stringResource(R.string.threshold_off)
    val options = remember { listOf(-1) + (5..100 step 5).toList() }
    val labelOf: (Int) -> String = { v -> if (v < 0) offLabel else "$v%" }
    SimpleDropdown(
        options = options.map { it to labelOf(it) },
        selected = value,
        labelOf = { labelOf(it) },
        onSelect = onSelect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SimpleDropdown(
    options: List<Pair<T, String>>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (opt, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
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
        style = MaterialTheme.typography.titleSmall,
        color = Color(0xFFCCCCCC),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun SettingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
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
