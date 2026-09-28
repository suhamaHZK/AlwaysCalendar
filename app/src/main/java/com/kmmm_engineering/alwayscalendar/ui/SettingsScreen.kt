package com.kmmm_engineering.alwayscalendar.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
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
    val context = LocalContext.current
    var webhookDraft by remember(settings.discordWebhookUrl) {
        mutableStateOf(settings.discordWebhookUrl)
    }
    var testSending by remember { mutableStateOf(false) }
    val testEmptyMsg = stringResource(R.string.discord_test_empty)
    val testOkMsg = stringResource(R.string.discord_test_ok)
    val testFailMsg = stringResource(R.string.discord_test_fail)
    val testContent = stringResource(R.string.discord_test_msg)

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
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

                SectionTitle(stringResource(R.string.discord_discharge))
                ThresholdDropdown(settings.discordDischargeThreshold) { v ->
                    scope.launch { repository.setDiscordDischarge(v) }
                }
                SectionTitle(stringResource(R.string.discord_charge))
                ThresholdDropdown(settings.discordChargeThreshold) { v ->
                    scope.launch { repository.setDiscordCharge(v) }
                }

                SwitchRow(
                    label = stringResource(R.string.debug_scrim_always_show),
                    checked = settings.debugScrimAlwaysShow,
                    onCheckedChange = { v -> scope.launch { repository.setDebugScrimAlwaysShow(v) } },
                )

                SectionTitle(stringResource(R.string.debug_scrim_opacity))
                DebugIntField(
                    value = settings.debugScrimOpacityPercent,
                    valueRange = UserSettings.DEBUG_SCRIM_OPACITY_MIN..UserSettings.DEBUG_SCRIM_OPACITY_MAX,
                    defaultValue = UserSettings.DEBUG_SCRIM_OPACITY_DEFAULT,
                    suffix = "%",
                    onCommit = { v -> scope.launch { repository.setDebugScrimOpacity(v) } },
                )

                SectionTitle(stringResource(R.string.debug_scrim_blur))
                DebugIntField(
                    value = settings.debugScrimBlurDp,
                    valueRange = UserSettings.DEBUG_SCRIM_BLUR_MIN..UserSettings.DEBUG_SCRIM_BLUR_MAX,
                    defaultValue = UserSettings.DEBUG_SCRIM_BLUR_DEFAULT,
                    suffix = "dp",
                    onCommit = { v -> scope.launch { repository.setDebugScrimBlur(v) } },
                )

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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugIntField(
    value: Int,
    valueRange: IntRange,
    defaultValue: Int,
    suffix: String,
    onCommit: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current

    fun finalize() {
        val clamped = (text.toIntOrNull() ?: defaultValue).coerceIn(valueRange)
        text = clamped.toString()
        onCommit(clamped)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            // Allow empty while typing; reject non-digits. Out-of-range is coerced on commit.
            if (raw.isEmpty()) {
                text = ""
                return@OutlinedTextField
            }
            if (raw.all { it.isDigit() } && raw.length <= 3) {
                text = raw
                raw.toIntOrNull()?.let { parsed ->
                    onCommit(parsed.coerceIn(valueRange))
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (!state.isFocused) finalize()
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                finalize()
                focusManager.clearFocus()
            },
        ),
        suffix = { Text(suffix, color = Color(0xFFAAAAAA)) },
        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
    )
}

private fun versionFooter(): String {
    return if (BuildConfig.APP_DEBUG.isNotEmpty()) {
        "${BuildConfig.VERSION_NAME} debug${BuildConfig.APP_DEBUG}"
    } else {
        "ver.${BuildConfig.VERSION_NAME}"
    }
}
