package de.ca.qcc.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.ca.qcc.R
import de.ca.qcc.domain.model.ComparisonStatus
import de.ca.qcc.feature.scan.ScanUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    scansToday: Int,
    mismatchesToday: Int,
    scanState: ScanUiState,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onFindDuplicates: () -> Unit,
    exportMessage: String?,
    onOpenDrawer: () -> Unit
) {
    val blackButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Black,
        contentColor = Color.White
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QCC", color = Color.Black, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatCard(
                            title = stringResource(R.string.scans_today),
                            value = scansToday.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = stringResource(R.string.mismatches_today),
                            value = mismatchesToday.toString(),
                            modifier = Modifier.weight(1f),
                            isError = mismatchesToday > 0
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ResultRow(
                                label = stringResource(R.string.qr_raw),
                                value = scanState.qr?.rawValue ?: "-"
                            )
                            Spacer(Modifier.height(8.dp))
                            ResultRow(
                                label = stringResource(R.string.qr_gtin),
                                value = scanState.qr?.sgtin ?: "-"
                            )
                            Spacer(Modifier.height(8.dp))
                            ResultRow(
                                label = stringResource(R.string.rfid_gtin),
                                value = scanState.rfid?.sgtin ?: "-"
                            )

                            if (scanState.comparison.status != ComparisonStatus.IDLE) {
                                Spacer(Modifier.height(16.dp))
                                val (color, icon, text) = when (scanState.comparison.status) {
                                    ComparisonStatus.MATCH -> Triple(
                                        Color(0xFF4CAF50),
                                        Icons.Default.CheckCircle,
                                        stringResource(R.string.match)
                                    )
                                    ComparisonStatus.MISMATCH -> Triple(
                                        Color.Red,
                                        Icons.Default.Cancel,
                                        stringResource(R.string.mismatch)
                                    )
                                    else -> Triple(Color.Gray, Icons.Default.Info, scanState.comparison.message)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, contentDescription = null, tint = color)
                                    Spacer(Modifier.width(8.dp))
                                    Text(text, color = color, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = blackButtonColors
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }

                item {
                    Button(
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = blackButtonColors
                    ) {
                        Text(stringResource(R.string.export_mismatches_csv))
                    }
                }

                item {
                    Button(
                        onClick = onFindDuplicates,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = blackButtonColors
                    ) {
                        Text(stringResource(R.string.find_duplicates))
                    }
                }

                exportMessage?.let {
                    item {
                        Text(
                            text = it,
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                scanState.error?.let {
                    item {
                        Text(
                            text = it,
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Black)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.Black)
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Black)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isError) Color.Red else Color.Black
            )
        }
    }
}
