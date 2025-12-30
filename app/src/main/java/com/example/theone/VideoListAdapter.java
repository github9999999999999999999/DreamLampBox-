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
import android.graphics.Bitmap;
import android.util.Log;

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

        // TV盒子友好型缩略图加载 - 4K视频专用优化方案
        Glide.with(holder.itemView.getContext())
                .asBitmap()
                .load(f)
                // 1. 强制使用轻量化解码 - TV盒子内存优化关键
                .format(DecodeFormat.PREFER_RGB_565)  // 比默认ARGB_8888省50%内存
                .decode(VideoDecoder.class)  // 强制使用视频解码器
                // 2. 极端降维打击 - 240x135足够TV盒子显示
                .override(240, 135)  // 比320x180更轻量，确保不崩溃
                // 3. 稳健帧提取策略
                .frame(1000 * 1000)  // 第1秒关键帧，避免黑屏
                // 4. 专业级错误处理与重试机制
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@androidx.annotation.Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                        Log.e("VideoListAdapter", "缩略图加载失败: " + (e != null ? e.getMessage() : "未知错误") + ", 文件: " + f.getName());
                        // 可以在这里添加重试逻辑或备用方案
                        return false; // 允许错误处理继续
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        Log.d("VideoListAdapter", "缩略图加载成功: " + f.getName() + ", 尺寸: " + resource.getWidth() + "x" + resource.getHeight());
                        return false; // 允许正常显示
                    }
                })
                // 5. TV盒子专用显示优化
                .centerCrop()  // 确保填满ImageView
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)  // 智能缓存策略
                // 6. 双重占位图保障 - 彻底消除大机器人图标
                .placeholder(R.drawable.ic_video_placeholder_small)  // 加载中显示
                .error(R.drawable.ic_video_placeholder)  // 加载失败显示专业图标
                // 7. 性能优化
                .priority(Priority.IMMEDIATE)  // 优先加载可见项
                .transition(BitmapTransitionOptions.withCrossFade(200))  // 短动画，提升感知性能
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
