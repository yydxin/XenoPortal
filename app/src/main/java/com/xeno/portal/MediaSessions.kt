package com.xeno.portal

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID

 data class MediaSession(val id: String, val file: File, val label: String) {
    val mime = "image/jpeg"
}

object MediaSessions {
    const val LEASE_MS = 10 * 60 * 1000L
    var current by mutableStateOf<MediaSession?>(null)
        private set
    private fun directory(c: Context) = File(c.cacheDir, "sessions").apply { mkdirs() }
    fun find(c: Context, id: String): MediaSession? {
        if (!ProbePolicy.validSessionId(id)) return null
        val f = File(directory(c), "$id.jpg")
        return if (f.isFile && f.length() > 0 && System.currentTimeMillis() - f.lastModified() < LEASE_MS)
            MediaSession(id, f, "本地会话") else null
    }
    fun create(c: Context, bitmap: Bitmap, label: String): MediaSession {
        val id = UUID.randomUUID().toString().replace("-", "")
        val f = File(directory(c), "$id.jpg")
        try { f.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) } }
        catch (e: Exception) { f.delete(); throw e }
        val session = MediaSession(id, f, label)
        Diagnostics.main.post {
            current?.let { discardIfUnshared(c, it.id) }
            current = session
            cleanup(c.applicationContext)
        }
        return session
    }
    fun sample(c: Context) {
        Diagnostics.io.execute {
            try {
                val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(15, 35, 48))
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(118, 212, 202) }
                canvas.drawCircle(400f, 330f, 190f, p)
                p.color = Color.rgb(15, 35, 48); canvas.drawCircle(400f, 330f, 120f, p)
                p.color = Color.WHITE; p.textSize = 58f; p.textAlign = Paint.Align.CENTER
                canvas.drawText("XenoPortal", 400f, 620f, p)
                p.textSize = 28f; canvas.drawText("TEST IMAGE · 本地合成测试图", 400f, 680f, p)
                val session = create(c, bitmap, "合成测试图（非抖音提取）"); bitmap.recycle()
                Diagnostics.stage(4, "成功", "session=${session.id} 生成合成测试图片 ${session.file.length()} bytes；仅用于显示/分享测试")
            } catch (e: Exception) { Diagnostics.exception(4, "生成测试图片", e) }
        }
    }
    fun markShared(c: Context, id: String) {
        check(ProbePolicy.validSessionId(id))
        File(directory(c), "$id.lease").writeText("shared")
        Diagnostics.log("CACHE", "session=$id 分享 URI 租期最长10分钟；接收端无通用完成回执")
    }
    fun discardIfUnshared(c: Context, id: String) {
        if (!ProbePolicy.validSessionId(id)) return
        if (!File(directory(c), "$id.lease").exists()) {
            File(directory(c), "$id.jpg").delete()
            if (current?.id == id) current = null
        }
    }
    private var cleanupTask: Runnable? = null
    fun cleanup(context: Context, all: Boolean = false) {
        val c = context.applicationContext
        directory(c).listFiles()?.forEach { if (all || System.currentTimeMillis() - it.lastModified() >= LEASE_MS) it.delete() }
        if (current?.file?.exists() == false) Diagnostics.main.post { current = null }
        cleanupTask?.let(Diagnostics.main::removeCallbacks)
        cleanupTask = Runnable {
            directory(c).listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() >= LEASE_MS) it.delete() }
            if (current?.file?.exists() == false) current = null
        }.also { Diagnostics.main.postDelayed(it, LEASE_MS) }
    }
    fun saveToAlbum(c: Context, session: MediaSession, done: (Boolean) -> Unit) {
        Diagnostics.io.execute {
            var uri: android.net.Uri? = null
            try {
                check(find(c, session.id) != null) { "会话过期" }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "XenoPortal_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, session.mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XenoPortal")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                uri = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert 返回 null")
                c.contentResolver.openOutputStream(uri)?.use { out -> session.file.inputStream().use { it.copyTo(out) } } ?: error("无法写入相册")
                val updated = c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                check(updated == 1) { "无法结束相册 pending 状态" }
                Diagnostics.stage(7, "成功", "本应用图片写入 Pictures/XenoPortal；相册可见性仍请人工检查")
                Diagnostics.main.post { done(true) }
            } catch (e: Exception) {
                uri?.let { runCatching { c.contentResolver.delete(it, null, null) } }
                Diagnostics.exception(7, "保存相册", e); Diagnostics.main.post { done(false) }
            }
        }
    }
}
