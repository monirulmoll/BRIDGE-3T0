package com.example.bridge

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TermuxBridgeClient(
    private val baseUrl: String = "http://127.0.0.1:8765"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("command", command)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/run")
                .post(payload.toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            val bodyString = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val output = parseOutput(bodyString)
                Log.d(TAG, "Bridge /run success [HTTP $code]: output len=${output.length}")
                Result.success(output)
            } else {
                Log.w(TAG, "Bridge /run HTTP error $code: $bodyString")
                Result.failure(Exception("Bridge returned HTTP $code: $bodyString"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed calling bridge /run: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun pingBridge(): Boolean = withContext(Dispatchers.IO) {
        try {
            val pingClient = client.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .build()

            pingClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful || response.code == 404 || response.code == 405
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseOutput(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                when {
                    json.has("output") -> return json.getString("output")
                    json.has("result") -> return json.getString("result")
                    json.has("stdout") -> return json.getString("stdout")
                    json.has("data") -> return json.getString("data")
                    json.has("response") -> return json.getString("response")
                }
            } catch (_: Exception) {}
        }
        return rawBody
    }

    companion object {
        private const val TAG = "TermuxBridgeClient"
    }
}
