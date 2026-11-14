package com.lc0.chess.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var etServerIp: EditText
    private lateinit var btnTestConnection: Button
    private lateinit var tvServerStatus: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var tvOverlayGranted: TextView
    private lateinit var btnStartOverlay: Button

    private val prefs by lazy { getSharedPreferences("lc0_chess_settings", Context.MODE_PRIVATE) }

    private var screenCaptureResultCode: Int = Activity.RESULT_CANCELED
    private var screenCaptureResultData: Intent? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            screenCaptureResultCode = result.resultCode
            screenCaptureResultData = result.data
            startOverlayService()
        } else {
            Toast.makeText(this, "Разрешение на захват экрана отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServerIp = findViewById(R.id.etServerIp)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        tvServerStatus = findViewById(R.id.tvServerStatus)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        tvOverlayGranted = findViewById(R.id.tvOverlayGranted)
        btnStartOverlay = findViewById(R.id.btnStartOverlay)

        // Автоматически загружаем последний сохраненный IP или по умолчанию 192.168.1.109:8765
        val savedIp = prefs.getString("server_ip", "192.168.1.109:8765") ?: "192.168.1.109:8765"
        etServerIp.setText(savedIp)

        // Автоматически сохраняем при любом изменении пользователем
        etServerIp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                s?.toString()?.trim()?.let {
                    if (it.isNotEmpty()) {
                        prefs.edit().putString("server_ip", it).apply()
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        updatePermissionsUI()

        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        btnTestConnection.setOnClickListener {
            val host = etServerIp.text.toString().trim()
            prefs.edit().putString("server_ip", host).apply()

            tvServerStatus.text = "● Проверка подключения..."
            tvServerStatus.setTextColor(Color.parseColor("#8C96A8"))

            lifecycleScope.launch {
                val ok = ChessApiClient.testConnection(host)
                if (ok) {
                    tvServerStatus.text = "● Связь с RTX 5060 установлена!"
                    tvServerStatus.setTextColor(Color.parseColor("#00FF88"))
                } else {
                    tvServerStatus.text = "● Нет связи с ПК. Проверьте Wi-Fi и выключите VPN."
                    tvServerStatus.setTextColor(Color.parseColor("#FF4455"))
                }
            }
        }

        btnStartOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Сначала разрешите показ поверх других окон!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val host = etServerIp.text.toString().trim()
            prefs.edit().putString("server_ip", host).apply()

            // Запрашиваем захват экрана перед стартом оверлея
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsUI()
    }

    private fun updatePermissionsUI() {
        val hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (hasOverlay) {
            tvOverlayGranted.text = "● Разрешение предоставлено"
            tvOverlayGranted.setTextColor(Color.parseColor("#00FF88"))
            btnGrantOverlay.isEnabled = false
            btnGrantOverlay.alpha = 0.5f
        } else {
            tvOverlayGranted.text = "● Требуется предоставить"
            tvOverlayGranted.setTextColor(Color.parseColor("#FF4455"))
            btnGrantOverlay.isEnabled = true
            btnGrantOverlay.alpha = 1.0f
        }
    }

    private fun startOverlayService() {
        val host = etServerIp.text.toString().trim()
        prefs.edit().putString("server_ip", host).apply()

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_SERVER_URL, host)
            putExtra(OverlayService.EXTRA_RESULT_CODE, screenCaptureResultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, screenCaptureResultData)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Оверлей запущен! Откройте Chess.com", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка запуска: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
