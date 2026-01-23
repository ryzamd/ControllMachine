@file:OptIn(ExperimentalMaterial3Api::class)

package com.ryzamd.shellycontroller.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Shelly Controller")
                        Text(
                            text = when {
                                uiState.isConnecting -> "Connecting..."
                                uiState.isBrokerConnected -> "Connected"
                                else -> "Disconnected"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                uiState.isConnecting -> MaterialTheme.colorScheme.tertiary
                                uiState.isBrokerConnected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDiscovery() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isConnecting -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Connecting to broker...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            !uiState.isBrokerConnected -> {
                EmptyStateView(
                    modifier = Modifier.padding(padding),
                    message = "Not connected to MQTT broker",
                    icon = Icons.Default.Warning,
                    actionText = "Configure",
                    onAction = onNavigateToSettings
                )
            }
            uiState.devices.isEmpty() -> {
                EmptyStateView(
                    modifier = Modifier.padding(padding),
                    message = "No Shelly devices found\n\nMake sure devices are:\n• Powered on\n• Connected to WiFi\n• MQTT configured",
                    icon = Icons.Default.Star
                )
            }
            else -> {
                DeviceList(
                    modifier = Modifier.padding(padding),
                    devices = uiState.devices,
                    onToggleSwitch = { viewModel.toggleSwitch(it) },
                    onConnectDevice = { viewModel.connectDevice(it.deviceId) },
                    onDeleteDevice = { viewModel.deleteDevice(it.deviceId) }
                )
            }
        }
    }
}

@Composable
private fun DeviceList(
    modifier: Modifier = Modifier,
    devices: List<ShellyDeviceUiState>,
    onToggleSwitch: (ShellyDeviceUiState) -> Unit,
    onConnectDevice: (ShellyDeviceUiState) -> Unit,
    onDeleteDevice: (ShellyDeviceUiState) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(devices, key = { it.deviceId }) { device ->
            DeviceCard(
                device = device,
                onToggleSwitch = { onToggleSwitch(device) },
                onConnect = { onConnectDevice(device) },
                onDelete = { onDeleteDevice(device) }
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: ShellyDeviceUiState,
    onToggleSwitch: () -> Unit,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isSaved) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !device.isOnline -> Color.Gray
                                device.isSwitchOn -> Color(0xFF4CAF50)
                                else -> Color(0xFFFF9800)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddCircle,  // Changed
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (device.isOnline) Icons.Default.CheckCircle else Icons.Default.Clear,  // Changed
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (device.isOnline) Color(0xFF4CAF50) else Color.Red
                        )
                        Text(
                            text = when {
                                !device.isSaved -> "New Device"
                                !device.isOnline -> "Offline"
                                device.isSwitchOn -> "ON"
                                else -> "OFF"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = device.deviceId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            if (device.isSaved) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = device.isSwitchOn,
                        onCheckedChange = { onToggleSwitch() },
                        enabled = device.isOnline
                    )
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null)
                                }
                            )
                        }
                    }
                }
            } else {
                if (device.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    FilledTonalButton(
                        onClick = onConnect,
                        enabled = device.isOnline
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))  // Changed
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    modifier: Modifier = Modifier,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (actionText != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
    }
}
