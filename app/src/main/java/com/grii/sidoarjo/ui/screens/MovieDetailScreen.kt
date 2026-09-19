package com.grii.sidoarjo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.grii.sidoarjo.data.model.Song
import com.grii.sidoarjo.ui.components.SongCover
import kotlinx.coroutines.launch

@Composable
fun MovieDetailScreen(
    movie: Song,
    modifier: Modifier = Modifier,
    canEditMetadata: Boolean = false,
    onWatchFilm: (String) -> Unit,
    onSaveMetadata: suspend (String, String, String, String?, String?, String?) -> Result<String?> = { _, _, _, _, _, _ -> Result.success(null) },
    onMetadataSaved: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onDeleteFilm: suspend () -> Result<Unit> = { Result.success(Unit) },
    onFilmDeleted: () -> Unit = {}
) {
    val youtubeUrl = movie.youtubeUrl.ifBlank { movie.movieUrl }
    val scope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    var editTitle by remember(movie.id) { mutableStateOf(movie.title) }
    var editYear by remember(movie.id) { mutableStateOf(movie.year) }
    var editDescription by remember(movie.id) { mutableStateOf(movie.synopsis.ifBlank { movie.description }) }
    var editError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var coverImageBase64 by remember { mutableStateOf<String?>(null) }
    var coverImageFileName by remember { mutableStateOf<String?>(null) }
    var coverImageMimeType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(movie.id, showEditDialog) {
        if (showEditDialog) {
            editTitle = movie.title
            editYear = movie.year
            editDescription = movie.synopsis.ifBlank { movie.description }
            editError = null
            selectedImageUri = null
            coverImageBase64 = null
            coverImageFileName = null
            coverImageMimeType = null
        }
    }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            coverImageBase64 = uriToBase64(context, uri)
            coverImageFileName = getFileName(context, uri)
            coverImageMimeType = getMimeType(context, uri)
        } else {
            coverImageBase64 = null
            coverImageFileName = null
            coverImageMimeType = null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                SongCover(
                    song = movie,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                startY = 420f
                            )
                        )
                )
                Button(
                    onClick = { if (youtubeUrl.isNotBlank()) onWatchFilm(youtubeUrl) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp, start = 20.dp, end = 20.dp)
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = youtubeUrl.isNotBlank(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                if (canEditMetadata) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Admin login required.",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete information",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (movie.year.isNotBlank()) {
                            Text(
                                text = movie.year,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = movie.synopsis.ifBlank { "No description available." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp
                    )
                }

                Button(
                    onClick = { if (youtubeUrl.isNotBlank()) onWatchFilm(youtubeUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = youtubeUrl.isNotBlank(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showEditDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Edit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = {
                            editTitle = it
                            editError = null
                        },
                        label = { Text("Title") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editYear,
                        onValueChange = { editYear = it },
                        label = { Text("Year") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description") },
                        minLines = 4,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Content Thumbnail",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedImageUri != null) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "New thumbnail preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val safeCoverUrl = movie.thumbnailUrl.ifBlank { movie.coverUrl }
                                if (safeCoverUrl.isNotBlank()) {
                                    val finalUrl = if (safeCoverUrl.contains("drive.google.com") || safeCoverUrl.contains("docs.google.com")) {
                                        val fileId = runCatching {
                                            val uri = Uri.parse(safeCoverUrl.trim())
                                            uri.getQueryParameter("id")?.takeIf { it.isNotBlank() } ?: uri.pathSegments.let { segments ->
                                                val idx = segments.indexOf("d")
                                                if (idx >= 0 && idx + 1 < segments.size) segments[idx + 1] else null
                                            }
                                        }.getOrNull()
                                        if (fileId != null) "https://drive.google.com/thumbnail?id=$fileId&sz=w200" else safeCoverUrl
                                    } else {
                                        safeCoverUrl
                                    }
                                    AsyncImage(
                                        model = finalUrl,
                                        contentDescription = "Current thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { if (!isSaving) imagePickerLauncher.launch("image/*") },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text("Choose Thumbnail", fontSize = 13.sp)
                        }
                    }
                    if (selectedImageUri != null) {
                        Text(
                            text = coverImageFileName ?: "Selected Image",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1
                        )
                    }

                    editError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalizedTitle = editTitle.trim()
                        if (normalizedTitle.isBlank()) {
                            editError = "Title is required."
                            return@TextButton
                        }
                        scope.launch {
                            isSaving = true
                            val result = onSaveMetadata(
                                normalizedTitle,
                                editYear.trim(),
                                editDescription.trim(),
                                coverImageBase64,
                                coverImageFileName,
                                coverImageMimeType
                            )
                            isSaving = false
                            result.onSuccess { newThumbnailUrl ->
                                showEditDialog = false
                                onMetadataSaved(normalizedTitle, editYear.trim(), editDescription.trim(), newThumbnailUrl)
                            }.onFailure { error ->
                                editError = error.message ?: "Failed to save metadata."
                            }
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text(if (isSaving) "Saving..." else "Save", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isSaving) showEditDialog = false }, enabled = !isSaving) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            text = {
                Text(
                    text = "are you sure to delete?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            val result = onDeleteFilm()
                            isSaving = false
                            result.onSuccess {
                                showDeleteConfirm = false
                                showEditDialog = false
                                onFilmDeleted()
                            }.onFailure { error ->
                                showDeleteConfirm = false
                                editError = error.message ?: "Failed to delete content."
                            }
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(if (isSaving) "Deleting..." else "Yes", style = MaterialTheme.typography.bodyMedium)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isSaving) showDeleteConfirm = false },
                    enabled = !isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("No", style = MaterialTheme.typography.bodyMedium)
                }
            }
        )
    }
}

private fun uriToBase64(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        null
    }
}

private fun getMimeType(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.getType(uri)
}

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "thumbnail.jpg"
}

