package com.autolog.carcare.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.autolog.carcare.data.ReceiptStorageManager
import com.autolog.carcare.data.local.CarExpense
import com.autolog.carcare.ui.components.toCurrency
import com.autolog.carcare.ui.components.toDateString
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceiptViewerDialog(
    expense: CarExpense,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var totalPages by remember { mutableIntStateOf(1) }
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var isLoadingInitial by remember { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val fileSizeStr = remember(expense.receiptFileName) {
        ReceiptStorageManager.getReceiptFileSizeFormatted(context, expense.receiptFileName)
    }

    val isPdfFile = remember(expense.receiptFileName) {
        ReceiptStorageManager.isPdf(expense.receiptFileName)
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { totalPages.coerceAtLeast(1) }
    )

    // Reset zoom & pan when switching pages
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    // Load page count and initial pages
    LaunchedEffect(expense.receiptFileName) {
        isLoadingInitial = true
        pageBitmaps.clear()
        val count = ReceiptStorageManager.getReceiptPageCount(context, expense.receiptFileName).coerceAtLeast(1)
        totalPages = count
        // Preload first page immediately so UI renders right away
        val firstBmp = ReceiptStorageManager.loadReceiptPageBitmap(
            context = context,
            fileName = expense.receiptFileName,
            pageIndex = 0,
            maxDimension = 1600
        )
        if (firstBmp != null) {
            pageBitmaps[0] = firstBmp
        }
        isLoadingInitial = false

        // Preload adjacent pages in background for smooth swiping
        for (idx in 1 until minOf(count, 8)) {
            val bmp = ReceiptStorageManager.loadReceiptPageBitmap(
                context = context,
                fileName = expense.receiptFileName,
                pageIndex = idx,
                maxDimension = 1600
            )
            if (bmp != null) {
                pageBitmaps[idx] = bmp
            }
        }
    }

    // Ensure current & neighbor pages are loaded when swiping through larger PDFs
    LaunchedEffect(pagerState.currentPage, totalPages) {
        val cur = pagerState.currentPage
        listOf(cur, cur - 1, cur + 1).forEach { pageIdx ->
            if (pageIdx in 0 until totalPages && !pageBitmaps.containsKey(pageIdx)) {
                val bmp = ReceiptStorageManager.loadReceiptPageBitmap(
                    context = context,
                    fileName = expense.receiptFileName,
                    pageIndex = pageIdx,
                    maxDimension = 1600
                )
                if (bmp != null) {
                    pageBitmaps[pageIdx] = bmp
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(660.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = expense.receiptOriginalName.ifBlank { "Attached Receipt / Invoice" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${expense.title} • ${expense.amount.toCurrency()} • ${expense.dateMillis.toDateString()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Zoom Controls & Metadata Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                append(if (isPdfFile) "PDF Document" else "Receipt Image")
                                if (totalPages > 1) append(" (${pagerState.currentPage + 1}/$totalPages)")
                                if (fileSizeStr.isNotBlank()) append(" • $fileSizeStr")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    scale = (scale - 0.35f).coerceAtLeast(1f)
                                    if (scale == 1f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                            }
                            TextButton(
                                onClick = {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            ) {
                                Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(
                                onClick = { scale = (scale + 0.35f).coerceAtMost(3.5f) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Multi-Page Swipeable PDF / Image Viewer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingInitial && pageBitmaps.isEmpty()) {
                        CircularProgressIndicator()
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = scale <= 1.02f,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            val bmp = pageBitmaps[pageIndex]
                            val isCurrentPage = pageIndex == pagerState.currentPage

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(isCurrentPage) {
                                        if (!isCurrentPage) return@pointerInput
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            do {
                                                val event = awaitPointerEvent()
                                                val pointerCount = event.changes.size
                                                if (pointerCount >= 2 || scale > 1.02f) {
                                                    val zoomChange = event.calculateZoom()
                                                    val panChange = event.calculatePan()
                                                    if (zoomChange != 1f || panChange.x != 0f || panChange.y != 0f) {
                                                        val newScale = (scale * zoomChange).coerceIn(1f, 3.5f)
                                                        scale = newScale
                                                        if (newScale > 1.02f) {
                                                            offsetX += panChange.x
                                                            offsetY += panChange.y
                                                        } else {
                                                            offsetX = 0f
                                                            offsetY = 0f
                                                        }
                                                        event.changes.forEach { change ->
                                                            if (change.positionChanged()) {
                                                                change.consume()
                                                            }
                                                        }
                                                    }
                                                }
                                            } while (event.changes.any { it.pressed })
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    bmp != null -> {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Receipt / Invoice Page ${pageIndex + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp)
                                                .graphicsLayer(
                                                    scaleX = if (isCurrentPage) scale else 1f,
                                                    scaleY = if (isCurrentPage) scale else 1f,
                                                    translationX = if (isCurrentPage) offsetX else 0f,
                                                    translationY = if (isCurrentPage) offsetY else 0f
                                                )
                                        )
                                    }
                                    else -> {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Multi-page navigation bar at bottom when PDF has > 1 page
                if (totalPages > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage > 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                },
                                enabled = pagerState.currentPage > 0,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Page"
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Page ${pagerState.currentPage + 1} of $totalPages",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Swipe left or right to turn pages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (totalPages <= 10) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (i in 0 until totalPages) {
                                            val isSelected = i == pagerState.currentPage
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isSelected) 8.dp else 6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                    )
                                                    .clickable {
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(i)
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage < totalPages - 1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                },
                                enabled = pagerState.currentPage < totalPages - 1,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Page"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
