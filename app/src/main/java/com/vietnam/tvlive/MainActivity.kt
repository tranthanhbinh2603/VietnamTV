package com.vietnam.tvlive

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vietnam.tvlive.adapter.ChannelHorizontalAdapter
import com.vietnam.tvlive.databinding.ActivityMainBinding
import com.vietnam.tvlive.model.Channel
import com.vietnam.tvlive.model.ChannelStatus
import com.vietnam.tvlive.network.ChannelHealthChecker
import com.vietnam.tvlive.network.M3uParser
import com.vietnam.tvlive.service.TvPlayerService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var playerService: TvPlayerService? = null
    private var isBound = false

    private lateinit var horizontalAdapter: ChannelHorizontalAdapter
    private lateinit var gestureDetector: GestureDetector

    private var channelHealthChecker: ChannelHealthChecker? = null
    private val channels: MutableList<Channel> = mutableListOf()
    private var hasStartedInitialPlayback = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideBottomControls() }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TvPlayerService.LocalBinder
            playerService = binder.getService()
            isBound = true

            // Attach player to PlayerView
            binding.playerView.player = playerService?.player
            setupPlayerListeners()

            if (channels.isNotEmpty()) {
                playerService?.channelList = channels
            }

            // If player already running (e.g. returning to app), resume smoothly
            playerService?.currentChannel?.let { current ->
                horizontalAdapter.setSelectedChannel(current.streamUrl)
                binding.loadingLayout.visibility = View.GONE
            } ?: run {
                // If channels already loaded from network, start VTV1
                if (channels.isNotEmpty()) {
                    startInitialChannelPlayback()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableImmersiveMode()

        initViews()
        loadDynamicChannels()
        startAndBindPlayerService()
        setupBackPressHandler()
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }


    private fun initViews() {
        // Horizontal channel selector bar (Hidden by default on startup!)
        binding.bottomChannelBar.visibility = View.GONE

        horizontalAdapter = ChannelHorizontalAdapter(channels) { channel, _ ->
            selectAndPlayChannel(channel)
            // Auto hide bottom bar shortly after user selects a channel
            mainHandler.postDelayed({ hideBottomControls() }, 1800)
        }
        binding.rvHorizontalChannels.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvHorizontalChannels.adapter = horizontalAdapter

        // Gesture detector:
        // Swipe UP -> Show channel selection bar
        // Swipe DOWN -> Hide channel selection bar
        // Tap on screen -> Toggle channel selection bar
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // If tapped on the bottom bar itself, let the adapter clicks handle it
                val rect = Rect()
                if (binding.bottomChannelBar.visibility == View.VISIBLE) {
                    binding.bottomChannelBar.getGlobalVisibleRect(rect)
                    if (rect.contains(e.rawX.toInt(), e.rawY.toInt())) {
                        scheduleHideBottomControls()
                        return false
                    }
                }

                // Tap on video toggles the channel bar
                if (binding.bottomChannelBar.visibility == View.VISIBLE) {
                    hideBottomControls()
                } else {
                    showBottomControls()
                    scheduleHideBottomControls()
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null) {
                    val deltaY = e1.y - e2.y
                    val deltaX = Math.abs(e1.x - e2.x)

                    // Swipe Up gesture -> Reveal channel selection bar
                    if (deltaY > 50 && Math.abs(velocityY) > 120 && deltaY > deltaX) {
                        showBottomControls()
                        scheduleHideBottomControls()
                        scrollToCurrentChannel()
                        return true
                    }

                    // Swipe Down gesture -> Hide channel selection bar
                    if (deltaY < -50 && Math.abs(velocityY) > 120 && -deltaY > deltaX) {
                        hideBottomControls()
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        if (binding.bottomChannelBar.visibility == View.VISIBLE) {
            scheduleHideBottomControls()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun showBottomControls() {
        binding.bottomChannelBar.visibility = View.VISIBLE
    }

    private fun hideBottomControls() {
        binding.bottomChannelBar.visibility = View.GONE
    }

    private fun scheduleHideBottomControls() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, 5000)
    }

    private fun scrollToCurrentChannel() {
        val streamUrl = playerService?.currentChannel?.streamUrl ?: return
        val index = horizontalAdapter.getVisiblePosition(streamUrl)
        if (index >= 0) {
            binding.rvHorizontalChannels.smoothScrollToPosition(index)
        }
    }

    /**
     * Dynamically loads channel list from the online M3U repository
     */
    private fun loadDynamicChannels() {
        lifecycleScope.launch {
            val fetched = M3uParser.fetchDynamicChannels(applicationContext)
            if (fetched.isNotEmpty()) {
                channels.clear()
                channels.addAll(fetched)
                horizontalAdapter.setChannels(channels)
                playerService?.channelList = channels

                // If service is bound and haven't started playback yet, start first channel (VTV1)
                if (isBound && !hasStartedInitialPlayback) {
                    startInitialChannelPlayback()
                }
            } else {
                showNoticeBanner("Không thể tải danh sách kênh, vui lòng kiểm tra mạng.")
            }
        }
    }

    private fun startAndBindPlayerService() {
        val intent = Intent(this, TvPlayerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupPlayerListeners() {
        val service = playerService ?: return

        service.onPlaybackReady = { channel ->
            runOnUiThread {
                binding.loadingLayout.visibility = View.GONE
                binding.tvBannerNotice.visibility = View.GONE
                horizontalAdapter.setSelectedChannel(channel.streamUrl)

                // While user watches VTV1, dynamically check remaining channels in background
                startBackgroundChannelChecking(channel.id)
            }
        }

        service.onPlaybackFailed = { channel, reason ->
            runOnUiThread {
                showNoticeBanner("Kênh ${channel.name} mất tín hiệu, đang chuyển kênh…")
                horizontalAdapter.updateChannelStatus(channel)
            }
        }

        service.onStateChanged = { isPlaying ->
            runOnUiThread {
                if (isPlaying) {
                    binding.loadingLayout.visibility = View.GONE
                }
            }
        }
    }

    private fun startInitialChannelPlayback() {
        if (hasStartedInitialPlayback || channels.isEmpty()) return
        hasStartedInitialPlayback = true

        val vtv1 = channels[0]
        binding.tvLoadingMessage.text = "Đang kết nối tín hiệu ${vtv1.name}…"
        binding.loadingLayout.visibility = View.VISIBLE

        playerService?.playChannel(vtv1, 0)
    }

    private fun selectAndPlayChannel(channel: Channel) {
        binding.tvLoadingMessage.text = "Đang tải ${channel.name}…"
        binding.loadingLayout.visibility = View.VISIBLE
        horizontalAdapter.setSelectedChannel(channel.streamUrl)
        playerService?.playChannel(channel)
    }

    private fun startBackgroundChannelChecking(activeChannelId: String) {
        if (channelHealthChecker == null) {
            channelHealthChecker = ChannelHealthChecker { updatedChannel ->
                runOnUiThread {
                    horizontalAdapter.updateChannelStatus(updatedChannel)
                }
            }
        }
        channelHealthChecker?.startChecking(channels, skipChannelId = activeChannelId)
    }

    private fun showNoticeBanner(message: String) {
        binding.tvBannerNotice.text = message
        binding.tvBannerNotice.visibility = View.VISIBLE
        mainHandler.postDelayed({
            binding.tvBannerNotice.visibility = View.GONE
        }, 3000)
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomChannelBar.visibility == View.VISIBLE) {
                    hideBottomControls()
                } else {
                    // Minimize to background, audio continues
                    moveTaskToBack(true)
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        playerService?.let { service ->
            binding.playerView.player = service.player
            service.currentChannel?.let {
                horizontalAdapter.setSelectedChannel(it.streamUrl)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
    }

    override fun onStop() {
        super.onStop()
        // Detach player surface so background audio plays uninterrupted without video holding resources
        binding.playerView.player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        channelHealthChecker?.stop()
        mainHandler.removeCallbacksAndMessages(null)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
