package com.example.smartfamilygrocerylist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.smartfamilygrocerylist.ui.components.TripOptimizerCard
import com.example.smartfamilygrocerylist.ui.viewmodel.OptimizerViewModel

@Composable
fun OptimizerScreen(
    optimizerViewModel: OptimizerViewModel,
    activeCount: Int,
    modifier: Modifier = Modifier
) {
    val optimizationState by optimizerViewModel.optimization.collectAsState()
    val isLoadingState by optimizerViewModel.isLoading.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.optimizer_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (activeCount == 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active items on your list to optimize. Add items first!",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Button to Calculate Route
                Button(
                    onClick = { optimizerViewModel.calculateRoute() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text(stringResource(R.string.optimize_btn), fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (isLoadingState) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6366F1))
                        }
                    } else if (optimizationState == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Press the button above to calculate the most gas and time efficient route for your $activeCount items.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        val opt = optimizationState!!
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Text(
                                    text = "Compare Route Strategies:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.LightGray
                                )
                            }

                            item {
                                TripOptimizerCard(
                                    title = stringResource(R.string.option_single_store),
                                    result = opt.singleStore,
                                    isRecommended = opt.recommended == "single_store"
                                )
                            }

                            item {
                                TripOptimizerCard(
                                    title = stringResource(R.string.option_two_stores),
                                    result = opt.twoStore,
                                    isRecommended = opt.recommended == "two_store"
                                )
                            }

                            item {
                                TripOptimizerCard(
                                    title = stringResource(R.string.option_multi_stores),
                                    result = opt.multiStore,
                                    isRecommended = opt.recommended == "multi_store"
                                )
                            }
                            
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
