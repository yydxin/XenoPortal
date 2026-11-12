package com.xeno.portal.root

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.Window
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * LSPosed entry point. Observes an existing Activity window callback only in Douyin's
 * main process. Never consumes, replaces, delays for IPC, or replays MotionEvents.
 * Accessibility authorization and XenoPortal's provider-side gate are still required.
 */
class DouyinTouchHook : IXposedHookLoadPackage {
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val bridgeUri by lazy { Uri.parse("content://com.xeno.portal.rootbridge") }
    private val worker by lazy {
        ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8), { task ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            }, "XenoPortalBridge").apply { isDaemon = true }
        }, ThreadPoolExecutor.AbortPolicy())
    }
    private val hookedMethods = mutableSetOf<Method>()
    private var activityRef = WeakReference<Activity>(null)
    private var callbackRef = WeakReference<Window.Callback>(null)
    private var resumeEpoch = 0L
    private val gestureGeneration = AtomicLong(0L)
    private var detector: HoldGesture? = null
    private var press: Press? = null
    private var holdTimer: Runnable? = null
    private var enabled = false
    private var thresholdMillis = 350
    private var configReadAt = 0L
    private var lastErrorAt = -30_000L
    private var initialized = false
    private var dispatchDepth = 0

    private data class DisplayFrame(val id: Int, val rotation: Int, val width: Int, val height: Int)
    private data class Press(
        val id: String,
        val rawX: Float,
        val rawY: Float,
        val downTime: Long,
        val frame: DisplayFrame,
        val generation: Long,
    )

    private val refresh = object : Runnable {
        override fun run() {
            val activity = activityRef.get() ?: return
            guarded("refresh") {
                installCurrentCallback(activity)
                refreshConfig(activity, "refresh")
            }
            main.postDelayed(this, CONFIG_TTL_MS)
        }
    }

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != DOUYIN || param.processName != DOUYIN || initialized) return
        initialized = true
        guarded("install_lifecycle") {
            XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    // A subclass may replace Window.Callback after calling super.onResume().
                    main.post { guarded("resume") { onResumed(activity) } }
                }
            })
            XposedHelpers.findAndHookMethod(Activity::class.java, "onPause", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    guarded("pause") {
                        if (activityRef.get() === activity) onPaused(activity)
                    }
                }
            })
        }
    }

    private fun onResumed(activity: Activity) {
        if (activity.isDestroyed || activity.isFinishing || activity.packageName != DOUYIN) return
        resumeEpoch++
        cancelHold()
        enabled = false
        activityRef = WeakReference(activity)
        installCurrentCallback(activity)
        refreshConfig(activity, "resumed")
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, CONFIG_TTL_MS)
    }

    private fun onPaused(activity: Activity) {
        resumeEpoch++
        cancelHold()
        enabled = false
        main.removeCallbacks(refresh)
        val callbackClass = callbackRef.get()?.javaClass?.name.orEmpty()
        activityRef.clear()
        callbackRef.clear()
        val context = activity.applicationContext
        submit { reportStatus(context, "paused", callbackClass) }
    }

    private fun installCurrentCallback(activity: Activity) {
        val callback = activity.window.callback ?: run { cancelHold(); callbackRef.clear(); return }
        if (callbackRef.get() !== callback) cancelHold()
        // getMethod resolves inherited public implementations as well as overrides.
        val method = callback.javaClass.getMethod("dispatchTouchEvent", MotionEvent::class.java)
        if (!hookedMethods.contains(method)) {
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (Looper.myLooper() !== main.looper || param.thisObject !== callbackRef.get()) return
                    param.setObjectExtra("xeno_dispatch_depth",true)
                    dispatchDepth++
                    if(dispatchDepth!=1) return
                    val event = param.args.firstOrNull() as? MotionEvent ?: return
                    guarded("touch") { observe(event) }
                    // Deliberately do not set param.result/args or invoke any gesture API.
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    if(param.getObjectExtra("xeno_dispatch_depth")==true) dispatchDepth=(dispatchDepth-1).coerceAtLeast(0)
                }
            })
            hookedMethods.add(method)
        }
        callbackRef = WeakReference(callback)
    }

    private fun refreshConfig(activity: Activity, event: String) {
        val epoch = resumeEpoch
        val context = activity.applicationContext
        val callbackClass = callbackRef.get()?.javaClass?.name.orEmpty()
        submit {
            try {
                val config = context.contentResolver.call(bridgeUri, "config", null, null)
                val requestedEnabled = config?.getBoolean("enabled", false) == true
                val requestedThreshold = config?.getInt("threshold", 350) ?: 350
                val receivedAt = SystemClock.uptimeMillis()
                main.post {
                    if (epoch != resumeEpoch || activityRef.get() == null) return@post
                    if (requestedThreshold !in 250..500 || !requestedEnabled) cancelHold()
                    if (thresholdMillis != requestedThreshold) cancelHold()
                    enabled = requestedEnabled && requestedThreshold in 250..500
                    thresholdMillis = requestedThreshold.coerceIn(250, 500)
                    configReadAt = receivedAt
                }
                reportStatus(context, event, callbackClass)
            } catch (error: Throwable) {
                failClosed(epoch)
                logError("config", error)
            }
        }
    }

    private fun observe(event: MotionEvent) {
        val activity = activityRef.get()
        if (activity == null || !canObserve(activity) || !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            cancelHold()
            return
        }
        if (event.pointerCount != 1 || event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) {
            cancelHold()
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelHold()
                val frame = displayFrame(activity) ?: return
                if (!event.rawX.isFinite() || !event.rawY.isFinite() ||
                    event.rawX < 0f || event.rawY < 0f || event.rawX >= frame.width || event.rawY >= frame.height
                ) return
                val now = SystemClock.uptimeMillis()
                if (event.downTime > now || now - event.downTime > thresholdMillis) return
                val hold = HoldGesture(thresholdMillis, ViewConfiguration.get(activity).scaledTouchSlop.toFloat())
                if (!hold.down(event.x, event.y, event.downTime, event.pointerCount)) return
                detector = hold
                val current = Press(UUID.randomUUID().toString().replace("-", ""), event.rawX, event.rawY,
                    event.downTime, frame, gestureGeneration.get())
                press = current
                val timer = Runnable { guarded("hold_timer") { detectHold(current) } }
                holdTimer = timer
                main.postAtTime(timer, event.downTime + thresholdMillis)
            }
            MotionEvent.ACTION_MOVE -> {
                val hold = detector ?: return
                // Local coordinates are consistent with scaledTouchSlop. Export only the
                // original raw display coordinates, never local historical approximations.
                for (i in 0 until event.historySize) {
                    if (!hold.move(event.getHistoricalX(0, i), event.getHistoricalY(0, i))) break
                }
                if (!hold.move(event.x, event.y, event.pointerCount)) cancelHold()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_OUTSIDE -> cancelHold()
        }
    }

    private fun detectHold(current: Press) {
        holdTimer = null
        val activity = activityRef.get()
        if (press !== current || activity == null || !canObserve(activity) || displayFrame(activity) != current.frame) {
            cancelHold()
            return
        }
        val hold = detector?.poll(SystemClock.uptimeMillis()) ?: run { cancelHold(); return }
        val context = activity.applicationContext
        val epoch = resumeEpoch
        val extras = Bundle().apply {
            putString("gestureId", current.id)
            putFloat("x", current.rawX)
            putFloat("y", current.rawY)
            putLong("downTime", current.downTime)
            putLong("detectedTime", hold.detectedTime)
            putInt("displayId", current.frame.id)
            putInt("rotation", current.frame.rotation)
            putInt("width", current.frame.width)
            putInt("height", current.frame.height)
        }
        submit {
            // UP/CANCEL/pause or another DOWN cancels a request still waiting in our queue.
            if (gestureGeneration.get() != current.generation) return@submit
            try {
                val response = context.contentResolver.call(bridgeUri, "hold", null, extras)
                if (response?.getBoolean("accepted", false) != true) {
                    XposedBridge.log("XenoPortal R1: bridge declined hold; see XenoPortal diagnostics")
                }
            } catch (error: Throwable) {
                failClosed(epoch)
                logError("hold_ipc", error)
            }
        }
    }

    private fun canObserve(activity: Activity): Boolean {
        val age = SystemClock.uptimeMillis() - configReadAt
        return enabled && age in 0 until CONFIG_TTL_MS && !activity.isDestroyed && !activity.isFinishing && !activity.isInMultiWindowMode &&
            activity.hasWindowFocus() && activity.window.callback === callbackRef.get() &&
            activity.window.peekDecorView()?.isAttachedToWindow == true
    }

    @Suppress("DEPRECATION")
    private fun displayFrame(activity: Activity): DisplayFrame? {
        val display = activity.window.peekDecorView()?.display ?: return null
        // First beta deliberately excludes external/virtual displays.
        if (display.displayId != Display.DEFAULT_DISPLAY) return null
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) return null
        return DisplayFrame(display.displayId, display.rotation, metrics.widthPixels, metrics.heightPixels)
    }

    private fun cancelHold() {
        gestureGeneration.incrementAndGet()
        holdTimer?.let(main::removeCallbacks)
        holdTimer = null
        detector?.cancel()
        detector = null
        press = null
    }

    private fun reportStatus(context: Context, event: String, callbackClass: String) {
        try {
            context.contentResolver.call(bridgeUri, "status", null, Bundle().apply {
                putString("event", event.take(40))
                putString("callbackClass", callbackClass.take(160))
            })
        } catch (error: Throwable) { logError("status", error) }
    }

    private fun failClosed(epoch: Long) {
        main.post {
            if (epoch == resumeEpoch) {
                enabled = false
                cancelHold()
            }
        }
    }

    private fun submit(block: () -> Unit) {
        try { worker.execute(block) }
        catch (error: RejectedExecutionException) { logError("queue_full", error) }
    }

    private inline fun guarded(stage: String, block: () -> Unit) {
        try { block() }
        catch (error: Throwable) {
            // Observation failure must never escape into Douyin's original dispatch.
            if (Looper.myLooper() === main.looper) {
                enabled = false
                cancelHold()
            }
            logError(stage, error)
            activityRef.get()?.applicationContext?.let { context ->
                submit {reportStatus(context,"error_$stage:${error.javaClass.simpleName}","")}
            }
        }
    }

    @Synchronized private fun logError(stage: String, error: Throwable) {
        val now = SystemClock.uptimeMillis()
        if (now - lastErrorAt < 10_000L) return
        lastErrorAt = now
        // No exception message, stack, window text, URI data, or user content in Xposed logs.
        XposedBridge.log("XenoPortal R1 $stage: ${error.javaClass.simpleName}")
    }

    private companion object {
        const val DOUYIN = "com.ss.android.ugc.aweme"
        const val CONFIG_TTL_MS = 30_000L
    }
}
