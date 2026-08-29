package com.yassmine.projetpfe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.yassmine.projetpfe.BuildConfig
import com.yassmine.projetpfe.ui.theme.ConveneAccent
import com.yassmine.projetpfe.ui.theme.ConvenePrimaryContainer

fun String.initials(): String =
    split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { take(2).uppercase() }

@Composable
fun UserAvatar(
    profilePicture: String?,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) { name.initials().ifBlank { "?" } }
    val resolvedImageUrl = remember(profilePicture) { buildAvatarUrl(profilePicture) }
    val hasImage = !resolvedImageUrl.isNullOrBlank()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (hasImage) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resolvedImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                    else -> InitialsCircle(initials = initials, size = size)
                }
            }
        } else {
            InitialsCircle(initials = initials, size = size)
        }
    }
}

@Composable
private fun InitialsCircle(initials: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(ConvenePrimaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.take(2).uppercase(),
            fontSize = (size.value * 0.33f).sp,
            fontWeight = FontWeight.SemiBold,
            color = ConveneAccent
        )
    }
}

private fun buildAvatarUrl(path: String?): String? {
    val safePath = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (safePath.startsWith("http")) return safePath

    val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
    return when {
        safePath.startsWith("/") -> {
            val host = baseUrl.removeSuffix("/api")
            "$host$safePath"
        }
        else -> "$baseUrl/$safePath"
    }
}
