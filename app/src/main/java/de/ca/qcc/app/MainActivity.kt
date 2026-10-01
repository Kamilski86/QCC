package de.ca.qcc.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.ca.qcc.R
import de.ca.qcc.device.honeywell.HoneywellCt37ScannerGateway
import de.ca.qcc.feature.about.AboutScreen
import de.ca.qcc.feature.dashboard.DashboardScreen
import de.ca.qcc.feature.dashboard.DashboardViewModel
import de.ca.qcc.feature.duplicates.DuplicateSearchScreen
import de.ca.qcc.feature.duplicates.DuplicateSearchViewModel
import de.ca.qcc.feature.export.ExportViewModel
import de.ca.qcc.feature.language.LanguageScreen
import de.ca.qcc.feature.reader.ReaderConfigScreen
import de.ca.qcc.feature.scan.ScanViewModel
import de.ca.qcc.navigation.Screen
import de.ca.qcc.ui.theme.QccTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // Injected for lifecycle management (claim/release/close)
    @Inject lateinit var honeywellScanner: HoneywellCt37ScannerGateway

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
            val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] == true

            if (connectGranted && scanGranted) {
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth permissions are required for Zebra reader", Toast.LENGTH_LONG).show()
            }
        }

    private fun ensureBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val connectGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        val scanGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        if (!connectGranted || !scanGranted) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        ensureBluetoothPermissions()

        setContent {
            QccTheme { AppNav() }
        }
    }

    override fun onResume()  { super.onResume();  honeywellScanner.claim() }
    override fun onPause()   { super.onPause();   honeywellScanner.release() }
    override fun onDestroy() { super.onDestroy(); honeywellScanner.close() }
}

@Composable
private fun AppNav() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val exportViewModel: ExportViewModel = hiltViewModel()
    val scanViewModel: ScanViewModel = hiltViewModel()

    // The duplicate finder owns the scanners while it is on screen; QCC must not react to them
    LaunchedEffect(currentRoute) {
        scanViewModel.setSuspended(currentRoute == Screen.Duplicates.route)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute != Screen.Splash.route,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    DrawerItem(
                        label = stringResource(R.string.dashboard),
                        icon = Icons.Default.Menu,
                        selected = currentRoute == Screen.Dashboard.route,
                        onClick = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = true }
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    DrawerItem(
                        label = stringResource(R.string.find_duplicates),
                        icon = Icons.Default.Search,
                        selected = currentRoute == Screen.Duplicates.route,
                        onClick = {
                            // Never stack a second Duplicates entry (and view model)
                            navController.navigate(Screen.Duplicates.route) {
                                launchSingleTop = true
                                popUpTo(Screen.Dashboard.route)
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    DrawerItem(
                        label = stringResource(R.string.reader),
                        icon = Icons.Default.SettingsInputAntenna,
                        selected = currentRoute == Screen.Reader.route,
                        onClick = {
                            navController.navigate(Screen.Reader.route)
                            scope.launch { drawerState.close() }
                        }
                    )
                    DrawerItem(
                        label = stringResource(R.string.language),
                        icon = Icons.Default.Language,
                        selected = currentRoute == Screen.Language.route,
                        onClick = {
                            navController.navigate(Screen.Language.route)
                            scope.launch { drawerState.close() }
                        }
                    )
                    DrawerItem(
                        label = stringResource(R.string.about),
                        icon = Icons.Default.Info,
                        selected = currentRoute == Screen.About.route,
                        onClick = {
                            navController.navigate(Screen.About.route)
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Screen.Splash.route) {
            composable(Screen.Splash.route) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }
            }

            composable(Screen.Dashboard.route) {
                val state by dashboardViewModel.uiState.collectAsState()
                val scanState by scanViewModel.uiState.collectAsState()
                val exportState by exportViewModel.uiState.collectAsState()

                DashboardScreen(
                    scansToday = state.scansToday,
                    mismatchesToday = state.mismatchesToday,
                    scanState = scanState,
                    onQrScan = { scanViewModel.triggerQrScan() },
                    onManualQrSubmit = { scanViewModel.setManualQrGtin(it) },
                    onRfidScan = { scanViewModel.triggerRfidScan() },
                    onReset = { scanViewModel.reset() },
                    onExport = { exportViewModel.exportCsv() },
                    exportMessage = exportState.message,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }

            composable(Screen.Duplicates.route) {
                // Scoped to this destination, unlike the activity-wide QCC view models
                val duplicateViewModel: DuplicateSearchViewModel = hiltViewModel()
                val state by duplicateViewModel.uiState.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current

                // Stop locating and hand the trigger back to QCC when leaving or backgrounding.
                // The destination stays on the back stack when the drawer navigates away, so
                // leaving composition (onDispose) is what stops it; entering starts it again.
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> duplicateViewModel.onScreenStarted()
                            Lifecycle.Event.ON_STOP -> duplicateViewModel.onScreenStopped()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        duplicateViewModel.onScreenStarted()
                    }
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        duplicateViewModel.onScreenStopped()
                    }
                }

                // The app hides the keyboard globally (ADJUST_NOTHING). This screen has text
                // fields, so let the window resize here so focused fields stay visible;
                // restore the activity's mode when leaving.
                val window = (LocalContext.current as? Activity)?.window
                DisposableEffect(window) {
                    window?.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                    )
                    onDispose {
                        window?.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        )
                    }
                }

                DuplicateSearchScreen(
                    state = state,
                    onInputChange = duplicateViewModel::onInputChange,
                    onSgtinFilterChange = duplicateViewModel::onSgtinFilterChange,
                    onApplyInput = duplicateViewModel::applyInput,
                    onCapture = duplicateViewModel::captureFromReader,
                    onStoreChange = duplicateViewModel::onStoreChange,
                    onStartSearch = duplicateViewModel::startSearch,
                    onContinuousChange = duplicateViewModel::setContinuous,
                    onConfirmFind = duplicateViewModel::confirmFind,
                    onEndSearch = duplicateViewModel::endSearch,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }

            composable(Screen.Reader.route) {
                val scanState by scanViewModel.uiState.collectAsState()
                ReaderConfigScreen(
                    powerLevel = scanState.readerPower,
                    onPowerLevelChange = { scanViewModel.setReaderPower(it) },
                    onConnect = { scanViewModel.reconnectReader() },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    errorMessage = scanState.error,
                    readerStatusText = scanState.readerStatus.message
                )
            }

            composable(Screen.Language.route) {
                LanguageScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            }

            composable(Screen.About.route) {
                AboutScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            }
        }
    }
}

@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, color = Color.Black) },
        icon = { Icon(icon, contentDescription = null, tint = Color.Black) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
