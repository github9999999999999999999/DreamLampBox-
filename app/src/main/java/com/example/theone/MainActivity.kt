package com.example.theone

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.EdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_READ_STORAGE = 1001
        private const val REQ_MANAGE_STORAGE = 1002
        private const val PREF_LAST_PATH = "last_path"
        private const val PREF_LAST_POS = "last_pos"
        private const val TAG = "MainActivity"
    }

    private lateinit var tvNoVideo: TextView
    private lateinit var playerView: StyledPlayerView
    private lateinit var rvVideos: RecyclerView
    private lateinit var drawerContainer: FrameLayout
    
    private val videoFiles = mutableListOf<File>()
    private var currentIndex = 0
    private lateinit var adapter: VideoListAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var gestureDetector: GestureDetector
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(this)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvNoVideo = findViewById(R.id.tv_no_video)
        playerView = findViewById(R.id.player_view)
        rvVideos = findViewById(R.id.rv_videos)
        drawerContainer = findViewById(R.id.drawer_container)

        // 设置列表容器背景为黑色
        drawerContainer.setBackgroundColor(android.graphics.Color.BLACK)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // 初始化手势检测
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e2 != null && (e2.x - e1.x) > 80 && velocityX < -200) {
                    toggleDrawer()
                    return true
                }
                return false
            }
        })

        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, REQ_MANAGE_STORAGE)
            } else {
                scanFiles()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQ_READ_STORAGE)
            } else {
                scanFiles()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_READ_STORAGE && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanFiles()
        } else {
            Toast.makeText(this, "需要存储权限才能播放", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                scanFiles()
            }
        }
    }

    private fun scanFiles() {
        Thread {
            val files = VideoScanner.scan(this)
            // 按文件名排序
            files.sortWith { f1, f2 -> f1.name.compareTo(f2.name, ignoreCase = true) }

            runOnUiThread {
                videoFiles.clear()
                videoFiles.addAll(files)
                
                // 打印文件数量
                Log.d(TAG, "扫描到视频文件数量: ${files.size}")
                
                if (videoFiles.isEmpty()) {
                    Toast.makeText(this, "路径错误：未找到视频文件", Toast.LENGTH_LONG).show()
                    showNoVideo()
                    return@runOnUiThread
                }
                
                initPlayerUI()
                // 开机首播 - 播放第一个文件
                playVideo(videoFiles[0])
            }
        }.start()
    }

    private fun initPlayerUI() {
        // 初始化 ExoPlayer
        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this
            
            // 强制绑定监听器
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "播放完成，准备下一集")
                            // 自动下一集逻辑
                            currentIndex = (currentIndex + 1) % videoFiles.size()
                            playVideo(videoFiles[currentIndex])
                        }
                        Player.STATE_READY -> {
                            Log.d(TAG, "播放器准备就绪")
                        }
                        Player.STATE_BUFFERING -> {
                            Log.d(TAG, "正在缓冲")
                        }
                        Player.STATE_IDLE -> {
                            Log.d(TAG, "播放器空闲")
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    Log.d(TAG, "播放状态改变: isPlaying = $isPlaying")
                    
                    // 修复暂停显示逻辑
                    if (isPlaying) {
                        // 播放时隐藏侧边栏
                        drawerContainer.visibility = View.GONE
                    } else {
                        // 暂停时显示侧边栏
                        drawerContainer.visibility = View.VISIBLE
                    }
                }

                override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                    super.onPlayerError(error)
                    Log.e(TAG, "播放错误: ${error.message}")
                    Toast.makeText(this@MainActivity, "播放失败，尝试下一个", Toast.LENGTH_SHORT).show()
                    playNext()
                }
            })
        }

        // 设置列表适配器
        adapter = VideoListAdapter(this, videoFiles)
        adapter.setOnItemClickListener { file ->
            saveProgress()
            currentIndex = videoFiles.indexOf(file)
            playVideo(file)
            toggleDrawer(false)
        }
        rvVideos.layoutManager = LinearLayoutManager(this)
        rvVideos.adapter = adapter
    }

    private fun playVideo(file: File) {
        tvNoVideo.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        
        player?.let { exoPlayer ->
            // 创建媒体项
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            
            // 恢复断点播放
            val lastPath = prefs.getString(PREF_LAST_PATH, "")
            val lastPos = prefs.getInt(PREF_LAST_POS, 0)
            
            // 设置媒体项并准备
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            
            // 如果是同一个文件且有断点，则恢复播放位置
            if (file.absolutePath == lastPath && lastPos > 0) {
                exoPlayer.seekTo(lastPos.toLong())
                Toast.makeText(this, "已为你恢复上次播放进度", Toast.LENGTH_SHORT).show()
            }
            
            // 开始播放
            exoPlayer.play()
            
            // 初始状态：列表必须是 GONE 隐藏状态
            drawerContainer.visibility = View.GONE
        }
    }

    private fun playNext() {
        if (videoFiles.isEmpty()) return
        currentIndex = (currentIndex + 1) % videoFiles.size()
        playVideo(videoFiles[currentIndex])
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveProgress()
        player?.release()
    }

    private fun saveProgress() {
        if (videoFiles.isEmpty() || player == null) return
        
        try {
            val currentFile = videoFiles[currentIndex]
            val position = player?.currentPosition ?: 0
            prefs.edit()
                .putString(PREF_LAST_PATH, currentFile.absolutePath)
                .putInt(PREF_LAST_POS, position.toInt())
                .apply()
            Log.d(TAG, "保存进度: ${currentFile.name} 位置: $position")
        } catch (e: Exception) {
            Log.e(TAG, "保存进度失败", e)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                toggleDrawer()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (drawerContainer.visibility == View.VISIBLE) {
                    toggleDrawer(false)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                player?.let {
                    if (it.isPlaying) {
                        it.pause()
                    } else {
                        it.play()
                    }
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun toggleDrawer() {
        toggleDrawer(drawerContainer.visibility != View.VISIBLE)
    }

    private fun toggleDrawer(open: Boolean) {
        drawerContainer.visibility = if (open) View.VISIBLE else View.GONE
        if (open) {
            rvVideos.requestFocus()
        } else {
            playerView.requestFocus()
        }
        Log.d(TAG, "侧边栏状态: ${if (open) "显示" else "隐藏"}")
    }

    private fun showNoVideo() {
        tvNoVideo.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        drawerContainer.visibility = View.GONE
    }
}