package com.grii.sidoarjo.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.grii.sidoarjo.R
import com.grii.sidoarjo.data.model.Song
import kotlin.math.abs

private val ProfessionalRedBrushes = listOf(
    Brush.verticalGradient(listOf(Color(0xFF4C0F19), Color(0xFF33050C))),
    Brush.verticalGradient(listOf(Color(0xFF5D1220), Color(0xFF3B0B14))),
    Brush.verticalGradient(listOf(Color(0xFF6E1627), Color(0xFF4C0F19))),
    Brush.verticalGradient(listOf(Color(0xFF4C0F19), Color(0xFFC5A880))),
    Brush.verticalGradient(listOf(Color(0xFF2C2A29), Color(0xFF1F1D1D)))
)

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onArtistClick: ((String) -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongCover(song = song, modifier = Modifier.size(width = 92.dp, height = 124.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.year.isNotBlank()) {
                    Text(
                        text = song.year,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = song.synopsis.ifBlank { song.description }.ifBlank { "Tidak ada deskripsi tersedia." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(song.category.ifBlank { "Live" }, fontSize = 10.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    border = null,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

@Composable
fun MoviePosterCard(
    song: Song,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.shadow(4.dp, RoundedCornerShape(12.dp))) {
            SongCover(
                song = song,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (song.year.isNotBlank()) {
                Text(
                    text = song.year,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Text(
                text = song.synopsis.ifBlank { song.description }.ifBlank { "Tidak ada deskripsi tersedia." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SongCover(
    song: Song,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val shape = RoundedCornerShape(8.dp)
    val fallbackPainter = painterResource(id = R.drawable.splash)
    val safeCoverUrl = song.thumbnailUrl.ifBlank { song.coverUrl }
    val imageModelUrl = safeCoverUrl.toDisplayImageUrl()
    val isRemoteUrl = imageModelUrl.startsWith("http", ignoreCase = true)

    if (isRemoteUrl) {
        AsyncImage(
            model = imageModelUrl,
            contentDescription = "${song.title} cover",
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = contentScale,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            fallback = fallbackPainter
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(song.professionalBrush())
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = song.year.ifBlank { "LIVE" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun Song.professionalBrush(): Brush {
    val index = abs(id.hashCode()) % ProfessionalRedBrushes.size
    return ProfessionalRedBrushes[index]
}

private fun String.toDisplayImageUrl(): String {
    val rawUrl = trim()
    if (rawUrl.isBlank()) return ""
    val fileId = rawUrl.extractGoogleDriveFileId() ?: return rawUrl
    return "https://drive.google.com/thumbnail?id=$fileId&sz=w1200"
}

private fun String.extractGoogleDriveFileId(): String? {
    return runCatching {
        val uri = Uri.parse(this)
        val host = uri.host.orEmpty().lowercase()
        if (!host.contains("drive.google.com") && !host.contains("docs.google.com")) return@runCatching null
        uri.getQueryParameter("id")?.takeIf { it.isNotBlank() }?.let { return@runCatching it }
        val segments = uri.pathSegments
        val fileIndex = segments.indexOf("d")
        if (fileIndex >= 0 && fileIndex + 1 < segments.size) {
            return@runCatching segments[fileIndex + 1].takeIf { it.isNotBlank() }
        }
        null
    }.getOrNull()
}
