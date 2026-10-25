package com.vietnam.tvlive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vietnam.tvlive.adapter.ChannelHorizontalAdapter
import com.vietnam.tvlive.databinding.ActivityMainBinding
import com.vietnam.tvlive.model.Channel
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
    private var orientationEventListener: OrientationEventListener? = null

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
                updatePlayPauseButton()
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
        // Keep screen awake while watching video regardless of system screen timeout
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Force landscape orientation unconditionally regardless of device rotation lock
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOrientationListener()
        enableImmersiveMode()
        setupWindowInsets()

        initViews()
        loadDynamicChannels()
        startAndBindPlayerService()
        setupBackPressHandler()
    }

    /**
     * Listens to sensor orientation to allow smooth flipping between normal and reverse landscape
     * even when system auto-rotate is locked (never switches to portrait).
     */
    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                // Normal landscape (~270°)
                if (orientation in 225..315) {
                    if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
                // Reverse landscape (~90°)
                else if (orientation in 45..135) {
                    if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                }
            }
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val basePaddingTop = (20 * density).toInt()
            val basePaddingBottom = (16 * density).toInt()

            binding.bottomChannelBar.setPadding(
                systemBars.left,
                basePaddingTop,
                systemBars.right,
                systemBars.bottom.coerceAtLeast(basePaddingBottom)
            )
            insets
        }
    }

    private fun initViews() {
        // Horizontal channel selector bar & play/pause button (Hidden by default on startup!)
        binding.bottomChannelBar.visibility = View.GONE
        binding.btnPlayPause.visibility = View.GONE

        // Play / Pause button click listener
        binding.btnPlayPause.setOnClickListener {
            playerService?.togglePlayPause()
            updatePlayPauseButton()
            scheduleHideBottomControls()
        }

        horizontalAdapter = ChannelHorizontalAdapter(channels) { channel, _ ->
            selectAndPlayChannel(channel)
            // Auto hide controls shortly after user selects a channel
            mainHandler.postDelayed({ hideBottomControls() }, 1800)
        }
        binding.rvHorizontalChannels.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvHorizontalChannels.adapter = horizontalAdapter

        // Gesture detector:
        // Tap on screen -> Instantly show/hide controls and system navigation bar (Home, Recents, Back)
        // Swipe UP -> Reveal channel selection bar & navigation buttons
        // Swipe DOWN -> Hide controls
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (isTouchInsideView(binding.bottomChannelBar, e)) {
                    scheduleHideBottomControls()
                    return false
                }

                if (isTouchInsideView(binding.btnPlayPause, e)) {
                    scheduleHideBottomControls()
                    return false
                }

                // Tap on video background toggles the channel bar, play/pause button, and system navigation bar
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

                    // Swipe Up gesture -> Reveal channel selection bar & navigation buttons
                    if (deltaY > 50 && Math.abs(velocityY) > 120 && deltaY > deltaX) {
                        showBottomControls()
                        scheduleHideBottomControls()
                        scrollToCurrentChannel()
                        return true
                    }

                    // Swipe Down gesture -> Hide controls
                    if (deltaY < -50 && Math.abs(velocityY) > 120 && -deltaY > deltaX) {
                        hideBottomControls()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun isTouchInsideView(view: View, e: MotionEvent): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        return rect.contains(e.x.toInt(), e.y.toInt())
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
        if (binding.loadingLayout.visibility != View.VISIBLE) {
            binding.btnPlayPause.visibility = View.VISIBLE
            updatePlayPauseButton()
        }

        // Show system navigation bar (3 buttons: Home, Recents, Back) immediately upon tap
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun hideBottomControls() {
        binding.bottomChannelBar.visibility = View.GONE
        binding.btnPlayPause.visibility = View.GONE

        // Hide system navigation bar and status bar back into immersive mode
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun scheduleHideBottomControls() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, 5000)
    }

    private fun updatePlayPauseButton() {
        val isPlaying = playerService?.player?.isPlaying == true
        if (isPlaying) {
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            binding.btnPlayPause.contentDescription = "Tạm dừng video"
        } else {
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            binding.btnPlayPause.contentDescription = "Phát tiếp video"
        }
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
                updatePlayPauseButton()

                // While user watches active channel, dynamically check remaining channels in background
                startBackgroundChannelChecking(channel.id)
            }
        }

        service.onPlaybackFailed = { channel, reason ->
            runOnUiThread {
                showNoticeBanner("Kênh ${channel.name} mất tín hiệu, đang chuyển kênh…")
                horizontalAdapter.updateChannelStatus(channel)
                updatePlayPauseButton()
            }
        }

        service.onStateChanged = { isPlaying ->
            runOnUiThread {
                if (isPlaying) {
                    binding.loadingLayout.visibility = View.GONE
                }
                updatePlayPauseButton()
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
        binding.btnPlayPause.visibility = View.GONE
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
        orientationEventListener?.enable()
        playerService?.let { service ->
            binding.playerView.player = service.player
            service.currentChannel?.let {
                horizontalAdapter.setSelectedChannel(it.streamUrl)
            }
            updatePlayPauseButton()
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        updatePlayPauseButton()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener?.disable()
        // Detach player surface so background audio plays uninterrupted without video holding resources
        binding.playerView.player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationEventListener?.disable()
        channelHealthChecker?.stop()
        mainHandler.removeCallbacksAndMessages(null)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
