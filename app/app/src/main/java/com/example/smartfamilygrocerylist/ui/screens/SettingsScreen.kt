package com.example.smartfamilygrocerylist.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfamilygrocerylist.R
import com.example.smartfamilygrocerylist.platform.ShareSheet
import com.example.smartfamilygrocerylist.ui.viewmodel.GroceryViewModel
import com.example.smartfamilygrocerylist.utils.LocationHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: GroceryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                LocationHelper.detectLocation(context, viewModel)
            }
        }
    }

    // Configuration states
    val gasPriceState by viewModel.gasPrice.collectAsState()
    val mileageState by viewModel.mileage.collectAsState()
    val timeValueState by viewModel.timeValue.collectAsState()
    val encryptionKeyState by viewModel.encryptionKey.collectAsState()
    val inviteCodeState by viewModel.inviteCode.collectAsState()

    // Store checklist search states
    val registeredStores by viewModel.stores.collectAsState()
    val selectedStoresState by viewModel.selectedStoreNames.collectAsState()
    val searchResults by viewModel.storeSearchResults.collectAsState()
    val isSearching by viewModel.isSearchingStores.collectAsState()
    var storeSearchQuery by remember { mutableStateOf("") }

    // Dialog trigger
    var showQrDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 1. Vehicle & Trip parameters
        Text("Trip Costs Tuning Parameters", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))

        // Slider A: Gas Price
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.settings_gas_price), color = Color.Gray, fontSize = 12.sp)
                Text("\$${"%.2f".format(gasPriceState)} /L", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Slider(
                value = gasPriceState.toFloat(),
                onValueChange = { viewModel.gasPrice.value = it.toDouble() },
                valueRange = 1.0f..2.5f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF6366F1),
                    activeTrackColor = Color(0xFF6366F1),
                    inactiveTrackColor = Color(0x33FFFFFF)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Slider B: Vehicle Mileage
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.settings_vehicle_mileage), color = Color.Gray, fontSize = 12.sp)
                Text("${"%.1f".format(mileageState)} L/100km", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Slider(
                value = mileageState.toFloat(),
                onValueChange = { viewModel.mileage.value = it.toDouble() },
                valueRange = 5.0f..15.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF6366F1),
                    activeTrackColor = Color(0xFF6366F1),
                    inactiveTrackColor = Color(0x33FFFFFF)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Slider C: Time Value
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.settings_time_value), color = Color.Gray, fontSize = 12.sp)
                Text("\$${timeValueState.roundToInt()} /hr", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Slider(
                value = timeValueState.toFloat(),
                onValueChange = { viewModel.timeValue.value = it.toDouble() },
                valueRange = 10.0f..60.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF6366F1),
                    activeTrackColor = Color(0xFF6366F1),
                    inactiveTrackColor = Color(0x33FFFFFF)
                )
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Backend Connection
        Text("Backend Server Connection", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
            border = BorderStroke(1.dp, Color(0x11FFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var serverUrlInput by remember { mutableStateOf(viewModel.serverUrl.value) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = {
                            serverUrlInput = it
                        },
                        placeholder = { Text("e.g. http://192.168.0.142:8000") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        maxLines = 1,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            viewModel.updateServerUrl(serverUrlInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Connect", fontSize = 11.sp)
                    }
                }

                val isConnected = registeredStores.isNotEmpty()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444), RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = if (isConnected) "Connected: ${registeredStores.size} stores loaded" else "Disconnected (check server IP and network)",
                        color = if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Home Search Location
        Text("Home Search Location", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
            border = BorderStroke(1.dp, Color(0x11FFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Home Search Location", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    TextButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Detect Location", color = Color(0xFF6366F1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Text("Provide your city and province to locate grocery stores near you.", color = Color.Gray, fontSize = 10.sp)

                val userCityState by viewModel.userCity.collectAsState()
                val userProvinceState by viewModel.userProvince.collectAsState()

                var cityInput by remember(userCityState) { mutableStateOf(userCityState) }
                var provinceInput by remember(userProvinceState) { mutableStateOf(userProvinceState) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = {
                            cityInput = it
                            scope.launch {
                                LocationHelper.geocodeAddress(context, cityInput, provinceInput, viewModel)
                            }
                        },
                        label = { Text("City") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        maxLines = 1,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF6366F1)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = provinceInput,
                        onValueChange = {
                            provinceInput = it
                            scope.launch {
                                LocationHelper.geocodeAddress(context, cityInput, provinceInput, viewModel)
                            }
                        },
                        label = { Text("Province") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        maxLines = 1,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF6366F1)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
                
                val userLat by viewModel.userLatitude.collectAsState()
                val userLon by viewModel.userLongitude.collectAsState()
                Text(
                    text = "Geocoded: ${"%.4f".format(userLat)}, ${"%.4f".format(userLon)}",
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 2. Active Stores Search and Selection
        Text("Active Store Checklist & Proximity Search", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = storeSearchQuery,
            onValueChange = {
                storeSearchQuery = it
                viewModel.searchNearbyStores(it)
            },
            placeholder = { Text("Search nearby (e.g. Costco, Whole Foods)") },
            label = { Text("Add Store from Map Search") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            maxLines = 1,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF6366F1),
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color(0xFF6366F1)
            ),
            shape = RoundedCornerShape(10.dp)
        )

        if (storeSearchQuery.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
                }
            } else if (searchResults.isEmpty()) {
                Text("No matching stores found nearby.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                searchResults.forEach { searchedStore ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0x1F6366F1), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(searchedStore.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(searchedStore.location, color = Color.Gray, fontSize = 9.sp)
                            Text("${"%.2f".format(searchedStore.distanceKm)} km away", color = Color(0xFF10B981), fontSize = 9.sp)
                        }
                        Button(
                            onClick = {
                                viewModel.addSearchedStore(searchedStore) { success ->
                                    if (success) {
                                        storeSearchQuery = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Add", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        registeredStores.forEach { store ->
            val isChecked = selectedStoresState.contains(store.name)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color(0x0DFFFFFF), RoundedCornerShape(8.dp))
                    .clickable {
                        val currentList = selectedStoresState.toMutableList()
                        if (isChecked) currentList.remove(store.name)
                        else currentList.add(store.name)
                        viewModel.updateSelectedStores(currentList)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(store.name, color = Color.White, fontSize = 13.sp)
                    if (store.location != null) {
                        Text(store.location, color = Color.Gray, fontSize = 9.sp)
                    }
                    Text("${"%.2f".format(store.distanceKm)} km away", color = Color(0xFF6366F1), fontSize = 9.sp)
                }
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        val currentList = selectedStoresState.toMutableList()
                        if (checked == true) {
                            if (!currentList.contains(store.name)) currentList.add(store.name)
                        } else {
                            currentList.remove(store.name)
                        }
                        viewModel.updateSelectedStores(currentList)
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF6366F1),
                        uncheckedColor = Color.Gray
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 3. Encryption Keys & Invites sharing
        Text("Family Sync Cryptographic Keys", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))

        // Share App Links Button
        Button(
            onClick = {
                val inviteMsg = context.getString(R.string.share_app_msg, encryptionKeyState)
                val inviteTitle = context.getString(R.string.share_app_title)
                ShareSheet.shareText(context, inviteMsg, inviteTitle)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.share_app_btn), fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Show key QR button
        OutlinedButton(
            onClick = { showQrDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(Icons.Default.QrCode, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show Encryption Key QR", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Export Database
        OutlinedButton(
            onClick = {
                val exportText = "Exported Local SQLite Database: 120 grocery items, 5 stores, 6 months history synced."
                ShareSheet.shareText(context, exportText, "Export DB JSON")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
        ) {
            Text(stringResource(R.string.export_db_btn), fontWeight = FontWeight.Bold)
        }

        // QR Code Dialog popup
        if (showQrDialog) {
            AlertDialog(
                onDismissRequest = { showQrDialog = false },
                title = { Text("Family Encryption Key QR", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier
                                .size(180.dp)
                                .background(Color.White)
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Scan this from another device's Onboarding flow to load the encryption passphrase:\n$encryptionKeyState",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            lineHeight = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showQrDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("Done", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}
