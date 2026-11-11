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
        Diagnostics.stage(3, "阻断", "免Root菜单验证：开启窗口采样后自动识别候选；尚无可靠原触点，不自动保存或乱截")
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
    private var lastTargetPackage = ""
    private var foregroundPackage = ""
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = SystemClock.elapsedRealtime()
        val pkg = event.packageName?.toString().orEmpty()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg.isNotBlank() && pkg != "com.android.systemui") {
            foregroundPackage = pkg
        }
        if (pkg == packageName) return
        val record = now - lastEventLog >= 1200
        if (record) lastEventLog = now
        val type = AccessibilityEvent.eventTypeToString(event.eventType)
        Diagnostics.stage(1, "成功", "package=$pkg type=$type window=${event.windowId}", record)
        if (!Preferences.enabled(this)) return
        val whitelist = Preferences.whitelist(this)
        val allowed = pkg in whitelist
        if (allowed) { lastTargetPackage = pkg; if (foregroundPackage.isBlank()) foregroundPackage = pkg }
        val relatedWindowChange = !allowed && ProbePolicy.shouldInspectEvent(pkg, foregroundPackage, whitelist)
        Diagnostics.stage(2, if (allowed) "成功" else if (relatedWindowChange) "待核验" else "跳过",
            "事件包=$pkg 白名单=$allowed；最近目标=$lastTargetPackage 前台线索=$foregroundPackage；关联窗口变化=$relatedWindowChange", record)
        if ((!allowed && !relatedWindowChange) || !Preferences.treeEnabled(this)) return
        val critical = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        if (now - lastTree < if (critical) 250 else 1500) return
        lastTree = now
        val source = try { event.source } catch (e: Exception) {
            Diagnostics.log("TREE", "event.source读取失败:${e.javaClass.simpleName}"); null
        }
        sampleWindows(pkg, event.windowId, source, "event=$type")
    }
    fun sampleWindows(
        eventPackage: String = "", eventWindowId: Int = -1,
        eventSource: AccessibilityNodeInfo? = null, reason: String = "manual",
    ) {
        if (!Preferences.enabled(this) || !Preferences.treeEnabled(this)) {
            Diagnostics.log("TREE", "采样被阻止：请先打开总开关和窗口文字树调试")
            return
        }
        try {
            val timestamp = android.text.format.DateFormat.format("HH:mm:ss.SSS", System.currentTimeMillis())
            val output = StringBuilder("captured=$timestamp $reason eventPackage=$eventPackage eventWindow=$eventWindowId\n")
            val labels = mutableSetOf<String>()
            val treeLines = mutableListOf<String>()
            val visitedWindows = mutableSetOf<Int>()
            var count = 0
            var eligibleRoots = 0
            var emptyRoots = 0
            var skippedRoots = 0
            var errors = 0
            val whitelist = Preferences.whitelist(this)
            fun readRoot(root: AccessibilityNodeInfo?, windowId: Int, route: String) {
                if (root == null) { emptyRoots++; output.appendLine("route=$route window=$windowId root=null"); return }
                try {
                    val pkg = root.packageName?.toString().orEmpty()
                    if (pkg !in whitelist) {
                        skippedRoots++; output.appendLine("route=$route window=$windowId package=$pkg 非白名单，不读取文字"); return
                    }
                    if (windowId >= 0 && windowId in visitedWindows) return
                    if (windowId >= 0) visitedWindows.add(windowId)
                    eligibleRoots++
                    val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
                    queue.add(root to 0)
                    output.appendLine("route=$route window=$windowId package=$pkg")
                    var truncated = false
                    while (queue.isNotEmpty() && count < 1000) {
                        val (node, depth) = queue.removeFirst(); count++
                        try {
                            val text = node.text?.toString().orEmpty().take(160)
                            val desc = node.contentDescription?.toString().orEmpty().take(160)
                            val cls = node.className?.toString().orEmpty()
                            if (text.isNotBlank()) labels.add(text.trim())
                            if (desc.isNotBlank()) labels.add(desc.trim())
                            val imageLike = listOf("Image", "Picture", "Video", "SurfaceView", "TextureView").any { cls.contains(it, true) }
                            if (text.isNotBlank() || desc.isNotBlank() || imageLike) {
                                val bounds = Rect(); node.getBoundsInScreen(bounds)
                                val line = "w=$windowId text=$text desc=$desc id=${node.viewIdResourceName} class=$cls click=${node.isClickable} bounds=$bounds mediaCandidate=$imageLike"
                                output.appendLine(line); treeLines.add(line)
                            }
                            if (depth < 30) for (i in 0 until node.childCount.coerceAtMost(100)) {
                                if (queue.size < 1000) node.getChild(i)?.let { queue.add(it to depth + 1) }
                                else truncated = true
                            } else if (node.childCount > 0) truncated = true
                        } catch (e: Exception) { errors++; output.appendLine("NODE_UNAVAILABLE ${e.javaClass.simpleName}") }
                    }
                    if (truncated || queue.isNotEmpty()) output.appendLine("TRUNCATED 节点/深度/队列达到上限")
                } catch (e: Exception) { errors++; output.appendLine("ROOT_UNAVAILABLE window=$windowId ${e.javaClass.simpleName}") }
            }
            val all = try { windows } catch (e: Exception) {
                errors++; output.appendLine("WINDOW_LIST_UNAVAILABLE ${e.javaClass.simpleName}"); emptyList()
            }
            for (window in all) {
                try { readRoot(window.root, window.id, "interactive-windows") }
                catch (e: Exception) { errors++; output.appendLine("WINDOW_UNAVAILABLE id=${window.id} ${e.javaClass.simpleName}") }
            }
            if (eligibleRoots == 0) {
                try { val root = rootInActiveWindow; readRoot(root, root?.windowId ?: -1, "active-root-fallback") }
                catch (e: Exception) { errors++; output.appendLine("ACTIVE_ROOT_UNAVAILABLE ${e.javaClass.simpleName}") }
                readRoot(eventSource, eventWindowId, "event-source-fallback")
            }
            val summary = "captured=$timestamp windows=${all.size} eligibleRoots=$eligibleRoots emptyRoots=$emptyRoots skippedRoots=$skippedRoots errors=$errors visited=$count reason=$reason"
            output.appendLine(summary)
            if (count == 0) output.appendLine("此次无白名单节点；不证明抖音菜单不可见。保留此前成功快照。")
            Diagnostics.windowScan(summary, output.toString().take(100000), count, ProbePolicy.menuCandidate(labels))
            Diagnostics.batch("TREE", listOf(summary) + treeLines.take(100) +
                if (count == 0) listOf(output.toString().take(2200)) else listOf("完整受限快照已保留；菜单候选不等于可靠长按或目标坐标"))
            if (ProbePolicy.menuCandidate(labels) && SystemClock.elapsedRealtime() - lastCandidate > 2000) {
                lastCandidate = SystemClock.elapsedRealtime()
                Diagnostics.stage(3, "待验证", "命中菜单候选：分享评论给 + 保存到相册；原触点未知，尚未自动截图/点击保存，需与普通分享面板对照")
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
