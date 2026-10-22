package dev.anzxkr.padhid

import android.view.KeyEvent

/** USB HID keyboard usage IDs (US layout) and modifier bits. */
object Keymap {
    const val MOD_CTRL = 0x01
    const val MOD_SHIFT = 0x02
    const val MOD_ALT = 0x04
    const val MOD_META = 0x08

    const val ENTER = 0x28
    const val ESC = 0x29
    const val BKSP = 0x2A
    const val TAB = 0x2B
    const val DEL = 0x4C
    const val HOME = 0x4A
    const val PGUP = 0x4B
    const val END = 0x4D
    const val PGDN = 0x4E
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52

    private val SYMBOLS: Map<Char, Pair<Int, Int>> = HashMap<Char, Pair<Int, Int>>().apply {
        put('0', 0x27 to 0)
        put(' ', 0x2C to 0)
        put('\n', ENTER to 0)
        put('\t', TAB to 0)

        // shifted digits: ! @ # $ % ^ & * ( )
        val digitSyms = charArrayOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')')
        for (i in digitSyms.indices) put(digitSyms[i], (0x1E + i) to MOD_SHIFT)

        val base = "-=[]\\;'`,./"
        val shifted = "_+{}|:\"~<>?"
        val ids = intArrayOf(0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38)
        for (i in ids.indices) {
            put(base[i], ids[i] to 0)
            put(shifted[i], ids[i] to MOD_SHIFT)
        }
    }

    /** Returns (usage, modifiers) for a character, or null if it has no key on a US layout. */
    fun fromChar(c: Char): Pair<Int, Int>? {
        if (c in 'a'..'z') return (0x04 + (c - 'a')) to 0
        if (c in 'A'..'Z') return (0x04 + (c - 'A')) to MOD_SHIFT
        if (c in '1'..'9') return (0x1E + (c - '1')) to 0
        return SYMBOLS[c]
    }

    /** Android KeyEvent code (from the on-screen keyboard) to HID usage. */
    fun fromKeyCode(code: Int): Int? = when (code) {
        KeyEvent.KEYCODE_DEL -> BKSP
        KeyEvent.KEYCODE_FORWARD_DEL -> DEL
        KeyEvent.KEYCODE_ENTER -> ENTER
        KeyEvent.KEYCODE_TAB -> TAB
        KeyEvent.KEYCODE_ESCAPE -> ESC
        KeyEvent.KEYCODE_SPACE -> 0x2C
        KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> UP
        KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
        KeyEvent.KEYCODE_MOVE_HOME -> HOME
        KeyEvent.KEYCODE_MOVE_END -> END
        KeyEvent.KEYCODE_PAGE_UP -> PGUP
        KeyEvent.KEYCODE_PAGE_DOWN -> PGDN
        else -> null
    }
}
