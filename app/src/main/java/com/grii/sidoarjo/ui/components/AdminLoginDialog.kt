package com.grii.sidoarjo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.grii.sidoarjo.data.local.PortalSessionStore
import com.grii.sidoarjo.data.repository.PortalRepository
import kotlinx.coroutines.launch

@Composable
fun AdminLoginDialog(
    repository: PortalRepository,
    sessionStore: PortalSessionStore,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Admin Login") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Log in using admin username and password.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; message = null },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !submitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                        focusedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; message = null },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                        focusedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting && username.isNotBlank() && password.isNotBlank(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                onClick = {
                    submitting = true
                    scope.launch {
                        repository.loginAdmin(username.trim(), password).onSuccess { session ->
                            sessionStore.saveSession(session)
                            onSuccess()
                        }.onFailure { error ->
                            message = error.message ?: "Failed to login."
                        }
                        submitting = false
                    }
                }
            ) { Text(if (submitting) "Logging in..." else "Login") }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) { Text("Cancel") }
        }
    )
}
