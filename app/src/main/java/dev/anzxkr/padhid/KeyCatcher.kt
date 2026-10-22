package dev.anzxkr.padhid

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * A tiny invisible text target. Whatever the on-screen keyboard "types" into it is
 * turned into HID key presses instead of text.
 */
class KeyCatcher(ctx: Context) : View(ctx) {
    var hid: Hid? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_ACTION_NONE
        return Conn(this)
    }

    private inner class Conn(v: View) : BaseInputConnection(v, false) {
        private var composing = ""

        /** Make the host's text match `newText`: backspace the tail that changed, type the rest. */
        private fun apply(newText: String) {
            val h = hid
            if (h != null) {
                var i = 0
                while (i < composing.length && i < newText.length && composing[i] == newText[i]) i++
                repeat(composing.length - i) { h.key(Keymap.BKSP) }
                if (newText.length > i) h.text(newText.substring(i))
            }
            composing = newText
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            apply(text?.toString() ?: "")
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            apply(text?.toString() ?: "")
            composing = ""
            return true
        }

        override fun finishComposingText(): Boolean {
            composing = ""
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val h = hid ?: return true
            repeat(beforeLength) { h.key(Keymap.BKSP) }
            repeat(afterLength) { h.key(Keymap.DEL) }
            composing = ""
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action != KeyEvent.ACTION_DOWN) return true
            val h = hid ?: return true
            val usage = Keymap.fromKeyCode(event.keyCode)
            if (usage != null) {
                h.key(usage)
            } else {
                val u = event.unicodeChar
                if (u != 0) h.text(u.toChar().toString())
            }
            return true
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            hid?.key(Keymap.ENTER)
            return true
        }
    }
}
