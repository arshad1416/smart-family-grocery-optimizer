package com.example.smartfamilygrocerylist.data.model

import com.google.gson.annotations.SerializedName

data class Store(
    val id: Int,
    val name: String,
    @SerializedName("distance_km") val distanceKm: Double,
    val location: String?
)

data class Product(
    val id: Int,
    @SerializedName("store_id") val storeId: Int,
    @SerializedName("product_name") val productName: String,
    @SerializedName("product_hash") val productHash: String,
    val brand: String?,
    val price: Double,
    val category: String?,
    val weight: String?
)

data class ListItem(
    val id: Int,
    @SerializedName("list_id") val listId: Int,
    @SerializedName("encrypted_name") val encryptedName: String,
    @SerializedName("item_hash") val itemHash: String,
    val quantity: Int,
    val category: String,
    @SerializedName("added_by") val addedBy: String?,
    @SerializedName("is_completed") val isCompleted: Boolean
)

data class PriceHistoryEntry(
    val price: Double,
    val date: String
)

data class StoreProductHistory(
    @SerializedName("store_name") val storeName: String,
    @SerializedName("product_name") val productName: String,
    val brand: String?,
    val weight: String?,
    val category: String?,
    @SerializedName("current_price") val currentPrice: Double,
    val history: List<PriceHistoryEntry>
)

data class OptimizedItem(
    @SerializedName("item_hash") val itemHash: String,
    @SerializedName("product_name") val productName: String,
    val price: Double,
    @SerializedName("store_name") val storeName: String
)

data class OptimizationResult(
    val stores: List<String>?,
    @SerializedName("store_name") val storeName: String?, // For single store
    @SerializedName("items_price") val itemsPrice: Double,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("fuel_cost") val fuelCost: Double,
    @SerializedName("travel_time_hr") val travelTimeHr: Double,
    @SerializedName("time_value_cost") val timeValueCost: Double,
    @SerializedName("total_effective_cost") val totalEffectiveCost: Double,
    val items: List<OptimizedItem>,
    @SerializedName("unmatched_count") val unmatchedCount: Int
)

data class FullOptimization(
    @SerializedName("single_store") val singleStore: OptimizationResult,
    @SerializedName("two_store") val twoStore: OptimizationResult,
    @SerializedName("multi_store") val multiStore: OptimizationResult,
    val recommended: String
)

data class ScraperStatus(
    val status: String,
    @SerializedName("last_run") val lastRun: String?,
    val logs: List<String>
)

data class SearchedStore(
    val name: String,
    val location: String,
    @SerializedName("distance_km") val distanceKm: Double,
    val latitude: Double,
    val longitude: Double
)
