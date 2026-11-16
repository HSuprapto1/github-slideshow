package com.roamatlas.presentation.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Creates a destination URI for the system camera.
 * Prefers the phone Gallery (MediaStore Pictures/RoamAtlas) so originals stay in Gallery;
 * falls back to app-private storage if MediaStore insert is unavailable.
 */
fun createPlaceCameraPhotoUri(context: Context): Uri? {
    return createGalleryCameraPhotoUri(context) ?: createAppPrivateCameraPhotoUri(context)
}

fun createGalleryCameraPhotoUri(context: Context): Uri? {
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
        context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )
    }.getOrNull()
}

fun finalizeGalleryCameraPhoto(context: Context, uri: Uri) {
    if (uri.scheme != "content") return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }
}

fun createAppPrivateCameraPhotoUri(context: Context): Uri? {
    return runCatching {
        val photosDir = File(context.getExternalFilesDir(null), "place_photos").apply {
            if (!exists()) mkdirs()
        }
        val photoFile = File(photosDir, "RoamAtlas_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }.getOrNull()
}
