package com.roamatlas.presentation.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Simple in-app multi-page PDF preview using Android PdfRenderer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPreviewDialog(
    uriString: String,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(uriString) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            val uri = Uri.parse(uriString)
            pfd = when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: error("Missing PDF path"))
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                "content" -> context.contentResolver.openFileDescriptor(uri, "r")
                else -> {
                    val file = File(uriString.removePrefix("file://"))
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } ?: error("Could not open PDF")
            renderer = PdfRenderer(pfd)
            val bitmaps = buildList {
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val width = (page.width * 2).coerceAtLeast(1)
                        val height = (page.height * 2).coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        add(bitmap)
                    }
                }
            }
            pages = bitmaps
            loading = false
        } catch (t: Throwable) {
            error = t.message ?: "Could not preview PDF"
            loading = false
        }
        onDispose {
            renderer?.close()
            pfd?.close()
            pages.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            error != null || pages.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        error ?: "No pages to show",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(onClick = onOpenExternal) {
                        Text("Open with another app")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { pages.size })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Image(
                        bitmap = pages[page].asImageBitmap(),
                        contentDescription = "PDF page ${page + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${pages.size}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenExternal) {
                        Text("External")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
