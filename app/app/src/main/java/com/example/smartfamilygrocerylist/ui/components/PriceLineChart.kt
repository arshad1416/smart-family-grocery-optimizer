package com.example.smartfamilygrocerylist.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfamilygrocerylist.data.model.PriceHistoryEntry
import com.example.smartfamilygrocerylist.data.model.StoreProductHistory
import kotlin.math.roundToInt

@Composable
fun PriceLineChart(
    productHistoryList: List<StoreProductHistory>,
    modifier: Modifier = Modifier
) {
    if (productHistoryList.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp)),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("No price history available for this item.", color = Color.Gray, fontSize = 14.sp)
        }
        return
    }

    // Color definitions for major stores
    val storeColors = mapOf(
        "Walmart Supercentre" to Color(0xFF1E88E5),  // Blue
        "No Frills" to Color(0xFFFDD835),             // Yellow/Gold
        "Loblaws" to Color(0xFFE53935),               // Red
        "Metro" to Color(0xFFD81B60),                 // Magenta/Pink
        "Sobeys" to Color(0xFF43A047)                 // Green
    )
    val fallbackColor = Color(0xFF8E24AA)

    // Merge and parse all historical points to calculate bounds
    var minPrice = Float.MAX_VALUE
    var maxPrice = Float.MIN_VALUE
    var maxPointsCount = 0

    productHistoryList.forEach { history ->
        if (history.history.size > maxPointsCount) {
            maxPointsCount = history.history.size
        }
        history.history.forEach { point ->
            val p = point.price.toFloat()
            if (p < minPrice) minPrice = p
            if (p > maxPrice) maxPrice = p
        }
    }

    // Add padding to bounds
    minPrice = (minPrice - 0.5f).coerceAtLeast(0f)
    maxPrice += 0.5f
    val priceRange = maxPrice - minPrice

    var dragOffset by remember { mutableStateOf<Offset?>(null) }
    var tooltipText by remember { mutableStateOf("") }
    var tooltipColor by remember { mutableStateOf(Color.White) }

    Column(modifier = modifier) {
        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            productHistoryList.forEach { history ->
                val color = storeColors[history.storeName] ?: fallbackColor
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = history.storeName.replace(" Supercentre", ""),
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Main Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> dragOffset = offset },
                        onDragEnd = { dragOffset = null },
                        onDragCancel = { dragOffset = null },
                        onDrag = { change, _ -> dragOffset = change.position }
                    )
                }
                .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Draw Y-Axis grid lines & Labels
                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = height * i / gridLines
                    val labelPrice = maxPrice - (priceRange * i / gridLines)
                    
                    // Grid line
                    drawLine(
                        color = Color(0x11FFFFFF),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Plot Lines for each store
                productHistoryList.forEach { storeHistory ->
                    val color = storeColors[storeHistory.storeName] ?: fallbackColor
                    val points = storeHistory.history
                    if (points.size < 2) return@forEach

                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = width * index / (points.size - 1)
                        val y = height - ((point.price.toFloat() - minPrice) / priceRange * height)
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // Interactive drag selection highlight
                dragOffset?.let { offset ->
                    val x = offset.x.coerceIn(0f, width)
                    // Find closest index
                    val pointIndex = (x / width * (maxPointsCount - 1)).roundToInt()
                        .coerceIn(0, maxPointsCount - 1)
                    
                    // Draw selection vertical line
                    val selectX = width * pointIndex / (maxPointsCount - 1)
                    drawLine(
                        color = Color(0x66FFFFFF),
                        start = Offset(selectX, 0f),
                        end = Offset(selectX, height),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Find prices at this index and format tooltip text
                    var tooltipData = ""
                    var primaryColor = Color.White
                    productHistoryList.forEach { storeHistory ->
                        val color = storeColors[storeHistory.storeName] ?: fallbackColor
                        val points = storeHistory.history
                        if (pointIndex < points.size) {
                            val p = points[pointIndex]
                            tooltipData += "${storeHistory.storeName.split(" ")[0]}: \$${p.price} (${p.date})\n"
                            primaryColor = color
                        }
                    }
                    if (tooltipData.isNotEmpty()) {
                        tooltipText = tooltipData.trim()
                        tooltipColor = primaryColor
                    }
                }
            }
        }

        // Active Tooltip Info panel on gesture drag
        if (dragOffset != null && tooltipText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = tooltipText,
                    color = tooltipColor,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
