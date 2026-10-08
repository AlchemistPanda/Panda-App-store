package com.pandagallery.app.ui.cover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pandagallery.app.data.local.entity.MediaEntity

/**
 * High-contrast, streamlined media viewer designed specifically for foldable cover screens (e.g. Galaxy Z Flip/Fold).
 */
@Composable
fun CoverViewerScreen(
    mediaList: List<MediaEntity>,
    initialIndex: Int = 0,
    onClose: () -> Unit,
    onToggleFavorite: (MediaEntity) -> Unit = {},
    onCoverActiveChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onCoverActiveChanged?.invoke(true)
        onDispose {
            onCoverActiveChanged?.invoke(false)
        }
    }

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))) }
    val currentItem = mediaList.getOrNull(currentIndex)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (currentItem != null) {
            AsyncImage(
                model = currentItem.uri,
                contentDescription = currentItem.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom navigation arrows for easy thumb tapping on small cover screens
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentIndex > 0) currentIndex--
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (currentIndex > 0) Color(0x88000000) else Color(0x33000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        tint = if (currentIndex > 0) Color.White else Color.Gray
                    )
                }

                Text(
                    text = "${currentIndex + 1} / ${mediaList.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )

                IconButton(
                    onClick = {
                        if (currentIndex < mediaList.size - 1) currentIndex++
                    },
                    enabled = currentIndex < mediaList.size - 1,
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (currentIndex < mediaList.size - 1) Color(0x88000000) else Color(0x33000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = if (currentIndex < mediaList.size - 1) Color.White else Color.Gray
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Media Available", color = Color.White)
            }
        }

        // Top bar with close and favorite (always visible so user is never trapped)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0x66000000), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            if (currentItem != null) {
                IconButton(
                    onClick = { onToggleFavorite(currentItem) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0x66000000), CircleShape)
                ) {
                    Icon(
                        imageVector = if (currentItem.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (currentItem.isFavorite) Color.Red else Color.White
                    )
                }
            }
        }
    }
}
