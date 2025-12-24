package com.example.theone;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VideoScanner {
    private static final String[] EXTENSIONS = {
            "mp4", "mkv", "avi", "flv", "mov", "rmvb", "wmv", "3gp", "webm", "ts"
    };

    public static List<File> scan(Context ctx) {
        List<File> list = new ArrayList<>();
        // 1. 优先用 MediaStore（速度快，权限简单）
        ContentResolver cr = ctx.getContentResolver();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Video.Media.DATA};
        try (Cursor c = cr.query(uri, proj, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (c != null) {
                int col = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                while (c.moveToNext()) {
                    String path = c.getString(col);
                    if (path != null && isVideo(path)) {
                        File f = new File(path);
                        if (f.exists() && !list.contains(f)) list.add(f);
                    }
                }
            }
        }
        // 2. MediaStore 没找到时兜底扫描 /Movies 和根目录
        if (list.isEmpty()) {
            List<File> roots = Arrays.asList(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    Environment.getExternalStorageDirectory()
            );
            for (File dir : roots) {
                walk(dir, list);
                if (!list.isEmpty()) break; // 找到就停
            }
        }
        return list;
    }

    private static void walk(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] arr = dir.listFiles();
        if (arr == null) return;
        for (File f : arr) {
            if (f.isDirectory()) walk(f, out);
            else if (isVideo(f.getName())) out.add(f);
        }
    }

    private static boolean isVideo(String name) {
        if (name == null) return false;
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        for (String e : EXTENSIONS) if (e.equals(ext)) return true;
        return false;
    }
}