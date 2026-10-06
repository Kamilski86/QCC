package de.ca.qcc.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// TEMPORARY screen (English literals on purpose): remove together with Ih45Probe after phase 0
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Ih45DiagnosticScreen(
    onOpenDrawer: () -> Unit,
    viewModel: Ih45DiagnosticViewModel = hiltViewModel()
) {
    val log by viewModel.log.collectAsState()
    val bonded by viewModel.bonded.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IH45 diagnostics", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = viewModel::usbDevices) { Text("Android USB list") }
                Button(onClick = viewModel::requestUsbPermission) { Text("USB permission") }
                Button(onClick = viewModel::scanBluetooth) { Text("Scan BT") }
                Button(onClick = viewModel::init) { Text("1 Init") }
                Button(onClick = viewModel::attachedUsb) { Text("USB?") }
                Button(onClick = viewModel::connectUsb) { Text("Conn USB") }
                Button(onClick = viewModel::createReader) { Text("2 createReader") }
                OutlinedButton(onClick = viewModel::state) { Text("State") }
                OutlinedButton(onClick = viewModel::info) { Text("Info") }
                OutlinedButton(onClick = viewModel::triggerRfid) { Text("Trig RFID") }
                OutlinedButton(onClick = viewModel::triggerBarcode) { Text("Trig BARCODE") }
                OutlinedButton(onClick = { viewModel.startRead(false) }) { Text("Read EPC") }
                OutlinedButton(onClick = { viewModel.startRead(true) }) { Text("Read +TID") }
                OutlinedButton(onClick = viewModel::stopRead) { Text("Stop") }
                listOf(0, 100, 500, 1000, 2000, 3000).forEach { raw ->
                    OutlinedButton(onClick = { viewModel.setPower(raw) }) { Text("pwr $raw") }
                }
                OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
                OutlinedButton(onClick = viewModel::refreshBonded) { Text("List BT") }
                OutlinedButton(onClick = viewModel::clear) { Text("Clear") }
            }
            if (bonded.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bonded.forEach { d ->
                        Button(onClick = { viewModel.connectBluetooth(d.address) }) { Text("BT: ${d.name}") }
                    }
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                items(log) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                }
            }
        }
    }
}
