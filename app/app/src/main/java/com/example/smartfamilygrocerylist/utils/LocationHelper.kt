package com.example.smartfamilygrocerylist.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import com.example.smartfamilygrocerylist.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun detectLocation(context: Context, viewModel: SettingsViewModel): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }
                
                if (provider != null) {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null) {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        val address = addresses?.firstOrNull()
                        if (address != null) {
                            val city = address.locality ?: address.subLocality ?: "Waterdown"
                            val province = address.adminArea ?: "Ontario"
                            
                            viewModel.updateLocation(
                                city = city,
                                province = province,
                                lat = location.latitude,
                                lon = location.longitude
                            )
                            return@withContext true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            false
        }
    }

    suspend fun geocodeAddress(context: Context, city: String, province: String, viewModel: SettingsViewModel): Boolean {
        return withContext(Dispatchers.IO) {
            if (city.isBlank()) return@withContext false
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val query = if (province.isNotBlank()) "$city, $province" else city
                val addresses = geocoder.getFromLocationName(query, 1)
                val address = addresses?.firstOrNull()
                if (address != null) {
                    viewModel.updateLocation(
                        city = city,
                        province = province,
                        lat = address.latitude,
                        lon = address.longitude
                    )
                    return@withContext true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            false
        }
    }
}

