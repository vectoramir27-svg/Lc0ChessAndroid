package com.lc0.chess.overlay

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream

class ScreenCaptureManager {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 2400

    @SuppressLint("WrongConstant")
    fun start(projection: MediaProjection, width: Int, height: Int, density: Int) {
        this.mediaProjection = projection
        this.screenWidth = width
        this.screenHeight = height

        try {
            // Android 14+ строго требует регистрации Callback до вызова createVirtualDisplay
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stop()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "Lc0ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun captureCroppedBoard(cropRect: Rect): ByteArray? {
        val reader = imageReader ?: return null
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return null

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val safeLeft = cropRect.left.coerceIn(0, bitmap.width - 1)
            val safeTop = cropRect.top.coerceIn(0, bitmap.height - 1)
            val safeW = cropRect.width().coerceAtMost(bitmap.width - safeLeft)
            val safeH = cropRect.height().coerceAtMost(bitmap.height - safeTop)

            if (safeW <= 10 || safeH <= 10) return null

            val cropped = Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeW, safeH)

            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            return baos.toByteArray()
        } catch (e: Exception) {
            return null
        } finally {
            try {
                image.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun stop() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {}
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {}
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        mediaProjection = null
    }
}
