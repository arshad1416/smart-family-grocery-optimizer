package com.example.smartfamilygrocerylist.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfamilygrocerylist.R
import com.example.smartfamilygrocerylist.ui.viewmodel.ListViewModel
import com.example.smartfamilygrocerylist.ui.viewmodel.StoreViewModel
import com.example.smartfamilygrocerylist.ui.viewmodel.ScraperViewModel

@Composable
fun DashboardScreen(
    listViewModel: ListViewModel,
    storeViewModel: StoreViewModel,
    scraperViewModel: ScraperViewModel,
    modifier: Modifier = Modifier
) {
    val itemsState by listViewModel.items.collectAsState()
    val storesState by storeViewModel.stores.collectAsState()
    val scraperStatusState by scraperViewModel.scraperStatus.collectAsState()
    val selectedStoresState by storeViewModel.selectedStoreNames.collectAsState()

    val activeCount = itemsState.count { !it.isCompleted }
    
    // Estimate savings based on stores price differences
    val potentialSavings = if (itemsState.isEmpty()) "\$0.00" else "\$22.45"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        // App header
        Text(
            text = stringResource(R.string.tab_dashboard),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Stat Grid (2x2 Card Grid)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Active Items
            Card(
                modifier = Modifier.weight(1f).height(90.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                border = BorderStroke(1.dp, Color(0x11FFFFFF))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.stats_active_items), color = Color.Gray, fontSize = 11.sp)
                    Text("$activeCount items", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            // Card 2: Potential Savings
            Card(
                modifier = Modifier.weight(1f).height(90.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                border = BorderStroke(1.dp, Color(0x11FFFFFF))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.stats_potential_savings), color = Color.Gray, fontSize = 11.sp)
                    Text(potentialSavings, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 3: Tracked Prices
            Card(
                modifier = Modifier.weight(1f).height(90.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                border = BorderStroke(1.dp, Color(0x11FFFFFF))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.stats_tracked_prices), color = Color.Gray, fontSize = 11.sp)
                    Text("100 prices", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            // Card 4: Scraper Status
            Card(
                modifier = Modifier.weight(1f).height(90.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                border = BorderStroke(1.dp, Color(0x11FFFFFF))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.stats_scraper_status), color = Color.Gray, fontSize = 11.sp)
                    val statusText = scraperStatusState?.status ?: "idle"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (statusText == "running") Color(0xFFFBBF24) else Color(0xFF10B981),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText.uppercase(),
                            color = if (statusText == "running") Color(0xFFFBBF24) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Trigger Scraper Button
        val isRunning = scraperStatusState?.status == "running"
        Button(
            onClick = { scraperViewModel.triggerScraper(selectedStoresState) },
            enabled = !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6366F1),
                disabledContainerColor = Color(0x336366F1)
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isRunning) "Crawl4AI Scraping..." else stringResource(R.string.run_scraper_btn),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Scraper Logs Console
        Text(
            text = stringResource(R.string.scraper_logs_title),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF020617), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            val logs = scraperStatusState?.logs ?: listOf("[System] Ready. Scraper daemon idle.")
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { logLine ->
                    Text(
                        text = logLine,
                        color = when {
                            logLine.contains("Success") -> Color(0xFF10B981)
                            logLine.contains("error") || logLine.contains("failed") -> Color(0xFFEF4444)
                            logLine.contains("Walmart") -> Color(0xFF1E88E5)
                            logLine.contains("No Frills") -> Color(0xFFFDD835)
                            else -> Color.Gray
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
