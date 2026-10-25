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
     * - Đồng Nai: all channels
     * - Vĩnh Long: all channels
     * - Ordering: VTV1, HTV9, HTV7, Đồng Nai stations, Vĩnh Long stations, remaining channels ordered normally.
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
                var streamUrl = trimmed
                val extInf = currentExtInf

                val titleMatcher = titlePattern.matcher(extInf)
                val title = if (titleMatcher.find()) titleMatcher.group(1)?.trim().orEmpty() else ""

                val groupMatcher = groupPattern.matcher(extInf)
                val group = if (groupMatcher.find()) groupMatcher.group(1)?.trim().orEmpty() else ""

                val logoMatcher = logoPattern.matcher(extInf)
                val logo = if (logoMatcher.find()) logoMatcher.group(1)?.trim().orEmpty() else ""

                val idMatcher = idPattern.matcher(extInf)
                val id = if (idMatcher.find()) idMatcher.group(1)?.trim().orEmpty() else ""

                val upperTitle = title.uppercase()
                val upperGroup = group.uppercase()
                val lowerId = id.lowercase()

                // Filter logic:
                // 1. VTV: all channels
                val isVtv = upperGroup == "VTV" || upperTitle.startsWith("VTV")

                // 2. HTV: numbered channels 1 -> 9
                val isHtvNumbered = (upperGroup == "HTV" || upperTitle.startsWith("HTV"))
                        && HTV_NUMBERED_REGEX.containsMatchIn(title)

                // 3. Dong Nai stations
                val isDongNai = title.contains("Đồng Nai", ignoreCase = true)
                        || title.contains("Dong Nai", ignoreCase = true)
                        || lowerId.contains("dongnai")
                        || group.contains("Đồng Nai", ignoreCase = true)
                        || group.contains("Dong Nai", ignoreCase = true)

                // 4. Vinh Long stations
                val isVinhLong = title.contains("Vĩnh Long", ignoreCase = true)
                        || title.contains("Vinh Long", ignoreCase = true)
                        || upperTitle.startsWith("THVL")
                        || lowerId.contains("vinhlong")
                        || lowerId.contains("thvl")
                        || group.contains("Vĩnh Long", ignoreCase = true)
                        || group.contains("Vinh Long", ignoreCase = true)

                if (isVtv || isHtvNumbered || isDongNai || isVinhLong) {
                    val assignedGroup = when {
                        isVtv -> "VTV"
                        isHtvNumbered -> "HTV"
                        isDongNai -> "Đồng Nai"
                        else -> "Vĩnh Long"
                    }

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

        // Priority ordering: VTV1, HTV9, HTV7, Đồng Nai, Vĩnh Long, then remaining
        fun matchesTarget(channel: Channel, target: String): Boolean {
            val t = channel.name.trim()
            return t.equals(target, ignoreCase = true) ||
                    t.startsWith("$target ", ignoreCase = true) ||
                    t.startsWith("$target-", ignoreCase = true) ||
                    t.startsWith("${target}HD", ignoreCase = true)
        }

        val prioritized = mutableListOf<Channel>()

        // 1. VTV1
        val vtv1Index = result.indexOfFirst { matchesTarget(it, "VTV1") }
        if (vtv1Index >= 0) {
            prioritized.add(result.removeAt(vtv1Index))
        }

        // 2. HTV9
        val htv9Index = result.indexOfFirst { matchesTarget(it, "HTV9") }
        if (htv9Index >= 0) {
            prioritized.add(result.removeAt(htv9Index))
        }

        // 3. HTV7
        val htv7Index = result.indexOfFirst { matchesTarget(it, "HTV7") }
        if (htv7Index >= 0) {
            prioritized.add(result.removeAt(htv7Index))
        }

        // 4. Các đài Đồng Nai (sorted by name: Đồng Nai 1, Đồng Nai 2...)
        val dongNaiChannels = result.filter { it.isDongNai }
            .sortedWith(compareBy { it.name })
        result.removeAll { it.isDongNai }
        prioritized.addAll(dongNaiChannels)

        // 5. Các đài Vĩnh Long (sorted by name: Vĩnh Long 1, Vĩnh Long 2...)
        val vinhLongChannels = result.filter { it.isVinhLong }
            .sortedWith(compareBy { it.name })
        result.removeAll { it.isVinhLong }
        prioritized.addAll(vinhLongChannels)

        // Prepend prioritized channels at the beginning; remaining channels remain in original order
        result.addAll(0, prioritized)

        return result
    }
}
