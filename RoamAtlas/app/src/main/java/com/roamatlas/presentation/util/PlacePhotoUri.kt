package com.roamatlas.presentation.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Destination URI for the system camera.
 *
 * Always uses app FileProvider storage. Writing straight into a MediaStore
 * "pending" Gallery URI crashes the Camera app on some Samsung devices.
 * After a successful shot, call [publishCapturedPhotoToGallery] to copy into Gallery.
 */
fun createPlaceCameraPhotoUri(context: Context): Uri? {
    return createAppPrivateCameraPhotoUri(context)
}

fun createAppPrivateCameraPhotoUri(context: Context): Uri? {
    return runCatching {
        val photosDir = File(context.getExternalFilesDir(null), "place_photos").apply {
            if (!exists()) mkdirs()
        }
        val photoFile = File(photosDir, "RoamAtlas_${System.currentTimeMillis()}.jpg")
        if (!photoFile.exists()) {
            photoFile.createNewFile()
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }.getOrNull()
}

/**
 * Copies a successful camera capture into phone Gallery (`Pictures/RoamAtlas`)
 * and returns the Gallery content URI. Falls back to [captureUri] if copy fails.
 */
fun publishCapturedPhotoToGallery(context: Context, captureUri: Uri): Uri {
    return runCatching {
        val displayName = "RoamAtlas_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RoamAtlas")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val galleryUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return@runCatching captureUri

        resolver.openInputStream(captureUri)?.use { input ->
            resolver.openOutputStream(galleryUri)?.use { output ->
                input.copyTo(output)
            } ?: run {
                resolver.delete(galleryUri, null, null)
                return@runCatching captureUri
            }
        } ?: run {
            resolver.delete(galleryUri, null, null)
            return@runCatching captureUri
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(galleryUri, done, null, null)
        }
        galleryUri
    }.getOrDefault(captureUri)
}

/** @deprecated Use [publishCapturedPhotoToGallery] after FileProvider capture. */
fun finalizeGalleryCameraPhoto(context: Context, uri: Uri) {
    // No-op for legacy MediaStore capture URIs; kept so older call sites compile
    // until they switch to publishCapturedPhotoToGallery.
    if (uri.scheme != "content") return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }
}
