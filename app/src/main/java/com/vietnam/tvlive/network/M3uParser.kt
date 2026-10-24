package com.vietnam.tvlive.network

import android.content.Context
import com.vietnam.tvlive.model.Channel
import com.vietnam.tvlive.model.ChannelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object M3uParser {

    const val M3U_URL = "https://raw.githubusercontent.com/khanh71/All-In-One-IPTV/main/http-iptv.m3u"
    private const val CACHE_FILE_NAME = "iptv_dynamic_cache.m3u"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val HTV_NUMBERED_REGEX = Regex("^HTV\\s*[1-9](\\s|$|HD|SD)", RegexOption.IGNORE_CASE)

    /**
     * Dynamically fetch and parse channels directly from the online M3U repository.
     * Uses disk cache if network is unreachable on launch.
     */
    suspend fun fetchDynamicChannels(context: Context): List<Channel> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)

        try {
            val request = Request.Builder()
                .url(M3U_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val content = response.body?.string().orEmpty()
                    if (content.isNotBlank()) {
                        try {
                            cacheFile.writeText(content)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        val parsed = parseM3uContent(content)
                        if (parsed.isNotEmpty()) {
                            return@withContext parsed
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to cached dynamic file if network failed
        if (cacheFile.exists()) {
            try {
                val cachedContent = cacheFile.readText()
                val parsed = parseM3uContent(cachedContent)
                if (parsed.isNotEmpty()) {
                    return@withContext parsed
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext emptyList()
    }

    /**
     * Parses arbitrary M3U content dynamically:
     * - VTV: all channels
     * - HTV: only channels 1 -> 9
     * - Priorities: VTV1, HTV7, HTV9 at the head of the list; remaining channels ordered normally.
     */
    fun parseM3uContent(content: String): List<Channel> {
        val lines = content.lines()
        val result = mutableListOf<Channel>()
        var currentExtInf: String? = null

        val idPattern = Pattern.compile("tvg-id=\"([^\"]*)\"")
        val logoPattern = Pattern.compile("tvg-logo=\"([^\"]*)\"")
        val groupPattern = Pattern.compile("group-title=\"([^\"]*)\"")
        val titlePattern = Pattern.compile(",(.*)$")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                currentExtInf = trimmed
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && currentExtInf != null) {
                val streamUrl = trimmed
                val extInf = currentExtInf

                val titleMatcher = titlePattern.matcher(extInf)
                val title = if (titleMatcher.find()) titleMatcher.group(1)?.trim().orEmpty() else ""

                val groupMatcher = groupPattern.matcher(extInf)
                val group = if (groupMatcher.find()) groupMatcher.group(1)?.trim().orEmpty() else ""

                val logoMatcher = logoPattern.matcher(extInf)
                val logo = if (logoMatcher.find()) logoMatcher.group(1)?.trim().orEmpty() else ""

                val idMatcher = idPattern.matcher(extInf)
                val id = if (idMatcher.find()) idMatcher.group(1)?.trim().orEmpty() else ""

                // Filter logic:
                // 1. VTV: all channels
                val isVtv = group.equals("VTV", ignoreCase = true) || title.uppercase().startsWith("VTV")

                // 2. HTV: only channels 1 -> 9
                val isHtvNumbered = (group.equals("HTV", ignoreCase = true) || title.uppercase().startsWith("HTV"))
                        && HTV_NUMBERED_REGEX.containsMatchIn(title)

                if (isVtv || isHtvNumbered) {
                    val assignedGroup = if (isVtv) "VTV" else "HTV"
                    result.add(
                        Channel(
                            id = id.ifEmpty { title },
                            name = title,
                            group = assignedGroup,
                            logoUrl = logo,
                            streamUrl = streamUrl,
                            status = ChannelStatus.CHECKING
                        )
                    )
                }
                currentExtInf = null
            }
        }

        // Priority ordering: VTV1, HTV9, HTV7 at the head of the list
        val priorityTargets = listOf("VTV1", "HTV9", "HTV7")
        val prioritized = mutableListOf<Channel>()

        for (target in priorityTargets) {
            val index = result.indexOfFirst {
                val t = it.name.trim()
                t.equals(target, ignoreCase = true) ||
                t.startsWith("$target ", ignoreCase = true) ||
                t.startsWith("$target-", ignoreCase = true)
            }
            if (index >= 0) {
                prioritized.add(result.removeAt(index))
            }
        }

        // Prepend prioritized channels at the beginning
        result.addAll(0, prioritized)

        return result
    }
}
