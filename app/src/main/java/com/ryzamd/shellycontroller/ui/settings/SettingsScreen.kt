package com.ryzamd.shellycontroller.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ryzamd.shellycontroller.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
        val uiState by viewModel.uiState.collectAsState()
        var tapCount by remember { mutableIntStateOf(0) }
        var showIpDialog by remember { mutableStateOf(false) }

        LaunchedEffect(uiState.saveSuccess) {
                if (uiState.saveSuccess) {
                        onNavigateBack()
                        viewModel.resetSaveSuccess()
                }
        }

        Scaffold(
                topBar = {
                        TopAppBar(
                                title = { Text("Server Settings") },
                                navigationIcon = {
                                        IconButton(onClick = onNavigateBack) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                        }
                                },
                                colors =
                                        TopAppBarDefaults.topAppBarColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        )
                        )
                }
        ) { padding ->
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .padding(padding)
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        Card(
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                        )
                        ) {
                                Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                        Text(
                                                text = "Server Configuration",
                                                style = MaterialTheme.typography.titleMedium
                                        )

                                        Card(
                                                colors =
                                                        CardDefaults.cardColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme
                                                                                .surface
                                                        ),
                                                modifier =
                                                        Modifier.fillMaxWidth().clickable(
                                                                        interactionSource =
                                                                                remember {
                                                                                        MutableInteractionSource()
                                                                                },
                                                                        indication = null
                                                                ) {
                                                                tapCount++
                                                                if (tapCount >= 10) {
                                                                        showIpDialog = true
                                                                        tapCount = 0
                                                                }
                                                        }
                                        ) {
                                                Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(4.dp)
                                                ) {
                                                        Text(
                                                                text = "Server: RCCServer",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium
                                                        )
                                                }
                                        }

                                        OutlinedTextField(
                                                value =
                                                        if (uiState.config.port == 0) ""
                                                        else uiState.config.port.toString(),
                                                onValueChange = { viewModel.updatePort(it) },
                                                label = { Text("Port") },
                                                placeholder = { Text("1883") },
                                                modifier = Modifier.fillMaxWidth(),
                                                keyboardOptions =
                                                        KeyboardOptions(
                                                                keyboardType = KeyboardType.Number
                                                        ),
                                                singleLine = true
                                        )
                                }
                        }

                        uiState.error?.let { error ->
                                Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .errorContainer
                                                )
                                ) {
                                        Text(
                                                text = error,
                                                modifier = Modifier.padding(16.dp),
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                }
                        }

                        Button(
                                onClick = { viewModel.saveConfig() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = !uiState.isSaving && uiState.config.host.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                        ) {
                                if (uiState.isSaving) {
                                        CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = SuccessGreen
                                        )
                                } else {
                                        Text("Save & Reconnect")
                                }
                        }
                }
        }

        if (showIpDialog) {
                var ipInput by remember { mutableStateOf(uiState.config.resolvedIp ?: "") }

                AlertDialog(
                        onDismissRequest = { showIpDialog = false },
                        title = { Text("Edit Server IP") },
                        text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                                text =
                                                        "Current IP: ${uiState.config.resolvedIp ?: "Not set"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                                value = ipInput,
                                                onValueChange = { ipInput = it },
                                                label = { Text("IP Address") },
                                                placeholder = { Text("192.168.1.100") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                }
                        },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                viewModel.updateResolvedIp(ipInput)
                                                showIpDialog = false
                                        }
                                ) { Text("Save") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showIpDialog = false }) { Text("Cancel") }
                        }
                )
        }
}