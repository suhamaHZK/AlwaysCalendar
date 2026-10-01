package com.kmmm_engineering.alwayscalendar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kmmm_engineering.alwayscalendar.R
import com.kmmm_engineering.alwayscalendar.data.CustomDayRule
import com.kmmm_engineering.alwayscalendar.data.CustomDayTransfer
import com.kmmm_engineering.alwayscalendar.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Export / import controls for custom holidays & weekdays.
 * Placed near the custom-day sections on the settings screen.
 */
@Composable
fun CustomDayTransferSection(
    holidays: List<CustomDayRule>,
    weekdays: List<CustomDayRule>,
    repository: SettingsRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImportPaste by remember { mutableStateOf(false) }
    var pendingParse by remember { mutableStateOf<CustomDayTransfer.ParseResult?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.custom_days_transfer),
            color = Color(0xFFCCCCCC),
        )
        Text(
            text = stringResource(R.string.custom_days_transfer_hint),
            color = Color(0xFF888888),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = {
                    val json = CustomDayTransfer.encodePackage(holidays, weekdays)
                    copyAndShare(context, json)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.custom_days_export))
            }
            OutlinedButton(
                onClick = { showImportPaste = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.custom_days_import))
            }
        }
    }

    if (showImportPaste) {
        ImportPasteDialog(
            onDismiss = { showImportPaste = false },
            onParsed = { result ->
                showImportPaste = false
                when (result) {
                    is CustomDayTransfer.ParseResult.Failure -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                    else -> pendingParse = result
                }
            },
        )
    }

    val parse = pendingParse
    if (parse != null && parse !is CustomDayTransfer.ParseResult.Failure) {
        ImportApplyDialog(
            parse = parse,
            onDismiss = { pendingParse = null },
            onApply = { mode, categoryOrNull ->
                pendingParse = null
                scope.launch {
                    val report = applyImport(repository, parse, mode, categoryOrNull)
                    Toast.makeText(context, report.toastMessage(), Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

private fun copyAndShare(context: Context, json: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(CustomDayTransfer.FORMAT, json))
    Toast.makeText(
        context,
        context.getString(R.string.custom_days_export_copied),
        Toast.LENGTH_SHORT,
    ).show()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, json)
        putExtra(
            Intent.EXTRA_SUBJECT,
            context.getString(R.string.custom_days_export_subject),
        )
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.custom_days_export)),
    )
}

@Composable
private fun ImportPasteDialog(
    onDismiss: () -> Unit,
    onParsed: (CustomDayTransfer.ParseResult) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onParsed(CustomDayTransfer.parse(text)) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.custom_days_import_next)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.custom_days_cancel))
            }
        },
        title = { Text(stringResource(R.string.custom_days_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.custom_days_import_paste_hint),
                    color = Color(0xFFAAAAAA),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    placeholder = {
                        Text(stringResource(R.string.custom_days_import_placeholder))
                    },
                    minLines = 6,
                    maxLines = 14,
                )
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )
}

