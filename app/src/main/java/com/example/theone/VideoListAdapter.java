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

        // Glide 异步加载视频第一帧 - 优化4K/HEVC兼容性
        Glide.with(holder.itemView.getContext())
                .asBitmap()
                .load(f)
                .centerCrop() // Explicitly centerCrop
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                // Strategy 1: "降维打击" - 强制缩小尺寸，极大减少内存占用
                .override(320, 180)  // 16:9 比例，适合TV显示
                // Strategy 1: 获取第1秒的帧，通常比第0秒更稳定
                .frame(1000 * 1000)  // 1秒，单位是微秒
                // Strategy 2: "美颜遮瑕" - 加载失败时显示美观的占位图
                .error(R.drawable.ic_video_placeholder)
                // Strategy 2: 加载中显示轻量级占位图
                .placeholder(R.drawable.ic_video_placeholder_small)
                // 添加淡入动画提升用户体验
                .transition(BitmapTransitionOptions.withCrossFade(300))
                // 设置优先级，优先加载可见项目
                .priority(Priority.IMMEDIATE)
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
