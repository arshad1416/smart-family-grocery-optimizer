package com.example.smartfamilygrocerylist.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var baseUrl = "http://10.0.2.2:8000/" // Android emulator default gateway to localhost
    private var apiService: ApiService? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getService(): ApiService {
        if (apiService == null) {
            rebuildService()
        }
        return apiService!!
    }

    /**
     * Updates the API gateway URL (e.g., to your Pi's Tailscale IP or your AWS Fargate endpoint)
     */
    fun setBaseUrl(newUrl: String) {
        var cleanUrl = newUrl
        if (!cleanUrl.endsWith("/")) {
            cleanUrl += "/"
        }
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "http://$cleanUrl"
        }
        
        if (baseUrl != cleanUrl) {
            baseUrl = cleanUrl
            rebuildService()
        }
    }

    fun getBaseUrl(): String {
        return baseUrl
    }

    private fun rebuildService() {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }
}
