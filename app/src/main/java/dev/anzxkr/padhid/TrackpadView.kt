package dev.anzxkr.padhid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.min

/**
 * One finger: move. Tap: left click. Two-finger tap: right click. Two-finger drag: scroll.
 * Hold the on-screen L button with another finger to drag.
 */
class TrackpadView(ctx: Context) : View(ctx) {
    var hid: Hid? = null

    companion object {
        const val BASE_GAIN = 1.5f      // pointer speed for slow movement
        const val ACCEL_GAIN = 1.8f     // extra gain added for fast swipes
        const val NATURAL_SCROLL = true // fingers down = content down (like a phone)
    }

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val density = resources.displayMetrics.density

    private var downTime = 0L
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var maxPointers = 0
    private var moved = false
    private var remX = 0f
    private var remY = 0f
    private var scrollAcc = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF777777.toInt()
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
    }

    init {
        setBackgroundColor(0xFF1E1E1E.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText("trackpad", width / 2f, height / 2f, paint)
    }

    private fun cx(e: MotionEvent, skip: Int = -1): Float {
        var s = 0f
        var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { s += e.getX(i); n++ }
        return if (n > 0) s / n else 0f
    }

    private fun cy(e: MotionEvent, skip: Int = -1): Float {
        var s = 0f
        var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { s += e.getY(i); n++ }
        return if (n > 0) s / n else 0f
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val h = hid ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = e.eventTime
                startX = e.x; startY = e.y
                lastX = e.x; lastY = e.y
                maxPointers = 1
                moved = false
                remX = 0f; remY = 0f
                scrollAcc = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                lastX = cx(e); lastY = cy(e)
                startX = lastX; startY = lastY
                scrollAcc = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val x = cx(e)
                val y = cy(e)
                val dx = x - lastX
                val dy = y - lastY
                lastX = x; lastY = y
                if (!moved && hypot(x - startX, y - startY) > slop) moved = true

                if (e.pointerCount == 1) {
                    val speed = hypot(dx, dy)
                    val gain = BASE_GAIN + min(speed, 40f) / 40f * ACCEL_GAIN
                    remX += dx * gain
                    remY += dy * gain
                    val ix = remX.toInt()
                    val iy = remY.toInt()
                    remX -= ix
                    remY -= iy
                    if (ix != 0 || iy != 0) h.move(ix, iy)
                } else if (e.pointerCount == 2) {
                    scrollAcc += dy
                    val step = 18f * density
                    val ticks = (scrollAcc / step).toInt()
                    if (ticks != 0) {
                        scrollAcc -= ticks * step
                        h.scroll(if (NATURAL_SCROLL) ticks else -ticks)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val up = e.actionIndex
                lastX = cx(e, up); lastY = cy(e, up)
            }
            MotionEvent.ACTION_UP -> {
                val dur = e.eventTime - downTime
                if (!moved && dur < 280) {
                    when (maxPointers) {
                        1 -> h.click(1)
                        2 -> h.click(2)
                    }
                }
            }
        }
        return true
    }
}
