package de.ca.qcc.feature.duplicates

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ButtonColors
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
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

    // Warnings and errors block the screen until confirmed; plain success feedback is a
    // snackbar so it never shifts the layout
    state.message?.takeIf { it.requiresAcknowledgement }?.let { MessageDialog(it, onMessageDismissed) }
    val snackbarHostState = remember { SnackbarHostState() }
    val plainMessage = state.message?.takeIf { !it.requiresAcknowledgement }
    val plainText = plainMessage?.text()
    LaunchedEffect(plainMessage) {
        if (plainText != null) snackbarHostState.showSnackbar(plainText)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) {
                Snackbar(it, containerColor = Color.Black, contentColor = Color.White)
            }
        },
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
        if (state.phase == DuplicatePhase.TARGET) {
            TargetPhase(
                state, onInputChange, onSgtinFilterChange, onApplyInput, onStoreChange, onStartSearch,
                blackButtonColors, Modifier.padding(padding)
            )
        } else {
            SearchPhase(
                state, onContinuousChange, onConfirmFind, onEndSearch, blackButtonColors, Modifier.padding(padding)
            )
        }
    }
}

// The keyboard can take most of the screen here, so this phase may scroll
@Composable
private fun TargetPhase(
    state: DuplicateUiState,
    onInputChange: (String) -> Unit,
    onSgtinFilterChange: (Int) -> Unit,
    onApplyInput: () -> Unit,
    onStoreChange: (String) -> Unit,
    onStartSearch: () -> Unit,
    blackButtonColors: ButtonColors,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                enabled = state.input.isNotBlank() && state.readerConnected && !state.busy,
                colors = blackButtonColors,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.dup_start_search)) }
        }
    }
}

// Everything must be visible at once: no scrolling while the user works with the trigger.
// The cards keep their look; the gauge takes whatever height is left.
@Composable
private fun SearchPhase(
    state: DuplicateUiState,
    onContinuousChange: (Boolean) -> Unit,
    onConfirmFind: () -> Unit,
    onEndSearch: () -> Unit,
    blackButtonColors: ButtonColors,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.target?.let { target ->
            WhiteCard(padding = COMPACT_CARD_PADDING) {
                LabeledValue(stringResource(R.string.dup_target_sgtin), target.sgtin, compact = true)
                LabeledValue(stringResource(R.string.dup_epc), target.epc, monospace = true, compact = true)
                LabeledValue(stringResource(R.string.dup_tid), state.lastTid ?: "-", monospace = true, compact = true)
                if (state.store.isNotBlank()) {
                    LabeledValue(stringResource(R.string.dup_store), state.store, compact = true)
                }
            }
        }
        WhiteCard(modifier = Modifier.weight(1f), padding = COMPACT_CARD_PADDING, fillHeight = true) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ProximityGauge(
                    proximity = state.proximity,
                    active = state.locating,
                    modifier = Modifier.testTag("gauge")
                )
            }
            Text(
                stringResource(
                    if (state.continuous) R.string.dup_hint_continuous else R.string.dup_hint_hold_trigger
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF555555),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        WhiteCard(padding = COMPACT_CARD_PADDING) {
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
        WhiteCard(padding = COMPACT_CARD_PADDING) {
            Text(stringResource(R.string.dup_found_count), style = MaterialTheme.typography.labelSmall, color = Color.Black)
            Text("${state.foundCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Button(
            onClick = onConfirmFind,
            enabled = !state.busy,
            colors = blackButtonColors,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.dup_confirm_find)) }
        Button(
            onClick = onEndSearch,
            enabled = !state.busy,
            colors = blackButtonColors,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.dup_end_search)) }
    }
}

private val COMPACT_CARD_PADDING = 8.dp

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
private fun WhiteCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(padding).then(if (fillHeight) Modifier.fillMaxSize() else Modifier),
            content = content
        )
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
private fun LabeledValue(label: String, value: String, monospace: Boolean = false, compact: Boolean = false) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Black)
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        color = Color.Black
    )
    Spacer(Modifier.height(if (compact) 0.dp else 6.dp))
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
