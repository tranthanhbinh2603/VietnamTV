package com.vietnam.tvlive.network

import com.vietnam.tvlive.model.Channel
import com.vietnam.tvlive.model.ChannelStatus
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ChannelHealthChecker(
    private val onChannelUpdated: (Channel) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var checkJob: Job? = null

    fun startChecking(channels: List<Channel>, skipChannelId: String? = null) {
        checkJob?.cancel()
        checkJob = CoroutineScope(Dispatchers.IO).launch {
            // Concurrency semaphore or sequential check to avoid choking user's stream bandwidth
            for (channel in channels) {
                if (!isActive) break

                // If currently playing or already resolved as ALIVE by player, skip or verify
                if (channel.id == skipChannelId && channel.status == ChannelStatus.ALIVE) {
                    continue
                }

                val isAlive = testChannelStream(channel.streamUrl)
                channel.status = if (isAlive) ChannelStatus.ALIVE else ChannelStatus.DEAD

                withContext(Dispatchers.Main) {
                    onChannelUpdated(channel)
                }

                // Small delay between checks so network remains smooth for active video
                delay(250)
            }
        }
    }

    private fun testChannelStream(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.peekBody(2048).string()
                    // Check if it looks like an HLS playlist or playable stream
                    body.contains("#EXTM3U") || body.contains("#EXT") || response.code == 200 || response.code == 206
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        checkJob?.cancel()
        checkJob = null
    }
}
