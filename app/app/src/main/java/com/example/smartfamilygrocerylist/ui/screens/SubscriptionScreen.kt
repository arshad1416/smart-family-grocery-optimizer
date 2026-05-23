package com.example.smartfamilygrocerylist.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dns
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
import com.example.smartfamilygrocerylist.data.api.RetrofitClient
import com.example.smartfamilygrocerylist.ui.viewmodel.GroceryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewModel: GroceryViewModel,
    modifier: Modifier = Modifier
) {
    val syncModeState by viewModel.syncMode.collectAsState()
    
    // Credit card simulator
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvc by remember { mutableStateOf("") }
    var cardSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.sub_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Card 1: Self-Hosted Tailscale Sync (Free)
        val localSelected = syncModeState == "local"
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    viewModel.syncMode.value = "local"
                    RetrofitClient.setBaseUrl("http://10.0.2.2:8000/") // Local emulator
                    viewModel.loadData()
                },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, if (localSelected) Color(0xFF6366F1) else Color(0x11FFFFFF)),
            colors = CardDefaults.cardColors(
                containerColor = if (localSelected) Color(0x126366F1) else Color(0x05FFFFFF)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = if (localSelected) Color(0xFF6366F1) else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.local_sync_title),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        if (localSelected) {
                            Text(
                                stringResource(R.string.active_badge).uppercase(),
                                color = Color(0xFF6366F1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.local_sync_desc),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: AWS Cloud Sync (Premium Subscription)
        val cloudSelected = syncModeState == "cloud"
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!cardSuccess) {
                        showCheckoutDialog = true
                    } else {
                        viewModel.syncMode.value = "cloud"
                        RetrofitClient.setBaseUrl("https://api.smartgrocery-cloud.com/") // Cloud mockup url
                    }
                },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, if (cloudSelected) Color(0xFF10B981) else Color(0x11FFFFFF)),
            colors = CardDefaults.cardColors(
                containerColor = if (cloudSelected) Color(0x1210B981) else Color(0x05FFFFFF)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = if (cloudSelected) Color(0xFF10B981) else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_sync_title),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        if (cloudSelected) {
                            Text(
                                stringResource(R.string.active_badge).uppercase(),
                                color = Color(0xFF10B981),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.cloud_sync_desc),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Subscription benefit checklist
        AnimatedVisibility(visible = !cardSuccess) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("AWS Premium Subscription Benefits:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("• 24/7 fully-managed AWS background scraper (No local browser overhead)", color = Color.LightGray, fontSize = 12.sp)
                Text("• Instant multi-device encrypted backups and sharing", color = Color.LightGray, fontSize = 12.sp)
                Text("• Email notifications for price drops on list favorites", color = Color.LightGray, fontSize = 12.sp)
                Text("• Google Keep list exports enabled", color = Color.LightGray, fontSize = 12.sp)
            }
        }

        // Checkout Dialog Simulator
        if (showCheckoutDialog) {
            AlertDialog(
                onDismissRequest = { showCheckoutDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Subscribe to AWS Cloud", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Amount: $4.99 CAD / month\n(This is a secure simulated payment portal)", color = Color.Gray, fontSize = 12.sp)
                        OutlinedTextField(
                            value = cardNumber,
                            onValueChange = { cardNumber = it },
                            placeholder = { Text("1234 5678 1234 5678") },
                            label = { Text("Card Number") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cardExpiry,
                                onValueChange = { cardExpiry = it },
                                placeholder = { Text("MM/YY") },
                                label = { Text("Expiry") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = cardCvc,
                                onValueChange = { cardCvc = it },
                                placeholder = { Text("123") },
                                label = { Text("CVC") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (cardNumber.length >= 12) {
                                cardSuccess = true
                                viewModel.syncMode.value = "cloud"
                                RetrofitClient.setBaseUrl("https://api.smartgrocery-cloud.com/")
                                showCheckoutDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Subscribe Now", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCheckoutDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}
