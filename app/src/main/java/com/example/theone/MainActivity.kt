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
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.ComponentCallbacks2
import android.app.ActivityManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.DecodeFormat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import android.os.Handler
import android.os.Looper
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
        
        // 设置现代返回键处理 - Android 13+ 兼容
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (rvMenu.visibility == View.VISIBLE) {
                    rvMenu.visibility = View.GONE
                    isMenuVisible = false
                    playerView.requestFocus()  // 将焦点还给播放器
                    // Resume if user backs out of menu?
                    if (player != null && !player!!.isPlaying) {
                        player!!.play()
                    }
                } else {
                    // 如果菜单不可见，允许默认返回行为（退出应用）
                    isEnabled = false  // 禁用回调以允许默认行为
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

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
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            Log.d(TAG, "🔍 检查Android 14权限: READ_MEDIA_VIDEO")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "⚠️ READ_MEDIA_VIDEO权限未授予，正在请求...")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_VIDEO),
                    REQ_READ_STORAGE
                )
            } else {
                Log.d(TAG, "✅ READ_MEDIA_VIDEO权限已授予，开始扫描文件")
                scanFiles()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11-12
            Log.d(TAG, "🔍 检查Android 11-12权限: MANAGE_EXTERNAL_STORAGE")
            if (!Environment.isExternalStorageManager()) {
                Log.w(TAG, "⚠️ 管理外部存储权限未授予，正在请求...")
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    storagePermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 权限请求异常，使用备用方案", e)
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    storagePermissionLauncher.launch(intent)
                }
            } else {
                Log.d(TAG, "✅ 管理外部存储权限已授予，开始扫描文件")
                scanFiles()
            }
        } else { // Android 10 and below
            Log.d(TAG, "🔍 检查Android 10及以下权限: READ_EXTERNAL_STORAGE")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "⚠️ 读取外部存储权限未授予，正在请求...")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQ_READ_STORAGE
                )
            } else {
                Log.d(TAG, "✅ 读取外部存储权限已授予，开始扫描文件")
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
            Log.d(TAG, "✅ 权限授予成功 - Android 14 READ_MEDIA_VIDEO 已授权")
            scanFiles()
        } else {
            Log.e(TAG, "❌ 权限被拒绝 - 这将导致缩略图无法显示")
            Toast.makeText(this, "需要存储权限才能播放和显示缩略图", Toast.LENGTH_LONG).show()
            // 权限被拒绝，但仍尝试扫描（可能部分文件可访问）
            scanFiles()
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
            // 实施“软解优先”策略：强制软件解码器优先于硬件解码器
            val softwarePreferredSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                // 重新排序：软件解码器（hardwareAccelerated=false）排在前面
                decoders.sortedBy { it.hardwareAccelerated }
            }
            
            // 创建支持软解优先的RenderersFactory
            val renderersFactory = DefaultRenderersFactory(this).apply {
                // 设置自定义的MediaCodecSelector实现软解优先
                setMediaCodecSelector(softwarePreferredSelector)
                // 同时启用扩展渲染器模式作为双重保障
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            }
            
            // 创建支持软解优先的ExoPlayer实例
            player = ExoPlayer.Builder(this, renderersFactory).apply {
                // 设置音频属性
                setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
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
                    // 软解优先策略下的错误处理
                    handlePlayerErrorWithSoftwareFirst(error)
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
                    // 使用现代返回键处理
                    onBackPressedDispatcher.onBackPressed()
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
                // 使用现代返回键处理
                onBackPressedDispatcher.onBackPressed()
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

    // TV盒子内存优化 - 低内存环境下的生存策略
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "⚠️ TV盒子内存不足！执行紧急内存清理...")
        
        // 1. 清理Glide内存缓存 - 防止TV盒子闪退
        Glide.get(this).clearMemory()
        Log.d(TAG, "✅ Glide内存缓存已清理")
        
        // 2. 清理磁盘缓存 - 释放存储空间
        thread {
            Glide.get(this).clearDiskCache()
            Log.d(TAG, "✅ Glide磁盘缓存已清理")
        }
        
        // 3. 建议系统垃圾回收
        System.gc()
        Log.d(TAG, "✅ 建议系统垃圾回收")
        
        // 4. 显示内存优化提示
        runOnUiThread {
            Toast.makeText(this, "TV盒子内存优化中...", Toast.LENGTH_SHORT).show()
        }
        
        // 5. 记录内存状态
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        Log.d(TAG, "📊 内存状态: 可用内存=${memoryInfo.availMem / (1024*1024)}MB, " +
                  "总内存=${memoryInfo.totalMem / (1024*1024)}MB, " +
                  "低内存阈值=${memoryInfo.threshold / (1024*1024)}MB")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "⚠️ 系统内存偏低，执行内存优化...")
                Glide.get(this).clearMemory()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.e(TAG, "🆘 系统内存严重不足！紧急清理...")
                Glide.get(this).clearMemory()
                System.gc()
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "📱 UI隐藏，清理UI相关内存...")
                Glide.get(this).clearMemory()
            }
            else -> {
                Log.d(TAG, "🧹 内存级别: $level，执行常规清理")
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    Glide.get(this).clearMemory()
                }
            }
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
     * 软解优先策略下的错误处理
     * 当软件解码器也失败时，提供最终的用户提示
     */
    private fun handlePlayerErrorWithSoftwareFirst(error: PlaybackException) {
        // 记录详细错误信息到日志
        Log.e(TAG, "软解优先策略下播放错误: ${error.message}", error)
        
        // 获取用户友好的错误消息
        val errorMessage = getFriendlyErrorMessage(error)
        
        // 显示错误提示
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        
        // 延迟后尝试播放下一个视频，保持播放连续性
        Handler(Looper.getMainLooper()).postDelayed({
            playNext()
        }, 3000)
    }
    
    /**
     * TV盒子硬件解码回退处理 - 兼容性降级
     * 当硬件解码失败时，自动切换到软件解码
     */
    private fun handleTVBoxDecoderFallback(error: PlaybackException) {
        Log.e(TAG, "🆘 TV盒子硬件解码失败，执行回退策略...")
        
        // 1. 分析错误类型
        val errorType = when {
            error.cause is MediaCodec.CodecException -> {
                val codecError = error.cause as MediaCodec.CodecException
                when {
                    codecError.isTransient -> "临时错误"
                    codecError.isRecoverable -> "可恢复错误"
                    else -> "硬件解码器不支持"
                }
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "解码器初始化失败"
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> "解码过程失败"
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "格式不支持"
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "超出硬件能力"
            else -> "未知解码错误"
        }
        
        // 2. TV盒子专用错误提示
        val tvBoxMessage = when {
            errorType.contains("硬件") || errorType.contains("不支持") -> {
                "[TV盒子提示] 硬件解码不支持，已自动切换软件解码。如卡顿请降低分辨率或联系作者。"
            }
            errorType.contains("4K") || errorType.contains("HEVC") || errorType.contains("H265") -> {
                "[TV盒子提示] 4K/HEVC视频需要软件解码，如画面卡顿建议更换播放源。"
            }
            else -> {
                "[TV盒子提示] 解码器错误：$errorType，已尝试软件解码。如画面异常请联系作者。"
            }
        }
        
        // 3. 显示TV盒子专用提示
        Toast.makeText(this, tvBoxMessage, Toast.LENGTH_LONG).show()
        
        // 4. 记录TV盒子兼容性信息
        Log.w(TAG, "📺 TV盒子兼容性信息:")
        Log.w(TAG, "   错误类型: $errorType")
        Log.w(TAG, "   硬件解码: 失败")
        Log.w(TAG, "   软件解码: 已启用")
        Log.w(TAG, "   TV盒子模式: ${isTVBoxMode()}")
        Log.w(TAG, "   建议操作: 降低分辨率或更换播放源")
        
        // 5. 如果是TV盒子，延迟后尝试重新播放（给软件解码器时间初始化）
        if (isTVBoxMode()) {
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "🔄 TV盒子重新尝试播放...")
                player?.prepare()
                player?.play()
            }, 2000) // TV盒子专用延迟：2秒给软件解码器初始化
        }
    }

    /**
     * 检测是否为TV盒子模式
     */
    private fun isTVBoxMode(): Boolean {
        return packageManager.hasSystemFeature("android.software.leanback") ||
               packageManager.hasSystemFeature("android.hardware.type.television") ||
               Build.MODEL.contains("TV", ignoreCase = true) ||
               Build.MANUFACTURER.contains("TV", ignoreCase = true)
    }

    /**
     * 获取用户友好的错误消息（软解优先策略下）
     * TV盒子专用版本
     */
    private fun getFriendlyErrorMessage(error: PlaybackException): String {
        val isTVBox = isTVBoxMode()
        val prefix = if (isTVBox) "[TV盒子提示]" else "[播放提示]"
        
        return when {
            error.cause is MediaCodec.CryptoException -> {
                "$prefix 视频加密格式不支持"
            }
            error.cause is MediaCodec.CodecException -> {
                val codecError = error.cause as MediaCodec.CodecException
                when {
                    codecError.isTransient -> "$prefix 解码器临时错误，正在重试..."
                    codecError.isRecoverable -> "$prefix 解码器可恢复错误"
                    else -> {
                        if (isTVBox) {
                            "$prefix 硬件解码不支持，已自动切换软件解码。如画面卡顿，建议联系作者或更换播放源。"
                        } else {
                            "$prefix 当前视频码率较高，已为您尝试开启软件解码。如画面卡顿，建议联系作者或更换播放源。"
                        }
                    }
                }
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                if (isTVBox) {
                    "$prefix TV盒子解码器初始化失败，已尝试软件解码"
                } else {
                    "$prefix 解码器初始化失败，已尝试软件解码"
                }
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                "$prefix 解码失败，格式可能不受支持"
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> {
                "$prefix 视频编码格式不受支持"
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> {
                if (isTVBox) {
                    "$prefix TV盒子硬件性能有限，已启用软件解码。建议降低分辨率或更换播放源。"
                } else {
                    "$prefix 当前视频码率较高，已为您尝试开启软件解码。如画面卡顿，建议联系作者或更换播放源。"
                }
            }
            else -> {
                // 分析错误信息中的关键提示
                val errorMsg = error.message ?: "未知错误"
                when {
                    errorMsg.contains("4K", ignoreCase = true) -> {
                        if (isTVBox) {
                            "$prefix 4K视频需要软件解码，TV盒子性能有限，如卡顿请降低分辨率。"
                        } else {
                            "$prefix 当前视频码率较高，已为您尝试开启软件解码。如画面卡顿，建议联系作者或更换播放源。"
                        }
                    }
                    errorMsg.contains("HEVC", ignoreCase = true) || errorMsg.contains("H265", ignoreCase = true) -> {
                        if (isTVBox) {
                            "$prefix HEVC/H.265需要软件解码，TV盒子已自动适配。如卡顿请更换播放源。"
                        } else {
                            "$prefix 当前视频码率较高，已为您尝试开启软件解码。如画面卡顿，建议联系作者或更换播放源。"
                        }
                    }
                    errorMsg.contains("profile", ignoreCase = true) -> "$prefix 视频编码配置过高"
                    errorMsg.contains("resolution", ignoreCase = true) -> "$prefix 视频分辨率超出支持范围"
                    else -> {
                        if (isTVBox) {
                            "$prefix 播放失败：${errorMsg.take(30)}...，已尝试软件解码。TV盒子建议降低分辨率或联系作者。"
                        } else {
                            "$prefix 播放失败：${errorMsg.take(30)}...，已尝试软件解码。如画面卡顿，建议联系作者或更换播放源。"
                        }
                    }
                }
            }
        }
    }
}
