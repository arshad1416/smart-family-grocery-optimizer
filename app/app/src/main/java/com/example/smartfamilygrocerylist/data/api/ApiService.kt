package com.example.smartfamilygrocerylist.data.api

import com.example.smartfamilygrocerylist.data.model.*
import okhttp3.ResponseBody
import retrofit2.http.*

@JvmSuppressWildcards
interface ApiService {
    @GET("api/stores")
    suspend fun getStores(): List<Store>

    @POST("api/stores/request")
    suspend fun requestStore(@Body body: Map<String, String>): ResponseBody

    @GET("api/lists")
    suspend fun getLists(): List<Map<String, Any>>

    @GET("api/lists/{list_id}/items")
    suspend fun getItems(@Path("list_id") listId: Int): List<ListItem>

    @POST("api/lists/{list_id}/items")
    suspend fun addItem(
        @Path("list_id") listId: Int,
        @Body body: Map<String, Any>
    ): Map<String, Any>

    @PUT("api/items/{item_id}")
    suspend fun updateItem(
        @Path("item_id") itemId: Int,
        @Body body: Map<String, Any>
    ): Map<String, Any>

    @DELETE("api/items/{item_id}")
    suspend fun deleteItem(@Path("item_id") itemId: Int): Map<String, Any>

    @POST("api/optimize")
    suspend fun getOptimization(@Body body: Map<String, Any>): FullOptimization

    @GET("api/prices/history")
    suspend fun getPriceHistory(
        @Query("item_hashes") itemHashes: List<String>
    ): Map<String, List<StoreProductHistory>>

    @POST("api/webhooks/smart-home")
    suspend fun simulateVoiceCommand(@Body body: Map<String, String>): Map<String, Any>

    @POST("api/collaboration/invite")
    suspend fun createInvite(): Map<String, Any>

    @POST("api/collaboration/join")
    suspend fun joinInvite(@Query("invite_code") code: String): Map<String, Any>

    @POST("api/scraper/run")
    suspend fun runScraper(
        @Query("selected_stores") selectedStores: List<String>?
    ): Map<String, Any>

    @GET("api/scraper/status")
    suspend fun getScraperStatus(): ScraperStatus

    @GET("api/stores/search-nearby")
    suspend fun searchNearbyStores(
        @Query("query") query: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): List<SearchedStore>

    @POST("api/stores/add-custom")
    suspend fun addCustomStore(
        @Body body: Map<String, Any>
    ): Map<String, Any>

    @POST("api/collaboration/register-token")
    suspend fun registerToken(@Body body: Map<String, String>): Map<String, Any>
}
