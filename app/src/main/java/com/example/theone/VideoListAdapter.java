package com.example.theone;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.util.List;

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.VH> {
    private final LayoutInflater inflater;
    private final List<File> data;
    private OnItemClickListener listener;
    private boolean isTVMode = false; // TV盒子模式开关

    public interface OnItemClickListener{ void onItemClick(File f); }

    public VideoListAdapter(Context ctx, List<File> data){
        this.inflater = LayoutInflater.from(ctx);
        this.data = data;
        // 自动检测TV盒子模式
        this.isTVMode = ctx.getPackageManager().hasSystemFeature("android.software.leanback") ||
                       ctx.getPackageManager().hasSystemFeature("android.hardware.type.television");
    }
    
    public VideoListAdapter(Context ctx, List<File> data, boolean forceTVMode){
        this.inflater = LayoutInflater.from(ctx);
        this.data = data;
        this.isTVMode = forceTVMode;
    }
    public void setOnItemClickListener(OnItemClickListener l){ this.listener = l; }
    public File getItem(int pos){ return data.get(pos); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // TV盒子模式：使用专用布局
        int layoutId = isTVMode ? R.layout.item_video_tv : R.layout.item_video;
        return new VH(inflater.inflate(layoutId, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        File f = data.get(position);
        holder.tvName.setText(f.getName());

        // 赛博佛道规范：240x135 16:9 + RGB_565 极致内存防御
        Glide.with(holder.itemView.getContext())
                .asBitmap()
                .load(f)
                .override(240, 135)  // 16:9极致降维
                .format(DecodeFormat.PREFER_RGB_565)  // 节省50%内存
                .frame(1000 * 1000)  // 取第1秒
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .placeholder(R.drawable.ic_movie_placeholder)  // 赛博佛道占位图
                .error(R.drawable.ic_movie_placeholder)
                .into(holder.ivThumb);

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if(listener != null) listener.onItemClick(f);
        });

        // TV盒子专业焦点管理 - D-Pad遥控器友好
        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);
        
        // 专业焦点变化监听器 - 商业级视觉反馈
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // 获得焦点：专业TV盒子动画
                v.setBackgroundResource(R.drawable.tv_item_focus_selector);
                v.startAnimation(android.view.animation.AnimationUtils.loadAnimation(
                    v.getContext(), R.anim.tv_focus_scale_in));
                
                // 确保焦点可见性 - 3米外观看优化
                v.setElevation(8f); // 提升层次感
                
                Log.d("VideoListAdapter", "🎯 TV盒子焦点获得: " + f.getName());
            } else {
                // 失去焦点：平滑恢复
                v.setBackgroundResource(0);
                v.startAnimation(android.view.animation.AnimationUtils.loadAnimation(
                    v.getContext(), R.anim.tv_focus_scale_out));
                
                v.setElevation(0f); // 恢复原始层级
                
                Log.d("VideoListAdapter", "👁️ TV盒子焦点失去: " + f.getName());
            }
        });
        
        // 确保初始状态正确
        if (holder.itemView.hasFocus()) {
            holder.itemView.setBackgroundResource(R.drawable.tv_item_focus_selector);
            holder.itemView.setElevation(8f);
        }
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
