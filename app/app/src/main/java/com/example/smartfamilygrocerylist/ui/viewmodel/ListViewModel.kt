package com.example.smartfamilygrocerylist.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfamilygrocerylist.data.api.RetrofitClient
import com.example.smartfamilygrocerylist.data.model.ListItem
import com.example.smartfamilygrocerylist.data.model.StoreProductHistory
import com.example.smartfamilygrocerylist.security.Cryptography
import com.example.smartfamilygrocerylist.security.Tokenizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ListViewModel(application: Application) : AndroidViewModel(application) {
    @Volatile
    private var encryptionKey: String = ""

    private val _items = MutableStateFlow<List<ListItem>>(emptyList())
    val items: StateFlow<List<ListItem>> = _items

    private val _priceTrends = MutableStateFlow<Map<String, List<StoreProductHistory>>>(emptyMap())
    val priceTrends: StateFlow<Map<String, List<StoreProductHistory>>> = _priceTrends

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun setEncryptionKey(key: String) {
        if (encryptionKey != key) {
            encryptionKey = key
            if (key.isNotEmpty()) {
                loadData()
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch list items (using list ID = 1 as default family list)
                val rawItems = RetrofitClient.getService().getItems(1)
                
                // Decrypt names in-memory for UI
                _items.value = rawItems.map { item ->
                    item.copy(
                        encryptedName = Cryptography.decrypt(item.encryptedName, encryptionKey)
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

    fun addItem(rawName: String) {
        if (rawName.isBlank()) return
        viewModelScope.launch {
            try {
                val encrypted = Cryptography.encrypt(rawName, encryptionKey)
                val hash = Tokenizer.tokenize(rawName)
                val category = guessCategory(rawName)

                val body = mapOf(
                    "encrypted_name" to encrypted,
                    "item_hash" to hash,
                    "quantity" to 1,
                    "category" to category,
                    "added_by" to "Family Phone"
                )

                RetrofitClient.getService().addItem(1, body)
                loadData()
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
