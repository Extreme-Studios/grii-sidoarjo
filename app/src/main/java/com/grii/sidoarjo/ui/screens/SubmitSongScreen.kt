package com.grii.sidoarjo.ui.screens

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.grii.sidoarjo.data.model.SongRegistrationPayload
import com.grii.sidoarjo.data.repository.SongRegistrationRepository
import com.grii.sidoarjo.ui.SelectedImagePayload
import com.grii.sidoarjo.ui.readSelectedImage
import kotlinx.coroutines.launch

private val DEFAULT_KATEGORI_OPTIONS = listOf("YouTube", "Instagram", "Facebook")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitSongScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    repository: SongRegistrationRepository = remember { SongRegistrationRepository() }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current

    var title by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var synopsis by remember { mutableStateOf("") }
    var kategori by remember { mutableStateOf("YouTube") }
    var uploadLink by remember { mutableStateOf("") }
    var titleManuallyEdited by remember { mutableStateOf(false) }
    var synopsisManuallyEdited by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val fieldErrors = remember { mutableStateMapOf<String, String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun validate(): Boolean {
        fieldErrors.clear()
        if (uploadLink.isBlank()) fieldErrors["uploadLink"] = "Link is required"
        return fieldErrors.isEmpty()
    }

    fun clearForm() {
        title = ""
        year = ""
        synopsis = ""
        kategori = "YouTube"
        uploadLink = ""
        titleManuallyEdited = false
        synopsisManuallyEdited = false
        fieldErrors.clear()
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (onBack != null) {
                item {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Upload Video",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                Text(
                    text = "Upload Video",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            item {
                FormField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleManuallyEdited = true
                        fieldErrors.remove("title")
                    },
                    label = "Title",
                    error = fieldErrors["title"]
                )
            }
            item {
                FormField(
                    value = year,
                    onValueChange = {
                        year = it
                        fieldErrors.remove("year")
                    },
                    label = "Year",
                    error = fieldErrors["year"],
                    keyboardType = KeyboardType.Number
                )
            }
            item {
                FormField(
                    value = synopsis,
                    onValueChange = {
                        synopsis = it
                        synopsisManuallyEdited = true
                        fieldErrors.remove("synopsis")
                    },
                    label = "Description",
                    error = fieldErrors["synopsis"],
                    minLines = 4
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DEFAULT_KATEGORI_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = kategori.equals(option, ignoreCase = true),
                                onClick = { kategori = option },
                                label = { Text(option) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    FormField(
                        value = kategori,
                        onValueChange = { kategori = it },
                        label = "Additional category",
                        error = null
                    )
                }
            }
            item {
                FormField(
                    value = uploadLink,
                    onValueChange = {
                        uploadLink = it
                        fieldErrors.remove("uploadLink")
                    },
                    label = "Link",
                    error = fieldErrors["uploadLink"]
                )
            }

            item {
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        if (!validate()) {
                            scope.launch { snackbarHostState.showSnackbar("Form is incomplete. Please check required fields.") }
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            val payload = SongRegistrationPayload(
                                title = title.trim(),
                                synopsis = synopsis.trim(),
                                year = year.trim(),
                                kategori = kategori.trim().ifBlank { "YouTube" },
                                uploadLink = uploadLink.trim(),
                                copyrightAgreement = true,
                                coverImageBase64 = null,
                                coverImageFileName = null,
                                coverImageMimeType = null
                            )

                            val result = repository.submitRegistration(payload)
                            isLoading = false

                            result.onSuccess {
                                snackbarHostState.showSnackbar("Video successfully uploaded.")
                                clearForm()
                            }.onFailure { error ->
                                snackbarHostState.showSnackbar("Failed to upload content: ${error.message ?: "An error occurred."}")
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Submit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        isError = error != null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            focusedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(
            imeAction = if (minLines == 1) ImeAction.Done else ImeAction.Default,
            keyboardType = keyboardType
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus(force = true) }
        ),
        supportingText = {
            if (error != null) {
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

