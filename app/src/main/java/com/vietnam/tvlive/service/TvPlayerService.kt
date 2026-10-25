package com.vietnam.tvlive.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.vietnam.tvlive.MainActivity
import com.vietnam.tvlive.R
import com.vietnam.tvlive.TvApplication
import com.vietnam.tvlive.model.Channel
import com.vietnam.tvlive.model.ChannelStatus

class TvPlayerService : Service() {

    private val binder = LocalBinder()
    var player: ExoPlayer? = null
        private set

    var currentChannel: Channel? = null
        private set

    var channelList: List<Channel> = emptyList()

    private var currentChannelIndex: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectionTimeoutRunnable: Runnable? = null

    // Listeners for activity UI updates
    var onPlaybackReady: ((Channel) -> Unit)? = null
    var onPlaybackFailed: ((Channel, String) -> Unit)? = null
    var onStateChanged: ((Boolean) -> Unit)? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.vietnam.tvlive.ACTION_PLAY_PAUSE"
        const val ACTION_STOP = "com.vietnam.tvlive.ACTION_STOP"
        private const val CONNECTION_TIMEOUT_MS = 12000L
    }

    inner class LocalBinder : Binder() {
        fun getService(): TvPlayerService = this@TvPlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        initPlayer()
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000, // minBufferMs
                10000, // maxBufferMs
                1000, // bufferForPlaybackMs
                2000  // bufferForPlaybackAfterRebufferMs
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true
                addListener(playerListener)
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    cancelTimeout()
                    currentChannel?.let { ch ->
                        ch.status = ChannelStatus.ALIVE
                        updateNotification(ch, isPlaying = true)
                        onPlaybackReady?.invoke(ch)
                    }
                    onStateChanged?.invoke(player?.isPlaying == true)
                }
                Player.STATE_BUFFERING -> {
                    onStateChanged?.invoke(false)
                }
                Player.STATE_ENDED -> {
                    // Try to re-connect live stream
                    player?.prepare()
                }
                Player.STATE_IDLE -> {
                    onStateChanged?.invoke(false)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            currentChannel?.let { ch ->
                updateNotification(ch, isPlaying)
            }
            onStateChanged?.invoke(isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            cancelTimeout()
            currentChannel?.let { failedChannel ->
                failedChannel.status = ChannelStatus.DEAD
                onPlaybackFailed?.invoke(failedChannel, error.message ?: "Lỗi phát luồng")
            }
            // Auto-switch to next available channel
            playNextChannel(isFallback = true)
        }
    }

    @OptIn(UnstableApi::class)
    fun playChannel(channel: Channel, index: Int? = null) {
        cancelTimeout()
        currentChannel = channel
        if (index != null) {
            currentChannelIndex = index
        } else {
            val idx = channelList.indexOfFirst { it.streamUrl == channel.streamUrl }
            if (idx >= 0) currentChannelIndex = idx
        }

        channelList.forEach { it.isPlaying = (it.streamUrl == channel.streamUrl) }

        val exo = player ?: return
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(MediaItem.fromUri(channel.streamUrl))

        exo.stop()
        exo.setMediaSource(mediaSource)
        exo.prepare()
        exo.playWhenReady = true

        startForegroundWithChannel(channel)

        // Set a timeout to detect dead streams that hang without firing error
        connectionTimeoutRunnable = Runnable {
            if (exo.playbackState != Player.STATE_READY) {
                channel.status = ChannelStatus.DEAD
                onPlaybackFailed?.invoke(channel, "Quá thời gian kết nối (Timeout)")
                playNextChannel(isFallback = true)
            }
        }
        mainHandler.postDelayed(connectionTimeoutRunnable!!, CONNECTION_TIMEOUT_MS)
    }

    fun playNextChannel(isFallback: Boolean = false) {
        if (channelList.isEmpty()) return
        var nextIndex = (currentChannelIndex + 1) % channelList.size
        var attempts = 0
        while (attempts < channelList.size) {
            val candidate = channelList[nextIndex]
            if (candidate.status != ChannelStatus.DEAD) {
                playChannel(candidate, nextIndex)
                return
            }
            nextIndex = (nextIndex + 1) % channelList.size
            attempts++
        }
    }

    fun playPreviousChannel() {
        if (channelList.isEmpty()) return
        val prevIndex = if (currentChannelIndex - 1 < 0) channelList.size - 1 else currentChannelIndex - 1
        val prevChannel = channelList[prevIndex]
        playChannel(prevChannel, prevIndex)
    }

    fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
        }
    }

    private fun cancelTimeout() {
        connectionTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            connectionTimeoutRunnable = null
        }
    }

    private fun startForegroundWithChannel(channel: Channel) {
        val notification = buildNotification(channel, isPlaying = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(channel: Channel, isPlaying: Boolean) {
        val notification = buildNotification(channel, isPlaying)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(channel: Channel, isPlaying: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TvPlayerService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseTitle = if (isPlaying) "Tạm dừng" else "Phát"

        return NotificationCompat.Builder(this, TvApplication.CHANNEL_ID)
            .setContentTitle("Đang phát: ${channel.name}")
            .setContentText("Kênh trực tiếp ${channel.group} • Âm thanh nền")
            .setSmallIcon(R.drawable.ic_tv)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_STOP -> {
                player?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimeout()
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }
}
