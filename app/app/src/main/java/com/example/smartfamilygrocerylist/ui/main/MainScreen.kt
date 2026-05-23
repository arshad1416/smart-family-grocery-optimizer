package com.example.smartfamilygrocerylist.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.smartfamilygrocerylist.R
import com.example.smartfamilygrocerylist.ui.screens.*
import com.example.smartfamilygrocerylist.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    listViewModel: ListViewModel = viewModel(),
    storeViewModel: StoreViewModel = viewModel(),
    optimizerViewModel: OptimizerViewModel = viewModel(),
    scraperViewModel: ScraperViewModel = viewModel()
) {
    val showOnboarding by settingsViewModel.showOnboarding.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    // Coordinate the encryptionKey from settingsViewModel to listViewModel
    val encryptionKey by settingsViewModel.encryptionKey.collectAsState()
    LaunchedEffect(encryptionKey) {
        listViewModel.setEncryptionKey(encryptionKey)
    }

    // Coordinate location from settingsViewModel to storeViewModel and optimizerViewModel
    val lat by settingsViewModel.userLatitude.collectAsState()
    val lon by settingsViewModel.userLongitude.collectAsState()
    LaunchedEffect(lat, lon) {
        storeViewModel.setLocation(lat, lon)
        optimizerViewModel.setLocation(lat, lon)
    }

    if (showOnboarding) {
        OnboardingScreen(
            settingsViewModel = settingsViewModel,
            storeViewModel = storeViewModel,
            listViewModel = listViewModel,
            onComplete = { settingsViewModel.completeOnboarding() }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF1E293B), // Slate Dark Pane
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text(stringResource(R.string.tab_dashboard), fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF6366F1),
                            selectedTextColor = Color(0xFF6366F1),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0x1F6366F1)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "List") },
                        label = { Text(stringResource(R.string.tab_list), fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF6366F1),
                            selectedTextColor = Color(0xFF6366F1),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0x1F6366F1)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.ShowChart, contentDescription = "History") },
                        label = { Text(stringResource(R.string.tab_history), fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF6366F1),
                            selectedTextColor = Color(0xFF6366F1),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0x1F6366F1)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Route, contentDescription = "Optimizer") },
                        label = { Text(stringResource(R.string.tab_optimizer), fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF6366F1),
                            selectedTextColor = Color(0xFF6366F1),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0x1F6366F1)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.SettingsInputAntenna, contentDescription = "Smart Home") },
                        label = { Text(stringResource(R.string.tab_integrations), fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF6366F1),
                            selectedTextColor = Color(0xFF6366F1),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0x1F6366F1)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        icon = { Icon(Icons.Default.CloudSync, contentDescription = "Sync Options") },
                        label = { Text("Sync Options", fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF6366F1),
                            selectedTextColor = Color(0xFF6366F1),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0x1F6366F1)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 6,
                        onClick = { selectedTab = 6 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text(stringResource(R.string.tab_settings), fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF6366F1),
                            selectedTextColor = Color(0xFF6366F1),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0x1F6366F1)
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        listViewModel = listViewModel,
                        storeViewModel = storeViewModel,
                        scraperViewModel = scraperViewModel
                    )
                    1 -> GroceryListScreen(listViewModel = listViewModel)
                    2 -> PriceHistoryScreen(listViewModel = listViewModel)
                    3 -> {
                        val items by listViewModel.items.collectAsState()
                        val activeCount = items.count { !it.isCompleted }
                        OptimizerScreen(
                            optimizerViewModel = optimizerViewModel,
                            activeCount = activeCount
                        )
                    }
                    4 -> IntegrationScreen(settingsViewModel = settingsViewModel, listViewModel = listViewModel)
                    5 -> SubscriptionScreen(settingsViewModel = settingsViewModel)
                    6 -> SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        storeViewModel = storeViewModel,
                        optimizerViewModel = optimizerViewModel
                    )
                }
            }
        }
    }
}
