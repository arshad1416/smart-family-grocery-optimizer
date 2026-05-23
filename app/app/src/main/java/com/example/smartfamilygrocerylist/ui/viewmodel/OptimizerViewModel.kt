package com.example.smartfamilygrocerylist.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfamilygrocerylist.data.api.RetrofitClient
import com.example.smartfamilygrocerylist.data.model.FullOptimization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OptimizerViewModel(application: Application) : AndroidViewModel(application) {
    @Volatile
    private var userLatitude: Double = 43.3333

    @Volatile
    private var userLongitude: Double = -79.8833

    private val _gasPrice = MutableStateFlow(1.55)
    val gasPrice: StateFlow<Double> = _gasPrice

    private val _mileage = MutableStateFlow(8.5)
    val mileage: StateFlow<Double> = _mileage

    private val _timeValue = MutableStateFlow(25.0)
    val timeValue: StateFlow<Double> = _timeValue

    private val _optimization = MutableStateFlow<FullOptimization?>(null)
    val optimization: StateFlow<FullOptimization?> = _optimization

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun setLocation(lat: Double, lon: Double) {
        userLatitude = lat
        userLongitude = lon
    }

    fun updateGasPrice(price: Double) {
        _gasPrice.value = price
    }

    fun updateMileage(mileageVal: Double) {
        _mileage.value = mileageVal
    }

    fun updateTimeValue(value: Double) {
        _timeValue.value = value
    }

    fun calculateRoute() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val body = mapOf(
                    "list_id" to 1,
                    "gas_price" to _gasPrice.value,
                    "mileage" to _mileage.value,
                    "time_value" to _timeValue.value,
                    "user_lat" to userLatitude,
                    "user_lon" to userLongitude
                )
                _optimization.value = RetrofitClient.getService().getOptimization(body)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
