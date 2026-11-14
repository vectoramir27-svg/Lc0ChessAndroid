package com.lc0.chess.overlay

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

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun formatUrl(serverUrl: String, path: String): String {
        val clean = serverUrl.trim()
        val base = when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean.trimEnd('/')
            else -> "http://${clean.trimEnd('/')}"
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$base$cleanPath"
    }

    suspend fun testConnection(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = formatUrl(serverUrl, "/mode")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getMoveByFen(serverUrl: String, fen: String): MoveResult? = withContext(Dispatchers.IO) {
        try {
            val encodedFen = URLEncoder.encode(fen, "UTF-8")
            val url = formatUrl(serverUrl, "/move?fen=$encodedFen")
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Error("HTTP ${response.code}")
                }
                val body = response.body?.string() ?: return@withContext ApiResult.Error("Пустой ответ")
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
