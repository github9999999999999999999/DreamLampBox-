package com.example.theone;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_READ = 100;
    private TextView tvNoVideo;
    private RecyclerView rvVideos;

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
        tvNoVideo = findViewById(R.id.tv_no_video);
        rvVideos = findViewById(R.id.rv_videos);

        if (hasStoragePermission()) {
            scanVideos();
        } else {
            requestStoragePermission();
        }
    }

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
            startActivity(i); // 简单跳转，用户手动开启
            // 也可以 startActivityForResult + 回调再 scan
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanVideos();
        } else {
            showNoVideo();
        }
    }

    private void scanVideos() {
        List<File> videos = VideoScanner.scan(this);
        if (videos.isEmpty()) {
            showNoVideo();
        } else {
            // TODO: 把 videos 展示到 RecyclerView
            rvVideos.setVisibility(View.VISIBLE);
            tvNoVideo.setVisibility(View.GONE);
        }
    }

    private void showNoVideo() {
        rvVideos.setVisibility(View.GONE);
        tvNoVideo.setVisibility(View.VISIBLE);
    }
}