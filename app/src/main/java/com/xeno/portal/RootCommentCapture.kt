package com.xeno.portal

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

data class RootImageTarget(val window:Int,val id:String,val description:String,val bounds:Rect)

object RootCaptureRules {
    fun select(targets:List<RootImageTarget>,x:Float,y:Float,menu:Boolean,complete:Boolean):RootImageTarget {
        check(!menu) {"抖音菜单已出现，停止截图，不能保证未压暗"}
        check(complete) {"节点树不完整或窗口未知，不猜测目标"}
        check(x.isFinite() && y.isFinite()) {"触点无效"}
        val hits=targets.filter { !it.bounds.isEmpty && x>=it.bounds.left && x<it.bounds.right && y>=it.bounds.top && y<it.bounds.bottom }
            .distinctBy {Triple(it.window,it.id,it.bounds)}
        check(hits.size==1) {"触点命中${hits.size}个评论图片；必须唯一，不随机选择"}
        return hits.single()
    }
    fun geometry(r:RootTouchRequest,rotation:Int,width:Int,height:Int,magnification:Float):Boolean =
        r.displayId==Display.DEFAULT_DISPLAY && r.rotation==rotation && r.width==width && r.height==height &&
            magnification.isFinite() && magnification==1f
}

/** R1: a real host touch point, then conservative accessibility hit testing and screenshot.
 * No menu clicking, gesture interception, arbitrary screenshot fallback or album writes.
 */
object RootCommentCapture {
    @Volatile private var generation=0L
    fun cancel() {generation++}
    private fun allowed(service:PortalAccessibilityService,ticket:Long)=ticket==generation &&
        PortalAccessibilityService.active===service && Preferences.enabled(service) && Preferences.rootEnabled(service) &&
        RootBridgePolicy.HOST in Preferences.whitelist(service)

    private fun target(service:PortalAccessibilityService,r:RootTouchRequest):RootImageTarget {
        val found=mutableListOf<RootImageTarget>()
        var complete=true;var menu=false;var visited=0;var hostWindow=false
        val windows=service.windows
        check(windows.isNotEmpty()) {"没有可读取窗口"}
        for(w in windows) {
            val root=w.root
            if(root==null) {if(w.isActive || w.isFocused) complete=false;continue}
            val host=root.packageName?.toString()==RootBridgePolicy.HOST
            if(!host) {
                if(w.isActive && w.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) complete=false
                continue
            }
            val eligible=w.isActive || w.isFocused
            if(eligible) hostWindow=true
            val queue=ArrayDeque<Triple<AccessibilityNodeInfo,Boolean,Int>>()
            queue.add(Triple(root,false,0))
            while(queue.isNotEmpty() && visited<1000) {
                val (node,parentComment,depth)=queue.removeFirst();visited++
                val id=node.viewIdResourceName.orEmpty()
                val inComment=parentComment || id=="${RootBridgePolicy.HOST}:id/fmu"
                val desc=node.contentDescription?.toString()?.trim().orEmpty()
                val text=node.text?.toString()?.trim().orEmpty()
                if(node.isVisibleToUser && (text in CommentImagePicker.MENU_LABELS || desc in CommentImagePicker.MENU_LABELS)) menu=true
                val rect=Rect();node.getBoundsInScreen(rect)
                if(eligible && CommentImageRules.selectable(desc,inComment,node.isVisibleToUser,rect) && CommentImageRules.inside(rect,r.width,r.height)) {
                    found.add(RootImageTarget(w.id,id,desc,Rect(rect)))
                }
                val n=node.childCount
                if(n>100 || (depth>=30 && n>0)) complete=false
                if(depth<30) for(i in 0 until n.coerceAtMost(100)) {
                    if(queue.size>=1000) {complete=false;break}
                    val child=node.getChild(i)
                    if(child==null) complete=false else queue.add(Triple(child,inComment,depth+1))
                }
            }
            if(queue.isNotEmpty()) complete=false
        }
        check(hostWindow) {"抖音评论窗口不在前台，停止取图"}
        val result=RootCaptureRules.select(found,r.x,r.y,menu,complete && visited<1000)
        Diagnostics.log("ROOT_TARGET","window=${result.window} bounds=${result.bounds} nodes=$visited；真实触点命中唯一评论图片")
        return result
    }

