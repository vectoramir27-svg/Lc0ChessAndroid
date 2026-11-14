package com.lc0.chess.overlay

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MoveResult(
    val from: String,
    val to: String,
    val san: String,
    val eval: String,
    val speech: String? = null
)

sealed class ApiResult {
    data class Success(val move: MoveResult) : ApiResult()
    data class Error(val message: String) : ApiResult()
}

object ChessApiClient {

    private fun getClient(context: Context? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        // Обход VPN: принудительно направляем запросы напрямую через Wi-Fi чип (wlan0)
        // даже если на телефоне активен VPN для Chess.com
        if (context != null) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val wifiNetwork = cm?.allNetworks?.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (wifiNetwork != null) {
                    builder.socketFactory(wifiNetwork.socketFactory)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return builder.build()
    }

    private fun formatUrl(serverUrl: String, path: String): String {
        val clean = serverUrl.trim()
        val base = when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean.trimEnd('/')
            else -> "http://${clean.trimEnd('/')}"
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$base$cleanPath"
    }

    suspend fun testConnection(context: Context? = null, serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = formatUrl(serverUrl, "/mode")
            val request = Request.Builder().url(url).build()
            getClient(context).newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getMoveByFen(context: Context? = null, serverUrl: String, fen: String): MoveResult? = withContext(Dispatchers.IO) {
        try {
            val encodedFen = URLEncoder.encode(fen, "UTF-8")
            val url = formatUrl(serverUrl, "/move?fen=$encodedFen")
            val request = Request.Builder().url(url).build()

            getClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                if (json.has("from") && json.has("to")) {
                    return@withContext MoveResult(
                        from = json.getString("from"),
                        to = json.getString("to"),
                        san = json.optString("san", ""),
                        eval = json.optString("eval", "0.00"),
                        speech = json.optString("speech", null)
                    )
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun analyzeSnapshot(
        context: Context? = null,
        serverUrl: String,
        jpegBytes: ByteArray,
        color: String = "w"
    ): ApiResult = withContext(Dispatchers.IO) {
        try {
            val url = formatUrl(serverUrl, "/analyze_image?color=$color")
            val mediaType = "image/jpeg".toMediaType()
            val requestBody = jpegBytes.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            getClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Error("HTTP ${response.code}")
                }
                val body = response.body?.string() ?: return@withContext ApiResult.Error("Пустой ответ от сервера")
                val json = JSONObject(body)

                if (json.has("error")) {
                    return@withContext ApiResult.Error(json.getString("error"))
                }

                if (json.has("from") && json.has("to")) {
                    val move = MoveResult(
                        from = json.getString("from"),
                        to = json.getString("to"),
                        san = json.optString("san", ""),
                        eval = json.optString("eval", "0.00"),
                        speech = json.optString("speech", null)
                    )
                    return@withContext ApiResult.Success(move)
                }
                ApiResult.Error("Ход не найден")
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            ApiResult.Error(msg)
        }
    }
}
