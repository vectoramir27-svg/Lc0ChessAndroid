package com.lc0.chess.overlay

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

class OverlayService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_SERVER_URL = "extra_server_url"
        const val CHANNEL_ID = "lc0_overlay_channel"
        const val NOTIFICATION_ID = 101
    }

    private lateinit var windowManager: WindowManager
    private var canvasView: OverlayCanvasView? = null
    private var controlsView: View? = null

    private val captureManager = ScreenCaptureManager()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var serverUrl: String = "http://192.168.1.109:8765"
    private var tts: TextToSpeech? = null
    private var isPlayingWhite: Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lc0 Chess Assistant Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Активный оверлей поверх Chess.com"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lc0 Chess Assistant активен")
            .setContentText("Стрелки отображаются поверх Chess.com")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL) ?: "http://192.168.1.108:8765"

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection: MediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            val metrics = resources.displayMetrics
            captureManager.start(projection, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        }

        setupOverlayViews()
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayViews() {
        if (canvasView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Прозрачный полноэкранный холст со стрелками (клики проходят насквозь в Chess.com!)
        val canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        canvasView = OverlayCanvasView(this)
        windowManager.addView(canvasView, canvasParams)

        // 2. Плавающий компактный виджет с кнопками
        val controlsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }

        val inflater = LayoutInflater.from(this)
        controlsView = inflater.inflate(R.layout.overlay_controls, null)

        val tvMove = controlsView?.findViewById<TextView>(R.id.tvCurrentMove)
        val btnColorToggle = controlsView?.findViewById<TextView>(R.id.btnColorToggle)
        val btnScan = controlsView?.findViewById<Button>(R.id.btnQuickScan)
        val btnClose = controlsView?.findViewById<TextView>(R.id.btnCloseOverlay)
        val dragHandle = controlsView?.findViewById<View>(R.id.tvDragHandle)

        btnColorToggle?.setOnClickListener {
            isPlayingWhite = !isPlayingWhite
            btnColorToggle.text = if (isPlayingWhite) "⚪" else "⚫"
            canvasView?.clearArrow()
            Toast.makeText(this, if (isPlayingWhite) "Белые фигуры" else "Черные фигуры", Toast.LENGTH_SHORT).show()
        }

        btnScan?.setOnClickListener {
            performBoardScan()
        }

        btnClose?.setOnClickListener {
            stopSelf()
        }

        // Перетаскивание виджета пальцем по экрану
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = controlsParams.x
                    initialY = controlsParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    controlsParams.x = initialX + (event.rawX - touchX).toInt()
                    controlsParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(controlsView, controlsParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(controlsView, controlsParams)
    }

    private fun performBoardScan() {
        val tvMove = controlsView?.findViewById<TextView>(R.id.tvCurrentMove)
        tvMove?.text = "Анализ..."

        scope.launch {
            val metrics = resources.displayMetrics
            val size = metrics.widthPixels
            val top = (metrics.heightPixels - size) / 2
            val boardCrop = Rect(0, top, size, top + size)

            // Передаем координаты доски холсту для стрелок
            canvasView?.boardRect = RectF(0f, top.toFloat(), size.toFloat(), (top + size).toFloat())

            val bytes = withContext(Dispatchers.Default) {
                captureManager.captureCroppedBoard(boardCrop)
            }

            if (bytes == null) {
                // Если захват кадра пустой, отправляем тестовый запрос
                tvMove?.text = "Кадр пуст"
                return@launch
            }

            val colorCode = if (isPlayingWhite) "w" else "b"
            val result = ChessApiClient.analyzeSnapshot(serverUrl, bytes, colorCode)
            if (result != null) {
                tvMove?.text = "${result.from}➔${result.to} (${result.eval})"
                canvasView?.setMoveArrow(result.from, result.to, !isPlayingWhite)

                result.speech?.let { text ->
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "move_speech")
                }
            } else {
                tvMove?.text = "Ошибка связи"
                Toast.makeText(this@OverlayService, "Проверьте запуск сервера на ПК!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru", "RU")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        captureManager.stop()
        tts?.stop()
        tts?.shutdown()

        canvasView?.let { windowManager.removeView(it) }
        controlsView?.let { windowManager.removeView(it) }

        super.onDestroy()
    }
}
