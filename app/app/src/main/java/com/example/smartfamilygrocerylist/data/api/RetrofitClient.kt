package com.example.smartfamilygrocerylist.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    @Volatile
    private var baseUrl = "http://10.0.2.2:8000/" // Android emulator default gateway to localhost

    @Volatile
    private var apiService: ApiService? = null

    @Volatile
    private var authToken = ""

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        if (authToken.isNotEmpty()) {
            builder.header("Authorization", "Bearer $authToken")
        }
        chain.proceed(builder.build())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun getService(): ApiService {
        if (apiService == null) {
            rebuildService()
        }
        return apiService!!
    }

    @Synchronized
    fun setAuthToken(token: String) {
        authToken = token
    }

    /**
     * Updates the API gateway URL (e.g., to your Pi's Tailscale IP or your AWS Fargate endpoint)
     */
    @Synchronized
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

    @Synchronized
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
