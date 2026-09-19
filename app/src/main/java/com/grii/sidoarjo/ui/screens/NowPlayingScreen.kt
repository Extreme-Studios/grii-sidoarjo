package com.grii.sidoarjo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grii.sidoarjo.data.model.Playlist
import com.grii.sidoarjo.playback.AutoVolumeStrength
import com.grii.sidoarjo.playback.EqualizerPreset
import com.grii.sidoarjo.playback.PlaybackMode
import com.grii.sidoarjo.playback.PlaybackManager
import com.grii.sidoarjo.ui.components.SongCover
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playlists: List<Playlist>,
    modifier: Modifier = Modifier,
    onOpenLibrary: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAddSongToPlaylist: (playlistId: String, songId: String) -> Unit
) {
    val currentSong by PlaybackManager.currentSong.collectAsState()
    val isPlaying by PlaybackManager.isPlaying.collectAsState()
    val isBuffering by PlaybackManager.isBuffering.collectAsState()
    val positionMs by PlaybackManager.positionMs.collectAsState()
    val durationMs by PlaybackManager.durationMs.collectAsState()
    val mode by PlaybackManager.mode.collectAsState()
    val playerError by PlaybackManager.error.collectAsState()
    val hasPrevious by PlaybackManager.hasPrevious.collectAsState()
    val hasNext by PlaybackManager.hasNext.collectAsState()
    val equalizerSettings by PlaybackManager.equalizerSettings.collectAsState()
    val equalizerSupported by PlaybackManager.equalizerSupported.collectAsState()
    val autoVolumeSupported by PlaybackManager.autoVolumeSupported.collectAsState()
    val audioEffectMessage by PlaybackManager.audioEffectMessage.collectAsState()
    var equaliserExpanded by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var isTimelineDragging by remember { mutableStateOf(false) }
    var timelineDragMs by remember { mutableStateOf(0f) }

    val safeDurationMs = durationMs.coerceAtLeast(1L)
    val effectivePositionMs = if (isTimelineDragging) timelineDragMs.toLong() else positionMs
    val positionSeconds = (effectivePositionMs / 1000L).toInt()
    val durationSeconds = (durationMs / 1000L).toInt()

    LaunchedEffect(currentSong?.id) {
        isTimelineDragging = false
        timelineDragMs = 0f
    }

    LaunchedEffect(positionMs, durationMs, isTimelineDragging) {
        if (!isTimelineDragging) {
            timelineDragMs = positionMs.coerceIn(0L, safeDurationMs).toFloat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (currentSong == null) {
            EmptyNowPlayingState(onOpenLibrary = onOpenLibrary, modifier = Modifier.padding(paddingValues))
            return@Scaffold
        }

        val song = currentSong!!
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Box(modifier = Modifier.shadow(12.dp, RoundedCornerShape(24.dp))) {
                    SongCover(
                        song = song,
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )
                }
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.clickable { onArtistClick(song.artist) }
                    )
                    Spacer(Modifier.height(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(song.genre) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = timelineDragMs.coerceIn(0f, safeDurationMs.toFloat()),
                        onValueChange = { newValue ->
                            isTimelineDragging = true
                            timelineDragMs = newValue
                        },
                        onValueChangeFinished = {
                            PlaybackManager.seekTo(timelineDragMs.toLong())
                            isTimelineDragging = false
                        },
                        valueRange = 0f..safeDurationMs.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onPrimary,
                            activeTrackColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(positionSeconds), style = MaterialTheme.typography.labelMedium)
                        Text(formatDuration(durationSeconds), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { PlaybackManager.previous() }, enabled = hasPrevious) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_previous),
                            contentDescription = "Previous",
                            tint = if (hasPrevious) MaterialTheme.colorScheme.onPrimary else Color.LightGray
                        )
                    }
                    IconButton(
                        onClick = { PlaybackManager.togglePlayPause() },
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause
                                else android.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { PlaybackManager.next() }, enabled = hasNext) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_next),
                            contentDescription = "Next",
                            tint = if (hasNext) MaterialTheme.colorScheme.onPrimary else Color.LightGray
                        )
                    }
                }
            }

            item {
                RepeatModeRow(
                    currentMode = mode,
                    onModeSelected = { PlaybackManager.setMode(it) },
                    onPlaylistClick = { showPlaylistDialog = true }
                )
            }

            item {
                ElevatedCard(
                    onClick = { equaliserExpanded = !equaliserExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_sort_by_size),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Audio Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painter = painterResource(
                                if (equaliserExpanded) android.R.drawable.arrow_up_float
                                else android.R.drawable.arrow_down_float
                            ),
                            contentDescription = null
                        )
                    }
                    if (equaliserExpanded) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (equalizerSupported) {
                                EqualizerPresetRow(onPresetSelected = { PlaybackManager.applyPreset(it) })
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val levels = equalizerSettings.bandLevels()
                                    listOf("60Hz", "230Hz", "910Hz", "3kHz", "14kHz").forEachIndexed { i, label ->
                                        GraphicEqualizerBar(label, levels[i]) { PlaybackManager.updateEqualizerBand(i, it) }
                                    }
                                }
                            }
                            Divider()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Auto Volume", modifier = Modifier.weight(1f))
                                Switch(
                                    checked = equalizerSettings.autoVolumeEnabled,
                                    onCheckedChange = { PlaybackManager.setAutoVolumeEnabled(it) },
                                    enabled = autoVolumeSupported
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Save to Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playlists.isEmpty()) {
                        Text("No playlist available.")
                    } else {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    onAddSongToPlaylist(playlist.id, currentSong!!.id)
                                    showPlaylistDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, textAlign = androidx.compose.ui.text.style.TextAlign.Start, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPlaylistDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text("Close") }
            }
        )
    }
}

