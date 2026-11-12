package com.xeno.portal.root

/** Single-pointer, passive hold detection. All times are SystemClock uptime milliseconds. */
class HoldGesture(
    val thresholdMillis: Int,
    private val touchSlopPx: Float,
    private val maxTimerDelayMillis: Long = 150L,
) {
    data class Hold(val x: Float, val y: Float, val downTime: Long, val detectedTime: Long)

    private data class Press(val x: Float, val y: Float, val downTime: Long, var emitted: Boolean = false)
    private var press: Press? = null

    init {
        require(thresholdMillis in 250..500)
        require(touchSlopPx.isFinite() && touchSlopPx >= 0f)
        require(maxTimerDelayMillis >= 0L)
    }

    val isActive: Boolean get() = press != null

    fun down(x: Float, y: Float, downTime: Long, pointerCount: Int = 1): Boolean {
        cancel()
        if (pointerCount != 1 || !x.isFinite() || !y.isFinite() || downTime < 0L) return false
        press = Press(x, y, downTime)
        return true
    }

    /** Feed every available historical MOVE sample too: an excursion cannot be undone. */
    fun move(x: Float, y: Float, pointerCount: Int = 1): Boolean {
        val current = press ?: return false
        val dx = x.toDouble() - current.x
        val dy = y.toDouble() - current.y
        if (pointerCount != 1 || !x.isFinite() || !y.isFinite() ||
            dx * dx + dy * dy > touchSlopPx.toDouble() * touchSlopPx
        ) {
            cancel()
            return false
        }
        return true
    }

    fun poll(now: Long): Hold? {
        val current = press ?: return null
        if (now < current.downTime) {
            cancel()
            return null
        }
        val age = now - current.downTime
        if (age > thresholdMillis + maxTimerDelayMillis) {
            cancel()
            return null
        }
        if (current.emitted || age < thresholdMillis) return null
        current.emitted = true
        return Hold(current.x, current.y, current.downTime, now)
    }

    fun cancel() { press = null }
}
