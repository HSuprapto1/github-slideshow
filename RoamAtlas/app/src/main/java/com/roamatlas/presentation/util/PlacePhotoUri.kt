package com.roamatlas.presentation.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun createPlaceCameraPhotoUri(context: Context): Uri? {
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
