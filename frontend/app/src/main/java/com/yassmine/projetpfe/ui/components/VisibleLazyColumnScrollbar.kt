package com.yassmine.projetpfe.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.compose.material3.MaterialTheme

private data class ScrollbarMetrics(
    val progress: Float,
    val visibleFraction: Float,
)

@Composable
fun VisibleLazyColumnScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thickness: Dp = 7.dp,
    minThumbHeight: Dp = 28.dp,
    trackColor: Color = Color(0x1A000000),
    thumbColor: Color = MaterialTheme.colorScheme.primary,
) {
    val metrics by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo.size

            if (totalItems <= 0 || visibleItems <= 0) {
                return@derivedStateOf null
            }

            val canScroll =
                totalItems > visibleItems ||
                    listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 0

            if (!canScroll) {
                return@derivedStateOf null
            }

            val firstVisibleSize = layoutInfo.visibleItemsInfo
                .firstOrNull()
                ?.size
                ?.takeIf { it > 0 }
                ?: 1

            val scrollOffsetItems =
                listState.firstVisibleItemScrollOffset.toFloat() / firstVisibleSize.toFloat()
            val absolutePosition = listState.firstVisibleItemIndex.toFloat() + scrollOffsetItems
            val maxScrollableItems = max(totalItems - visibleItems, 1).toFloat()

            ScrollbarMetrics(
                progress = (absolutePosition / maxScrollableItems).coerceIn(0f, 1f),
                visibleFraction = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 1f),
            )
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (metrics != null) 1f else 0f,
        label = "lazy_scrollbar_alpha",
    )

    if (metrics == null || alpha <= 0f) return

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(thickness),
    ) {
        val currentMetrics = metrics ?: return@Canvas
        val trackWidth = size.width
        val trackHeight = size.height
        val thumbHeight = max(trackHeight * currentMetrics.visibleFraction, minThumbHeightPx)
        val thumbTop = (trackHeight - thumbHeight) * currentMetrics.progress
        val corner = CornerRadius(trackWidth / 2f, trackWidth / 2f)

        drawRoundRect(
            color = trackColor.copy(alpha = trackColor.alpha * alpha),
            topLeft = Offset(0f, 0f),
            size = Size(trackWidth, trackHeight),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = thumbColor.copy(alpha = thumbColor.alpha * alpha),
            topLeft = Offset(0f, thumbTop),
            size = Size(trackWidth, thumbHeight),
            cornerRadius = corner,
        )
    }
}
