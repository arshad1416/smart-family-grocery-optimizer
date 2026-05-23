package com.example.smartfamilygrocerylist.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
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
import com.example.smartfamilygrocerylist.ui.viewmodel.GroceryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    viewModel: GroceryViewModel,
    modifier: Modifier = Modifier
) {
    val logsState by viewModel.smartHomeLogs.collectAsState()
    var voiceInputText by remember { mutableStateOf("") }
    var selectedDevice by remember { mutableStateOf("siri") } // siri, google, alexa

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_integrations),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Instruction block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
            border = BorderStroke(1.dp, Color(0x0FFFFFFF))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sync items with Google Home, Alexa, Siri Shortcuts, or Home Assistant using the simulator below or the scripts in /docs.",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Device Selection
        Text("Select Device to Simulate", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val devices = listOf("siri" to "Apple Siri", "google" to "Google Home", "alexa" to "Amazon Alexa")
            devices.forEach { (key, label) ->
                val isSelected = selectedDevice == key
                Button(
                    onClick = { selectedDevice = key },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color(0xFF6366F1) else Color(0x0DFFFFFF)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(label, fontSize = 10.sp, color = if (isSelected) Color.White else Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Command Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = voiceInputText,
                onValueChange = { voiceInputText = it },
                placeholder = { Text("e.g. Add Organic Bananas", fontSize = 12.sp, color = Color.Gray) },
                modifier = Modifier.weight(1f),
                singleLine = true,
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
                    if (voiceInputText.isNotBlank()) {
                        viewModel.simulateVoiceSpeech(voiceInputText, selectedDevice)
                        voiceInputText = ""
                    }
                },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Simulate Speak", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Smart Home Logs Console
        Text(
            text = stringResource(R.string.device_payload_title),
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
            if (logsState.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No voice webhook triggers logged yet.\nUse the microphone simulator above.",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logsState) { logLine ->
                        Text(
                            text = logLine,
                            color = when {
                                logLine.contains("Response: 200") -> Color(0xFF10B981)
                                logLine.contains("Error") -> Color(0xFFEF4444)
                                logLine.contains("encryption") -> Color(0xFF818CF8)
                                else -> Color.LightGray
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
}
