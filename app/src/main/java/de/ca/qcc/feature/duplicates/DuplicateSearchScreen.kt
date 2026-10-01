package de.ca.qcc.feature.duplicates

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.ca.qcc.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateSearchScreen(
    state: DuplicateUiState,
    onInputChange: (String) -> Unit,
    onSgtinFilterChange: (Int) -> Unit,
    onApplyInput: () -> Unit,
    onCapture: () -> Unit,
    onStoreChange: (String) -> Unit,
    onStartSearch: () -> Unit,
    onContinuousChange: (Boolean) -> Unit,
    onConfirmFind: () -> Unit,
    onEndSearch: () -> Unit,
    onMessageDismissed: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val blackButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Black,
        contentColor = Color.White
    )

    state.message?.takeIf { it.requiresAcknowledgement }?.let { MessageDialog(it, onMessageDismissed) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.find_duplicates),
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.message?.takeIf { !it.requiresAcknowledgement }
                ?.let { message -> item(key = "message") { MessageCard(message) } }

            if (state.phase == DuplicatePhase.TARGET) {
                item(key = "input") {
                    WhiteCard {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = onInputChange,
                            label = { Text(stringResource(R.string.dup_target_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onApplyInput() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Filter value only matters for a typed SGTIN (gcp.itemRef.serial)
                        if (state.input.contains('.')) {
                            Spacer(Modifier.height(8.dp))
                            SgtinFilterSelector(
                                selected = state.sgtinFilter,
                                enabled = !state.busy,
                                onSelect = onSgtinFilterChange
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onApplyInput,
                            enabled = state.input.isNotBlank(),
                            colors = blackButtonColors,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.apply)) }
                        Button(
                            onClick = onCapture,
                            enabled = state.readerConnected && !state.busy,
                            colors = blackButtonColors,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.dup_read_with_reader)) }
                        Text(
                            stringResource(R.string.dup_hint_capture),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black
                        )
                    }
                }
                state.target?.let { target ->
                    item(key = "target") {
                        WhiteCard {
                            LabeledValue(stringResource(R.string.dup_target_sgtin), target.sgtin)
                            LabeledValue(stringResource(R.string.dup_epc), target.epc, monospace = true)
                        }
                    }
                }
                item(key = "store") {
                    WhiteCard {
                        OutlinedTextField(
                            value = state.store,
                            onValueChange = onStoreChange,
                            label = { Text(stringResource(R.string.dup_store_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item(key = "start") {
                    Button(
                        onClick = onStartSearch,
                        enabled = state.target != null && state.readerConnected && !state.busy,
                        colors = blackButtonColors,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.dup_start_search)) }
                }
            } else {
                val target = state.target
                if (target != null) {
                    item(key = "targetInfo") {
                        WhiteCard {
                            LabeledValue(stringResource(R.string.dup_target_sgtin), target.sgtin)
                            LabeledValue(stringResource(R.string.dup_epc), target.epc, monospace = true)
                            if (state.store.isNotBlank()) {
                                LabeledValue(stringResource(R.string.dup_store), state.store)
                            }
                        }
                    }
                }
                item(key = "gauge") {
                    WhiteCard {
                        ProximityGauge(proximity = state.proximity, active = state.locating)
                        Text(
                            stringResource(
                                if (state.continuous) R.string.dup_hint_continuous else R.string.dup_hint_hold_trigger
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF555555),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                item(key = "continuous") {
                    WhiteCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = state.continuous,
                                    enabled = !state.busy,
                                    role = Role.Switch,
                                    onValueChange = onContinuousChange
                                )
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.dup_continuous), color = Color.Black, fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.dup_continuous_sub),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF555555)
                                )
                            }
                            Switch(
                                checked = state.continuous,
                                onCheckedChange = null,
                                enabled = !state.busy,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color.Black
                                )
                            )
                        }
                    }
                }
                item(key = "counters") {
                    WhiteCard {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.dup_found_count), style = MaterialTheme.typography.labelSmall, color = Color.Black)
                                Text("${state.foundCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            state.lastTid?.let { tid ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(stringResource(R.string.dup_last), style = MaterialTheme.typography.labelSmall, color = Color.Black)
                                    Text(tid.shortTid(), fontFamily = FontFamily.Monospace, color = Color.Black)
                                }
                            }
                        }
                    }
                }
                item(key = "confirm") {
                    Button(
                        onClick = onConfirmFind,
                        enabled = !state.busy,
                        colors = blackButtonColors,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.dup_confirm_find)) }
                }
                item(key = "end") {
                    Button(
                        onClick = onEndSearch,
                        enabled = !state.busy,
                        colors = blackButtonColors,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.dup_end_search)) }
                }
            }
        }
    }
}

