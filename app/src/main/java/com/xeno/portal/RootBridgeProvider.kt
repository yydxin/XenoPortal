package com.xeno.portal

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class RootTouchRequest(
    val gestureId:String, val x:Float, val y:Float, val downTime:Long, val detectedTime:Long,
    val displayId:Int, val rotation:Int, val width:Int, val height:Int,
)

/** Validates application identity, not which code within the allowed process made a call. */
object RootBridgePolicy {
    const val HOST="com.ss.android.ugc.aweme"
    // A bounded freshness budget, not an assertion about Douyin's menu delay.
    const val MAX_GESTURE_AGE_MS=650L
    fun allowedPackages(packages:Array<String>?) = packages?.toList() == listOf(HOST)
    fun rejection(r:RootTouchRequest, now:Long, threshold:Int):String? = when {
        !ProbePolicy.validSessionId(r.gestureId) -> "手势编号无效"
        !r.x.isFinite() || !r.y.isFinite() -> "坐标不是有限数值"
        r.displayId!=0 || r.rotation !in 0..3 -> "仅支持主屏幕与有效旋转角度"
        r.width !in 1..10000 || r.height !in 1..10000 || r.x<0 || r.y<0 || r.x>=r.width || r.y>=r.height -> "坐标或屏幕尺寸越界"
        r.downTime<=0 || r.detectedTime<r.downTime || r.detectedTime>now -> "触摸时间顺序无效"
        r.detectedTime-r.downTime !in threshold.coerceIn(250,500).toLong()..(threshold.coerceIn(250,500)+150L) -> "长按阈值已变化或判定太迟"
        now-r.detectedTime>150L || now-r.downTime>MAX_GESTURE_AGE_MS -> "跨进程消息过期；无法及时截取"
        else -> null
    }
}

object RootBridgeStatus {
    var status by mutableStateOf("本次进程尚未收到抖音模块连接；这不代表手机是否已 Root")
        private set
    fun record(value:String) {
        Diagnostics.main.post {status=value}
        Diagnostics.log("ROOT_BRIDGE",value)
    }
}

object RootBridgeAccess {
    private val handshake=Uri.parse("content://com.xeno.portal.rootbridge/handshake")
    /** A grant makes this provider's package visible to the host on Android 11+.
     * The handshake URI exposes no data: this provider has no query/openFile implementation.
     */
    fun refresh(c:Context) {
        try {
            if(Preferences.enabled(c) && Preferences.rootEnabled(c) && RootBridgePolicy.HOST in Preferences.whitelist(c)) {
                c.grantUriPermission(RootBridgePolicy.HOST,handshake,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                c.revokeUriPermission(RootBridgePolicy.HOST,handshake,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                RootCommentCapture.cancel()
            }
        } catch(e:Exception) {Diagnostics.exception(3,"Root 握手可见性授权失败，请确认抖音已安装",e)}
    }
}

/** Only bounded primitive messages from the explicitly scoped host UID are accepted.
 * No file access, arbitrary intents, shell commands, or screenshot bytes are exposed here.
 */
class RootBridgeProvider:ContentProvider() {
    private val lock=Any()
    private val seen=LinkedHashSet<String>()
    private var lastHold:Long?=null
    private var lastStatus:Long?=null
    private var lastRejection:Long?=null
    override fun onCreate()=true

    override fun call(method:String,arg:String?,extras:Bundle?):Bundle {
        val c=context ?: return answer(false,"连接未就绪")
        val uid=Binder.getCallingUid()
        // Do not accept shared UIDs or trust a package name supplied in extras.
        if(!RootBridgePolicy.allowedPackages(c.packageManager.getPackagesForUid(uid))) {
            throw SecurityException("Root bridge caller is not the scoped host UID")
        }
        return try {
            val now=SystemClock.uptimeMillis()
            val enabled=Preferences.enabled(c) && Preferences.rootEnabled(c) && RootBridgePolicy.HOST in Preferences.whitelist(c)
            when(method) {
                "config" -> Bundle().apply {
                    putBoolean("enabled",enabled);putInt("threshold",Preferences.threshold(c))
                }
                "status" -> {
                    val accepted=synchronized(lock) {
                        if(lastStatus?.let {now-it<1000}==true) false else {lastStatus=now;true}
                    }
                    if(accepted) {
                        val event=extras?.getString("event").orEmpty().take(120).replace('\n',' ')
                        val cls=extras?.getString("callbackClass").orEmpty().take(160).replace('\n',' ')
                        RootBridgeStatus.record("抖音模块：$event callback=$cls enabled=$enabled uptime=$now；连接不等于截图成功")
                    }
                    answer(accepted)
                }
                "hold" -> {
                    if(!enabled) return reject(now,"总开关 / Root 触发未开启，或抖音不在白名单")
                    val e=extras ?: return reject(now,"触摸信息为空")
                    val r=RootTouchRequest(e.getString("gestureId").orEmpty(),e.getFloat("x",Float.NaN),e.getFloat("y",Float.NaN),
                        e.getLong("downTime",-1),e.getLong("detectedTime",-1),e.getInt("displayId",-1),e.getInt("rotation",-1),
                        e.getInt("width",0),e.getInt("height",0))
                    RootBridgePolicy.rejection(r,now,Preferences.threshold(c))?.let {return reject(now,it)}
                    val admitted=synchronized(lock) {
                        if(r.gestureId in seen || lastHold?.let {now-it<1200}==true) false
                        else {
                            lastHold=now;seen.add(r.gestureId)
                            if(seen.size>64) seen.remove(seen.first())
                            true
                        }
                    }
                    if(!admitted) return reject(now,"重复手势或触发间隔不足1200ms")
                    // Return immediately: reading the host's accessibility tree must not block Binder.
                    Diagnostics.main.post {
                        if(!Preferences.enabled(c) || !Preferences.rootEnabled(c) || RootBridgePolicy.HOST !in Preferences.whitelist(c)) return@post
                        val service=PortalAccessibilityService.active
                        Diagnostics.stage(3,"触摸命中","Root 单指长按 (${r.x.toInt()},${r.y.toInt()}) 阈值=${r.detectedTime-r.downTime}ms IPC=${SystemClock.uptimeMillis()-r.detectedTime}ms；待核验图片节点")
                        if(service==null) {Diagnostics.stage(4,"阻断","Root 已收到触点，但无障碍服务未连接，无法查节点和截图");return@post}
                        RootCommentCapture.capture(service,r)
                    }
                    answer(true,"仅排队，截图与显示结果见 S4/S5")
                }
                else -> answer(false,"不支持的方法")
            }
        } catch(e:Exception) {
            reject(SystemClock.uptimeMillis(),"报文处理失败 ${e.javaClass.simpleName}")
        }
    }
    private fun reject(now:Long,why:String):Bundle {
        val log=synchronized(lock) {
            if(lastRejection?.let {now-it<1000}==true) false else {lastRejection=now;true}
        }
        if(log) Diagnostics.stage(3,"阻断","Root：$why")
        return answer(false,why)
    }
    private fun answer(ok:Boolean,reason:String="")=Bundle().apply {putBoolean("accepted",ok);putString("reason",reason)}
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=throw UnsupportedOperationException("call only")
    override fun getType(uri:Uri):String?=null
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException("call only")
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException("call only")
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException("call only")
}
