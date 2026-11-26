package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.BillReceiptSheet
import com.example.ui.screens.BillingScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DailyRegisterScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

object MasoorRoutes {
    const val ROUTE_BILLING = "billing"
    const val ROUTE_DAILY_REGISTER = "daily_register"
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_CUSTOMERS = "customers"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_SETTINGS = "settings"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val bottomDestinations = listOf(
    NavDestination(
        route = MasoorRoutes.ROUTE_BILLING,
        label = "बिलिंग",
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
        testTag = "nav_billing"
    ),
    NavDestination(
        route = MasoorRoutes.ROUTE_DASHBOARD,
        label = "डॅशबोर्ड",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        testTag = "nav_dashboard"
    ),
    NavDestination(
        route = MasoorRoutes.ROUTE_CUSTOMERS,
        label = "ग्राहक",
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People,
        testTag = "nav_customers"
    ),
    NavDestination(
        route = MasoorRoutes.ROUTE_HISTORY,
        label = "इतिहास",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    ),
    NavDestination(
        route = MasoorRoutes.ROUTE_SETTINGS,
        label = "सेटिंग्ज",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

private val railDestinations = listOf(
    bottomDestinations[0],
    NavDestination(
        route = MasoorRoutes.ROUTE_DAILY_REGISTER,
        label = "फोटो रजिस्टर",
        selectedIcon = Icons.Filled.DocumentScanner,
        unselectedIcon = Icons.Outlined.DocumentScanner,
        testTag = "nav_daily_register"
    ),
    bottomDestinations[1],
    bottomDestinations[2],
    bottomDestinations[3],
    bottomDestinations[4]
)

@Composable
fun MasoorBillingApp(
    viewModel: MasoorBillingViewModel,
    dailyRegisterViewModel: DailyRegisterViewModel? = null
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MasoorRoutes.ROUTE_BILLING

    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val calculation by viewModel.currentCalculation.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val monthOffset by viewModel.selectedMonthOffset.collectAsStateWithLifecycle()
    val previewBill by viewModel.previewBill.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val registerRecords = dailyRegisterViewModel?.records?.collectAsStateWithLifecycle()?.value.orEmpty()
    val registerTotalIncome = remember(registerRecords) { registerRecords.sumOf { it.totalIncome } }

    var openPendingInHistory by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (dailyRegisterViewModel != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val isRegisterMode = currentRoute == MasoorRoutes.ROUTE_DAILY_REGISTER
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 520.dp)
                            ) {
                                SegmentedButton(
                                    selected = !isRegisterMode,
                                    onClick = {
                                        if (isRegisterMode) navigateToTab(MasoorRoutes.ROUTE_BILLING)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    icon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ReceiptLong,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("mode_pos_billing")
                                ) {
                                    Text("थेट बिलिंग (POS)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                SegmentedButton(
                                    selected = isRegisterMode,
                                    onClick = { navigateToTab(MasoorRoutes.ROUTE_DAILY_REGISTER) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    icon = {
                                        Icon(
                                            Icons.Filled.DocumentScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("mode_photo_register")
                                ) {
                                    Text("फोटो रजिस्टर (OCR)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        bottomDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (dest.route != MasoorRoutes.ROUTE_HISTORY) {
                                        openPendingInHistory = false
                                    }
                                    navigateToTab(dest.route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    val railItems = if (dailyRegisterViewModel != null) railDestinations else bottomDestinations
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        railItems.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (dest.route != MasoorRoutes.ROUTE_HISTORY) {
                                        openPendingInHistory = false
                                    }
                                    navigateToTab(dest.route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = MasoorRoutes.ROUTE_BILLING,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                    ) {
                        composable(MasoorRoutes.ROUTE_BILLING) {
                            BillingScreen(
                                formState = formState,
                                calculation = calculation,
                                settings = settings,
                                customers = customers,
                                onNameChange = viewModel::onCustomerNameChanged,
                                onMobileChange = viewModel::onCustomerMobileChanged,
                                onSelectCustomer = viewModel::selectCustomer,
                                onWalkInClick = viewModel::fillWalkInCustomer,
                                onAdjustFull = viewModel::adjustFullMasoor,
                                onAdjustHalf = viewModel::adjustHalfMasoor,
                                onAdjustRoti = viewModel::adjustRoti,
                                onCashChange = viewModel::onCashInputChanged,
                                onOnlineChange = viewModel::onOnlineInputChanged,
                                onOnlineConfirmedChange = viewModel::onOnlineConfirmedChanged,
                                onFillFullCash = viewModel::fillFullCash,
                                onFillFullOnline = viewModel::fillFullOnline,
                                onResetForm = viewModel::resetCurrentBillForm,
                                onSaveBill = viewModel::saveCurrentBill,
                                onOpenPhotoRegister = if (dailyRegisterViewModel != null) {
                                    { navigateToTab(MasoorRoutes.ROUTE_DAILY_REGISTER) }
                                } else null
                            )
                        }

                        composable(MasoorRoutes.ROUTE_DAILY_REGISTER) {
                            if (dailyRegisterViewModel != null) {
                                DailyRegisterScreen(
                                    viewModel = dailyRegisterViewModel,
                                    onShowAppSnackbar = { msg ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    },
                                    onOpenSettingsTab = {
                                        navigateToTab(MasoorRoutes.ROUTE_SETTINGS)
                                    }
                                )
                            }
                        }

                        composable(MasoorRoutes.ROUTE_DASHBOARD) {
                            DashboardScreen(
                                stats = dashboardStats,
                                monthOffset = monthOffset,
                                onShiftMonth = viewModel::shiftMonth,
                                onOpenPendingBills = {
                                    openPendingInHistory = true
                                    navigateToTab(MasoorRoutes.ROUTE_HISTORY)
                                },
                                registerTotalIncome = registerTotalIncome,
                                registerRecordsCount = registerRecords.size,
                                onOpenPhotoRegister = if (dailyRegisterViewModel != null) {
                                    { navigateToTab(MasoorRoutes.ROUTE_DAILY_REGISTER) }
                                } else null
                            )
                        }

                        composable(MasoorRoutes.ROUTE_CUSTOMERS) {
                            CustomersScreen(
                                customers = customers,
                                onAddCustomer = viewModel::saveCustomer,
                                onOpenBill = viewModel::openBillPreview
                            )
                        }

                        composable(MasoorRoutes.ROUTE_HISTORY) {
                            HistoryScreen(
                                bills = bills,
                                settings = settings,
                                initialOnlyPending = openPendingInHistory,
                                onClearInitialPending = { openPendingInHistory = false },
                                onOpenBill = viewModel::openBillPreview,
                                onMessage = viewModel::showMessage
                            )
                        }

                        composable(MasoorRoutes.ROUTE_SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                cloudSyncState = cloudSyncState,
                                onSaveSettings = viewModel::saveSettings,
                                onResetDefaultPrices = viewModel::resetDefaultPrices,
                                onBackupToCloud = viewModel::backupToCloud,
                                onRestoreFromCloud = viewModel::restoreFromCloud,
                                onClearCloudStatus = viewModel::resetCloudSyncState
                            )
                        }
                    }
                }
            }
        }
    }

    val activePreview = previewBill
    if (activePreview != null) {
        BillReceiptSheet(
            bill = activePreview,
            settings = settings,
            onDismiss = viewModel::closeBillPreview,
            onSettlePayment = viewModel::settlePendingPayment,
            onMessage = viewModel::showMessage
        )
    }
}
