package com.vietnam.tvlive.model

enum class ChannelStatus {
    CHECKING,
    ALIVE,
    DEAD
}

data class Channel(
    val id: String,
    val name: String,
    val group: String,
    val logoUrl: String,
    val streamUrl: String,
    var status: ChannelStatus = ChannelStatus.CHECKING,
    var isPlaying: Boolean = false
) {
    val isVtv: Boolean
        get() = group.equals("VTV", ignoreCase = true) || name.uppercase().startsWith("VTV")

    val isHtv: Boolean
        get() = group.equals("HTV", ignoreCase = true) || name.uppercase().startsWith("HTV")

    val isDongNai: Boolean
        get() = group.equals("Đồng Nai", ignoreCase = true) || name.contains("Đồng Nai", ignoreCase = true) || name.contains("Dong Nai", ignoreCase = true)

    val isVinhLong: Boolean
        get() = group.equals("Vĩnh Long", ignoreCase = true) || name.contains("Vĩnh Long", ignoreCase = true) || name.contains("Vinh Long", ignoreCase = true) || name.contains("THVL", ignoreCase = true)
}
