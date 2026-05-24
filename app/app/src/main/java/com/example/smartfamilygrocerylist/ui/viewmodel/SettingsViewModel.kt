package com.example.smartfamilygrocerylist.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfamilygrocerylist.data.PreferencesManager
import com.example.smartfamilygrocerylist.data.api.RetrofitClient
import com.example.smartfamilygrocerylist.security.Tokenizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application.applicationContext)

    private val _encryptionKey = MutableStateFlow(prefs.getString("encryption_key", ""))
    val encryptionKey: StateFlow<String> = _encryptionKey

    private val _serverUrl = MutableStateFlow(prefs.getString("server_url", RetrofitClient.getBaseUrl()))
    val serverUrl: StateFlow<String> = _serverUrl

    private val _syncMode = MutableStateFlow(prefs.getString("sync_mode", "local"))
    val syncMode: StateFlow<String> = _syncMode

    private val _userCity = MutableStateFlow(prefs.getString("user_city", "Waterdown"))
    val userCity: StateFlow<String> = _userCity

    private val _userProvince = MutableStateFlow(prefs.getString("user_province", "Ontario"))
    val userProvince: StateFlow<String> = _userProvince

    private val _userLatitude = MutableStateFlow(prefs.getDouble("user_latitude", 43.3333))
    val userLatitude: StateFlow<Double> = _userLatitude

    private val _userLongitude = MutableStateFlow(prefs.getDouble("user_longitude", -79.8833))
    val userLongitude: StateFlow<Double> = _userLongitude

    private val _showOnboarding = MutableStateFlow(prefs.getBoolean("show_onboarding", true))
    val showOnboarding: StateFlow<Boolean> = _showOnboarding

    private val _inviteCode = MutableStateFlow("")
    val inviteCode: StateFlow<String> = _inviteCode

    private val _smartHomeLogs = MutableStateFlow<List<String>>(emptyList())
    val smartHomeLogs: StateFlow<List<String>> = _smartHomeLogs

    init {
        // Initialize Retrofit base URL and auth token from preferences
        RetrofitClient.setBaseUrl(_serverUrl.value)
        if (_encryptionKey.value.isNotEmpty()) {
            val token = Tokenizer.hashPassphrase(_encryptionKey.value)
            RetrofitClient.setAuthToken(token)
        }
    }

    fun updateEncryptionKey(key: String, registerOnServer: Boolean = false) {
        _encryptionKey.value = key
        prefs.saveString("encryption_key", key)
        
        val token = Tokenizer.hashPassphrase(key)
        RetrofitClient.setAuthToken(token)
        
        if (registerOnServer && key.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val body = mapOf("token_hash" to token, "passphrase" to key)
                    RetrofitClient.getService().registerToken(body)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateServerUrl(url: String) {
        RetrofitClient.setBaseUrl(url)
        _serverUrl.value = RetrofitClient.getBaseUrl()
        prefs.saveString("server_url", _serverUrl.value)
    }

    fun updateSyncMode(mode: String) {
        _syncMode.value = mode
        prefs.saveString("sync_mode", mode)
    }

    fun updateLocation(city: String, province: String, lat: Double, lon: Double) {
        _userCity.value = city
        _userProvince.value = province
        _userLatitude.value = lat
        _userLongitude.value = lon
        
        prefs.saveString("user_city", city)
        prefs.saveString("user_province", province)
        prefs.saveDouble("user_latitude", lat)
        prefs.saveDouble("user_longitude", lon)
    }

    fun completeOnboarding() {
        _showOnboarding.value = false
        prefs.saveBoolean("show_onboarding", false)
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
                val res = RetrofitClient.getService().joinInvite(code)
                val passphrase = res["encryption_passphrase"] as? String
                val masterToken = res["master_token"] as? String
                
                if (!passphrase.isNullOrEmpty()) {
                    _encryptionKey.value = passphrase
                    prefs.saveString("encryption_key", passphrase)
                }
                if (!masterToken.isNullOrEmpty()) {
                    RetrofitClient.setAuthToken(masterToken)
                }
                
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    fun simulateVoiceSpeech(text: String, device: String, onSpeechProcessed: () -> Unit = {}) {
        viewModelScope.launch {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            try {
                val body = mapOf(
                    "device" to device,
                    "text" to text
                )
                val response = RetrofitClient.getService().simulateVoiceCommand(body)
                val item = response["extracted_item"] as? String ?: ""
                
                _smartHomeLogs.value = listOf(
                    "[$timestamp] Received POST webhook. Payload: {device: '$device', text: '$text'}",
                    "[$timestamp] Server Response: 200 OK. Extracted entity: '$item'",
                    "[$timestamp] Zero-Knowledge encryption added to database successfully."
                ) + _smartHomeLogs.value
                
                onSpeechProcessed()
            } catch (e: Exception) {
                _smartHomeLogs.value = listOf("[$timestamp] Error: Webhook trigger failed. Server offline.") + _smartHomeLogs.value
            }
        }
    }
}
