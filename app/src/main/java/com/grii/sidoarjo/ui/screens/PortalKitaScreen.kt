package com.grii.sidoarjo.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.grii.sidoarjo.data.local.PortalSessionStore
import com.grii.sidoarjo.data.model.PortalPost
import com.grii.sidoarjo.data.model.PortalUserSession
import com.grii.sidoarjo.data.repository.PortalRepository
import com.grii.sidoarjo.ui.SelectedImagePayload
import com.grii.sidoarjo.ui.readSelectedImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortalKitaScreen(
    posts: List<PortalPost>,
    categories: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onPostPreview: (PortalPost) -> Unit,
    onOpenPosterSubmission: () -> Unit,
    onLoginClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionStore = remember { PortalSessionStore(context) }
    val repository = remember { PortalRepository() }
    val session by sessionStore.sessionFlow.collectAsState(initial = null)
    val canPostPoster = session?.status.equals("ACTIVE", ignoreCase = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deletePost by remember { mutableStateOf<PortalPost?>(null) }
    var boardNotice by remember { mutableStateOf<UiNotice?>(null) }

    var selectedCategory by remember { mutableStateOf("All") }
    val filteredPosts = remember(posts, selectedCategory) {
        posts.filter { selectedCategory == "All" || it.category == selectedCategory }
    }

    val listState = rememberLazyListState()
    val dragAccum = remember { floatArrayOf(0f) }

    androidx.compose.runtime.LaunchedEffect(session?.username, session?.status, canPostPoster) {
        Log.d("PortalKita", "hubSession status=${session?.status ?: "null"}")
        Log.d("PortalKita", "canPostPoster=$canPostPoster")
        Log.d("PortalKita", "Pasang Informasi visible=$canPostPoster")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLoading) {
                detectVerticalDragGestures { _, dragAmount ->
                    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    if (!isLoading && atTop && dragAmount > 0f) {
                        dragAccum[0] += dragAmount
                        if (dragAccum[0] > 150f) {
                            dragAccum[0] = 0f
                            onRefresh()
                        }
                    } else if (dragAmount < 0f) {
                        dragAccum[0] = 0f
                    }
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        item {
            Column {
                Text(
                    text = "BOARD INFO",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Information space for ministry, announcements, and news of GRII Sidoarjo congregation.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                if (canPostPoster) {
                    FilledTonalButton(
                        onClick = onOpenPosterSubmission,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("+ Post Information", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                boardNotice?.let { notice ->
                    Spacer(Modifier.height(12.dp))
                    NoticeCard(
                        message = notice.message,
                        type = notice.type,
                        onDismiss = { boardNotice = null }
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Text(
                    text = "Loading information...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (errorMessage != null && filteredPosts.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRefresh) {
                        Text("Try Again")
                    }
                }
            }
        } else if (filteredPosts.isEmpty()) {
            item {
                Text(
                    text = "No information available in this category.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filteredPosts, key = { it.id }) { post ->
                PortalPostCard(
                    post = post,
                    onPrimaryClick = {
                        if (post.targetUrl.isNotBlank()) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(post.targetUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            onPostPreview(post)
                        }
                    },
                    onPreviewClick = { onPostPreview(post) },
                    canDelete = canPostPoster,
                    onDeleteClick = { deletePost = post }
                )
            }
        }
    }
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}

    deletePost?.let { post ->
        AlertDialog(
            onDismissRequest = { deletePost = null },
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
                        val activeSession = session
                        if (activeSession == null || !activeSession.status.equals("ACTIVE", ignoreCase = true)) {
                            boardNotice = UiNotice("Admin login required.", UiNoticeType.WARNING)
                            deletePost = null
                            return@TextButton
                        }
                        scope.launch {
                            repository.deletePoster(activeSession, post.id, post.posterUrl)
                                .onSuccess {
                                    boardNotice = UiNotice("Information deleted successfully.", UiNoticeType.SUCCESS)
                                    deletePost = null
                                    onRefresh()
                                }
                                .onFailure { error ->
                                    boardNotice = UiNotice(error.message ?: "Failed to delete information.", UiNoticeType.ERROR)
                                    deletePost = null
                                }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text("Yes", style = MaterialTheme.typography.bodyMedium) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletePost = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text("No", style = MaterialTheme.typography.bodyMedium) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortalPosterSubmissionScreen(
    modifier: Modifier = Modifier,
    repository: PortalRepository = remember { PortalRepository() },
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    onLoginClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val sessionStore = remember { PortalSessionStore(context) }
    val session by sessionStore.sessionFlow.collectAsState(initial = null)
    var resolvedSession by remember { mutableStateOf<PortalUserSession?>(null) }
    androidx.compose.runtime.LaunchedEffect(session?.username, session?.status) {
        if (session?.status.equals("ACTIVE", ignoreCase = true)) {
            resolvedSession = session
        } else if (session == null) {
            // keep the last active session latched while submit flow is open
        } else {
            resolvedSession = null
        }
    }
    val canPostPoster = resolvedSession?.status.equals("ACTIVE", ignoreCase = true)
    val categories = remember { repository.getSubmissionCategories() }
    val fieldErrors = remember { mutableStateMapOf<String, String>() }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var selectedPosterImage by remember { mutableStateOf<SelectedImagePayload?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitNotice by remember { mutableStateOf<UiNotice?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            readSelectedImage(context, uri)
                .onSuccess {
                    selectedPosterImage = it
                    fieldErrors.remove("posterImage")
                }
                .onFailure { error ->
                    submitNotice = UiNotice(error.message ?: "Failed to load image.", UiNoticeType.ERROR)
                }
        }
    }

    fun validate(): Boolean {
        fieldErrors.clear()
        if (selectedCategory.isBlank()) fieldErrors["category"] = "Category is required"
        if (selectedPosterImage == null) fieldErrors["posterImage"] = "Poster image is required"
        return fieldErrors.isEmpty()
    }

    fun clearForm() {
        selectedCategory = categories.firstOrNull().orEmpty()
        selectedPosterImage = null
        fieldErrors.clear()
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
            }
            item {
                Text(
                    text = "Post Information",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            submitNotice?.let { notice ->
                item {
                    NoticeCard(
                        message = notice.message,
                        type = notice.type,
                        onDismiss = { submitNotice = null }
                    )
                }
            }
            if (!canPostPoster || resolvedSession == null) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Login to GRII Sidoarjo first to post information.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onLoginClick,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Admin Login")
                        }
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = {
                                        selectedCategory = category
                                        fieldErrors.remove("category")
                                    },
                                    label = { Text(category) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                        fieldErrors["category"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Poster Image",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(if (selectedPosterImage == null) "Choose Image" else "Change Image")
                        }
                        selectedPosterImage?.let { poster ->
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Image(
                                    bitmap = poster.previewBitmap.asImageBitmap(),
                                    contentDescription = "Preview poster",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                Text(
                                    text = poster.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        fieldErrors["posterImage"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                item {
                    Button(
                        onClick = {
                            if (!validate()) {
                                submitNotice = UiNotice("Please choose a poster image first.", UiNoticeType.WARNING)
                                return@Button
                            }
                            isSubmitting = true
                            scope.launch {
                                val activeSession = resolvedSession
                                if (activeSession == null || !activeSession.status.equals("ACTIVE", ignoreCase = true)) {
                                    isSubmitting = false
                                    submitNotice = UiNotice("Login to GRII Sidoarjo first to post poster.", UiNoticeType.WARNING)
                                    return@launch
                                }
                                val result = repository.submitPoster(
                                    session = activeSession,
                                    category = selectedCategory,
                                    posterImageBase64 = requireNotNull(selectedPosterImage).base64,
                                    posterImageFileName = requireNotNull(selectedPosterImage).fileName,
                                    posterImageMimeType = requireNotNull(selectedPosterImage).mimeType
                                )
                                isSubmitting = false
                                result.onSuccess {
                                    submitNotice = UiNotice("Poster successfully posted on Board Info.", UiNoticeType.SUCCESS)
                                    clearForm()
                                    onSubmitted()
                                }.onFailure { error ->
                                    submitNotice = UiNotice(error.message ?: "Failed to submit poster.", UiNoticeType.ERROR)
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isSubmitting) {
                            Text("Submitting...")
                        } else {
                            Text("Submit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggedInPortalHeader(
    session: PortalUserSession
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Logged in as admin: ${session.username}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Admin: ${session.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PortalPostDetailScreen(
    post: PortalPost,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isFullscreenPreview by remember { mutableStateOf(false) }
    if (isFullscreenPreview) {
        FullscreenPosterViewer(
            imageUrl = post.posterUrl,
            title = post.title,
            onExit = { isFullscreenPreview = false }
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
        item {
            RemotePoster(
                imageUrl = post.posterUrl,
                title = post.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clickable { isFullscreenPreview = true }
            )
        }
        item {
            val displayTitle = post.displayTitle()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                if (!post.submitterName.equals(displayTitle, ignoreCase = true)) {
                    Text(
                        text = post.submitterName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = post.displayDate(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                AssistChip(
                    onClick = {},
                    label = { Text(post.category) },
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                        labelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                if (post.shortCaption.isNotBlank()) {
                    Text(
                        text = post.shortCaption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (post.eventDate.isNotBlank()) {
                    Text(
                        text = post.eventDate,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (post.targetUrl.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(post.targetUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    ) {
                        Text("Open Link")
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalPostCard(
    post: PortalPost,
    onPrimaryClick: () -> Unit,
    onPreviewClick: () -> Unit,
    canDelete: Boolean = false,
    onDeleteClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPrimaryClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RemotePoster(
                imageUrl = post.posterUrl,
                title = post.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = post.submitterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(post.category) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        border = null
                    )
                    if (post.eventDate.isNotBlank()) {
                        Text(
                            text = post.eventDate,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (post.shortCaption.isNotBlank()) {
                    Text(
                        text = post.shortCaption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onPreviewClick) {
                        Text("Preview")
                    }
                    if (post.targetUrl.isNotBlank()) {
                        TextButton(onClick = onPrimaryClick) {
                            Text("Open Link")
                        }
                    }
                    if (canDelete) {
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete information",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemotePoster(
    imageUrl: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedImageUrl = normalizeDriveImageUrl(imageUrl)
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(resolvedImageUrl)
            .crossfade(true)
            .build(),
        contentDescription = "$title information",
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Failed to load information",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun FullscreenPosterViewer(
    imageUrl: String,
    title: String,
    onExit: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        if (nextScale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(scale) {
                detectTapGestures(
                    onTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            onExit()
                        }
                    },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.4f
                        }
                    }
                )
            }
            .transformable(transformableState),
        contentAlignment = Alignment.Center
    ) {
        RemotePoster(
            imageUrl = imageUrl,
            title = title,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}

private fun PortalPost.displayDate(): String {
    val rawDate = eventDate.ifBlank { createdAt }.trim()
    if (rawDate.isBlank()) return "Posting date not available"
    return runCatching {
        val parsed = OffsetDateTime.parse(rawDate)
        "Posted on ${parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))}"
    }.getOrElse {
        "Posted on $rawDate"
    }
}

private fun PortalPost.displayTitle(): String {
    val raw = title.trim()
    val categoryPrefix = "${category.trim()} - "
    return if (category.isNotBlank() && raw.startsWith(categoryPrefix, ignoreCase = true)) {
        raw.removePrefix(categoryPrefix).trim().ifBlank { raw }
    } else {
        raw
    }
}

private fun normalizeDriveImageUrl(rawUrl: String): String {
    val url = rawUrl.trim()
    if (url.isBlank()) return url

    val fileIdByPath = Regex("/file/d/([^/]+)").find(url)?.groupValues?.get(1)
    if (!fileIdByPath.isNullOrBlank()) {
        return "https://drive.google.com/uc?export=view&id=$fileIdByPath"
    }

    val fileIdByQuery = Regex("[?&]id=([^&]+)").find(url)?.groupValues?.get(1)
    if (!fileIdByQuery.isNullOrBlank()) {
        return "https://drive.google.com/uc?export=view&id=$fileIdByQuery"
    }

    return url
}

private enum class UiNoticeType { SUCCESS, WARNING, ERROR, INFO }

private data class UiNotice(
    val message: String,
    val type: UiNoticeType
)

@Composable
private fun NoticeCard(
    message: String,
    type: UiNoticeType,
    onDismiss: () -> Unit
) {
    val (bg, fg, border) = when (type) {
        UiNoticeType.SUCCESS -> Triple(Color(0x1A2E7D32), Color(0xFFB9F6CA), Color(0x6632C45A))
        UiNoticeType.WARNING -> Triple(Color(0x1A7A5A00), Color(0xFFFFE082), Color(0x66FFC107))
        UiNoticeType.ERROR -> Triple(Color(0x1A8E2430), Color(0xFFFFCDD2), Color(0x66EF5350))
        UiNoticeType.INFO -> Triple(Color(0x1A0277BD), Color(0xFF81D4FA), Color(0x66039BE5))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(14.dp)),
        color = bg,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Close", color = fg)
            }
        }
    }
}



