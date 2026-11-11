package com.xeno.portal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider

 data class ShareTarget(val id: String, val name: String, val packageName: String? = null)
object Sharing {
    val targets = listOf(
        ShareTarget("album", "保存到相册"), ShareTarget("wechat", "微信", "com.tencent.mm"),
        ShareTarget("qq", "QQ", "com.tencent.mobileqq"), ShareTarget("mishare", "小米互传", "com.miui.mishare.connectivity"),
        ShareTarget("chatgpt", "ChatGPT", "com.openai.chatgpt"), ShareTarget("claude", "Claude", "com.anthropic.claude"),
        ShareTarget("doubao", "豆包", "com.larus.nova"), ShareTarget("deepseek", "DeepSeek", "com.deepseek.chat")
    )
    fun installed(c: Context): List<ShareTarget> = targets.filter {
        it.packageName == null || runCatching { c.packageManager.getApplicationInfo(it.packageName, PackageManager.ApplicationInfoFlags.of(0)); true }.getOrDefault(false)
    }
    fun send(c: Context, session: MediaSession, target: ShareTarget): Boolean {
        try {
            check(MediaSessions.find(c, session.id) != null) { "会话已过期，请重新生成" }
            val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", session.file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = session.mime; putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(c.contentResolver, "XenoPortal", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val explicit = Intent(intent).apply { setPackage(target.packageName) }
            val available = target.packageName != null && explicit.resolveActivity(c.packageManager) != null
            val outgoing = if (available) explicit else Intent.createChooser(intent, "选择分享应用（${target.name} 未验证接收能力）")
            MediaSessions.markShared(c, session.id)
            if (c !is android.app.Activity) outgoing.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(outgoing)
            Diagnostics.stage(7, "已提交", "session=${session.id} target=${target.name} mime=${session.mime} route=${if (available) "包限定ACTION_SEND" else "系统分享面板降级"}；对方接收与发送结果待人工验证")
            return true
        } catch (e: Exception) { Diagnostics.exception(7, "启动分享", e); return false }
    }
}
