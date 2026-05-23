package com.example.smartfamilygrocerylist.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfamilygrocerylist.data.api.RetrofitClient
import com.example.smartfamilygrocerylist.data.model.*
import com.example.smartfamilygrocerylist.security.Cryptography
import com.example.smartfamilygrocerylist.security.Tokenizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GroceryViewModel(application: Application) : AndroidViewModel(application) {

    // --- Configuration State ---
    var gasPrice = MutableStateFlow(1.55)
    var mileage = MutableStateFlow(8.5)
    var timeValue = MutableStateFlow(25.0)
    var encryptionKey = MutableStateFlow("FamilyKey2026")
    var syncMode = MutableStateFlow("local") // local or cloud
    var serverUrl = MutableStateFlow(RetrofitClient.getBaseUrl())
    var userLatitude = MutableStateFlow(43.3333) // Default to Waterdown, ON
    var userLongitude = MutableStateFlow(-79.8833)
    var userCity = MutableStateFlow("Waterdown")
    var userProvince = MutableStateFlow("Ontario")

    fun updateLocation(city: String, province: String, lat: Double, lon: Double) {
        userCity.value = city
        userProvince.value = province
        userLatitude.value = lat
        userLongitude.value = lon
    }

    fun updateServerUrl(url: String) {
        RetrofitClient.setBaseUrl(url)
        serverUrl.value = RetrofitClient.getBaseUrl()
        loadData()
    }

    // --- Reactive UI States ---
    private val _stores = MutableStateFlow<List<Store>>(emptyList())
    val stores: StateFlow<List<Store>> = _stores

    private val _items = MutableStateFlow<List<ListItem>>(emptyList())
    val items: StateFlow<List<ListItem>> = _items

    private val _selectedStoreNames = MutableStateFlow<List<String>>(
        listOf("Walmart Supercentre", "No Frills", "Loblaws", "Metro", "Sobeys")
    )
    val selectedStoreNames: StateFlow<List<String>> = _selectedStoreNames

    private val _optimization = MutableStateFlow<FullOptimization?>(null)
    val optimization: StateFlow<FullOptimization?> = _optimization

    private val _priceTrends = MutableStateFlow<Map<String, List<StoreProductHistory>>>(emptyMap())
    val priceTrends: StateFlow<Map<String, List<StoreProductHistory>>> = _priceTrends

    private val _scraperStatus = MutableStateFlow<ScraperStatus?>(null)
    val scraperStatus: StateFlow<ScraperStatus?> = _scraperStatus

    private val _inviteCode = MutableStateFlow("")
    val inviteCode: StateFlow<String> = _inviteCode

    private val _smartHomeLogs = MutableStateFlow<List<String>>(emptyList())
    val smartHomeLogs: StateFlow<List<String>> = _smartHomeLogs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _storeSearchResults = MutableStateFlow<List<SearchedStore>>(emptyList())
    val storeSearchResults: StateFlow<List<SearchedStore>> = _storeSearchResults

    private val _isSearchingStores = MutableStateFlow(false)
    val isSearchingStores: StateFlow<Boolean> = _isSearchingStores

    init {
        // Automatically sync initial lists
        loadData()
        startScraperStatusPoller()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch stores
                _stores.value = RetrofitClient.getService().getStores()
                
                // Fetch list items (using list ID = 1 as default family list)
                val rawItems = RetrofitClient.getService().getItems(1)
                
                // Decrypt names in-memory for UI
                _items.value = rawItems.map { item ->
                    item.copy(
                        encryptedName = Cryptography.decrypt(item.encryptedName, encryptionKey.value)
                    )
                }

                // If items exist, load their price trends in background
                if (_items.value.isNotEmpty()) {
                    loadPriceTrends()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSelectedStores(names: List<String>) {
        _selectedStoreNames.value = names
    }

    fun addItem(rawName: String) {
        if (rawName.isBlank()) return
        viewModelScope.launch {
            try {
                // 1. Encrypt raw item name client-side using shared key
                val encrypted = Cryptography.encrypt(rawName, encryptionKey.value)
                
                // 2. Hash canonical name for zero-knowledge price matching
                val hash = Tokenizer.tokenize(rawName)

                // Guess category locally based on keyword
                val category = guessCategory(rawName)

                val body = mapOf(
                    "encrypted_name" to encrypted,
                    "item_hash" to hash,
                    "quantity" to 1,
                    "category" to category,
                    "added_by" to "Family Phone"
                )

                RetrofitClient.getService().addItem(1, body)
                loadData() // Refresh list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleItem(item: ListItem) {
        viewModelScope.launch {
            try {
                val body = mapOf(
                    "is_completed" to !item.isCompleted
                )
                RetrofitClient.getService().updateItem(item.id, body)
                loadData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteItem(item: ListItem) {
        viewModelScope.launch {
            try {
                RetrofitClient.getService().deleteItem(item.id)
                loadData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun calculateRoute() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val body = mapOf(
                    "list_id" to 1,
                    "gas_price" to gasPrice.value,
                    "mileage" to mileage.value,
                    "time_value" to timeValue.value
                )
                _optimization.value = RetrofitClient.getService().getOptimization(body)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadPriceTrends() {
        viewModelScope.launch {
            try {
                val hashes = _items.value.map { it.itemHash }
                if (hashes.isNotEmpty()) {
                    _priceTrends.value = RetrofitClient.getService().getPriceHistory(hashes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun triggerScraper() {
        viewModelScope.launch {
            try {
                // Pass active target stores for selective scraping
                RetrofitClient.getService().runScraper(_selectedStoreNames.value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun requestStore(storeName: String, address: String) {
        viewModelScope.launch {
            try {
                val body = mapOf(
                    "store_name" to storeName,
                    "address_hint" to address
                )
                RetrofitClient.getService().requestStore(body)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun generateInvite() {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.getService().createInvite()
                _inviteCode.value = res["invite_code"] as? String ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun joinInvite(code: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                RetrofitClient.getService().joinInvite(code)
                loadData()
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    fun simulateVoiceSpeech(text: String, device: String) {
        viewModelScope.launch {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            try {
                val body = mapOf(
                    "device" to device,
                    "text" to text
                )
                val response = RetrofitClient.getService().simulateVoiceCommand(body)
                val item = response["extracted_item"] as? String ?: ""
                
                // Append webhook log
                _smartHomeLogs.value = listOf(
                    "[$timestamp] Received POST webhook. Payload: {device: '$device', text: '$text'}",
                    "[$timestamp] Server Response: 200 OK. Extracted entity: '$item'",
                    "[$timestamp] Zero-Knowledge encryption added to database successfully."
                ) + _smartHomeLogs.value
                
                // Refresh grocery list
                loadData()
            } catch (e: Exception) {
                _smartHomeLogs.value = listOf("[$timestamp] Error: Webhook trigger failed. Server offline.") + _smartHomeLogs.value
            }
        }
    }

    private fun startScraperStatusPoller() {
        viewModelScope.launch {
            while (true) {
                try {
                    _scraperStatus.value = RetrofitClient.getService().getScraperStatus()
                } catch (e: Exception) {
                    // Fail silently on poll
                }
                kotlinx.coroutines.delay(3000) // Poll every 3 seconds
            }
        }
    }

    fun searchNearbyStores(query: String) {
        if (query.isBlank()) {
            _storeSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearchingStores.value = true
            try {
                _storeSearchResults.value = RetrofitClient.getService().searchNearbyStores(
                    query = query,
                    lat = userLatitude.value,
                    lon = userLongitude.value
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _storeSearchResults.value = emptyList()
            } finally {
                _isSearchingStores.value = false
            }
        }
    }

    fun addSearchedStore(searchedStore: SearchedStore, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val body = mapOf(
                    "name" to searchedStore.name,
                    "location" to searchedStore.location,
                    "distance_km" to searchedStore.distanceKm
                )
                RetrofitClient.getService().addCustomStore(body)
                
                // Add the new store name to selectedStoreNames so it gets checked
                if (!_selectedStoreNames.value.contains(searchedStore.name)) {
                    _selectedStoreNames.value = _selectedStoreNames.value + searchedStore.name
                }
                
                loadData()
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    private fun guessCategory(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("milk") || lower.contains("egg") || lower.contains("yogurt") || lower.contains("cheese") || lower.contains("butter") -> "Dairy & Eggs"
            lower.contains("banana") || lower.contains("apple") || lower.contains("spinach") || lower.contains("tomato") || lower.contains("salad") || lower.contains("avocado") -> "Produce"
            lower.contains("chicken") || lower.contains("beef") || lower.contains("salmon") || lower.contains("pork") || lower.contains("steak") || lower.contains("meat") -> "Meat & Seafood"
            lower.contains("croissant") || lower.contains("bread") || lower.contains("cookie") || lower.contains("pastry") || lower.contains("muffin") -> "Bakery"
            else -> "Pantry"
        }
    }
}
