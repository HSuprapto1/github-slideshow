package com.roamatlas.presentation.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

private val photoMarkerCache = ConcurrentHashMap<String, BitmapDescriptor>()

@Composable
fun rememberPhotoMarkerIcon(
    photoUri: String?,
    size: Dp = 44.dp
): BitmapDescriptor {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    return remember(photoUri, sizePx) {
        photoUri
            ?.takeIf { it.isNotBlank() }
            ?.let { uri ->
                val cacheKey = "$uri@$sizePx"
                photoMarkerCache[cacheKey] ?: createPhotoMarkerDescriptor(context, uri, sizePx)
                    ?.also { photoMarkerCache[cacheKey] = it }
            }
            ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
    }
}

internal fun createPhotoMarkerDescriptor(
    context: Context,
    photoUri: String,
    sizePx: Int
): BitmapDescriptor? {
    val source = decodePhotoBitmap(context, photoUri, sizePx) ?: return null
    val pin = buildPhotoPinBitmap(source, sizePx)
    if (source !== pin) source.recycle()
    return BitmapDescriptorFactory.fromBitmap(pin)
}

private fun decodePhotoBitmap(context: Context, photoUri: String, sizePx: Int): Bitmap? {
    return try {
        when {
            photoUri.startsWith("file:") -> {
                val path = Uri.parse(photoUri).path ?: photoUri.removePrefix("file://")
                decodeSampledFile(File(path), sizePx)
            }
            photoUri.startsWith("/") -> decodeSampledFile(File(photoUri), sizePx)
            else -> {
                context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.let { full ->
                    val scaled = scaleCenterCrop(full, sizePx)
                    if (scaled !== full) full.recycle()
                    scaled
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun decodeSampledFile(file: File, sizePx: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sample = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, sizePx, sizePx)
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, sample) ?: return null
    val scaled = scaleCenterCrop(decoded, sizePx)
    if (scaled !== decoded) decoded.recycle()
    return scaled
}

private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
    var sample = 1
    if (height > reqH || width > reqW) {
        var halfH = height / 2
        var halfW = width / 2
        while (halfH / sample >= reqH && halfW / sample >= reqW) {
            sample *= 2
        }
    }
    return sample.coerceAtLeast(1)
}

private fun scaleCenterCrop(source: Bitmap, sizePx: Int): Bitmap {
    val minDim = min(source.width, source.height).coerceAtLeast(1)
    val left = (source.width - minDim) / 2
    val top = (source.height - minDim) / 2
    val square = Bitmap.createBitmap(source, left, top, minDim, minDim)
    if (square.width == sizePx && square.height == sizePx) return square
    val scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
    if (scaled !== square) square.recycle()
    return scaled
}

/**
 * Rounded photo square with a white border and a small pointer tip, suitable as a map pin.
 */
private fun buildPhotoPinBitmap(photo: Bitmap, photoSizePx: Int): Bitmap {
    val border = (photoSizePx * 0.08f).toInt().coerceAtLeast(2)
    val tipHeight = (photoSizePx * 0.28f).toInt().coerceAtLeast(8)
    val width = photoSizePx + border * 2
    val height = width + tipHeight
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55000000
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val body = RectF(0f, 0f, width.toFloat(), width.toFloat())
    val radius = width * 0.22f

    // Soft shadow under body + tip
    canvas.drawRoundRect(body, radius, radius, shadowPaint)
    val tip = Path().apply {
        moveTo(width * 0.28f, width * 0.82f)
        lineTo(width * 0.5f, height.toFloat())
        lineTo(width * 0.72f, width * 0.82f)
        close()
    }
    canvas.drawPath(tip, shadowPaint)
    canvas.drawPath(tip, borderPaint)
    canvas.drawRoundRect(body, radius, radius, borderPaint)

    val photoRect = RectF(
        border.toFloat(),
        border.toFloat(),
        (width - border).toFloat(),
        (width - border).toFloat()
    )
    val clip = Path().apply {
        addRoundRect(photoRect, radius * 0.75f, radius * 0.75f, Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(clip)
    canvas.drawBitmap(photo, null, photoRect, photoPaint)
    canvas.restore()

    return output
}
