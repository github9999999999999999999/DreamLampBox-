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
import android.widget.TextView
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

    private lateinit var playerView: PlayerView
    private lateinit var rvVideos: RecyclerView
    private lateinit var tvNoVideo: TextView

    private var player: ExoPlayer? = null
    private val videoFiles = ArrayList<File>()
    private var currentIndex = 0
    private var adapter: VideoListAdapter? = null

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

        // Initialize Views
        playerView = findViewById(R.id.player_view)
        rvVideos = findViewById(R.id.rv_videos)
        tvNoVideo = findViewById(R.id.tv_no_video)

        // Setup RecyclerView (Standard Vertical Scroll)
        rvVideos.layoutManager = LinearLayoutManager(this)
        
        // Hide list initially
        rvVideos.visibility = View.GONE

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
                    tvNoVideo.visibility = View.VISIBLE
                    return@runOnUiThread
                }

                adapter = VideoListAdapter(this, videoFiles)
                adapter?.setOnItemClickListener { file ->
                    currentIndex = videoFiles.indexOf(file)
                    playVideo(file)
                }
                rvVideos.adapter = adapter

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
            rvVideos.visibility = View.VISIBLE
            rvVideos.scrollToPosition(currentIndex)
        } else {
            rvVideos.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (rvVideos.visibility == View.VISIBLE) {
            rvVideos.visibility = View.GONE
            // Resume if user backs out of menu?
            if (player != null && !player!!.isPlaying) {
                player!!.play()
            }
        } else {
            super.onBackPressed()
        }
    }
}
