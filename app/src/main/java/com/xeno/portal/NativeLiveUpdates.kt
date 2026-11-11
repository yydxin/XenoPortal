package com.xeno.portal

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat

/** Public Android notification promotion only; no MIUI payload, media impersonation or FGS. */
object NativeLiveUpdates {
    const val NOTIFICATION_ID = 201
    const val PROMOTION_PERMISSION = "android.permission.POST_PROMOTED_NOTIFICATIONS"
    data class Capability(val supported: Boolean, val allowed: Boolean?, val detail: String)
    var capability by mutableStateOf(Capability(false, null, "尚未查询系统实时通知权限"))
        private set
    var status by mutableStateOf("尚未请求系统实时通知")
        private set
    var observation by mutableStateOf("尚未读取系统提升标志")
        private set
    private var generation = 0
    private var expiration: Runnable? = null

    private fun readCapability(c: Context): Capability {
        if (Build.VERSION.SDK_INT < 36) return Capability(false, false, "API=${Build.VERSION.SDK_INT}；Android 16实时通知入口不可用")
        return try {
            val nm = c.getSystemService(NotificationManager::class.java)
            val allowed = nm.canPostPromotedNotifications()
            val permissionKnown = try { c.packageManager.getPermissionInfo(PROMOTION_PERMISSION,0); true }
                catch (_: PackageManager.NameNotFoundException) { false }
            Capability(true, allowed, "API=${Build.VERSION.SDK_INT} canPostPromotedNotifications=$allowed permissionKnown=$permissionKnown（该权限在36.1加入，旧系统可能没有）；OEM可能有额外条件")
        } catch (e: Exception) { Capability(true, null, "系统实时通知查询失败:${e.javaClass.simpleName}:${e.message}") }
    }
    fun refresh(c: Context) {
        val app=c.applicationContext
        Diagnostics.io.execute {
            val result=readCapability(app)
            Diagnostics.main.post { capability=result; Diagnostics.log("NATIVE_CAPABILITY",result.detail) }
        }
    }
    fun settingsIntent(c: Context): Intent {
        if (Build.VERSION.SDK_INT >= 36) {
            val intent=Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName)
            if (intent.resolveActivity(c.packageManager)!=null) return intent
            Diagnostics.log("NATIVE_SETTINGS","本ROM没有公开的实时通知设置Activity，退回应用通知设置")
        }
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName)
    }
    internal fun buildNotification(c: Context, session: MediaSession, seconds: Int): Notification {
        fun pending(target: String): PendingIntent = PendingIntent.getActivity(c,0,
            Intent(c,ShareActivity::class.java).apply {
                data=Uri.parse("xenoportal://native/${session.id}/$target")
                putExtra("session",session.id);putExtra("target",target);putExtra("source","android-live-update")
            },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder=NotificationCompat.Builder(c,PortalDisplay.CHANNEL)
            .setSmallIcon(R.drawable.ic_portal)
            .setContentTitle("XenoPortal 系统岛验证")
            .setContentText("你发起的系统显示验证正在进行，30秒后结束；点击查看测试图片")
            .setOngoing(true).setAutoCancel(false).setColorized(false)
            .setRequestPromotedOngoing(true).setShortCriticalText("验证中")
            .setContentIntent(pending("more")).setTimeoutAfter(seconds*1000L)
        Sharing.installed(c).take(3).forEach { target ->
            builder.addAction(R.drawable.ic_portal,target.name,pending(target.id))
        }
        val standard=builder.build()
        if (Build.VERSION.SDK_INT>=36 && !standard.hasPromotableCharacteristics()) {
            // Android16/QPR2 AOSP requires colorized; newer framework rejects it.
            // Ask the installed framework, without claiming promotion or spoofing an FGS.
            val legacy=builder.setColorized(true).build()
            if (legacy.hasPromotableCharacteristics()) return legacy
        }
        return standard
    }
    fun show(c: Context, seconds: Int = 30) {
        val app=c.applicationContext
        if (!Preferences.enabled(app)) { Diagnostics.stage(5,"阻断","系统实时通知：总开关关闭");return }
        val session=MediaSessions.current
        if (session==null || MediaSessions.find(app,session.id)==null) { Diagnostics.stage(5,"阻断","先生成新的测试图");return }
        PortalDisplay.close(app)
        val ticket=++generation
        status="正在检查系统实时通知权限"
        Diagnostics.io.execute {
            val result=readCapability(app)
            Diagnostics.main.post {
                if(ticket!=generation || !Preferences.enabled(app) || MediaSessions.current?.id!=session.id) return@post
                capability=result
                val nm=app.getSystemService(NotificationManager::class.java)
                if (!result.supported || result.allowed!=true) {
                    status="系统实时通知未允许或不可用；未使用自绘窗口"
                    Diagnostics.stage(5,"阻断","$status；${result.detail}；请检查系统实时通知权限入口")
                    return@post
                }
                if (!nm.areNotificationsEnabled()) { status="通知总开关关闭";Diagnostics.stage(5,"阻断",status);return@post }
                try {
                    PortalDisplay.channel(app)
                    val n=buildNotification(app,session,seconds)
                    val eligible=if(Build.VERSION.SDK_INT>=36) n.hasPromotableCharacteristics() else false
                    Diagnostics.log("NATIVE_BUILD","eligible=$eligible colorized=${n.extras.getBoolean(Notification.EXTRA_COLORIZED)} ongoing=${n.flags and Notification.FLAG_ONGOING_EVENT!=0}；仅请求，未手动设置FLAG_PROMOTED_ONGOING；无miui.focus.param")
                    if(!eligible) { status="通知未满足系统提升结构要求";Diagnostics.stage(5,"阻断",status);return@post }
                    nm.notify(NOTIFICATION_ID,n)
                    status="已请求系统实时通知；等待系统结果"
                    observation="尚未读取系统返回标志"
                    Diagnostics.stage(5,"已提交","Android Live Updates；${result.detail}；eligible=$eligible session=${session.id}；不等于已出现官方岛")
                    listOf(500L,1500L).forEach { delay -> Diagnostics.main.postDelayed({
                        if(ticket==generation) observe(app,delay)
                    },delay) }
                    expiration=Runnable {
                        if(ticket==generation) { cancel(app);MediaSessions.discardIfUnshared(app,session.id);Diagnostics.log("NATIVE_END","系统显示验证已结束，显式取消通知") }
                    }.also { Diagnostics.main.postDelayed(it,seconds*1000L) }
                } catch(e:Exception) { status="系统实时通知请求失败";Diagnostics.exception(5,status,e) }
            }
        }
    }
    private fun observe(c: Context, delay: Long) {
        try {
            val nm=c.getSystemService(NotificationManager::class.java)
            val entries=nm.activeNotifications.filter {it.id==NOTIFICATION_ID}
            val promoted=Build.VERSION.SDK_INT>=36 && entries.any {it.notification.flags and Notification.FLAG_PROMOTED_ONGOING!=0}
            observation="${delay}ms后 active=${entries.size} promoted=$promoted flags=${entries.firstOrNull()?.notification?.flags}；Android已提升也仍需确认HyperOS显示形态"
            status=when { promoted -> "系统返回已提升标志";entries.isNotEmpty()->"系统已登记，尚未返回提升标志";else->"系统活跃列表未发现本通知" }
            Diagnostics.stage(5,when {promoted->"系统已提升";entries.isNotEmpty()->"仅登记";else->"未登记"},"$status；$observation；${capability.detail}")
        } catch(e:Exception) { observation="读取提升标志失败";Diagnostics.exception(5,observation,e) }
    }
    fun cancel(c: Context) {
        generation++
        expiration?.let(Diagnostics.main::removeCallbacks);expiration=null
        runCatching {c.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)}
            .onFailure {Diagnostics.exception(5,"取消系统实时通知",it)}
        status="未显示（已取消或尚未请求）"
    }
}
