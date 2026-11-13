package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class OfficialOblastStatus(
    val oblastName: String,
    val isAlertActive: Boolean,
    val changedAtMillis: Long
)

data class RemoteSyncMessage(
    val remoteId: String,
    val timeSeconds: Long,
    val envelope: JSONObject
)

class OnlineAlertSyncClient(
    private val context: Context
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val UBILLING_ALERTS_URL = "https://ubilling.net.ua/aerialalerts/"
        private const val NTFY_BASE_URL = "https://ntfy.sh"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fetches real-time official air raid alert states across Ukraine from Ubilling Aerial Alerts API
     * (no API token required).
     */
    fun fetchUkraineOfficialAlerts(): Result<Map<String, OfficialOblastStatus>> {
        return try {
            val request = Request.Builder()
                .url(UBILLING_ALERTS_URL)
                .header("User-Agent", "OmastroTrivoga-Android/1.0")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return Result.failure(IllegalStateException("Empty response"))
                }

                val root = JSONObject(body)
                val statesObj = root.optJSONObject("states")
                    ?: return Result.failure(IllegalStateException("Missing states object"))

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Europe/Kyiv")
                }

                val result = mutableMapOf<String, OfficialOblastStatus>()
                val keys = statesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = statesObj.optJSONObject(key) ?: continue
                    val alertNow = item.optBoolean("alertnow", false)
                    val changedStr = item.optString("changed", "")
                    val changedMillis = try {
                        if (changedStr.isNotBlank()) {
                            dateFormat.parse(changedStr)?.time ?: System.currentTimeMillis()
                        } else {
                            System.currentTimeMillis()
                        }
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                    result[key] = OfficialOblastStatus(
                        oblastName = key,
                        isAlertActive = alertNow,
                        changedAtMillis = changedMillis
                    )
                }
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Publishes an admin alert broadcast or discussion comment to the live online ntfy.sh topic
     * so all connected devices receive it in real time.
     */
    fun publishToChannel(
        channelId: String,
        title: String,
        envelope: JSONObject,
        priority: Int = 4
    ): Result<Unit> {
        val cleanChannel = sanitizeChannelId(channelId)
        if (cleanChannel.isBlank()) {
            return Result.failure(IllegalArgumentException("Empty channel ID"))
        }

        return try {
            val payload = JSONObject().apply {
                put("topic", cleanChannel)
                put("title", title)
                put("message", envelope.toString())
                put("priority", priority.coerceIn(1, 5))
            }

            val request = Request.Builder()
                .url("$NTFY_BASE_URL/")
                .header("User-Agent", "OmastroTrivoga-Android/1.0")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(IllegalStateException("HTTP ${response.code}"))
                } else {
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Polls recent messages from the live online ntfy.sh topic.
     */
    fun pollChannelMessages(
        channelId: String,
        since: String = "12h"
    ): Result<List<RemoteSyncMessage>> {
        val cleanChannel = sanitizeChannelId(channelId)
        if (cleanChannel.isBlank()) {
            return Result.success(emptyList())
        }

        return try {
            val url = "$NTFY_BASE_URL/$cleanChannel/json?poll=1&since=$since"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OmastroTrivoga-Android/1.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return Result.success(emptyList())
                }

                val messages = mutableListOf<RemoteSyncMessage>()
                body.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { line ->
                        try {
                            val obj = JSONObject(line)
                            if (obj.optString("event") == "message") {
                                val remoteId = obj.optString("id", "")
                                val timeSec = obj.optLong("time", 0L)
                                val msgRaw = obj.optString("message", "")
                                if (remoteId.isNotBlank() && msgRaw.startsWith("{")) {
                                    val envelope = JSONObject(msgRaw)
                                    messages.add(
                                        RemoteSyncMessage(
                                            remoteId = remoteId,
                                            timeSeconds = timeSec,
                                            envelope = envelope
                                        )
                                    )
                                }
                            }
                        } catch (_: Exception) {
                            // Ignore malformed or non-app messages on channel
                        }
                    }

                Result.success(messages)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sanitizeChannelId(raw: String): String {
        return raw.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_-]"), "-")
            .trim('-')
            .take(64)
    }
}
