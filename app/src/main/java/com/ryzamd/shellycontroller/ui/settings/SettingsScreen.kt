package com.ryzamd.shellycontroller.ui.settings

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ryzamd.shellycontroller.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
            viewModel.resetSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Broker Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "MQTT Broker Configuration",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = uiState.config.host,
                        onValueChange = { viewModel.updateHost(it) },
                        label = { Text("Broker Host") },
                        placeholder = { Text("192.168.1.100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = if (uiState.config.port == 0) "" else uiState.config.port.toString(),
                        onValueChange = { viewModel.updatePort(it) },
                        label = { Text("Port") },
                        placeholder = { Text("1883") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.config.username,
                        onValueChange = { viewModel.updateUsername(it) },
                        label = { Text("Username") },
                        placeholder = { Text("mqtt_user") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.config.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("Password") },
                        placeholder = { Text("Enter password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show")
                            }
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.config.clientId,
                        onValueChange = { viewModel.updateClientId(it) },
                        label = { Text("Client ID") },
                        placeholder = { Text("android_app") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text(
                                "Unique identifier for this app",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            }

            uiState.error?.let { error ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
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
}