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

        // TV盒子极致内存优化 - 200x112超低分辨率，适配1GB内存
        // 关键：比320x180更极致，确保低端盒子不闪退
        File videoFile = new File(f.getAbsolutePath());
        
        // TV盒子终极内存方案：200x112超低分辨率+RGB_565双重保障
        Glide.with(holder.itemView.getContext())
                .asBitmap()
                .load(videoFile)  // 使用File对象，确保本地文件识别
                // 1. 最稳健的视频帧提取参数 - TV盒子专用
                .set(VideoDecoder.FRAME_OPTION, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                // 2. RGB_565格式：比ARGB_8888节省50%内存，TV盒子必备
                .format(DecodeFormat.PREFER_RGB_565)
                // 3. 强制使用视频解码器，确保4K兼容性
                .decode(VideoDecoder.class)
                // 4. 200x112极致降维：比320x180更节省内存，适配1GB盒子
                .override(200, 112)  // TV盒子超低分辨率，极致内存优化
                // 5. 第1秒关键帧，避免黑屏问题
                .frame(1000 * 1000)
                // 6. TV盒子专业错误处理与内存诊断
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                        Log.e("VideoListAdapter", "❌ TV盒子缩略图加载失败: " + (e != null ? e.getMessage() : "未知错误"));
                        Log.e("VideoListAdapter", "📁 文件路径: " + videoFile.getAbsolutePath());
                        Log.e("VideoListAdapter", "📊 文件存在: " + videoFile.exists() + ", 可读: " + videoFile.canRead());
                        Log.e("VideoListAdapter", "💾 内存限制: 200x112 RGB_565 (超低内存占用)");
                        Log.e("VideoListAdapter", "💡 提示: 检查TV盒子内存和Android版本兼容性");
                        return false; // 允许错误处理继续
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d("VideoListAdapter", "✅ TV盒子缩略图加载成功: " + videoFile.getName());
                        Log.d("VideoListAdapter", "📐 缩略图尺寸: " + resource.getWidth() + "x" + resource.getHeight());
                        Log.d("VideoListAdapter", "💾 内存格式: RGB_565 200x112 (超低内存)");
                        Log.d("VideoListAdapter", "🎯 适配目标: 1GB内存TV盒子");
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
