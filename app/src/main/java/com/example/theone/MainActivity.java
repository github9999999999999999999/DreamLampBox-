package com.example.theone;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.media.MediaPlayer;
import android.graphics.Color;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_READ_STORAGE = 1001;
    private static final int REQ_MANAGE_STORAGE = 1002;
    private static final String PREF_LAST_PATH = "last_path";
    private static final String PREF_LAST_POS   = "last_pos";

    private TextView tvNoVideo;
    private VideoView videoView;
    private RecyclerView rvVideos;
    private FrameLayout drawerContainer;

    private List<File> videoFiles = new ArrayList<>();
    private int currentIndex = 0; // 当前播放索引
    private VideoListAdapter adapter;

    private SharedPreferences prefs;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvNoVideo       = findViewById(R.id.tv_no_video);
        videoView       = findViewById(R.id.video_view);
        rvVideos        = findViewById(R.id.rv_videos);
        drawerContainer = findViewById(R.id.drawer_container);

        // 动态设置侧边栏宽度为屏幕宽度的 1/3
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int drawerWidth = metrics.widthPixels / 3;
        drawerContainer.getLayoutParams().width = drawerWidth;
        drawerContainer.requestLayout();
        drawerContainer.setVisibility(View.GONE);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 != null && e2 != null && (e2.getX() - e1.getX()) > 80 && vx < -200) {
                    toggleDrawer();
                    return true;
                }
                return false;
            }
        });

        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQ_MANAGE_STORAGE);
            } else {
                scanAndInit();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQ_READ_STORAGE);
            } else {
                scanAndInit();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_STORAGE && grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanAndInit();
        } else {
            Toast.makeText(this, "需要存储权限才能播放", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                scanAndInit();
            }
        }
    }

    // 异步扫描 + 初始化播放
    private void scanAndInit() {
        new Thread(() -> {
            List<File> files = VideoScanner.scan(this);
            // 按文件名排序
            Collections.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

            runOnUiThread(() -> {
                videoFiles.clear();
                videoFiles.addAll(files);
                if (videoFiles.isEmpty()) {
                    showNoVideo();
                    return;
                }
                initPlayerUI();
            });
        }).start();
    }

    private void initPlayerUI() {
        // 设置列表适配器
        adapter = new VideoListAdapter(this, videoFiles);
        adapter.setOnItemClickListener(file -> {
            // 点击跳转
            saveProgress(); 
            currentIndex = videoFiles.indexOf(file);
            playVideo(file);
            toggleDrawer(false);
        });
        rvVideos.setLayoutManager(new LinearLayoutManager(this));
        rvVideos.setAdapter(adapter);

        // 恢复播放逻辑
        String lastPath = prefs.getString(PREF_LAST_PATH, "");
        int lastPos = prefs.getInt(PREF_LAST_POS, 0);
        
        currentIndex = 0;
        for (int i = 0; i < videoFiles.size(); i++) {
            if (videoFiles.get(i).getAbsolutePath().equals(lastPath)) {
                currentIndex = i;
                break;
            }
        }
        
        playVideoWithResume(videoFiles.get(currentIndex), lastPos);
    }

    private void playVideo(File file) {
        playVideoWithResume(file, 0);
    }

    private void playVideoWithResume(File file, int pos) {
        tvNoVideo.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        drawerContainer.setVisibility(View.GONE);

        videoView.setVideoURI(Uri.fromFile(file));
        videoView.setOnPreparedListener(mp -> {
            
            // 确保不循环，由 OnCompletionListener 接管
            mp.setLooping(false);

            if (pos > 0) {
                videoView.seekTo(pos);
                Toast.makeText(this, "恢复播放: " + file.getName(), Toast.LENGTH_SHORT).show();
            }
            videoView.start();
            
            // 开始播放时隐藏侧边栏
            drawerContainer.setVisibility(View.GONE);
        });

        // 播放完成 -> 自动下一个
        videoView.setOnCompletionListener(mp -> {
            playNext();
            // 播放完成时显示侧边栏
            drawerContainer.setVisibility(View.VISIBLE);
        });

        // 监听视频渲染开始（去除黑屏）
        videoView.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                videoView.setBackgroundColor(Color.TRANSPARENT);
                return true;
            }
            return false;
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            handlePlayError();
            return true;
        });
    }

    private void playNext() {
        if (videoFiles.isEmpty()) return;
        currentIndex = (currentIndex + 1) % videoFiles.size();
        playVideo(videoFiles.get(currentIndex));
    }

    private void handlePlayError() {
        Toast.makeText(this, "播放失败，尝试下一个", Toast.LENGTH_SHORT).show();
        playNext();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView.isPlaying()) {
            videoView.pause();
            // 暂停时显示侧边栏
            drawerContainer.setVisibility(View.VISIBLE);
        }
        saveProgress();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveProgress();
    }

    private void saveProgress() {
        if (videoFiles.isEmpty()) return;
        File cur = videoFiles.get(currentIndex);
        int pos = videoView.getCurrentPosition();
        prefs.edit()
             .putString(PREF_LAST_PATH, cur.getAbsolutePath())
             .putInt(PREF_LAST_POS, pos)
             .apply();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleDrawer();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (drawerContainer.getVisibility() == View.VISIBLE) {
                toggleDrawer(false);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void toggleDrawer() {
        toggleDrawer(drawerContainer.getVisibility() != View.VISIBLE);
    }

    private void toggleDrawer(boolean open) {
        drawerContainer.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) {
            rvVideos.requestFocus();
        } else {
            videoView.requestFocus();
        }
    }

    private void showNoVideo() {
        tvNoVideo.setVisibility(View.VISIBLE);
        videoView.setVisibility(View.GONE);
        drawerContainer.setVisibility(View.GONE);
    }
}