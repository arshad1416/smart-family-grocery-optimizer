package com.example.smartfamilygrocerylist.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfamilygrocerylist.R
import com.example.smartfamilygrocerylist.data.model.StoreProductHistory
import com.example.smartfamilygrocerylist.ui.components.PriceLineChart
import com.example.smartfamilygrocerylist.ui.viewmodel.ListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceHistoryScreen(
    listViewModel: ListViewModel,
    modifier: Modifier = Modifier
) {
    val itemsState by listViewModel.items.collectAsState()
    val priceTrendsState by listViewModel.priceTrends.collectAsState()

    var selectedItemHash by remember { mutableStateOf("") }
    
    // Auto-select first item if selection is empty and items exist
    LaunchedEffect(itemsState) {
        if (selectedItemHash.isEmpty() && itemsState.isNotEmpty()) {
            selectedItemHash = itemsState.first().itemHash
        }
    }

    val selectedItem = itemsState.find { it.itemHash == selectedItemHash }
    val productHistoryList = priceTrendsState[selectedItemHash] ?: emptyList()

    // Flatten all products across stores for a clean spreadsheet view
    val allTrackedPrices = remember(priceTrendsState) {
        priceTrendsState.values.flatten()
    }

    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedStoreFilter by remember { mutableStateOf("All") }

    val categories = listOf("All", "Produce", "Dairy & Eggs", "Pantry", "Meat & Seafood", "Bakery")
    val stores = listOf("All", "Walmart Supercentre", "No Frills", "Loblaws", "Metro", "Sobeys")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_history),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Price trends chart section
        if (itemsState.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Add items to your list to track price history.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            Column {
                Text(
                    text = "Price Trends: ${selectedItem?.encryptedName ?: "Select an item"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Item Quick-Selectors (horizontal chips)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsState.take(4).forEach { item ->
                        FilterChip(
                            selected = item.itemHash == selectedItemHash,
                            onClick = { selectedItemHash = item.itemHash },
                            label = { Text(item.encryptedName, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6366F1),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0x0DFFFFFF),
                                labelColor = Color.Gray
                            )
                        )
                    }
                }

                // Render Canvas price line chart
                PriceLineChart(
                    productHistoryList = productHistoryList,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Spreadsheet Catalog Filters
        Text("Tracked Prices Database", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category Filter dropdown simulator (cycles values on click for UI speed)
            Button(
                onClick = {
                    val nextIdx = (categories.indexOf(selectedCategoryFilter) + 1) % categories.size
                    selectedCategoryFilter = categories[nextIdx]
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x0DFFFFFF)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Category: $selectedCategoryFilter", fontSize = 11.sp, color = Color.White)
            }

            // Store Filter dropdown simulator
            Button(
                onClick = {
                    val nextIdx = (stores.indexOf(selectedStoreFilter) + 1) % stores.size
                    selectedStoreFilter = stores[nextIdx]
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x0DFFFFFF)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Store: ${selectedStoreFilter.replace(" Supercentre", "")}", fontSize = 11.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Spreadsheet Catalog List
        val filteredCatalog = allTrackedPrices.filter { priceObj ->
            (selectedCategoryFilter == "All" || priceObj.category == selectedCategoryFilter) &&
            (selectedStoreFilter == "All" || priceObj.storeName == selectedStoreFilter)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (filteredCatalog.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No records match the active database filter.", color = Color.DarkGray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCatalog) { priceEntry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x05FFFFFF)),
                            border = BorderStroke(1.dp, Color(0x0AFFFFFF))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = priceEntry.productName,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "\$${priceEntry.currentPrice}",
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF10B981),
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${priceEntry.storeName.replace(" Supercentre", "")} • ${priceEntry.brand ?: "Generic"}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "${priceEntry.category ?: ""} (${priceEntry.weight ?: ""})",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
