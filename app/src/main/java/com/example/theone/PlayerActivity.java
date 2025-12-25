package com.example.theone;

import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

import android.content.SharedPreferences;

public class PlayerActivity extends AppCompatActivity {
    private static final String PREF_LAST_PATH = "last_video_path";
    private static final String PREF_LAST_POS  = "last_video_position";

    private VideoView videoView;
    private RecyclerView rvList;
    private VideoListAdapter adapter;
    private FrameLayout sidePanel;
    private boolean isPanelShowing = false;

    private File currentFile;          // 当前播放文件
    private SharedPreferences prefs;   // 持久化存储

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        videoView = findViewById(R.id.video_view);
        rvList  = findViewById(R.id.rv_video_list);
        sidePanel = findViewById(R.id.side_panel);

        prefs = getSharedPreferences("theone_prefs", MODE_PRIVATE);

        // 取视频列表
        List<File> videos = VideoScanner.scan(this);
        if(videos.isEmpty()){
            Toast.makeText(this, "请放入视频文件", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 初始化列表
        adapter = new VideoListAdapter(this, videos);
        adapter.setOnItemClickListener(this::playVideo);
        rvList.setLayoutManager(new LinearLayoutManager(this));
        rvList.setAdapter(adapter);
        // 缓存优化
        rvList.setItemViewCacheSize(20);
        rvList.setHasFixedSize(true);
        rvList.setDrawingCacheEnabled(true);
        rvList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        // 监听播放/暂停
        videoView.setOnInfoListener((mp, what, extra) -> {
            if (what == android.media.MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT) {
                // 开始播放 -> 隐藏
                hidePanel();
            }
            return false;
        });
        videoView.setOnCompletionListener(mp -> hidePanel());

        // 点击播放区切换显示/隐藏
        findViewById(R.id.player_container).setOnClickListener(v -> togglePanel());

        // 默认播放第一个并隐藏列表
        playVideo(videos.get(0));
        hidePanel();
    }

    /* 播放指定文件 */
    public void playVideo(File f) {
        currentFile = f;                                    // 记录当前文件
        videoView.stopPlayback();
        videoView.setVideoURI(Uri.fromFile(f));
        hidePanel();

        // 恢复上次进度：等加载完成后再 seek，防止失败
        String savedPath = prefs.getString(PREF_LAST_PATH, "");
        if (f.getAbsolutePath().equals(savedPath)) {
            int pos = prefs.getInt(PREF_LAST_POS, 0);
            if (pos > 0) {
                // 监听 prepared 事件，确保视频已加载
                videoView.setOnPreparedListener(mp -> {
                    mp.seekTo(pos);
                    mp.start();
                    Toast.makeText(this, "已为你恢复上次播放进度", Toast.LENGTH_SHORT).show();
                });
                return; // 提前返回，避免下面再次 start()
            }
        }
        // 无进度或新文件：直接播放
        videoView.start();
    }

    /* 遥控器 OK 键播放当前焦点条目 */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            View focused = rvList.findFocus();
            if (focused != null) {
                int pos = rvList.getChildAdapterPosition(focused);
                if (pos != RecyclerView.NO_POSITION) {
                    File f = adapter.getItem(pos);
                    if (f != null) playVideo(f);
                }
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    /* 面板动画 */
    private void togglePanel() {
        if (isPanelShowing) hidePanel(); else showPanel();
    }
    private void showPanel() {
        sidePanel.animate()
                .translationX(0)
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        isPanelShowing = true;
    }
    private void hidePanel() {
        sidePanel.animate()
                .translationX(sidePanel.getWidth())
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        isPanelShowing = false;
    }

    /* ============== 断点续播 ============== */

    /* 保存播放进度 */
    private void saveProgress() {
        if (currentFile != null && videoView != null) {
            int pos = videoView.getCurrentPosition();
            prefs.edit()
                 .putString(PREF_LAST_PATH, currentFile.getAbsolutePath())
                 .putInt(PREF_LAST_POS, pos)
                 .apply();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
    }
}