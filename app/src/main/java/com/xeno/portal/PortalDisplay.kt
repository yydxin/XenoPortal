package com.xeno.portal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject

 data class FocusProbe(val protocol: Int = 0, val permission: Boolean? = null, val notifications: Boolean = false, val detail: String = "尚未查询")
object PortalDisplay {
    const val CHANNEL = "xeno_share_probe_v1"
    const val NOTIFICATION_ID = 200
    var probe by mutableStateOf(FocusProbe())
        private set
    var lastSubmission by mutableStateOf("尚未提交显示请求")
        private set
    var lastObservation by mutableStateOf("尚未检查通知是否登记")
        private set
    var visualConfirmation by mutableStateOf("官方超级岛实际显示尚未确认")
        private set
    fun confirmVisual(result: String) {
        visualConfirmation = result
        Diagnostics.log("VISUAL_CONFIRM", "$result；最近请求=$lastSubmission；最近观察=$lastObservation")
    }
    var visible by mutableStateOf("未显示")
        private set
    private var expiration: Runnable? = null
    private var generation = 0
    fun channel(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "XenoPortal 分享诊断", NotificationManager.IMPORTANCE_HIGH).apply { description = "仅在用户点击测试显示时发送，8秒收起" }
        )
    }
    fun query(c: Context, after: (() -> Unit)? = null) {
        Diagnostics.io.execute {
            var protocol = 0
            var permission: Boolean? = null
            var error = ""
            try { protocol = Settings.System.getInt(c.contentResolver, "notification_focus_protocol", 0) }
            catch (e: Exception) { error += "协议查询:${e.javaClass.simpleName};" }
            try {
                val result = c.contentResolver.call(Uri.parse("content://miui.statusbar.notification.public"), "canShowFocus", null, Bundle().apply { putString("package", c.packageName) })
                permission = if (result?.containsKey("canShowFocus") == true) result.getBoolean("canShowFocus") else null
                if (permission == null) error += "权限Bundle为空/缺字段;"
            } catch (e: Exception) { error += "权限查询:${e.javaClass.simpleName}:${e.message};" }
            val nm = c.getSystemService(NotificationManager::class.java)
            val granted = nm.areNotificationsEnabled() && nm.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
            val result = FocusProbe(protocol, permission, granted, "protocol=$protocol canShowFocus=$permission notification=$granted channel=$CHANNEL ${error.ifBlank { "查询结束；不代表签名鉴权或渲染成功" }}")
            Diagnostics.main.post {
                probe = result; Diagnostics.log("CAPABILITY", result.detail); after?.invoke()
            }
        }
    }
    fun show(c: Context, officialOnly: Boolean = false, observationSeconds: Int = 8) {
        if (!Preferences.enabled(c)) { Diagnostics.stage(5, "阻断", "总开关关闭"); return }
        val session = MediaSessions.current
        if (session == null || MediaSessions.find(c, session.id) == null) { Diagnostics.stage(5, "阻断", "先生成测试图或手动截图"); return }
        channel(c)
        val request = ++generation
        query(c) {
            if (generation != request || !Preferences.enabled(c) || MediaSessions.current?.id != session.id) return@query
            close(c)
            if (officialOnly || (!Preferences.forceOverlay(c) && probe.notifications && probe.permission == true && probe.protocol >= 2)) {
                if (!probe.notifications) { Diagnostics.stage(5, "阻断", "通知权限/渠道关闭，请先授权"); return@query }
                try {
                    official(c, session, observationSeconds)
                    visible = "官方探针已提交，是否上岛待人工确认"
                } catch (e: Exception) {
                    Diagnostics.exception(5, "通知构建/发送", e)
                    if (!officialOnly) showOverlay(c, session)
                }
            } else showOverlay(c, session)
            if (visible != "未显示") {
                expiration = Runnable {
                    close(c); MediaSessions.discardIfUnshared(c, session.id)
                    Diagnostics.log("DISPLAY", "${observationSeconds}秒到期：取消通知/窗口，清理未分享会话")
                }.also { Diagnostics.main.postDelayed(it, observationSeconds * 1000L) }
            }
        }
    }
    private fun pending(c: Context, session: MediaSession, target: String): PendingIntent {
        val intent = Intent(c, ShareActivity::class.java).apply {
            data = Uri.parse("xenoportal://session/${session.id}/$target")
            putExtra("session", session.id); putExtra("target", target); putExtra("source", "official-notification")
        }
        return PendingIntent.getActivity(c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun open(c: Context, session: MediaSession, target: String, source: String = "manual-tools") {
        try {
            c.startActivity(Intent(c, ShareActivity::class.java).apply {
                putExtra("session", session.id); putExtra("target", target); putExtra("source", source); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            close(c)
        } catch (e: Exception) { Diagnostics.exception(6, "打开分享目标页", e) }
    }
    private fun official(c: Context, session: MediaSession, seconds: Int) {
        val thumb = BitmapFactory.decodeFile(session.file.path)?.let { bitmap ->
            android.graphics.Bitmap.createScaledBitmap(bitmap, 224, 224, true).also { if (it !== bitmap) bitmap.recycle() }
        } ?: error("缩略图无法解码")
        val pics = Bundle().apply { putParcelable("miui.focus.pic_preview", Icon.createWithBitmap(thumb)) }
        val nativeActions = Bundle()
        val actions = JSONArray()
        Sharing.installed(c).take(3).forEach { target ->
            val icon = target.packageName?.let { pkg -> runCatching { Icon.createWithBitmap(c.packageManager.getApplicationIcon(pkg).toBitmap(80,80)) }.getOrNull() }
                ?: Icon.createWithResource(c, R.drawable.ic_portal)
            val key = "miui.focus.action_${target.id}"
            nativeActions.putParcelable(key, Notification.Action.Builder(icon, target.name, pending(c, session, target.id)).build())
            actions.put(JSONObject().put("type", 0).put("action", key).put("clickWithCollapse", true))
        }
        val image = JSONObject().put("type", 1).put("pic", "miui.focus.pic_preview")
        val island = JSONObject().put("islandProperty", 2).put("islandTimeout", seconds)
            .put("bigIslandArea", JSONObject().put("imageTextInfoLeft", JSONObject().put("type", 1).put("picInfo", image).put("textInfo", JSONObject().put("title", "分享").put("content", "测试"))))
            .put("smallIslandArea", JSONObject().put("picInfo", image))
        val param = JSONObject().put("protocol", 1).put("business", "xeno_share_probe")
            .put("islandFirstFloat", true).put("enableFloat", false).put("updatable", false)
            .put("filterWhenNoPermission", false).put("ticker", "分享测试").put("aodTitle", "XenoPortal 测试")
            .put("param_island", island).put("actions", actions)
        // Template 12: static chatInfo + ordinary actions is documented for OS2 and OS3.
        // Avoid animation enum discrepancies in the OS3-only iconTextInfo example.
        param.put("chatInfo", JSONObject().put("title", "分享图片").put("content", "点卡片查看全部目标").put("picProfile", "miui.focus.pic_preview"))
        val json = JSONObject().put("param_v2", param).toString()
        check(json.toByteArray(Charsets.UTF_8).size <= 3072) { "payload 超过3072字节" }
        val extras = Bundle().apply {
            putString("miui.focus.param", json); putBundle("miui.focus.pics", pics); putBundle("miui.focus.actions", nativeActions)
        }
        val builder = Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_portal)
            .setContentTitle("XenoPortal 分享测试").setContentText("点击选择分享目标；官方岛实际渲染待验证")
            .setContentIntent(pending(c, session, "more")).setAutoCancel(true).setTimeoutAfter(seconds * 1000L).addExtras(extras)
        c.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, builder.build())
        lastSubmission = "官方通知 session=${session.id} template=12 id=$NOTIFICATION_ID duration=${seconds}s"
        lastObservation = "等待500ms检查系统登记"
        visualConfirmation = "本次官方超级岛实际显示尚未确认"
        Diagnostics.stage(5, "已提交", "${probe.detail}；$lastSubmission；notify未抛异常；无平台APP_ID，是否上岛待验证")
        val ticket = generation
        val app = c.applicationContext
        Diagnostics.main.postDelayed({
            if (ticket == generation) {
                try {
                    val nm = app.getSystemService(NotificationManager::class.java)
                    val entries = nm.activeNotifications.filter { it.id == NOTIFICATION_ID }
                    val importance = nm.getNotificationChannel(CHANNEL)?.importance
                    lastObservation = "500ms后本应用活跃通知=${entries.size} 渠道importance=$importance；不等于已渲染超级岛"
                    Diagnostics.stage(5, if (entries.isEmpty()) "未观察到登记" else "系统已登记", "$lastSubmission；$lastObservation")
                } catch (e: Exception) {
                    lastObservation = "读取本应用活跃通知失败:${e.javaClass.simpleName}"
                    Diagnostics.exception(5,"活跃通知检查",e)
                }
            }
        },500)

    }
    private fun showOverlay(c: Context, session: MediaSession) {
        val service = PortalAccessibilityService.active
        if (service == null) { Diagnostics.stage(5, "阻断", "需无障碍服务实际连接才能显示覆盖窗口；通知探测=${probe.detail}"); return }
        try {
            val density = service.resources.displayMetrics.density
            fun dp(n: Int) = (n*density).toInt()
            val panel = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply { setColor(Color.rgb(20,25,32)); cornerRadius = dp(28).toFloat() }
            }
            val titleRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            titleRow.addView(ImageView(service).apply { setImageBitmap(BitmapFactory.decodeFile(session.file.path)); scaleType = ImageView.ScaleType.CENTER_CROP }, LinearLayout.LayoutParams(dp(48),dp(48)))
            titleRow.addView(TextView(service).apply { text = service.getString(R.string.overlay_title); setTextColor(Color.WHITE); setPadding(dp(12),0,0,0) }, LinearLayout.LayoutParams(0,dp(52),1f))
            titleRow.addView(Button(service).apply { text="关闭"; setOnClickListener { close(c); MediaSessions.discardIfUnshared(c,session.id) } })
            panel.addView(titleRow)
            val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
            Sharing.installed(c).forEach { target -> row.addView(Button(service).apply { text = target.name; setOnClickListener { open(service, session, target.id, "accessibility-overlay") } }) }
            panel.addView(HorizontalScrollView(service).apply { addView(row) })
            val width = (service.resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(200))
            val params = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y=dp(36)
            }
            service.attachOverlayWindow(panel, params)
            visible = "自绘岛（无障碍覆盖窗口）"
            lastSubmission = "无障碍覆盖窗口 session=${session.id}"
            Diagnostics.stage(5, "已显示请求", "${ProbePolicy.displayRoute(probe.protocol,probe.permission,probe.notifications,Preferences.forceOverlay(c))}；addView未抛异常，实际可见性需人工确认")
        } catch (e: Exception) { Diagnostics.exception(5, "无障碍覆盖窗口", e) }
    }
    fun close(c: Context) {
        NativeLiveUpdates.cancel(c)
        generation++ // Invalidates any in-flight asynchronous show request.
        expiration?.let(Diagnostics.main::removeCallbacks); expiration = null
        PortalAccessibilityService.active?.hideOverlayWindow()
        runCatching { c.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }.onFailure { Diagnostics.exception(5,"取消通知",it) }
        visible = "未显示"
    }
}