@Composable
private fun RepeatModeRow(
    currentMode: PlaybackMode,
    onModeSelected: (PlaybackMode) -> Unit,
    onPlaylistClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaybackMode.entries.forEach { mode ->
            val isSelected = currentMode == mode
            IconButton(
                onClick = { onModeSelected(mode) },
                modifier = Modifier
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
            ) {
                Icon(
                    painter = painterResource(PlaybackManager.repeatIconRes(mode)),
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
                )
            }
        }
        IconButton(onClick = onPlaylistClick) {
            Icon(imageVector = Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun EmptyNowPlayingState(onOpenLibrary: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(android.R.drawable.ic_media_play),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text("No video playing", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenLibrary,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary)
        ) { Text("Open Library") }
    }
}

@Composable
private fun EqualizerPresetRow(onPresetSelected: (EqualizerPreset) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(EqualizerPreset.entries.toList()) { preset ->
            SuggestionChip(
                onClick = { onPresetSelected(preset) },
                label = { Text(preset.label) }
            )
        }
    }
}

@Composable
private fun GraphicEqualizerBar(label: String, value: Float, onValueChange: (Float) -> Unit) {
    val min = -12f
    val max = 12f
    val range = max - min
    val activeColor = MaterialTheme.colorScheme.onPrimary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(value) {
                    detectDragGestures { change, _ ->
                        val normalized = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        onValueChange(min + (normalized * range))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val trackWidth = 4.dp.toPx()
                val normalized = ((value - min) / range).coerceIn(0f, 1f)
                val thumbY = (1f - normalized) * size.height
                
                drawRoundRect(
                    color = trackColor,
                    size = Size(trackWidth, size.height),
                    topLeft = Offset(centerX - trackWidth/2, 0f),
                    cornerRadius = CornerRadius(trackWidth)
                )
                drawRoundRect(
                    color = activeColor,
                    size = Size(trackWidth, size.height - thumbY),
                    topLeft = Offset(centerX - trackWidth/2, thumbY),
                    cornerRadius = CornerRadius(trackWidth)
                )
                drawCircle(color = activeColor, radius = 8.dp.toPx(), center = Offset(centerX, thumbY))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        Text("${value.toInt()}dB", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
