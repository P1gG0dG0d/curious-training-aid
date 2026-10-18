package com.haoqi.training.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** 把用户选的照片压到适合发给视觉模型的尺寸，省流量也省 token；同时按 EXIF 摆正方向。 */
object ImageCompressor {

    fun loadJpegBytes(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1024,
        quality: Int = 80,
    ): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val rotated = applyExifRotation(resolver.openInputStream(uri), bitmap)
        val scaled = scaleDown(rotated, maxDimension)
        if (scaled !== rotated) rotated.recycle()
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            scaled.recycle()
            out.toByteArray()
        }
    }

    /** 相机拍的照片常常是「像素横着 + EXIF 标记旋转」，不摆正的话模型看到的是歪的。 */
    private fun applyExifRotation(stream: InputStream?, bitmap: Bitmap): Bitmap {
        if (stream == null) return bitmap
        val orientation = try {
            stream.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (e: Exception) {
            return bitmap
        }
        // framework 的 ORIENTATION_* 常量在不同版本中取值一致
        val degrees = when (orientation) {
            6 -> 90f // ORIENTATION_ROTATE_90
            3 -> 180f // ORIENTATION_ROTATE_180
            8 -> 270f // ORIENTATION_ROTATE_270
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    fun mimeTypeOf(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri) ?: "image/jpeg"

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxDimension) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
