package com.kmmm_engineering.alwayscalendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kmmm_engineering.alwayscalendar.data.CustomDayKind
import com.kmmm_engineering.alwayscalendar.data.CustomDayRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * List + add/delete UI for custom holidays or custom weekdays.
 */
@Composable
fun CustomDayRuleSection(
    title: String,
    rules: List<CustomDayRule>,
    onAdd: (CustomDayRule) -> Unit,
    onRemove: (String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = Color(0xFFCCCCCC))
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "追加", tint = Color.White)
            }
        }
        if (rules.isEmpty()) {
            Text("（なし）", color = Color(0xFF666666))
        } else {
            rules.forEach { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rule.displayLabel(),
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(rule.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color(0xFFAAAAAA))
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddCustomDayDialog(
            onDismiss = { showAdd = false },
            onConfirm = { rule ->
                onAdd(rule)
                showAdd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomDayDialog(
    onDismiss: () -> Unit,
    onConfirm: (CustomDayRule) -> Unit,
) {
    val today = LocalDate.now()
    var kind by remember { mutableStateOf(CustomDayKind.ONCE) }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.monthValue) }
    var day by remember { mutableIntStateOf(today.dayOfMonth) }
    var nth by remember { mutableIntStateOf(1) }
    var dow by remember { mutableIntStateOf(today.dayOfWeek.value) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val rule = CustomDayRule(
                        kind = kind,
                        year = year,
                        month = month,
                        day = day,
                        nth = nth,
                        dayOfWeek = dow,
                    )
                    onConfirm(rule)
                },
            ) { Text("追加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
        title = { Text("ルールを追加") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("種別", color = Color(0xFFAAAAAA))
                KindDropdown(kind) { kind = it }

                when (kind) {
                    CustomDayKind.ONCE -> {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${year}年${month}月${day}日")
                        }
                    }
                    CustomDayKind.YEARLY_MD -> {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("基準日: ${month}月${day}日")
                        }
                    }
                    CustomDayKind.YEARLY_NTH_DOW -> {
                        MonthDropdown(month) { month = it }
                        NthDropdown(nth) { nth = it }
                        DowDropdown(dow) { dow = it }
                    }
                    CustomDayKind.YEARLY_MONTH_END -> {
                        MonthDropdown(month) { month = it }
                    }
                    CustomDayKind.MONTHLY_D -> {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${day}日")
                        }
                    }
                    CustomDayKind.MONTHLY_NTH_DOW -> {
                        NthDropdown(nth) { nth = it }
                        DowDropdown(dow) { dow = it }
                    }
                    CustomDayKind.MONTHLY_END -> {
                        Text("毎月末", color = Color.White)
                    }
                    CustomDayKind.WEEKLY_DOW -> {
                        DowDropdown(dow) { dow = it }
                    }
                }

                Text(
                    text = "プレビュー: " + CustomDayRule(
                        kind = kind, year = year, month = month, day = day, nth = nth, dayOfWeek = dow,
                    ).displayLabel(),
                    color = Color(0xFF888888),
                )
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val initialMillis = LocalDate.of(year, month, day.coerceIn(1, 28))
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = state.selectedDateMillis
                        if (millis != null) {
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            year = picked.year
                            month = picked.monthValue
                            day = picked.dayOfMonth
                            dow = picked.dayOfWeek.value
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindDropdown(selected: CustomDayKind, onSelect: (CustomDayKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = CustomDayRule.kindLabel(selected),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CustomDayKind.entries.forEach { k ->
                DropdownMenuItem(
                    text = { Text(CustomDayRule.kindLabel(k)) },
                    onClick = {
                        onSelect(k)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthDropdown(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text("月", color = Color(0xFFAAAAAA))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = "${selected}月",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..12).forEach { m ->
                DropdownMenuItem(
                    text = { Text("${m}月") },
                    onClick = {
                        onSelect(m)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NthDropdown(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text("第N", color = Color(0xFFAAAAAA))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = "第${selected}",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..5).forEach { n ->
                DropdownMenuItem(
                    text = { Text("第${n}") },
                    onClick = {
                        onSelect(n)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DowDropdown(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Display Sun-first for JP UX but store ISO values
    val order = listOf(7, 1, 2, 3, 4, 5, 6) // 日〜土
    Text("曜日", color = Color(0xFFAAAAAA))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = CustomDayRule.dayOfWeekLabel(selected) + "曜日",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            order.forEach { iso ->
                DropdownMenuItem(
                    text = { Text(CustomDayRule.dayOfWeekLabel(iso) + "曜日") },
                    onClick = {
                        onSelect(iso)
                        expanded = false
                    },
                )
            }
        }
    }
}
