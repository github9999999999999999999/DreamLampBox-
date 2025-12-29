package com.example.theone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.media.MediaCodec
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import java.util.Collections
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val REQ_READ_STORAGE = 1001
    
    // 断点记忆播放常量
    private val PREFS_NAME = "video_playback_prefs"
    private val KEY_VIDEO_PATH = "video_path_"
    private val KEY_PLAYBACK_POSITION = "playback_position_"

    private lateinit var playerView: PlayerView
    private lateinit var rvMenu: RecyclerView
    // private lateinit var tvNoVideo: TextView // Removed as per strict XML overwrite

    private var player: ExoPlayer? = null
    private val videoFiles = ArrayList<File>()
    private var currentIndex = 0
    private var adapter: VideoListAdapter? = null
    private lateinit var sharedPrefs: android.content.SharedPreferences
    private var currentPlayingFile: File? = null
    private var isMenuVisible = false
    private var lastFocusedView: View? = null

    // Register Activity Result for Android 11+ (R) storage permission
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                scanFiles()
            } else {
                Toast.makeText(this, "需要所有文件访问权限才能播放", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 SharedPreferences 用于断点记忆播放
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Initialize Views
        playerView = findViewById(R.id.player_view)
        rvMenu = findViewById(R.id.rv_menu)
        // tvNoVideo = findViewById(R.id.tv_no_video)

        // Setup RecyclerView (Standard Vertical Scroll)
        rvMenu.layoutManager = LinearLayoutManager(this)
        
        // 设置焦点变化监听器，确保焦点在菜单项目间流转
        rvMenu.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && isMenuVisible) {
                // 如果RecyclerView获得焦点，将焦点转移到当前项目
                rvMenu.post {
                    val currentView = rvMenu.layoutManager?.findViewByPosition(currentIndex)
                    currentView?.requestFocus()
                }
            }
        }
        
        // Hide list initially
        rvMenu.visibility = View.GONE

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = ViewCompat.getWindowInsetsController(window.decorView)
        windowInsetsController?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_VIDEO),
                    REQ_READ_STORAGE
                )
            } else {
                scanFiles()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11-12
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    storagePermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    storagePermissionLauncher.launch(intent)
                }
            } else {
                scanFiles()
            }
        } else { // Android 10 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQ_READ_STORAGE
                )
            } else {
                scanFiles()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_READ_STORAGE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            scanFiles()
        } else {
            Toast.makeText(this, "需要存储权限才能播放", Toast.LENGTH_LONG).show()
        }
    }

    private fun scanFiles() {
        Log.d(TAG, "scanFiles: Scanning started")
        thread {
            val files = VideoScanner.scan(this)
            Collections.sort(files) { f1, f2 -> f1.name.compareTo(f2.name, ignoreCase = true) }

            runOnUiThread {
                videoFiles.clear()
                videoFiles.addAll(files)

                if (videoFiles.isEmpty()) {
                    // tvNoVideo.visibility = View.VISIBLE
                    Toast.makeText(this, "没有找到视频文件", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                adapter = VideoListAdapter(this, videoFiles)
                adapter?.setOnItemClickListener { file ->
                    currentIndex = videoFiles.indexOf(file)
                    playVideo(file)
                }
                rvMenu.adapter = adapter

                // 设置适配器注册监听器，确保项目准备好后请求焦点
                rvMenu.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (isMenuVisible) {
                            requestMenuFocus()
                        }
                        // 只执行一次
                        rvMenu.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                })

                // Auto-play first video
                initPlayer()
                playVideo(videoFiles[0])
            }
        }
    }

    private fun initPlayer() {
        if (player == null) {
            // 创建支持软件解码回退的RenderersFactory
            val renderersFactory = DefaultRenderersFactory(this).apply {
                // 设置EXTENSION_RENDERER_MODE_PREFER以优先使用扩展解码器
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                // 允许使用软件解码器作为回退
                setEnableDecoderFallback(true)
                // 设置最大解码器数量，提高兼容性
                setMaxDecoderCount(16)
            }
            
            // 创建支持硬件加速回退的Player实例
            player = ExoPlayer.Builder(this, renderersFactory).apply {
                // 设置音频解码器回退
                setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
                // 设置视频解码器优先使用硬件，失败时回退到软件
                setVideoDecoderFactory { 
                    // 这里可以添加自定义解码器工厂
                    MediaCodecVideoRenderer.Factory()
                }
            }.build()
            
            playerView.player = player
            
            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        playNext()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Interaction Logic:
                    // Playing -> Close Menu (Pure Fullscreen)
                    // Paused -> Open Menu (Overlay)
                    updateListVisibility(!isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    // 详细的错误处理和用户友好的提示
                    handlePlayerError(error)
                }
            })
        }
    }

    private fun playVideo(file: File) {
        if (player == null) initPlayer()

        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play() 
    }

    private fun playNext() {
        if (videoFiles.isEmpty()) return
        currentIndex = (currentIndex + 1) % videoFiles.size
        playVideo(videoFiles[currentIndex])
    }

    private fun updateListVisibility(showList: Boolean) {
        if (showList) {
            rvMenu.visibility = View.VISIBLE
            rvMenu.scrollToPosition(currentIndex)
            isMenuVisible = true
            // 强制请求焦点到菜单的第一个项目
            rvMenu.post {
                val firstView = rvMenu.layoutManager?.findViewByPosition(currentIndex)
                if (firstView != null) {
                    lastFocusedView = firstView
                    firstView.requestFocus()
                } else {
                    // 如果第一个项目还没准备好，请求RecyclerView本身的焦点
                    rvMenu.requestFocus()
                }
            }
        } else {
            rvMenu.visibility = View.GONE
            isMenuVisible = false
            // 菜单关闭时，将焦点还给播放器区域
            playerView.requestFocus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (rvMenu.visibility == View.VISIBLE) {
            rvMenu.visibility = View.GONE
            isMenuVisible = false
            playerView.requestFocus()  // 将焦点还给播放器
            // Resume if user backs out of menu?
            if (player != null && !player!!.isPlaying) {
                player!!.play()
            }
        } else {
            super.onBackPressed()
        }
    }

    // TV遥控器按键支持
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // 如果菜单可见，拦截所有按键事件，防止透传到播放器
        if (isMenuVisible) {
            return when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    navigateUp()
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    navigateDown()
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    navigateLeft()
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    navigateRight()
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, 
                android.view.KeyEvent.KEYCODE_ENTER -> {
                    selectCurrentItem()
                    true
                }
                android.view.KeyEvent.KEYCODE_BACK -> {
                    onBackPressed()
                    true
                }
                android.view.KeyEvent.KEYCODE_MENU -> {
                    toggleMenu()
                    true
                }
                else -> true // 拦截所有其他按键，防止透传
            }
        }
        
        // 菜单不可见时的正常处理
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                navigateUp()
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                navigateDown()
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                navigateLeft()
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                navigateRight()
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, 
            android.view.KeyEvent.KEYCODE_ENTER -> {
                selectCurrentItem()
                true
            }
            android.view.KeyEvent.KEYCODE_BACK -> {
                onBackPressed()
                true
            }
            android.view.KeyEvent.KEYCODE_MENU -> {
                toggleMenu()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayback()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun navigateUp() {
        if (adapter != null && currentIndex > 0) {
            currentIndex--
            rvMenu.scrollToPosition(currentIndex)
            // 高亮当前项目并请求焦点
            rvMenu.post {
                val viewHolder = rvMenu.findViewHolderForAdapterPosition(currentIndex)
                viewHolder?.itemView?.requestFocus()
                if (viewHolder?.itemView != null) {
                    lastFocusedView = viewHolder.itemView
                }
            }
        }
    }

    private fun navigateDown() {
        if (adapter != null && currentIndex < videoFiles.size - 1) {
            currentIndex++
            rvMenu.scrollToPosition(currentIndex)
            // 高亮当前项目并请求焦点
            rvMenu.post {
                val viewHolder = rvMenu.findViewHolderForAdapterPosition(currentIndex)
                viewHolder?.itemView?.requestFocus()
                if (viewHolder?.itemView != null) {
                    lastFocusedView = viewHolder.itemView
                }
            }
        }
    }

    private fun navigateLeft() {
        // 快退10秒
        player?.let {
            val newPosition = maxOf(it.currentPosition - 10000, 0)
            it.seekTo(newPosition)
        }
    }

    private fun navigateRight() {
        // 快进10秒
        player?.let {
            val newPosition = minOf(it.currentPosition + 10000, it.duration)
            it.seekTo(newPosition)
        }
    }

    private fun selectCurrentItem() {
        if (videoFiles.isNotEmpty() && currentIndex >= 0 && currentIndex < videoFiles.size) {
            playVideo(videoFiles[currentIndex])
        }
    }

    private fun toggleMenu() {
        if (rvMenu.visibility == View.VISIBLE) {
            rvMenu.visibility = View.GONE
            isMenuVisible = false
            playerView.requestFocus()  // 将焦点还给播放器
            player?.play()
        } else {
            rvMenu.visibility = View.VISIBLE
            isMenuVisible = true
            player?.pause()
            // 延迟请求焦点，确保菜单完全显示
            rvMenu.postDelayed({
                requestMenuFocus()  // 使用专门的焦点请求方法
            }, 100)
        }
    }

    private fun togglePlayback() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                updateListVisibility(true)
            } else {
                it.play()
                updateListVisibility(false)
            }
        }
    }

    /**
     * 请求菜单焦点 - 确保焦点正确转移到菜单
     */
    private fun requestMenuFocus() {
        if (isMenuVisible) {
            rvMenu.post {
                val currentView = rvMenu.layoutManager?.findViewByPosition(currentIndex)
                if (currentView != null) {
                    currentView.requestFocus()
                    lastFocusedView = currentView
                } else {
                    // 如果当前项目视图还没准备好，请求RecyclerView焦点
                    rvMenu.requestFocus()
                }
            }
        }
    }

    /**
     * 处理播放器错误，提供用户友好的错误提示
     */
    private fun handlePlayerError(error: PlaybackException) {
        val errorMessage = when {
            error.cause is MediaCodec.CryptoException -> {
                "视频加密格式不支持"
            }
            error.cause is MediaCodec.CodecException -> {
                val codecError = error.cause as MediaCodec.CodecException
                when {
                    codecError.isTransient -> "解码器临时错误，正在重试..."
                    codecError.isRecoverable -> "解码器可恢复错误"
                    else -> "设备不支持此视频格式（4K/HEVC）"
                }
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                "解码器初始化失败，尝试软件解码..."
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                "解码失败，格式可能不受支持"
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> {
                "视频编码格式不受支持"
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> {
                "视频规格超出设备处理能力"
            }
            else -> {
                // 分析错误信息中的关键提示
                val errorMsg = error.message ?: "未知错误"
                when {
                    errorMsg.contains("4K", ignoreCase = true) -> "设备不支持4K视频播放"
                    errorMsg.contains("HEVC", ignoreCase = true) -> "设备不支持HEVC/H.265格式"
                    errorMsg.contains("H265", ignoreCase = true) -> "设备不支持H.265格式"
                    errorMsg.contains("profile", ignoreCase = true) -> "视频编码配置过高"
                    errorMsg.contains("resolution", ignoreCase = true) -> "视频分辨率超出支持范围"
                    else -> "播放失败：${errorMsg.take(50)}..."
                }
            }
        }
        
        // 显示用户友好的错误提示
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        
        // 记录详细错误信息到日志
        Log.e(TAG, "播放错误详情: ${error.message}", error)
        
        // 尝试播放下一个视频
        playNext()
    }
}
