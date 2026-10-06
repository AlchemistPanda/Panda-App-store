package com.pandaapps.appstore.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.pandaapps.appstore.appContainer
import com.pandaapps.appstore.data.StoreApp
import com.pandaapps.appstore.ui.theme.PandaGreen
import com.pandaapps.appstore.ui.theme.PandaGreenDark
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [AppIcon] for a catalog app. */
@Composable
fun AppIcon(app: StoreApp, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    AppIcon(
        packageName = app.packageName,
        name = app.name,
        iconUrl = app.app.iconUrl,
        modifier = modifier,
        size = size,
        installed = app.installed != null,
    )
}

/**
 * Rounded app icon. Order of preference: the catalog [iconUrl] (Coil, crossfaded), the installed
 * app's own icon from PackageManager (when [installed]), then a letter avatar.
 */
@Composable
fun AppIcon(
    packageName: String,
    name: String,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    installed: Boolean = true,
) {
    val shape = RoundedCornerShape(size * 0.26f)
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)

    val painter = rememberAsyncImagePainter(
        model = remember(iconUrl, sizePx) {
            iconUrl?.let { ImageRequest.Builder(context).data(it).size(sizePx * 2).build() }
        },
    )
    val painterState by painter.state.collectAsState()
    val remoteLoaded = iconUrl != null && painterState is AsyncImagePainter.State.Success

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = remoteLoaded, label = "appIcon") { loaded ->
            if (!loaded) {
                FallbackIcon(packageName = packageName, name = name, installed = installed, size = size, sizePx = sizePx)
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        if (iconUrl != null) {
            Image(
                painter = painter,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FallbackIcon(packageName: String, name: String, installed: Boolean, size: Dp, sizePx: Int) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    var systemIcon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName, installed, sizePx) {
        if (!installed || preview) {
            systemIcon = null
            return@LaunchedEffect
        }
        systemIcon = withContext(Dispatchers.IO) {
            runCatching {
                context.appContainer.installedAppsRepository.appIcon(packageName)
                    ?.toBitmap(sizePx, sizePx)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val icon = systemIcon
    if (icon != null) {
        Image(bitmap = icon, contentDescription = name, modifier = Modifier.fillMaxSize())
    } else {
        LetterAvatar(name = name, size = size)
    }
}

/** Gradient square with the app's initial; hue is stable per name so apps stay recognisable. */
@Composable
fun LetterAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val tint = remember(name) { AvatarTints[name.hashCode().absoluteValue % AvatarTints.size] }
    Box(
        modifier = modifier
            .size(size)
            .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.65f).compositeOverDark()))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).roundToInt().sp,
        )
    }
}

private fun Color.compositeOverDark(): Color = Color(
    red = red * alpha + 0.07f * (1 - alpha),
    green = green * alpha + 0.07f * (1 - alpha),
    blue = blue * alpha + 0.07f * (1 - alpha),
)

private val AvatarTints = listOf(
    PandaGreen,
    PandaGreenDark,
    Color(0xFF26A69A),
    Color(0xFF42A5F5),
    Color(0xFF7E57C2),
    Color(0xFFEF6C00),
    Color(0xFF8D6E63),
)
