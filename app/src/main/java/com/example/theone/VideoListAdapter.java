package com.example.theone;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.VideoDecoder;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.engine.GlideException;
import android.graphics.Bitmap;
import android.util.Log;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.util.List;

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.VH> {
    private final List<File> data;
    private final LayoutInflater inflater;
    private OnItemClickListener listener;

    public interface OnItemClickListener{ void onItemClick(File f); }

    public VideoListAdapter(Context ctx, List<File> data){
        this.inflater = LayoutInflater.from(ctx);
        this.data = data;
    }
    public void setOnItemClickListener(OnItemClickListener l){ this.listener = l; }
    public File getItem(int pos){ return data.get(pos); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(inflater.inflate(R.layout.item_video, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        File f = data.get(position);
        holder.tvName.setText(f.getName());

        // 使用File对象加载 - Android 14本地文件最稳健方案
        // 关键：使用File而非String路径，确保Glide正确识别为本地文件
        File videoFile = new File(f.getAbsolutePath());
        
        // Android 14终极优化：4K本地视频帧提取专用方案
        Glide.with(holder.itemView.getContext())
                .asBitmap()
                .load(videoFile)  // 使用File对象，确保本地文件识别
                // 1. 强制使用最稳健的视频帧提取参数
                .set(VideoDecoder.FRAME_OPTION, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                // 2. RGB_565格式：比ARGB_8888节省50%内存，防止4K帧OOM
                .format(DecodeFormat.PREFER_RGB_565)
                // 3. 强制使用视频解码器，确保4K兼容性
                .decode(VideoDecoder.class)
                // 4. 320x180降维打击：平衡质量与内存占用
                .override(320, 180)
                // 5. 第1秒关键帧，避免黑屏问题
                .frame(1000 * 1000)
                // 6. 专业级错误处理与权限诊断
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                        Log.e("VideoListAdapter", "❌ 本地视频缩略图加载失败: " + (e != null ? e.getMessage() : "未知错误"));
                        Log.e("VideoListAdapter", "📁 文件路径: " + videoFile.getAbsolutePath());
                        Log.e("VideoListAdapter", "📊 文件存在: " + videoFile.exists() + ", 可读: " + videoFile.canRead());
                        Log.e("VideoListAdapter", "💡 提示: 请检查Android 14 READ_MEDIA_VIDEO权限是否授予");
                        return false; // 允许错误处理继续
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d("VideoListAdapter", "✅ 本地视频缩略图加载成功: " + videoFile.getName());
                        Log.d("VideoListAdapter", "📐 缩略图尺寸: " + resource.getWidth() + "x" + resource.getHeight());
                        Log.d("VideoListAdapter", "💾 内存格式: RGB_565 (节省50%内存)");
                        return false; // 允许正常显示
                    }
                })
                // 7. TV盒子专用显示优化
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                // 8. 双重占位图保障 - 专业图标体系
                .placeholder(R.drawable.ic_video_placeholder_small)
                .error(R.drawable.ic_video_placeholder)
                // 9. 性能优化 - 短动画提升感知性能
                .priority(Priority.IMMEDIATE)
                .transition(BitmapTransitionOptions.withCrossFade(150))  // 更短动画，TV盒子优化
                .into(holder.ivThumb);

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if(listener != null) listener.onItemClick(f);
        });

        // 添加焦点变化监听器，确保视觉反馈
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // 获得焦点时的视觉反馈
                v.setBackgroundResource(R.drawable.bg_focused);
            } else {
                // 失去焦点时恢复默认背景
                v.setBackgroundResource(0);
            }
        });
        
        // 让 item 可获取焦点
        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder{
        ImageView ivThumb;
        TextView tvName;
        VH(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_thumb);
            tvName = itemView.findViewById(R.id.tv_title);
        }
    }
}
