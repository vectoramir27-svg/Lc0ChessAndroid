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

object ChessApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = if (!serverUrl.startsWith("http://")) "http://$serverUrl" else serverUrl
            val request = Request.Builder().url("$url/mode").build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getMoveByFen(serverUrl: String, fen: String): MoveResult? = withContext(Dispatchers.IO) {
        try {
            val base = if (!serverUrl.startsWith("http://")) "http://$serverUrl" else serverUrl
            val encodedFen = URLEncoder.encode(fen, "UTF-8")
            val request = Request.Builder().url("$base/move?fen=$encodedFen").build()

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

    suspend fun analyzeSnapshot(serverUrl: String, jpegBytes: ByteArray, color: String = "w"): MoveResult? = withContext(Dispatchers.IO) {
        try {
            val base = if (!serverUrl.startsWith("http://")) "http://$serverUrl" else serverUrl
            val mediaType = "image/jpeg".toMediaType()
            val requestBody = jpegBytes.toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$base/analyze_image?color=$color")
                .post(requestBody)
                .build()

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
}
