package dev.anzxkr.padhid

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.regex.Pattern

class MainActivity : Activity() {
    private var hid: Hid? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var statusView: TextView
    private lateinit var pathBox: EditText
    private lateinit var pad: TrackpadView
    private lateinit var catcher: KeyCatcher
    private val stickyButtons = LinkedHashMap<Int, Button>()

    private val gray = 0xFF3A3A3A.toInt()
    private val blue = 0xFF3D85C6.toInt()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("padhid", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121212.toInt())
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        statusView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 12f
            text = "Plug into the other phone, then tap Start."
        }
        pathBox = EditText(this).apply {
            hint = "usb-arsenal path (optional)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 12f
            setSingleLine()
            setText(prefs.getString("bin", ""))
        }
        pad = TrackpadView(this)
        catcher = KeyCatcher(this)

        root.addView(row(
            btn("Start") { start() },
            btn("Stop") { stop() },
            btn("Keyboard") { showKeyboard() }
        ))
        root.addView(statusView)
        root.addView(pathBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(pad, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(0, dp(4), 0, dp(4))
        })
        root.addView(row(mouseBtn("Left", 1), mouseBtn("Middle", 4), mouseBtn("Right", 2)))
        root.addView(row(
            keyBtn("Esc", Keymap.ESC),
            keyBtn("Tab", Keymap.TAB),
            sticky("Ctrl", Keymap.MOD_CTRL),
            sticky("Alt", Keymap.MOD_ALT),
            sticky("Win", Keymap.MOD_META),
            sticky("Shift", Keymap.MOD_SHIFT)
        ))
        root.addView(row(
            keyBtn("←", Keymap.LEFT),
            keyBtn("↓", Keymap.DOWN),
            keyBtn("↑", Keymap.UP),
            keyBtn("→", Keymap.RIGHT),
            keyBtn("⌫", Keymap.BKSP),
            keyBtn("⏎", Keymap.ENTER)
        ))
        root.addView(row(
            keyBtn("Home", Keymap.HOME),
            keyBtn("End", Keymap.END),
            keyBtn("PgUp", Keymap.PGUP),
            keyBtn("PgDn", Keymap.PGDN),
            keyBtn("Del", Keymap.DEL)
        ))
        root.addView(catcher, LinearLayout.LayoutParams(dp(1), dp(1)))

        setContentView(root)
    }

    // ---- UI helpers ----

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) {
            addView(v, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
    }

    private fun btn(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(gray)
        minWidth = 0
        minHeight = 0
        setPadding(0, 0, 0, 0)
        setOnClickListener { onClick() }
    }

    private fun keyBtn(label: String, usage: Int): Button = btn(label) { hid?.key(usage) }

    private fun sticky(label: String, mask: Int): Button {
        val b = btn(label) {
            val h = hid
            if (h != null) {
                h.sticky = h.sticky xor mask
                refreshSticky()
            }
        }
        stickyButtons[mask] = b
        return b
    }

    private fun refreshSticky() {
        val s = hid?.sticky ?: 0
        for ((mask, b) in stickyButtons) {
            b.setBackgroundColor(if ((s and mask) != 0) blue else gray)
        }
    }

    private fun mouseBtn(label: String, mask: Int): Button {
        val b = btn(label) { }
        b.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    hid?.button(mask, true)
                    v.setBackgroundColor(blue)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hid?.button(mask, false)
                    v.setBackgroundColor(gray)
                }
            }
            true
        }
        return b
    }

    private fun showKeyboard() {
        catcher.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(catcher, 0)
    }

    private fun setStatus(msg: String) = runOnUiThread { statusView.text = msg }

    // ---- start / stop ----

    private fun usbArsenalCmd(): String {
        val custom = pathBox.text.toString().trim()
        return if (custom.isNotEmpty()) custom
        else "\$(command -v usb-arsenal 2>/dev/null || echo /usb-arsenal-aarch64)"
    }

    private fun runSu(cmd: String): String {
        return try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor()
            text.replace(Regex("\u001B\\[[0-9;]*m"), "")
        } catch (e: Exception) {
            "su failed: ${e.message}"
        }
    }

    private fun start() {
        prefs.edit().putString("bin", pathBox.text.toString().trim()).apply()
        val bin = usbArsenalCmd()
        setStatus("starting…")
        Thread {
            val out = runSu("$bin hid start")
            var kbd = findDev(out, "keyboard")
            var mouse = findDev(out, "mouse")
            var note = ""
            if (kbd == null || mouse == null) {
                // maybe it was already started: fall back to the usual nodes if they exist
                val ls = runSu("ls /dev/hidg* 2>/dev/null")
                if (ls.contains("/dev/hidg0") && ls.contains("/dev/hidg1")) {
                    kbd = "/dev/hidg0"
                    mouse = "/dev/hidg1"
                    note = " (guessed nodes)"
                }
            }
            if (kbd == null || mouse == null) {
                setStatus("hid start failed: " + out.trim().take(200))
                return@Thread
            }
            val kd: String = kbd
            val md: String = mouse
            runOnUiThread {
                hid?.close()
                val h = Hid(kd, md)
                h.onStickyCleared = { runOnUiThread { refreshSticky() } }
                hid = h
                pad.hid = h
                catcher.hid = h
                refreshSticky()
                statusView.text = "Ready: keyboard $kd, mouse $md$note"
            }
        }.start()
    }

    private fun findDev(out: String, word: String): String? {
        val m = Pattern.compile("$word.*?(/dev/hidg\\d+)", Pattern.CASE_INSENSITIVE).matcher(out)
        return if (m.find()) m.group(1) else null
    }

    private fun stop() {
        val bin = usbArsenalCmd()
        hid?.close()
        hid = null
        pad.hid = null
        catcher.hid = null
        refreshSticky()
        setStatus("stopping…")
        Thread {
            runSu("$bin hid stop")
            setStatus("Stopped")
        }.start()
    }

    override fun onPause() {
        super.onPause()
        hid?.releaseAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        hid?.close()
    }
}
