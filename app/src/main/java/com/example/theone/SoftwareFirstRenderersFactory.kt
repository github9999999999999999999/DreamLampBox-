package com.example.theone

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * 梦灯盒 软解优先渲染器工厂
 * 
 * 核心策略：在低端TV盒子上，硬解4K往往导致崩溃
 * 解决方案：强制软件解码器优先于硬件解码器
 * 
 * 原理：
 * - 获取所有可用解码器
 * - 按 hardwareAccelerated 属性排序
 * - 软件解码器（hardwareAccelerated=false）排在前面
 * - 硬件解码器作为后备
 */
class SoftwareFirstRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    init {
        // 自定义MediaCodecSelector：软件解码器优先
        val softwareFirstSelector = MediaCodecSelector { 
            mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            
            val allDecoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType, 
                requiresSecureDecoder, 
                requiresTunnelingDecoder
            )
            
            // 排序：软件解码器（hardwareAccelerated=false）排在前面
            // sortedBy 返回升序，false < true，所以软解在前
            allDecoders.sortedBy { it.hardwareAccelerated }
        }
        
        // 应用自定义选择器
        setMediaCodecSelector(softwareFirstSelector)
        
        // 启用扩展渲染器模式作为双重保障
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
    }
}
