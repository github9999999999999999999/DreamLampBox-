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
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
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

    private lateinit var drawerLayout: DrawerLayout
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
        drawerLayout = findViewById(R.id.drawer_layout)
        playerView = findViewById(R.id.player_view)
        rvVideos = findViewById(R.id.rv_videos)
        tvNoVideo = findViewById(R.id.tv_no_video)

        // Immersive Fullscreen Mode
        val windowInsetsController = ViewCompat.getWindowInsetsController(window.decorView)
        windowInsetsController?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        
        // Ensure content extends into cutouts/system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout)) { v, insets ->
            insets
        }

        // Setup RecyclerView (Standard Vertical Scroll)
        rvVideos.layoutManager = LinearLayoutManager(this)
        
        // Setup Drawer Listener to auto-resume when drawer is closed manually
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                super.onDrawerClosed(drawerView)
                // If user swipes close, resume playback
                if (player != null && !player!!.isPlaying) {
                    player!!.play()
                }
            }
        })

        // Disable swipe to open?
        // User wants "On Pause: List Visible". "On Play: List Gone".
        // Usually we lock the drawer when playing so user doesn't accidentally swipe it out?
        // But user said "右侧菜单必须是一个浮在视频上方的 覆盖层".
        // Let's keep it unlocked for now, or maybe LOCK_CLOSED when playing?
        // "菜单划出时，视频底图不动".
        // To be safe, let's allow swipe.

        checkPermissions()
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
            // Call Java helper
            val files = VideoScanner.scan(this)
            // Sort by name
            Collections.sort(files) { f1, f2 -> f1.name.compareTo(f2.name, ignoreCase = true) }

            runOnUiThread {
                videoFiles.clear()
                videoFiles.addAll(files)

                if (videoFiles.isEmpty()) {
                    tvNoVideo.visibility = View.VISIBLE
                    return@runOnUiThread
                }

                // Setup Adapter
                adapter = VideoListAdapter(this, videoFiles)
                adapter?.setOnItemClickListener { file ->
                    // Click item in list -> Play selected video
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
                        Log.d(TAG, "Playback Ended -> Auto-playing next")
                        playNext()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d(TAG, "isPlaying: $isPlaying")
                    updateListVisibility(!isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player Error: ${error.message}")
                    Toast.makeText(this@MainActivity, "播放出错: ${error.message}", Toast.LENGTH_SHORT).show()
                    playNext()
                }
            })
        }
    }

    private fun playVideo(file: File) {
        if (player == null) initPlayer()

        Log.d(TAG, "Playing: ${file.name}")
        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play() // This will trigger onIsPlayingChanged(true) -> Hide List
    }

    private fun playNext() {
        if (videoFiles.isEmpty()) return
        currentIndex = (currentIndex + 1) % videoFiles.size
        playVideo(videoFiles[currentIndex])
    }

    private fun updateListVisibility(showList: Boolean) {
        if (showList) {
            // State: PAUSED -> Open Drawer (Overlay)
            drawerLayout.openDrawer(GravityCompat.END)
            rvVideos.scrollToPosition(currentIndex)
        } else {
            // State: PLAYING -> Close Drawer
            drawerLayout.closeDrawer(GravityCompat.END)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    // Handle Back Press to close drawer or exit
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
            // Optional: Resume playback if user closes menu via back?
            // The DrawerListener will handle resume if we close it here?
            // Yes, closeDrawer() triggers onDrawerClosed() which resumes.
        } else {
            super.onBackPressed()
        }
    }
}
