package com.example.smartfamilygrocerylist.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfamilygrocerylist.data.api.RetrofitClient
import com.example.smartfamilygrocerylist.data.model.SearchedStore
import com.example.smartfamilygrocerylist.data.model.Store
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    @Volatile
    private var userLatitude: Double = 43.3333 // Default to Waterdown, ON

    @Volatile
    private var userLongitude: Double = -79.8833

    private val _stores = MutableStateFlow<List<Store>>(emptyList())
    val stores: StateFlow<List<Store>> = _stores

    private val _selectedStoreNames = MutableStateFlow<List<String>>(
        listOf("Walmart Supercentre", "No Frills", "Fortinos", "Metro", "Sobeys")
    )
    val selectedStoreNames: StateFlow<List<String>> = _selectedStoreNames

    private val _storeSearchResults = MutableStateFlow<List<SearchedStore>>(emptyList())
    val storeSearchResults: StateFlow<List<SearchedStore>> = _storeSearchResults

    private val _isSearchingStores = MutableStateFlow(false)
    val isSearchingStores: StateFlow<Boolean> = _isSearchingStores

    val searchQuery = MutableStateFlow("")

    init {
        setupSearchDebounce()
        loadStores()
    }

    fun setLocation(lat: Double, lon: Double) {
        userLatitude = lat
        userLongitude = lon
    }

    fun loadStores() {
        viewModelScope.launch {
            try {
                _stores.value = RetrofitClient.getService().getStores()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateSelectedStores(names: List<String>) {
        _selectedStoreNames.value = names
    }

    fun searchNearbyStores(query: String) {
        searchQuery.value = query
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchDebounce() {
        viewModelScope.launch {
            searchQuery
                .debounce(500)
                .distinctUntilChanged()
                .collect { query ->
                    executeStoreSearch(query)
                }
        }
    }

    private suspend fun executeStoreSearch(query: String) {
        if (query.isBlank()) {
            _storeSearchResults.value = emptyList()
            return
        }
        _isSearchingStores.value = true
        try {
            _storeSearchResults.value = RetrofitClient.getService().searchNearbyStores(
                query = query,
                lat = userLatitude,
                lon = userLongitude
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _storeSearchResults.value = emptyList()
        } finally {
            _isSearchingStores.value = false
        }
    }

    fun addSearchedStore(searchedStore: SearchedStore, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val body = mapOf(
                    "name" to searchedStore.name,
                    "location" to searchedStore.location,
                    "distance_km" to searchedStore.distanceKm,
                    "latitude" to searchedStore.latitude,
                    "longitude" to searchedStore.longitude
                )
                RetrofitClient.getService().addCustomStore(body)
                
                if (!_selectedStoreNames.value.contains(searchedStore.name)) {
                    _selectedStoreNames.value = _selectedStoreNames.value + searchedStore.name
                }
                
                loadStores()
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
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
}
