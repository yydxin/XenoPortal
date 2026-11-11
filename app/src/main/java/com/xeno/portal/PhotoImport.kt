package com.xeno.portal

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import java.nio.ByteBuffer

/** Photo Picker grants access to the user-selected content URI; no broad read permission. */
object PhotoImport {
    const val MAX_BYTES=25*1024*1024
    fun load(c:Context,uri:Uri) {
        val app=c.applicationContext
        if(!Preferences.enabled(app)) {Diagnostics.stage(4,"阻断","图片导入：总开关关闭");return}
        if(uri.scheme!="content") {Diagnostics.stage(4,"阻断","图片导入仅接受系统选择的content URI");return}
        PortalActions.cancel();PortalDisplay.close(app)
        Diagnostics.stage(4,"进行中","正在读取用户选择的图片，仅复制到本机私有缓存")
        Diagnostics.io.execute {
            try {
                val bytes=app.contentResolver.openInputStream(uri)?.use {it.readNBytes(MAX_BYTES+1)}?:error("无法读取所选图片")
                require(bytes.isNotEmpty() && bytes.size<=MAX_BYTES) {"图片为空或超过25MiB，请选择较小图片"}
                val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) {decoder,info,_ ->
                    decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                    val width=info.size.width;val height=info.size.height
                    require(width>0 && height>0) {"图片尺寸无效"}
                    val scale=minOf(1.0,2048.0/maxOf(width,height))
                    decoder.setTargetSize((width*scale).toInt().coerceAtLeast(1),(height*scale).toInt().coerceAtLeast(1))
                }
                try {
                    if(!Preferences.enabled(app)) return@execute
                    val session=MediaSessions.create(app,bitmap,"照片选择 · JPEG静态图片")
                    Diagnostics.stage(4,"成功","照片导入 ${bitmap.width}x${bitmap.height} ${session.file.length()} bytes image/jpeg；动态图仅首帧，非原文件")
                    Diagnostics.main.post {if(!Preferences.enabled(app)) MediaSessions.discardIfUnshared(app,session.id)}
                } finally {bitmap.recycle()}
            } catch(e:Exception) {Diagnostics.exception(4,"导入所选图片",e)}
        }
    }
}