// Cannot be overlooked and blocks the screen until confirmed with OK
@Composable
private fun MessageDialog(message: DuplicateMessage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color.White,
        title = { Text(message.dialogTitle(), color = Color.Black, fontWeight = FontWeight.Bold) },
        text = { Text(message.text(), color = Color.Black) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
            ) { Text(stringResource(R.string.ok)) }
        }
    )
}

@Composable
private fun WhiteCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SgtinFilterSelector(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Text(stringResource(R.string.dup_sgtin_filter), style = MaterialTheme.typography.labelSmall, color = Color.Black)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        (0..7).forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                enabled = enabled,
                label = { Text("$value", fontWeight = FontWeight.Medium) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White,
                    labelColor = Color.Black,
                    selectedContainerColor = Color.Black,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
    Text(
        stringResource(R.string.dup_sgtin_filter_hint),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF555555)
    )
}

@Composable
private fun LabeledValue(label: String, value: String, monospace: Boolean = false) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Black)
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        color = Color.Black
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MessageCard(message: DuplicateMessage) {
    val isError = message !is DuplicateMessage.Saved &&
        message !is DuplicateMessage.Exported &&
        message !is DuplicateMessage.NothingExported
    WhiteCard {
        Text(
            message.text(),
            color = if (isError) Color.Red else Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DuplicateMessage.dialogTitle(): String = stringResource(
    if (this is DuplicateMessage.SavedAgain) R.string.dup_dialog_title_again else R.string.dup_dialog_title_already
)

private fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Composable
private fun DuplicateMessage.text(): String = when (this) {
    is DuplicateMessage.Saved -> stringResource(R.string.dup_msg_saved, findNo)
    is DuplicateMessage.SavedAgain ->
        stringResource(R.string.dup_msg_saved_again, findNo, tid, formatDateTime(previousFoundAt))
    is DuplicateMessage.AlreadyFound ->
        stringResource(R.string.dup_msg_already_found, findNo, tid, formatDateTime(foundAt))
    DuplicateMessage.NoTagAtReader -> stringResource(R.string.dup_msg_no_tag)
    DuplicateMessage.MultipleTags -> stringResource(R.string.dup_msg_multiple)
    DuplicateMessage.CaptureNoTag -> stringResource(R.string.dup_msg_capture_none)
    DuplicateMessage.CaptureMultipleTags -> stringResource(R.string.dup_msg_capture_multiple)
    DuplicateMessage.InvalidTarget -> stringResource(R.string.dup_msg_invalid_target)
    DuplicateMessage.ReaderNotConnected -> stringResource(R.string.dup_msg_reader_not_connected)
    is DuplicateMessage.ReaderError -> stringResource(R.string.dup_msg_reader_error, detail)
    is DuplicateMessage.Exported -> stringResource(R.string.dup_msg_exported, fileName)
    DuplicateMessage.NothingExported -> stringResource(R.string.dup_msg_nothing_exported)
    is DuplicateMessage.ExportFailed -> stringResource(R.string.dup_msg_export_failed, detail)
}

private fun String.shortTid(): String = if (length > 10) "${take(4)}…${takeLast(4)}" else this
