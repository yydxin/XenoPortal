package com.xeno.portal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** An explicit notification action: saves locally without launching any Activity. */
class AlbumSaveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        val id = intent.getStringExtra("session").orEmpty()
        val source = intent.getStringExtra("source").orEmpty()
        if (!Preferences.enabled(app) || !ProbePolicy.validSessionId(id)) {
            Diagnostics.stage(6,"阻断","后台保存：总开关关闭或会话ID无效")
            pending.finish(); return
        }
        Diagnostics.stage(6,"成功","Broadcast 回调 source=$source session=$id target=album；不启动Activity")
        // Cancel the expiry before asynchronous I/O so it cannot delete this session mid-save.
        PortalDisplay.close(app)
        val session = MediaSessions.find(app,id)
        if (session == null) {
            Diagnostics.stage(7,"阻断","后台保存：会话已过期或已经保存并清理")
            Toast.makeText(app,"图片已过期或已保存，请查看相册",Toast.LENGTH_SHORT).show()
            pending.finish(); return
        }
        try {
            MediaSessions.saveToAlbum(app,session) { result ->
                try {
                    val message = when(result) {
                        AlbumSaveResult.SAVED -> "已保存到 Pictures/XenoPortal"
                        AlbumSaveResult.BUSY -> "正在保存，请勿重复点击"
                        AlbumSaveResult.FAILED -> "保存失败，请查看诊断 S7"
                    }
                    Toast.makeText(app,message,Toast.LENGTH_SHORT).show()
                    if (result == AlbumSaveResult.SAVED) MediaSessions.discardIfUnshared(app,id)
                } finally { pending.finish() }
            }
        } catch(e:Exception) {
            Diagnostics.exception(7,"后台保存回调",e); pending.finish()
        }
    }
    companion object {
        const val ACTION = "com.xeno.portal.SAVE_ALBUM"
        fun pending(c:Context, session:MediaSession, source:String): PendingIntent =
            PendingIntent.getBroadcast(c,0,Intent(c,AlbumSaveReceiver::class.java).apply {
                action=ACTION
                data=Uri.parse("xenoportal://album/${session.id}")
                putExtra("session",session.id);putExtra("source",source)
            },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
