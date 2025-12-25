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
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_READ = 100;
    private static final String PREF_LAST_PATH = "last_path";
    private static final String PREF_LAST_POS  = "last_pos";

    private TextView tvNoVideo;
    private RecyclerView rvVideos;
    private VideoView videoView;
    private File currentFile;
    private FrameLayout drawerContainer;
    private SharedPreferences prefs;
    private List<File> videoFiles;
    private boolean isDrawerOpen = false;
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

        tvNoVideo   = findViewById(R.id.tv_no_video);
        videoView   = findViewById(R.id.video_view);
        drawerContainer = findViewById(R.id.drawer_container);
        rvVideos    = findViewById(R.id.rv_videos);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // 手势：右滑打开抽屉
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null && e1.getX() - e2.getX() < -200 && Math.abs(velocityX) > 200) {
                    toggleDrawer(true);
                    return true;
                }
                return false;
            }
        });

        // 点击播放区关闭抽屉
        videoView.setOnClickListener(v -> toggleDrawer(false));

        if (hasStoragePermission()) {
            scanAndPlay();
        } else {
            requestStoragePermission();
        }
    }

    /* 权限相关保持不变 */
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(i);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanAndPlay();
        } else {
            showNoVideo();
        }
    }

    /* 扫描 + 恢复播放 */
    private void scanAndPlay() {
        videoFiles = VideoScanner.scan(this);
        if (videoFiles.isEmpty()) {
            showNoVideo();
            return;
        }
        // 初始化列表适配器（TV焦点/点击）
        VideoListAdapter adapter = new VideoListAdapter(this, videoFiles);
        adapter.setOnItemClickListener(file -> {
            playVideo(file);
            toggleDrawer(false);
        });
        rvVideos.setLayoutManager(new LinearLayoutManager(this));
        rvVideos.setAdapter(adapter);

        // 手势识别：右滑呼出抽屉
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

        // 恢复断点
        String lastPath = prefs.getString(PREF_LAST_PATH, "");
        int lastPos   = prefs.getInt(PREF_LAST_POS, 0);
        File target = null;
        for (File f : videoFiles) {
            if (f.getAbsolutePath().equals(lastPath)) {
                target = f;
                break;
            }
        }
        if (target == null) target = videoFiles.get(0);

        playVideoWithResume(target, lastPos);
    }

    /* 带恢复进度的播放 */
    private void playVideoWithResume(File file, int pos) {
        currentFile = file;
        tvNoVideo.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        drawerContainer.setVisibility(View.GONE);   // 默认收起
        isDrawerOpen = false;

        videoView.setVideoURI(Uri.fromFile(file));
        videoView.setOnPreparedListener(mp -> {
            mp.setOnInfoListener((mp1, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    videoView.setBackgroundColor(Color.TRANSPARENT);
                    return true;
                }
                return false;
            });
            if (pos > 0) {
                videoView.seekTo(pos);
                Toast.makeText(this, "已为你恢复上次播放进度", Toast.LENGTH_SHORT).show();
            }
            videoView.start();
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            handlePlayError();
            return true;
        });
    }

    /* 普通播放 */
    private void playVideo(File file) {
        playVideoWithResume(file, 0);
    }

    /* 抽屉开关 */
    private void toggleDrawer(boolean open) {
        isDrawerOpen = open;
        drawerContainer.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) {
            rvVideos.requestFocus();                 // TV 焦点
        }
    }

    /* 保存进度 */
    private void saveProgress() {
        if (videoView != null) {
            int pos = videoView.getCurrentPosition();
            String path = "";
            // 简单记录当前播放文件路径（可根据业务调整）
            if (videoView.getTag() instanceof File) {
                path = ((File) videoView.getTag()).getAbsolutePath();
            }
            prefs.edit()
                    .putString(PREF_LAST_PATH, path)
                    .putInt(PREF_LAST_POS, pos)
                    .apply();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
    }

    /* TV 菜单键打开抽屉 */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleDrawer(!isDrawerOpen);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /* 手势分发 */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private void showNoVideo() {
        tvNoVideo.setVisibility(View.VISIBLE);
        videoView.setVisibility(View.GONE);
        drawerContainer.setVisibility(View.GONE);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void toggleDrawer() {
        if (drawerContainer.getVisibility() == View.VISIBLE) {
            drawerContainer.setVisibility(View.GONE);
            videoView.requestFocus();   // 焦点还给播放器
        } else {
            drawerContainer.setVisibility(View.VISIBLE);
            rvVideos.requestFocus();
        }
    }

    private void handlePlayError() {
        Toast.makeText(this, "播放失败，尝试下一个", Toast.LENGTH_SHORT).show();
        if (videoFiles.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < videoFiles.size(); i++) {
            if (videoFiles.get(i).equals(currentFile)) {
                idx = i;
                break;
            }
        }
        int next = (idx + 1) % videoFiles.size();
        File nextFile = videoFiles.get(next);
        prefs.edit()
             .putString(PREF_LAST_PATH, nextFile.getAbsolutePath())
             .putInt(PREF_LAST_POS, 0)
             .apply();
        playVideo(nextFile);
    }
}