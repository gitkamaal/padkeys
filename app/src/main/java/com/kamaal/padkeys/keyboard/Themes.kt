package com.kamaal.padkeys.keyboard

import android.content.Context

/** Every colour the keyboard draws. Colours are ARGB ints. */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val background: Long,
    val key: Long,
    /** Function keys (shift, delete, return, layer switches). */
    val modKey: Long,
    val label: Long,
    /** The "space" label and other quiet text. */
    val quietLabel: Long,
    /** Button hints in key corners and legend descriptions. */
    val hint: Long,
    /** Legend button glyphs ("A", "LT"). */
    val legend: Long,
    val leftCursor: Long,
    val rightCursor: Long,
    /** Shift key fill while shift is on for one letter. */
    val shiftOn: Long,
    /** Shift key fill and label while caps lock is on. */
    val capsFill: Long,
    val capsLabel: Long,
    val monospace: Boolean = false,
) {
    val isLight get() = luminance(background) > 0.5
}

private fun luminance(c: Long): Double {
    val r = (c shr 16 and 0xFF) / 255.0
    val g = (c shr 8 and 0xFF) / 255.0
    val b = (c and 0xFF) / 255.0
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

object Themes {
    val all = listOf(
        KeyboardTheme(
            "midnight", "Midnight",
            background = 0xFF111317, key = 0xFF23262E, modKey = 0xFF1A1D23,
            label = 0xFFE9EBF0, quietLabel = 0xFF8A91A1, hint = 0xFF7D8494, legend = 0xFFB9BECA,
            leftCursor = 0xFF4FD1C5, rightCursor = 0xFFF2A65A,
            shiftOn = 0xFF3A3F4B, capsFill = 0xFFE9EBF0, capsLabel = 0xFF111317,
        ),
        KeyboardTheme(
            "oled", "OLED",
            background = 0xFF000000, key = 0xFF151515, modKey = 0xFF0A0A0A,
            label = 0xFFF2F2F2, quietLabel = 0xFF7A7A7A, hint = 0xFF6B6B6B, legend = 0xFFBDBDBD,
            leftCursor = 0xFF5AC8FA, rightCursor = 0xFFFF6B8B,
            shiftOn = 0xFF2C2C2C, capsFill = 0xFFF2F2F2, capsLabel = 0xFF000000,
        ),
        KeyboardTheme(
            "paper", "Paper",
            background = 0xFFE6E3DC, key = 0xFFFBFAF7, modKey = 0xFFD3CFC6,
            label = 0xFF1F1E1B, quietLabel = 0xFF8A857B, hint = 0xFF8A857B, legend = 0xFF3F3C36,
            leftCursor = 0xFF0E8C7F, rightCursor = 0xFFD2601A,
            shiftOn = 0xFFBDB8AD, capsFill = 0xFF1F1E1B, capsLabel = 0xFFFBFAF7,
        ),
        KeyboardTheme(
            "sakura", "Sakura",
            background = 0xFF241A1F, key = 0xFF3A2A32, modKey = 0xFF2E2128,
            label = 0xFFFBE9F0, quietLabel = 0xFFB59AA6, hint = 0xFFA68593, legend = 0xFFE7CBD6,
            leftCursor = 0xFFFF8FB8, rightCursor = 0xFF9FE3C8,
            shiftOn = 0xFF553E49, capsFill = 0xFFFF8FB8, capsLabel = 0xFF241A1F,
        ),
        KeyboardTheme(
            "terminal", "Terminal",
            background = 0xFF080C08, key = 0xFF111B11, modKey = 0xFF0C140C,
            label = 0xFF7CFF8A, quietLabel = 0xFF3F8F48, hint = 0xFF3F8F48, legend = 0xFF7CFF8A,
            leftCursor = 0xFF7CFF8A, rightCursor = 0xFFFFD166,
            shiftOn = 0xFF1E3A20, capsFill = 0xFF7CFF8A, capsLabel = 0xFF080C08,
            monospace = true,
        ),
        KeyboardTheme(
            "ember", "Ember",
            background = 0xFF19110E, key = 0xFF2C1F1A, modKey = 0xFF221813,
            label = 0xFFF6E7DB, quietLabel = 0xFFA48B7C, hint = 0xFF977D6E, legend = 0xFFE2CBBB,
            leftCursor = 0xFFFF7A45, rightCursor = 0xFFFFC24B,
            shiftOn = 0xFF4A342B, capsFill = 0xFFFF7A45, capsLabel = 0xFF19110E,
        ),
        KeyboardTheme(
            "glacier", "Glacier",
            background = 0xFF0E1822, key = 0xFF1A2A3A, modKey = 0xFF14212E,
            label = 0xFFE6F1FA, quietLabel = 0xFF8AA2B8, hint = 0xFF7890A6, legend = 0xFFC4D6E6,
            leftCursor = 0xFF7FD4FF, rightCursor = 0xFFC3A6FF,
            shiftOn = 0xFF2B4157, capsFill = 0xFFE6F1FA, capsLabel = 0xFF0E1822,
        ),
    )

    val default = all.first()

    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: default

    private const val PREFS = "padkeys"
    private const val KEY_THEME = "theme"

    fun load(context: Context): KeyboardTheme =
        byId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, null))

    fun save(context: Context, theme: KeyboardTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, theme.id).apply()
    }
}
