package com.example.smartfamilygrocerylist.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfamilygrocerylist.R
import com.example.smartfamilygrocerylist.ui.viewmodel.SettingsViewModel
import com.example.smartfamilygrocerylist.ui.viewmodel.StoreViewModel
import com.example.smartfamilygrocerylist.ui.viewmodel.ListViewModel
import com.example.smartfamilygrocerylist.utils.LocationHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

private fun generateSecurePassphrase(): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..16)
        .map { allowedChars.random() }
        .joinToString("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    settingsViewModel: SettingsViewModel,
    storeViewModel: StoreViewModel,
    listViewModel: ListViewModel,
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                LocationHelper.detectLocation(context, settingsViewModel)
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Store Selection States linked to ViewModel
    val registeredStores by storeViewModel.stores.collectAsState()
    val selectedStoresState by storeViewModel.selectedStoreNames.collectAsState()
    val userLatitudeState by settingsViewModel.userLatitude.collectAsState()
    val userLongitudeState by settingsViewModel.userLongitude.collectAsState()
    
    // Store Search States
    val searchResults by storeViewModel.storeSearchResults.collectAsState()
    val isSearching by storeViewModel.isSearchingStores.collectAsState()
    var storeSearchQuery by remember { mutableStateOf("") }

    // Key management states
    var keyOption by remember { mutableStateOf(1) } // 1: Passphrase, 2: QR
    var passphrase by remember { mutableStateOf(generateSecurePassphrase()) }
    var isScanningSimulated by remember { mutableStateOf(false) }

    // Store Request Dialog State
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestStoreName by remember { mutableStateOf("") }
    var requestStoreAddress by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Slate Dark Background
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column {
                Text(
                    text = stringResource(R.string.welcome_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                
                // Step Indicator Progress Bar
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(Color(0xFF6366F1), RoundedCornerShape(2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (step >= 2) Color(0xFF6366F1) else Color(0x33FFFFFF),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Step Content
            Column {
                if (step == 1) {
                    // STEP 1: Store Checklist Selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.choose_stores_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.choose_stores_subtitle),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Backend Connection Setup Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Backend Connection Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Provide the IP address of your Mac or Tailscale server node hosting uvicorn.", color = Color.Gray, fontSize = 10.sp)
                            
                            var serverUrlInput by remember { mutableStateOf(settingsViewModel.serverUrl.value) }
                            
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
                                        settingsViewModel.updateServerUrl(serverUrlInput)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Text("Connect", fontSize = 11.sp)
                                }
                            }
                            
                            // Connection Status Badge
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Home Search Location Card
                    val userCityState by settingsViewModel.userCity.collectAsState()
                    val userProvinceState by settingsViewModel.userProvince.collectAsState()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
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
                                            LocationHelper.geocodeAddress(context, cityInput, provinceInput, settingsViewModel)
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
                                            LocationHelper.geocodeAddress(context, cityInput, provinceInput, settingsViewModel)
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
                            
                            val userLat by settingsViewModel.userLatitude.collectAsState()
                            val userLon by settingsViewModel.userLongitude.collectAsState()
                            Text(
                                text = "Geocoded: ${"%.4f".format(userLat)}, ${"%.4f".format(userLon)}",
                                color = Color.Gray,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Nearby Search UI component
                    OutlinedTextField(
                        value = storeSearchQuery,
                        onValueChange = {
                            storeSearchQuery = it
                            storeViewModel.searchNearbyStores(it)
                        },
                        placeholder = { Text("Search nearby stores (e.g. Costco, Whole Foods)") },
                        label = { Text("Find Grocery Store Nearby") },
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

                    // 2. Search Candidates dropdown
                    if (storeSearchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nearby Store Search Results", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isSearching) {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF6366F1), modifier = Modifier.size(24.dp))
                            }
                        } else if (searchResults.isEmpty()) {
                            Text("No stores found matching '$storeSearchQuery'.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
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
                                        Text(searchedStore.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(searchedStore.location, color = Color.Gray, fontSize = 10.sp)
                                        Text("${"%.2f".format(searchedStore.distanceKm)} km away", color = Color(0xFF10B981), fontSize = 10.sp)
                                    }
                                    Button(
                                        onClick = {
                                            storeViewModel.addSearchedStore(searchedStore) { success ->
                                                if (success) {
                                                    storeSearchQuery = ""
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Add", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Checklist of active stores
                    registeredStores.forEach { store ->
                        val isChecked = selectedStoresState.contains(store.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(Color(0x0DFFFFFF), RoundedCornerShape(8.dp))
                                .clickable {
                                    val currentList = selectedStoresState.toMutableList()
                                    if (isChecked) currentList.remove(store.name)
                                    else currentList.add(store.name)
                                    storeViewModel.updateSelectedStores(currentList)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(store.name, color = Color.White, fontSize = 14.sp)
                                if (store.location != null) {
                                    Text(store.location, color = Color.Gray, fontSize = 10.sp)
                                }
                                Text("${"%.2f".format(store.distanceKm)} km away", color = Color(0xFF6366F1), fontSize = 10.sp)
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
                                    storeViewModel.updateSelectedStores(currentList)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF6366F1),
                                    uncheckedColor = Color.Gray
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Request Store button
                    OutlinedButton(
                        onClick = { showRequestDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.request_store_button), fontSize = 13.sp)
                    }

                } else {
                    // STEP 2: Client Side Encryption Configuration
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.key_setup_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.key_setup_subtitle),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Tab Option Toggle (Passphrase vs QR code)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Color(0x0DFFFFFF), RoundedCornerShape(8.dp))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (keyOption == 1) Color(0xFF1E293B) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { keyOption = 1 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.passphrase_option),
                                color = if (keyOption == 1) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (keyOption == 2) Color(0xFF1E293B) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { keyOption = 2 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.qr_option),
                                color = if (keyOption == 2) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (keyOption == 1) {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("Family Passphrase") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            maxLines = 1,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedLabelColor = Color(0xFF10B981),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else {
                        // QR Scanner Simulator
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clickable {
                                    isScanningSimulated = true
                                    passphrase = "QR_SHARED_KEY_ABC123"
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = if (isScanningSimulated) Color(0xFF10B981) else Color.LightGray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isScanningSimulated) "Successfully Connected via QR Key!" else "Tap to Scan other Device's Key QR",
                                    color = if (isScanningSimulated) Color(0xFF10B981) else Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Navigation Buttons
            Button(
                onClick = {
                    if (step == 1) {
                        step = 2
                    } else {
                        settingsViewModel.updateEncryptionKey(passphrase, registerOnServer = true)
                        listViewModel.loadData()
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (step == 1) Color(0xFF6366F1) else Color(0xFF10B981)
                )
            ) {
                Text(
                    text = if (step == 1) "Continue" else stringResource(R.string.submit_key),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Request Store Sheet Dialog
        if (showRequestDialog) {
            AlertDialog(
                onDismissRequest = { showRequestDialog = false },
                title = { Text(stringResource(R.string.request_store_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = requestStoreName,
                            onValueChange = { requestStoreName = it },
                            placeholder = { Text("e.g. T&T Supermarket") },
                            label = { Text(stringResource(R.string.request_store_name_label)) },
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
                        OutlinedTextField(
                            value = requestStoreAddress,
                            onValueChange = { requestStoreAddress = it },
                            placeholder = { Text("e.g. 7070 Warden Ave") },
                            label = { Text(stringResource(R.string.request_store_address_label)) },
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
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (requestStoreName.isNotBlank()) {
                                storeViewModel.requestStore(requestStoreName, requestStoreAddress)
                                requestStoreName = ""
                                requestStoreAddress = ""
                                showRequestDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text(stringResource(R.string.send_request), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRequestDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}
