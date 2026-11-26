package com.karelisio.patoune.ui.components

import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
import com.karelisio.patoune.domain.Backdate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karelisio.patoune.domain.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, minLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = singleLine && minLines == 1, minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun NumberField(value: Double?, onChange: (Double?) -> Unit, label: String, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    Field(text, { t -> text = t; onChange(t.replace(',', '.').toDoubleOrNull()) }, label, modifier, keyboard = KeyboardType.Decimal)
}

/** Champ date (ISO yyyy-MM-dd) avec sélecteur Material 3. */
@Composable
fun DateField(value: String?, onChange: (String?) -> Unit, label: String, modifier: Modifier = Modifier, clearable: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = Dates.parse(value)?.format(Dates.day) ?: "",
        onValueChange = {}, readOnly = true, label = { Text(label) },
        trailingIcon = {
            if (clearable && !value.isNullOrBlank()) IconButton(onClick = { onChange(null) }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_clear)) }
            else IconButton(onClick = { open = true }) { Icon(Icons.Rounded.CalendarMonth, stringResource(R.string.action_pick_date)) }
        },
        modifier = modifier.fillMaxWidth(),
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { src ->
            androidx.compose.runtime.LaunchedEffect(src) {
                src.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Release) open = true }
            }
        },
    )
    if (open) {
        val initial = Dates.parse(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() ?: Instant.now().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

/**
 * Date + heure d'une entrée (fuseau local du téléphone), choisies séparément avec les sélecteurs Material 3.
 * Sans [allowFuture], les jours futurs ne sont pas proposés et une heure future est signalée en erreur
 * (l'écran appelant bloque l'enregistrement avec [Backdate.isFuture]).
 */
@Composable
fun DateTimeField(valueMs: Long, onChange: (Long) -> Unit, label: String, modifier: Modifier = Modifier, allowFuture: Boolean = false) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val zone = Dates.zone()
    val z = Instant.ofEpochMilli(valueMs).atZone(zone)
    val future = !allowFuture && Backdate.isFuture(valueMs, System.currentTimeMillis())
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerField(z.toLocalDate().format(Dates.day), label, Icons.Rounded.CalendarMonth, stringResource(R.string.action_pick_date), future, Modifier.weight(1.4f)) { pickDate = true }
            PickerField(z.toLocalTime().format(Dates.time), stringResource(R.string.field_time), Icons.Rounded.Schedule, stringResource(R.string.action_pick_time), future, Modifier.weight(1f)) { pickTime = true }
        }
        if (future) Text(stringResource(R.string.history_future_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp))
    }
    if (pickDate) {
        val todayUtc = Dates.today().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = z.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = allowFuture || utcTimeMillis <= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Backdate.withDate(valueMs, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(), zone)) }
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
    if (pickTime) TimeDialog(z.toLocalTime(), { pickTime = false }) { t -> onChange(Backdate.withTime(valueMs, t, zone)); pickTime = false }
}

/** Champ en lecture seule qui ouvre un sélecteur (tap sur le champ ou sur l'icône). */
@Composable
private fun PickerField(text: String, label: String, icon: ImageVector, iconDescription: String, isError: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val open by androidx.compose.runtime.rememberUpdatedState(onOpen)
    LaunchedEffect(source) { source.interactions.collect { if (it is PressInteraction.Release) open() } }
    OutlinedTextField(
        value = text, onValueChange = {}, readOnly = true, singleLine = true, isError = isError, label = { Text(label) },
        trailingIcon = { IconButton(onClick = onOpen) { Icon(icon, iconDescription) } },
        interactionSource = source, modifier = modifier,
    )
}

@Composable
fun TimeDialog(initial: LocalTime = LocalTime.of(8, 0), onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state) },
    )
}

/** Liste d'horaires "HH:mm" éditable sous forme de puces. */
@Composable
fun TimesEditor(csv: String, onChange: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val times = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.sorted()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        times.forEach { t ->
            InputChip(selected = true, onClick = { onChange((times - t).joinToString(",")) }, label = { Text(t) },
                trailingIcon = { Icon(Icons.Rounded.Close, stringResource(R.string.action_remove_time, t)) })
        }
        AssistChip(onClick = { adding = true }, label = { Text(stringResource(R.string.action_add_time)) }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
    }
    if (adding) TimeDialog(onDismiss = { adding = false }) { t ->
        adding = false
        onChange((times + "%02d:%02d".format(t.hour, t.minute)).distinct().sorted().joinToString(","))
    }
}

@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
fun MultiChips(options: List<String>, selectedCsv: String, label: @Composable (String) -> String = { it }, onChange: (String) -> Unit) {
    val sel = selectedCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o in sel, onClick = { onChange((if (o in sel) sel - o else sel + o).joinToString(",")) }, label = { Text(label(o)) })

        }
    }
}

fun LocalDate.iso(): String = toString()
