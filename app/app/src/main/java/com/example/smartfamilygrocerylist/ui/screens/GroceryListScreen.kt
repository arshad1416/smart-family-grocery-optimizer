package com.example.smartfamilygrocerylist.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfamilygrocerylist.R
import com.example.smartfamilygrocerylist.data.model.ListItem
import com.example.smartfamilygrocerylist.ui.viewmodel.ListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryListScreen(
    listViewModel: ListViewModel,
    modifier: Modifier = Modifier
) {
    val itemsState by listViewModel.items.collectAsState()
    val priceTrendsState by listViewModel.priceTrends.collectAsState()
    var newItemText by remember { mutableStateOf("") }
    
    // Dialog state for item price details
    var selectedItemForDetail by remember { mutableStateOf<ListItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_list),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Input Add Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                placeholder = { Text(stringResource(R.string.add_item_hint), fontSize = 13.sp, color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            )
            Button(
                onClick = {
                    if (newItemText.isNotBlank()) {
                        listViewModel.addItem(newItemText)
                        newItemText = ""
                    }
                },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add_item_button), fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Group items by category (e.g. Produce, Dairy & Eggs, Pantry)
        val groupedItems = itemsState.groupBy { it.category }

        if (itemsState.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Your grocery list is empty. Add items above to get started!",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedItems.forEach { (category, items) ->
                    item {
                        Text(
                            text = category.uppercase(),
                            color = Color(0xFF818CF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    items(items) { item ->
                        // Query the best matched price for this item
                        val trendsList = priceTrendsState[item.itemHash] ?: emptyList()
                        val cheapestStoreProduct = trendsList.minByOrNull { it.currentPrice }

                        val cardBgColor by animateColorAsState(
                            if (item.isCompleted) Color(0x05FFFFFF) else Color(0x0DFFFFFF)
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedItemForDetail = item },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, Color(0x0AFFFFFF))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = item.isCompleted,
                                        onCheckedChange = { listViewModel.toggleItem(item) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF10B981),
                                            uncheckedColor = Color.Gray
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = item.encryptedName,
                                                color = if (item.isCompleted) Color.Gray else Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                            if (cheapestStoreProduct != null && !item.isCompleted) {
                                                Text(
                                                    text = "-\$${cheapestStoreProduct.currentPrice}",
                                                    color = Color(0xFF10B981),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                        if (cheapestStoreProduct != null && !item.isCompleted) {
                                            Text(
                                                text = cheapestStoreProduct.storeName,
                                                color = Color(0xFF818CF8),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (item.addedBy != null && !item.isCompleted) {
                                            Text(
                                                text = "Added by: ${item.addedBy}",
                                                color = Color.DarkGray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Delete Item
                                    IconButton(
                                        onClick = { listViewModel.deleteItem(item) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Item",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Details dialog showing price lists across all retailers
        selectedItemForDetail?.let { item ->
            val trends = priceTrendsState[item.itemHash] ?: emptyList()
            AlertDialog(
                onDismissRequest = { selectedItemForDetail = null },
                title = { Text(item.encryptedName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Compare pricing across Ontario retailers:", color = Color.Gray, fontSize = 12.sp)
                        Divider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                        
                        if (trends.isEmpty()) {
                            Text("No store pricing found for this item hash. Run the Scraper to index prices.", color = Color.LightGray, fontSize = 12.sp)
                        } else {
                            trends.sortedBy { it.currentPrice }.forEach { trend ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(trend.storeName, color = Color.White, fontSize = 13.sp)
                                    Text(
                                        text = "\$${trend.currentPrice} (${trend.weight ?: ""})",
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { selectedItemForDetail = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("Close", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}
