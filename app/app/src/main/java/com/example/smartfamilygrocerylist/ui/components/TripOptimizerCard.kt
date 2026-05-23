package com.example.smartfamilygrocerylist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfamilygrocerylist.data.model.OptimizationResult

@Composable
fun TripOptimizerCard(
    title: String,
    result: OptimizationResult,
    isRecommended: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val activeBorder = if (isRecommended) {
        BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF10B981))))
    } else {
        BorderStroke(1.dp, Color(0x33FFFFFF))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        border = activeBorder,
        colors = CardDefaults.cardColors(
            containerColor = if (isRecommended) Color(0x1F6366F1) else Color(0x0DFFFFFF)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (result.stores != null) {
                            "Visits: ${result.stores.joinToString(" + ")}"
                        } else {
                            "Visits: ${result.storeName ?: "N/A"}"
                        },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                if (isRecommended) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Best Value", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            labelColor = Color(0xFF10B981),
                            containerColor = Color(0x1A10B981)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF10B981))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text("GROCERY COST", fontSize = 10.sp, color = Color.Gray)
                    Text("\$${result.itemsPrice}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }
                Column(horizontalAlignment = Alignment.Start) {
                    Text("GAS COST", fontSize = 10.sp, color = Color.Gray)
                    Text("\$${result.fuelCost}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }
                Column(horizontalAlignment = Alignment.Start) {
                    Text("TIME OVERHEAD", fontSize = 10.sp, color = Color.Gray)
                    Text("${result.travelTimeHr}h", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("TOTAL COST", fontSize = 10.sp, color = if (isRecommended) Color(0xFF10B981) else Color.Gray)
                    Text(
                        text = "\$${result.totalEffectiveCost}",
                        fontWeight = FontWeight.Black,
                        color = if (isRecommended) Color(0xFF10B981) else Color(0xFF6366F1),
                        fontSize = 17.sp
                    )
                }
            }

            // Warnings / Unmatched items
            if (result.unmatchedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ ${result.unmatchedCount} item(s) are not available at these store selections.",
                    fontSize = 11.sp,
                    color = Color(0xFFFBBF24),
                    fontWeight = FontWeight.Medium
                )
            }

            // Expand hint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Hide shopping list breakdown" else "View shopping list breakdown",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Expanded content showing items per store
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Divider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Group items by store
                    val itemsByStore = result.items.groupBy { it.storeName }
                    itemsByStore.forEach { (store, items) ->
                        Text(
                            text = store,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF6366F1),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.productName,
                                    fontSize = 12.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = "\$${item.price}",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
