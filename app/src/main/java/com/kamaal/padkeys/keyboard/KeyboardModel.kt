package com.kamaal.padkeys.keyboard

/** What a key does when typed. Interpreted by the IME service, never by the view. */
sealed interface KeyAction {
    /** Commit text. [shifted] is used when shift/caps is on. */
    data class Text(val text: String, val shifted: String = text.uppercase()) : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object Shift : KeyAction
    /** Switch to another layer (e.g. letters <-> symbols). */
    data class Layer(val target: LayerId) : KeyAction
    data object CaretLeft : KeyAction
    data object CaretRight : KeyAction
    data object Hide : KeyAction
}

enum class LayerId { LETTERS, SYMBOLS, SYMBOLS_2 }

enum class ShiftState { OFF, ON, CAPS }

/** Which on-screen cursor: left stick drives LEFT, right stick drives RIGHT. */
enum class CursorSide { LEFT, RIGHT }

data class Key(
    val action: KeyAction,
    val label: String,
    /** Width in key units (1 = standard key). */
    val width: Float = 1f,
    /** Optional small glyph hint shown on the key, e.g. the gamepad button bound to it ("X", "B"). */
    val hint: String? = null,
)

data class KeyRow(val keys: List<Key>, /** Leading offset in key units. */ val indent: Float = 0f)

data class KeyboardLayout(val id: LayerId, val rows: List<KeyRow>)
