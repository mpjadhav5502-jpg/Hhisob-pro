package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

object RegisterPhotoManager {
    private const val PHOTOS_DIR = "register_photos"
    private const val CAMERA_CACHE_DIR = "camera_captures"

    private val allowedMimeTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png"
    )
    private val allowedExtensions = setOf("jpg", "jpeg", "png")

    fun getPhotosDir(context: Context): File {
        return File(context.filesDir, PHOTOS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    fun createCameraCaptureUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, CAMERA_CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
        val file = File(dir, "cam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    fun getShareablePhotoUri(context: Context, photoPath: String): Uri? {
        val file = File(photoPath)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun saveOriginalPhotoFromUri(
        context: Context,
        uri: Uri,
        recordUuid: String
    ): Result<String> {
        return try {
            val resolver = context.contentResolver
            val rawMime = resolver.getType(uri)?.lowercase(Locale.US)
            val extFromMime = when (rawMime) {
                "image/png" -> "png"
                "image/jpeg", "image/jpg" -> "jpg"
                else -> null
            }
            val extFromPath = uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.US)
                ?.takeIf { it in allowedExtensions }
            val extFromMap = rawMime?.let {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it)?.lowercase(Locale.US)
            }?.takeIf { it in allowedExtensions }

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("फोटो उघडता आला नाही")
            require(bytes.isNotEmpty()) { "निवडलेली फोटो फाईल रिकामी आहे" }

            val isPngHeader = bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() &&
                bytes[4] == 0x0D.toByte() &&
                bytes[5] == 0x0A.toByte() &&
                bytes[6] == 0x1A.toByte() &&
                bytes[7] == 0x0A.toByte()
            val isJpegHeader = bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte()

            if (!isPngHeader && !isJpegHeader) {
                throw IllegalArgumentException("फक्त JPG, JPEG किंवा PNG फोटोच वापरता येतात")
            }

            // Verify image bounds & MIME if decoded by platform
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val decodedMime = options.outMimeType?.lowercase(Locale.US)
            if (decodedMime != null && decodedMime !in allowedMimeTypes) {
                throw IllegalArgumentException("फक्त JPG, JPEG किंवा PNG फोटोच वापरता येतात (मिळालेला प्रकार: $decodedMime)")
            }
            if (rawMime != null && rawMime !in allowedMimeTypes && decodedMime == null) {
                throw IllegalArgumentException("फक्त JPG, JPEG किंवा PNG फोटोच निवडा")
            }
            require(options.outWidth > 0 && options.outHeight > 0) {
                "ही वैध फोटो फाईल नाही (फक्त JPG, JPEG, PNG वापरा)"
            }

            val finalExt = when {
                isPngHeader -> "png"
                isJpegHeader -> "jpg"
                decodedMime == "image/png" -> "png"
                decodedMime == "image/jpeg" || decodedMime == "image/jpg" -> "jpg"
                extFromMime != null -> extFromMime
                extFromPath != null -> if (extFromPath == "jpeg") "jpg" else extFromPath
                extFromMap != null -> if (extFromMap == "jpeg") "jpg" else extFromMap
                else -> "jpg"
            }

            val outFile = File(getPhotosDir(context), "orig_${recordUuid}.$finalExt")
            FileOutputStream(outFile).use { it.write(bytes) }
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveOriginalPhotoFromFile(
        context: Context,
        sourceFile: File,
        recordUuid: String
    ): Result<String> {
        return try {
            require(sourceFile.exists() && sourceFile.length() > 0L) {
                "कॅमेरा फोटो जतन झाला नाही"
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            require(options.outWidth > 0 && options.outHeight > 0) {
                "कॅमेरा फोटो वैध नाही"
            }
            val outFile = File(getPhotosDir(context), "orig_${recordUuid}.jpg")
            sourceFile.copyTo(outFile, overwrite = true)
            runCatching { sourceFile.delete() }
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loadBitmapWithExif(filePath: String, maxDimension: Int = 2048): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var inSampleSize = 1
        while ((bounds.outWidth / inSampleSize) > maxDimension ||
            (bounds.outHeight / inSampleSize) > maxDimension
        ) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(filePath, decodeOpts) ?: return null

        val exifOrientation = runCatching {
            ExifInterface(filePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return raw

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    fun applyCropAndRotation(
        context: Context,
        sourcePath: String,
        rotationDegrees: Int,
        cropLeftNorm: Float,
        cropTopNorm: Float,
        cropRightNorm: Float,
        cropBottomNorm: Float,
        recordUuid: String
    ): Result<String> {
        return try {
            val base = loadBitmapWithExif(sourcePath, maxDimension = 2048)
                ?: throw IllegalStateException("फोटो लोड करता आला नाही")

            val normalizedRot = ((rotationDegrees % 360) + 360) % 360
            val rotated = if (normalizedRot != 0) {
                val matrix = Matrix().apply { postRotate(normalizedRot.toFloat()) }
                Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true).also {
                    if (it !== base) base.recycle()
                }
            } else {
                base
            }

            val left = (cropLeftNorm.coerceIn(0f, 0.9f) * rotated.width).toInt()
            val top = (cropTopNorm.coerceIn(0f, 0.9f) * rotated.height).toInt()
            val right = (cropRightNorm.coerceIn(0.1f, 1f) * rotated.width).toInt()
            val bottom = (cropBottomNorm.coerceIn(0.1f, 1f) * rotated.height).toInt()

            val cropW = (right - left).coerceAtLeast(32).coerceAtMost(rotated.width - left)
            val cropH = (bottom - top).coerceAtLeast(32).coerceAtMost(rotated.height - top)

            val cropped = if (left == 0 && top == 0 && cropW == rotated.width && cropH == rotated.height) {
                rotated
            } else {
                Bitmap.createBitmap(rotated, left, top, cropW, cropH).also {
                    if (it !== rotated) rotated.recycle()
                }
            }

            val outFile = File(
                getPhotosDir(context),
                "scan_${recordUuid}_${System.currentTimeMillis()}.jpg"
            )
            FileOutputStream(outFile).use { stream ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            }
            cropped.recycle()
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun prepareBitmapForOcr(filePath: String, maxDimension: Int = 1600): Bitmap? {
        val bmp = loadBitmapWithExif(filePath, maxDimension = maxDimension) ?: return null
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide <= maxDimension) return bmp
        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (bmp.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    fun deletePhotoFiles(paths: List<String>) {
        paths.forEach { path ->
            runCatching {
                val file = File(path)
                if (file.exists() && file.parentFile?.name == PHOTOS_DIR) {
                    file.delete()
                }
            }
        }
    }
}