@Composable
private fun ImportApplyDialog(
    parse: CustomDayTransfer.ParseResult,
    onDismiss: () -> Unit,
    onApply: (CustomDayTransfer.ApplyMode, CustomDayTransfer.TargetCategory?) -> Unit,
) {
    var mode by remember { mutableStateOf(CustomDayTransfer.ApplyMode.APPEND) }
    var category by remember { mutableStateOf(CustomDayTransfer.TargetCategory.HOLIDAYS) }
    val needsCategory = parse is CustomDayTransfer.ParseResult.ArrayOnly
    val summary = remember(parse) { summarizeParse(parse) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(mode, if (needsCategory) category else null)
                },
            ) { Text(stringResource(R.string.custom_days_import_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.custom_days_cancel))
            }
        },
        title = { Text(stringResource(R.string.custom_days_import_options)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(summary, color = Color(0xFFCCCCCC))

                Text(
                    text = stringResource(R.string.custom_days_import_mode),
                    color = Color(0xFFAAAAAA),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == CustomDayTransfer.ApplyMode.APPEND,
                        onClick = { mode = CustomDayTransfer.ApplyMode.APPEND },
                        label = { Text(stringResource(R.string.custom_days_mode_append)) },
                    )
                    FilterChip(
                        selected = mode == CustomDayTransfer.ApplyMode.REPLACE,
                        onClick = { mode = CustomDayTransfer.ApplyMode.REPLACE },
                        label = { Text(stringResource(R.string.custom_days_mode_replace)) },
                    )
                }
                Text(
                    text = stringResource(R.string.custom_days_mode_replace_hint),
                    color = Color(0xFF888888),
                )

                if (needsCategory) {
                    Text(
                        text = stringResource(R.string.custom_days_import_category),
                        color = Color(0xFFAAAAAA),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = category == CustomDayTransfer.TargetCategory.HOLIDAYS,
                            onClick = {
                                category = CustomDayTransfer.TargetCategory.HOLIDAYS
                            },
                            label = { Text(stringResource(R.string.custom_holidays)) },
                        )
                        FilterChip(
                            selected = category == CustomDayTransfer.TargetCategory.WEEKDAYS,
                            onClick = {
                                category = CustomDayTransfer.TargetCategory.WEEKDAYS
                            },
                            label = { Text(stringResource(R.string.custom_weekdays)) },
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )
}

private fun summarizeParse(parse: CustomDayTransfer.ParseResult): String = when (parse) {
    is CustomDayTransfer.ParseResult.Package -> {
        val h = parse.holidays
        val w = parse.weekdays
        buildString {
            if (h != null) append("休日 ${h.rules.size}件")
            if (h != null && w != null) append(" / ")
            if (w != null) append("平日 ${w.rules.size}件")
            val skipped = (h?.skipped ?: 0) + (w?.skipped ?: 0)
            if (skipped > 0) append("（不正スキップ $skipped 件）")
        }
    }
    is CustomDayTransfer.ParseResult.ArrayOnly -> {
        val b = parse.batch
        buildString {
            append("規則配列 ${b.rules.size}件")
            if (b.skipped > 0) append("（不正スキップ ${b.skipped} 件）")
            append("\n適用先カテゴリを選択してください")
        }
    }
    is CustomDayTransfer.ParseResult.Failure -> parse.message
}

private suspend fun applyImport(
    repository: SettingsRepository,
    parse: CustomDayTransfer.ParseResult,
    mode: CustomDayTransfer.ApplyMode,
    category: CustomDayTransfer.TargetCategory?,
): CustomDayTransfer.ApplyReport {
    var holidaysAdded = 0
    var holidaysReplaced = 0
    var weekdaysAdded = 0
    var weekdaysReplaced = 0
    var skipped = 0
    var duplicates = 0

    val current = if (mode == CustomDayTransfer.ApplyMode.APPEND) {
        repository.getSettingsOnce()
    } else {
        null
    }

    when (parse) {
        is CustomDayTransfer.ParseResult.Package -> {
            parse.holidays?.let { batch ->
                skipped += batch.skipped
                when (mode) {
                    CustomDayTransfer.ApplyMode.APPEND -> {
                        val filtered = CustomDayTransfer.filterAppendDuplicates(
                            current!!.customHolidays,
                            batch.rules,
                        )
                        if (filtered.toAdd.isNotEmpty()) {
                            repository.appendCustomHolidays(filtered.toAdd)
                        }
                        holidaysAdded = filtered.toAdd.size
                        duplicates += filtered.duplicates
                    }
                    CustomDayTransfer.ApplyMode.REPLACE -> {
                        repository.replaceCustomHolidays(batch.rules)
                        holidaysReplaced = batch.rules.size
                    }
                }
            }
            parse.weekdays?.let { batch ->
                skipped += batch.skipped
                when (mode) {
                    CustomDayTransfer.ApplyMode.APPEND -> {
                        val filtered = CustomDayTransfer.filterAppendDuplicates(
                            current!!.customWeekdays,
                            batch.rules,
                        )
                        if (filtered.toAdd.isNotEmpty()) {
                            repository.appendCustomWeekdays(filtered.toAdd)
                        }
                        weekdaysAdded = filtered.toAdd.size
                        duplicates += filtered.duplicates
                    }
                    CustomDayTransfer.ApplyMode.REPLACE -> {
                        repository.replaceCustomWeekdays(batch.rules)
                        weekdaysReplaced = batch.rules.size
                    }
                }
            }
        }
        is CustomDayTransfer.ParseResult.ArrayOnly -> {
            val batch = parse.batch
            skipped += batch.skipped
            val target = category ?: CustomDayTransfer.TargetCategory.HOLIDAYS
            when (target) {
                CustomDayTransfer.TargetCategory.HOLIDAYS -> {
                    when (mode) {
                        CustomDayTransfer.ApplyMode.APPEND -> {
                            val filtered = CustomDayTransfer.filterAppendDuplicates(
                                current!!.customHolidays,
                                batch.rules,
                            )
                            if (filtered.toAdd.isNotEmpty()) {
                                repository.appendCustomHolidays(filtered.toAdd)
                            }
                            holidaysAdded = filtered.toAdd.size
                            duplicates += filtered.duplicates
                        }
                        CustomDayTransfer.ApplyMode.REPLACE -> {
                            repository.replaceCustomHolidays(batch.rules)
                            holidaysReplaced = batch.rules.size
                        }
                    }
                }
                CustomDayTransfer.TargetCategory.WEEKDAYS -> {
                    when (mode) {
                        CustomDayTransfer.ApplyMode.APPEND -> {
                            val filtered = CustomDayTransfer.filterAppendDuplicates(
                                current!!.customWeekdays,
                                batch.rules,
                            )
                            if (filtered.toAdd.isNotEmpty()) {
                                repository.appendCustomWeekdays(filtered.toAdd)
                            }
                            weekdaysAdded = filtered.toAdd.size
                            duplicates += filtered.duplicates
                        }
                        CustomDayTransfer.ApplyMode.REPLACE -> {
                            repository.replaceCustomWeekdays(batch.rules)
                            weekdaysReplaced = batch.rules.size
                        }
                    }
                }
            }
        }
        is CustomDayTransfer.ParseResult.Failure -> Unit
    }

    return CustomDayTransfer.ApplyReport(
        holidaysAdded = holidaysAdded,
        holidaysReplaced = holidaysReplaced,
        weekdaysAdded = weekdaysAdded,
        weekdaysReplaced = weekdaysReplaced,
        skipped = skipped,
        duplicates = duplicates,
    )
}
