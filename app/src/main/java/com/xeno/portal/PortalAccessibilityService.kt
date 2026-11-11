package com.xeno.portal

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class PortalAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var connection = java.lang.ref.WeakReference<PortalAccessibilityService>(null)
        val active: PortalAccessibilityService? get() = connection.get()
    }
    private var overlayView: android.view.View? = null
    private var overlayWindowManager: android.view.WindowManager? = null
    fun attachOverlayWindow(view: android.view.View, params: android.view.WindowManager.LayoutParams) {
        hideOverlayWindow()
        val manager = getSystemService(android.view.WindowManager::class.java)
        manager.addView(view, params)
        overlayWindowManager = manager
        overlayView = view
    }
    fun hideOverlayWindow() {
        overlayView?.let { view ->
            runCatching { overlayWindowManager?.removeViewImmediate(view) }
                .onFailure { Diagnostics.exception(5,"移除覆盖窗口",it) }
        }
        overlayView = null; overlayWindowManager = null
    }
    private var lastEventLog = 0L
    private var lastTree = 0L
    private var lastCandidate = 0L
    override fun onServiceConnected() {
        connection = java.lang.ref.WeakReference(this)
        Diagnostics.stage(0, "成功", "onServiceConnected；全窗口读取/截图能力已声明，未申请触摸探索")
        Diagnostics.stage(3, "阻断", "诊断版未启用自动长按；开启文字树后仅记录菜单候选，坐标未知，不自动保存")
    }
    override fun onInterrupt() { Diagnostics.stage(0, "未知", "onInterrupt：仍需检查连接，不推断进程已停止") }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        detach(); return super.onUnbind(intent)
    }
    override fun onDestroy() { detach(); super.onDestroy() }
    private fun detach() {
        hideOverlayWindow()
        if (active === this) {
            PortalActions.cancel()
            PortalDisplay.close(this)
            connection.clear()
            Diagnostics.stage(0, "阻断", "无障碍服务已断开")
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = SystemClock.elapsedRealtime()
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg == packageName) return // Avoid diagnostics UI producing a self-event loop.
        val record = now - lastEventLog >= 1200
        if (record) lastEventLog = now
        Diagnostics.stage(1, "成功", "package=$pkg type=${AccessibilityEvent.eventTypeToString(event.eventType)} window=${event.windowId}", record)
        if (!Preferences.enabled(this) || pkg == packageName) return
        val allowed = pkg in Preferences.whitelist(this)
        Diagnostics.stage(2, if (allowed) "成功" else "跳过", "事件包=$pkg 白名单=$allowed", record)
        if (!allowed) return
        // No automatic node actions. Sampling occurs only with explicit debug opt-in.
        if (Preferences.treeEnabled(this) && now - lastTree >= 1500) {
            lastTree = now
            sampleWindows()
        }
    }
    fun sampleWindows() {
        if (!Preferences.enabled(this) || !Preferences.treeEnabled(this)) {
            Diagnostics.log("TREE", "采样被阻止：请先打开总开关和窗口文字树调试")
            return
        }
        try {
            val output = StringBuilder()
            val labels = mutableSetOf<String>()
            val treeLines = mutableListOf<String>()
            var count = 0
            val all = windows
            output.appendLine("windows=${all.size}；仅记录白名单窗口；上限1000节点/深度30")
            for (window in all) {
                val root = window.root
                if (root == null) { output.appendLine("window=${window.id} root=null"); continue }
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg !in Preferences.whitelist(this)) { output.appendLine("window=${window.id} 非白名单，略过"); continue }
                val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
                queue.add(root to 0)
                output.appendLine("window=${window.id} package=$pkg type=${window.type}")
                while (queue.isNotEmpty() && count < 1000) {
                    val (node, depth) = queue.removeFirst(); count++
                    val text = node.text?.toString().orEmpty().take(160)
                    val desc = node.contentDescription?.toString().orEmpty().take(160)
                    if (text.isNotBlank()) labels.add(text.trim())
                    if (desc.isNotBlank()) labels.add(desc.trim())
                    if (text.isNotBlank() || desc.isNotBlank()) {
                        val bounds = Rect(); node.getBoundsInScreen(bounds)
                        val line = "w=${window.id} text=$text desc=$desc id=${node.viewIdResourceName} class=${node.className} click=${node.isClickable} bounds=$bounds"
                        output.appendLine(line); treeLines.add(line)
                    }
                    if (depth < 30) for (i in 0 until node.childCount.coerceAtMost(100)) {
                        if (queue.size < 1000) node.getChild(i)?.let { queue.add(it to depth + 1) }
                        else output.appendLine("TRUNCATED 子节点队列上限")
                    }
                }
                if (queue.isNotEmpty()) output.appendLine("TRUNCATED 节点或深度上限")
                if (count >= 1000) break
            }
            output.appendLine("visited=$count；菜单候选不是长按证据")
            Diagnostics.updateTree(output.toString().take(100000))
            Diagnostics.batch("TREE", treeLines.take(100) + "本次采样visited=$count；日志最多100节点，完整受限快照见下方/导出")
            if (ProbePolicy.menuCandidate(labels) && SystemClock.elapsedRealtime() - lastCandidate > 2000) {
                lastCandidate = SystemClock.elapsedRealtime()
                Diagnostics.stage(3, "待验证", "菜单候选：分享评论给 + 保存到相册；来源未确认，坐标未知，未点击保存")
            }
        } catch (e: Exception) { Diagnostics.exception(3, "遍历全窗口", e) }
    }
    fun captureForPreview() {
        if (!Preferences.enabled(this)) { Diagnostics.stage(4, "阻断", "总开关关闭"); return }
        val requested = SystemClock.elapsedRealtime()
        Diagnostics.stage(4, "进行中", "用户主动请求全屏诊断截图；不表示自动取图成功")
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            ?: error("无法读取 HardwareBuffer")
                        val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: error("无法复制截图")
                        hardware.recycle()
                        Diagnostics.io.execute {
                            try {
                                val session = MediaSessions.create(this@PortalAccessibilityService, bitmap, "手动全屏截图（未裁剪）")
                                bitmap.recycle()
                                Diagnostics.stage(4, "成功", "手动全屏截图 ${session.file.length()} bytes image/jpeg；耗时=${SystemClock.elapsedRealtime()-requested}ms；压暗与否请检查预览；非长按验收")
                            } catch (e: Exception) { Diagnostics.exception(4, "保存截图", e) }
                        }
                    } catch (e: Exception) { Diagnostics.exception(4, "截图解码", e) }
                    finally { buffer.close() }
                }
                override fun onFailure(errorCode: Int) { Diagnostics.stage(4, "失败", "takeScreenshot error=$errorCode；可能限频、窗口安全限制或服务状态问题") }
            })
        } catch (e: Exception) { Diagnostics.exception(4, "takeScreenshot", e) }
    }
}
