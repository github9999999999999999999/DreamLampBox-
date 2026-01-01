package com.example.theone

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File

/**
 * 梦灯盒 视频列表适配器
 * 
 * 核心优化：极致内存防御策略
 * - 强制200x112超低分辨率
 * - RGB_565格式节省50%内存
 * - 矢量占位图兜底
 * - 适配1GB RAM TV盒子
 */
class VideoAdapter(
    private val context: Context,
    private val videoFiles: List<File>
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private var onItemClickListener: ((File) -> Unit)? = null

    fun setOnItemClickListener(listener: (File) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val videoFile = videoFiles[position]
        
        holder.tvTitle.text = videoFile.name
        
        // ============================================
        // 极致内存防御 - TV盒子保命参数 (赛博佛道规范)
        // ============================================
        Glide.with(context)
            .asBitmap()
            .load(videoFile)
            .override(240, 135)                              // 16:9极致降维
            .format(DecodeFormat.PREFER_RGB_565)             // 节省50%内存
            .frame(1000000)                                  // 取第1秒
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)   // 缓存处理后资源
            .placeholder(R.drawable.ic_movie_placeholder)    // 赛博佛道占位图
            .error(R.drawable.ic_movie_placeholder)          // 错误兜底图
            .into(holder.ivThumb)
        
        // 点击事件
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(videoFile)
        }
        
        // ============================================
        // TV焦点管理 - D-Pad遥控器必备
        // ============================================
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true
        
        // 焦点变化监听：1.1倍放大效果
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // 获得焦点：放大1.1倍 + 白色边框
                v.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .start()
                v.elevation = 8f
            } else {
                // 失去焦点：恢复原始大小
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
                v.elevation = 0f
            }
        }
    }

    override fun getItemCount(): Int = videoFiles.size

    fun getItem(position: Int): File = videoFiles[position]

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumb: ImageView = itemView.findViewById(R.id.iv_thumb)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
    }
}
