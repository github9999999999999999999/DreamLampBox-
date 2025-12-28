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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
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

                // Auto-play first video
                initPlayer()
                playVideo(videoFiles[0])
            }
        }
    }

    private fun initPlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(this).build()
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
                    Toast.makeText(this@MainActivity, "播放出错: ${error.message}", Toast.LENGTH_SHORT).show()
                    playNext()
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
        } else {
            rvMenu.visibility = View.GONE
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
            // 高亮当前项目
            val viewHolder = rvMenu.findViewHolderForAdapterPosition(currentIndex)
            viewHolder?.itemView?.requestFocus()
        }
    }

    private fun navigateDown() {
        if (adapter != null && currentIndex < videoFiles.size - 1) {
            currentIndex++
            rvMenu.scrollToPosition(currentIndex)
            // 高亮当前项目
            val viewHolder = rvMenu.findViewHolderForAdapterPosition(currentIndex)
            viewHolder?.itemView?.requestFocus()
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
            player?.play()
        } else {
            rvMenu.visibility = View.VISIBLE
            player?.pause()
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
}
