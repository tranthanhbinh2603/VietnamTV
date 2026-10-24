package com.vietnam.tvlive

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class TvApplication : Application() {

    companion object {
        const val CHANNEL_ID = "tv_live_playback_channel"
        const val CHANNEL_NAME = "TV Live Background Audio"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kênh thông báo phát âm thanh nền khi thoát ứng dụng"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
