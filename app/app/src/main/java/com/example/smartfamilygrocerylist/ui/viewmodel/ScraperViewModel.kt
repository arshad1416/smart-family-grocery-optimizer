package com.example.smartfamilygrocerylist.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfamilygrocerylist.data.api.RetrofitClient
import com.example.smartfamilygrocerylist.data.model.ScraperStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScraperViewModel(application: Application) : AndroidViewModel(application) {
    private val _scraperStatus = MutableStateFlow<ScraperStatus?>(null)
    val scraperStatus: StateFlow<ScraperStatus?> = _scraperStatus

    init {
        startScraperStatusPoller()
    }

    fun triggerScraper(selectedStoreNames: List<String>) {
        viewModelScope.launch {
            try {
                // Instantly set to running locally so poller speeds up immediately
                _scraperStatus.value = _scraperStatus.value?.copy(status = "running")
                    ?: ScraperStatus("running", null, emptyList())
                
                RetrofitClient.getService().runScraper(selectedStoreNames)
                
                // Immediate status check
                val res = RetrofitClient.getService().getScraperStatus()
                _scraperStatus.value = res
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startScraperStatusPoller() {
        viewModelScope.launch {
            // Initial load
            try {
                _scraperStatus.value = RetrofitClient.getService().getScraperStatus()
            } catch (e: Exception) {
                // Fail silently on initial poll
            }

            while (true) {
                val currentStatus = _scraperStatus.value?.status
                if (currentStatus == "running") {
                    try {
                        _scraperStatus.value = RetrofitClient.getService().getScraperStatus()
                    } catch (e: Exception) {
                        // Fail silently
                    }
                    delay(3000) // Fast poll (every 3 seconds) when actively scraping
                } else {
                    try {
                        _scraperStatus.value = RetrofitClient.getService().getScraperStatus()
                    } catch (e: Exception) {
                        // Fail silently
                    }
                    delay(15000) // Slow poll (every 15 seconds) when scraper is idle/success/error
                }
            }
        }
    }
}
