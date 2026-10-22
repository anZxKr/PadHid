package dev.anzxkr.padhid

import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One long-lived root `dd` per /dev/hidgN node. dd reads exactly `size` bytes per block
 * and writes each block straight to the device, so every write we make is one HID report.
 */
class HidSink(private val dev: String, private val size: Int) {
    private var proc: Process? = null
    private var out: OutputStream? = null
    private var lastTry = 0L

    @Synchronized
    private fun ensureOpen(): Boolean {
        if (out != null && proc?.isAlive == true) return true
        val now = System.currentTimeMillis()
        if (now - lastTry < 1000) return false
        lastTry = now
        closeQuiet()
        return try {
            val p = ProcessBuilder("su", "-c", "exec dd of=$dev bs=$size conv=notrunc >/dev/null 2>&1")
                .start()
            proc = p
            out = p.outputStream
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun send(report: ByteArray): Boolean {
        if (!ensureOpen()) return false
        return try {
            val o = out!!
            o.write(report)
            o.flush()
            true
        } catch (e: Exception) {
            closeQuiet()
            false
        }
    }

    @Synchronized
    fun closeQuiet() {
        try { out?.close() } catch (_: Exception) {}
        try { proc?.destroy() } catch (_: Exception) {}
        out = null
        proc = null
    }
}

/** Keyboard + mouse on one single-thread executor, so reports always go out in order. */
class Hid(kbdDev: String, mouseDev: String) {
    private val kbd = HidSink(kbdDev, 8)
    private val mouse = HidSink(mouseDev, 4)
    private val exec: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private val dx = AtomicInteger()
    private val dy = AtomicInteger()
    private val wheel = AtomicInteger()
    @Volatile private var buttons = 0

    /** One-shot modifiers (Ctrl/Alt/Win/Shift) applied to the next key. */
    @Volatile var sticky = 0
    var onStickyCleared: (() -> Unit)? = null

    init {
        exec.scheduleAtFixedRate(Runnable {
            try { flushMouse() } catch (_: Throwable) {}
        }, 0, 4, TimeUnit.MILLISECONDS)
    }

    private fun post(r: () -> Unit) {
        try {
            exec.execute { try { r() } catch (_: Throwable) {} }
        } catch (_: Exception) {}
    }

    // ---- mouse ----

    fun move(x: Int, y: Int) {
        dx.addAndGet(x)
        dy.addAndGet(y)
    }

    fun scroll(ticks: Int) {
        wheel.addAndGet(ticks)
    }

    private fun flushMouse() {
        var x = dx.getAndSet(0)
        var y = dy.getAndSet(0)
        var w = wheel.getAndSet(0)
        while (x != 0 || y != 0 || w != 0) {
            val cx = x.coerceIn(-127, 127)
            val cy = y.coerceIn(-127, 127)
            val cw = w.coerceIn(-127, 127)
            mouse.send(byteArrayOf(buttons.toByte(), cx.toByte(), cy.toByte(), cw.toByte()))
            x -= cx
            y -= cy
            w -= cw
        }
    }

    private fun setButton(mask: Int, down: Boolean) {
        flushMouse()
        buttons = if (down) buttons or mask else buttons and mask.inv()
        mouse.send(byteArrayOf(buttons.toByte(), 0, 0, 0))
    }

    /** mask: 1 = left, 2 = right, 4 = middle */
    fun button(mask: Int, down: Boolean) = post { setButton(mask, down) }

    fun click(mask: Int) {
        button(mask, true)
        try {
            exec.schedule(Runnable {
                try { setButton(mask, false) } catch (_: Throwable) {}
            }, 18, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {}
    }

    // ---- keyboard ----

    private fun takeSticky(): Int {
        val s = sticky
        if (s != 0) {
            sticky = 0
            onStickyCleared?.invoke()
        }
        return s
    }

    private fun press(usage: Int, mods: Int) {
        flushMouse()
        kbd.send(byteArrayOf(mods.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0))
        Thread.sleep(3)
        kbd.send(ByteArray(8))
        Thread.sleep(2)
    }

    fun key(usage: Int, mods: Int = 0) {
        val m = mods or takeSticky()
        post { press(usage, m) }
    }

    fun text(s: String) {
        var m = takeSticky()
        val items = ArrayList<Pair<Int, Int>>()
        for (c in s) {
            val k = Keymap.fromChar(c) ?: continue
            items.add(k.first to (k.second or m))
            m = 0
        }
        if (items.isEmpty()) return
        post { for ((u, md) in items) press(u, md) }
    }

    fun releaseAll() = post {
        buttons = 0
        mouse.send(ByteArray(4))
        kbd.send(ByteArray(8))
    }

    fun close() {
        try {
            mouse.send(ByteArray(4))
            kbd.send(ByteArray(8))
        } catch (_: Throwable) {}
        exec.shutdownNow()
        kbd.closeQuiet()
        mouse.closeQuiet()
    }
}