    fun capture(service:PortalAccessibilityService,request:RootTouchRequest) {
        PortalActions.cancel()
        PortalDisplay.close(service)
        val ticket=++generation
        if(!allowed(service,ticket)) {Diagnostics.stage(4,"阻断","Root 取图服务/开关已变化");return}
        val started=SystemClock.uptimeMillis()
        try {
            RootBridgePolicy.rejection(request,started,Preferences.threshold(service))?.let {error(it)}
            val display=service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                ?: error("主屏幕不可用")
            fun verifyGeometry() {
                val metrics=android.util.DisplayMetrics()
                @Suppress("DEPRECATION") display.getRealMetrics(metrics)
                check(RootCaptureRules.geometry(request,display.rotation,metrics.widthPixels,metrics.heightPixels,service.magnificationController.scale)) {
                    "屏幕旋转/尺寸变化或开启放大，不猜测坐标变换"
                }
            }
            verifyGeometry()
            val expected=target(service,request)
            val requestedAt=SystemClock.uptimeMillis()
            check(requestedAt-request.downTime<=RootBridgePolicy.MAX_GESTURE_AGE_MS) {"查节点后已超过按下650ms的本版时效预算，停止本次截图"}
            Diagnostics.stage(4,"进行中","Root 唯一目标${expected.bounds}；按下→请求=${requestedAt-request.downTime}ms；先验证截图，不保证菜单前成像")
            service.takeScreenshot(Display.DEFAULT_DISPLAY,service.mainExecutor,object:AccessibilityService.TakeScreenshotCallback {
                override fun onFailure(errorCode:Int) {
                    if(allowed(service,ticket)) Diagnostics.stage(4,"失败","Root takeScreenshot error=$errorCode；按下至失败=${SystemClock.uptimeMillis()-request.downTime}ms")
                }
                override fun onSuccess(result:AccessibilityService.ScreenshotResult) {
                    val buffer=result.hardwareBuffer
                    var frame:Bitmap?=null
                    try {
                        if(!allowed(service,ticket)) return
                        verifyGeometry()
                        check(target(service,request)==expected) {"截图期间评论目标/窗口发生变化，丢弃"}
                        val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)?:error("读取硬件截图失败")
                        frame=try {hardware.copy(Bitmap.Config.ARGB_8888,false)?:error("复制截图失败")} finally {hardware.recycle()}
                        val source=checkNotNull(frame)
                        check(source.width==request.width && source.height==request.height) {"截图与触点屏幕尺寸不一致，不猜测缩放"}
                        val crop=CommentImageRules.crop(source,expected.bounds)
                        val callbackAt=SystemClock.uptimeMillis()
                        Diagnostics.log("ROOT_TIMING","down=${request.downTime} detected=${request.detectedTime} request=$requestedAt screenshotResultUptime=${result.timestamp} callback=$callbackAt；时间不是精确像素帧时间，压暗需人工检查")
                        Diagnostics.io.execute {
                            var session:MediaSession?=null
                            try {
                                if(ticket!=generation) return@execute
                                val saved=MediaSessions.create(service,crop,"评论${expected.description} · Root 长按截图")
                                session=saved
                                Diagnostics.main.post {
                                    if(!allowed(service,ticket)) {MediaSessions.discardIfUnshared(service,saved.id);return@post}
                                    Diagnostics.stage(4,"成功","Root 触点裁图 ${expected.bounds.width()}x${expected.bounds.height()} ${saved.file.length()} bytes image/jpeg；按下→截图返回=${callbackAt-request.downTime}ms；未压暗需检查预览，非原文件")
                                    if(Build.VERSION.SDK_INT>=36) NativeLiveUpdates.show(service) else PortalDisplay.show(service,officialOnly=true)
                                }
                            } catch(e:Exception) {
                                session?.let {MediaSessions.discardIfUnshared(service,it.id)}
                                if(ticket==generation) Diagnostics.exception(4,"Root 裁图缓存",e)
                            } finally {crop.recycle()}
                        }
                    } catch(e:Exception) {if(allowed(service,ticket)) Diagnostics.exception(4,"Root 截图校验",e)}
                    finally {frame?.recycle();buffer.close()}
                }
            })
        } catch(e:Exception) {if(ticket==generation) Diagnostics.exception(4,"Root 评论取图",e)}
    }
}
