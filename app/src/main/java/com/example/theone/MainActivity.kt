package com.example.theone

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import kotlin.concurrent.thread

/**
 * 梦灯盒 主Activity
 * 
 * 四大铁律：
 * 1. 播放器内核：软解优先策略 (SoftwareFirstRenderersFactory)
 * 2. 图片加载：极致内存防御 (VideoAdapter 200x112 RGB_565)
 * 3. 交互：电视焦点铁律 (D-Pad + selector_item_focus.xml)
 * 4. 权限：跨时代适配 (Android 5.0 ~ 14)
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DreamLampBox"
        private const val REQ_PERMISSION = 1001
    }

    // Views
    private lateinit var playerView: PlayerView
    private lateinit var rvMenu: RecyclerView

    // Player
    private var player: ExoPlayer? = null
    
    // Data
    private val videoFiles = ArrayList<File>()
    private var currentIndex = 0
    private var adapter: VideoAdapter? = null
    private var isMenuVisible = false

    // Android 11+ 存储权限结果处理
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Log.d(TAG, "✅ MANAGE_EXTERNAL_STORAGE 已授权")
                scanAndPlayVideos()
            } else {
                Toast.makeText(this, "需要存储权限才能播放视频", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupBackHandler()
        checkAndRequestPermissions()
    }

    private fun initViews() {
        playerView = findViewById(R.id.player_view)
        rvMenu = findViewById(R.id.rv_menu)
        
        // RecyclerView 配置
        rvMenu.layoutManager = LinearLayoutManager(this)
        rvMenu.visibility = View.GONE
        
        // 焦点流转管理
        rvMenu.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && isMenuVisible) {
                rvMenu.post {
                    rvMenu.layoutManager?.findViewByPosition(currentIndex)?.requestFocus()
                }
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isMenuVisible) {
                    hideMenu()
                    player?.play()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ============================================
    // 权限：跨时代适配 (Android 5.0 ~ 14)
    // ============================================
    
    private fun checkAndRequestPermissions() {
        when {
            // Android 13+ (API 33+): READ_MEDIA_VIDEO
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                Log.d(TAG, "📱 Android 13+ 检查 READ_MEDIA_VIDEO")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_MEDIA_VIDEO),
                        REQ_PERMISSION
                    )
                } else {
                    scanAndPlayVideos()
                }
            }
            // Android 11-12 (API 30-32): MANAGE_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Log.d(TAG, "📱 Android 11-12 检查 MANAGE_EXTERNAL_STORAGE")
                if (!Environment.isExternalStorageManager()) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        manageStorageLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    }
                } else {
                    scanAndPlayVideos()
                }
            }
            // Android 5.0-10 (API 21-29): READ_EXTERNAL_STORAGE
            else -> {
                Log.d(TAG, "📱 Android 5-10 检查 READ_EXTERNAL_STORAGE")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        REQ_PERMISSION
                    )
                } else {
                    scanAndPlayVideos()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSION && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "✅ 权限已授予")
            scanAndPlayVideos()
        } else {
            Log.w(TAG, "⚠️ 权限被拒绝，尝试继续扫描")
            Toast.makeText(this, "需要存储权限才能播放视频", Toast.LENGTH_SHORT).show()
            scanAndPlayVideos()
        }
    }

    private fun scanAndPlayVideos() {
        Log.d(TAG, "🔍 开始扫描视频文件...")
        thread {
            val files = VideoScanner.scan(this)
            files.sortBy { it.name.lowercase() }

            runOnUiThread {
                videoFiles.clear()
                videoFiles.addAll(files)

                if (videoFiles.isEmpty()) {
                    Toast.makeText(this, "未找到视频文件", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                Log.d(TAG, "✅ 找到 ${videoFiles.size} 个视频文件")
                
                // 初始化适配器（使用新的VideoAdapter）
                adapter = VideoAdapter(this, videoFiles)
                adapter?.setOnItemClickListener { file ->
                    currentIndex = videoFiles.indexOf(file)
                    playVideo(file)
                }
                rvMenu.adapter = adapter

                // 初始化播放器并播放第一个视频
                initPlayer()
                playVideo(videoFiles[0])
            }
        }
    }

    // ============================================
    // 播放器内核：软解优先策略
    // ============================================
    
    private fun initPlayer() {
        if (player != null) return
        
        // 使用自定义的软解优先渲染器工厂
        val renderersFactory = SoftwareFirstRenderersFactory(this)
        
        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
        
        playerView.player = player
        
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playNextVideo()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 播放时隐藏菜单，暂停时显示菜单
                if (isPlaying) hideMenu() else showMenu()
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(error)
            }
        })
    }

    private fun playVideo(file: File) {
        if (player == null) initPlayer()
        
        Log.d(TAG, "▶️ 播放: ${file.name}")
        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }

    private fun playNextVideo() {
        if (videoFiles.isEmpty()) return
        currentIndex = (currentIndex + 1) % videoFiles.size
        playVideo(videoFiles[currentIndex])
    }

    // ============================================
    // 菜单显示/隐藏
    // ============================================
    
    private fun showMenu() {
        rvMenu.visibility = View.VISIBLE
        rvMenu.scrollToPosition(currentIndex)
        isMenuVisible = true
        
        // 请求焦点到当前项目
        rvMenu.post {
            rvMenu.layoutManager?.findViewByPosition(currentIndex)?.requestFocus()
                ?: rvMenu.requestFocus()
        }
    }

    private fun hideMenu() {
        rvMenu.visibility = View.GONE
        isMenuVisible = false
        playerView.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    // ============================================
    // TV遥控器按键处理 (D-Pad)
    // ============================================
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { navigateUp(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { navigateDown(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { seekBackward(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { seekForward(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { selectOrToggle(); true }
            KeyEvent.KEYCODE_MENU -> { toggleMenu(); true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayback(); true }
            KeyEvent.KEYCODE_BACK -> { onBackPressedDispatcher.onBackPressed(); true }
            else -> if (isMenuVisible) true else super.onKeyDown(keyCode, event)
        }
    }

    private fun navigateUp() {
        if (!isMenuVisible || currentIndex <= 0) return
        currentIndex--
        rvMenu.scrollToPosition(currentIndex)
        rvMenu.post {
            rvMenu.findViewHolderForAdapterPosition(currentIndex)?.itemView?.requestFocus()
        }
    }

    private fun navigateDown() {
        if (!isMenuVisible || currentIndex >= videoFiles.size - 1) return
        currentIndex++
        rvMenu.scrollToPosition(currentIndex)
        rvMenu.post {
            rvMenu.findViewHolderForAdapterPosition(currentIndex)?.itemView?.requestFocus()
        }
    }

    private fun seekBackward() {
        player?.let { it.seekTo(maxOf(it.currentPosition - 10000, 0)) }
    }

    private fun seekForward() {
        player?.let { it.seekTo(minOf(it.currentPosition + 10000, it.duration)) }
    }

    private fun selectOrToggle() {
        if (isMenuVisible && videoFiles.isNotEmpty()) {
            playVideo(videoFiles[currentIndex])
        } else {
            togglePlayback()
        }
    }

    private fun toggleMenu() {
        if (isMenuVisible) {
            hideMenu()
            player?.play()
        } else {
            showMenu()
            player?.pause()
        }
    }

    private fun togglePlayback() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    // ============================================
    // TV盒子内存优化 - OOM防御
    // ============================================
    
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "⚠️ 内存不足，清理Glide缓存")
        Glide.get(this).clearMemory()
        thread { Glide.get(this).clearDiskCache() }
        System.gc()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Log.d(TAG, "🧹 内存优化 level=$level")
            Glide.get(this).clearMemory()
        }
    }

    // ============================================
    // 播放错误处理
    // ============================================
    
    private fun handlePlaybackError(error: PlaybackException) {
        Log.e(TAG, "❌ 播放错误: ${error.message}", error)
        
        val message = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> 
                "解码器初始化失败，已尝试软件解码"
            PlaybackException.ERROR_CODE_DECODING_FAILED -> 
                "解码失败，格式可能不支持"
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> 
                "视频格式不支持"
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> 
                "视频超出设备解码能力，已启用软件解码"
            else -> "播放错误: ${error.message?.take(30) ?: "未知"}"
        }
        
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        
        // 3秒后播放下一个视频
        Handler(Looper.getMainLooper()).postDelayed({
            playNextVideo()
        }, 3000)
    }
}
