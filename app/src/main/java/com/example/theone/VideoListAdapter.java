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

        // Glide 异步加载视频第一帧
        Glide.with(holder.itemView.getContext())
                .asBitmap()
                .load(f)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(holder.ivThumb);

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if(listener != null) listener.onItemClick(f);
        });

        // TV 焦点高亮
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setBackgroundResource(R.drawable.bg_focused);
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
            } else {
                v.setBackgroundResource(0);
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
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
            tvName = itemView.findViewById(R.id.tv_name);
        }
    }
}