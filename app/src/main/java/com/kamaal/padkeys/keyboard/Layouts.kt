package com.kamaal.padkeys.keyboard

/**
 * Every row is 11.5 key units wide so the halves line up across layers. Hints name the gamepad
 * button that does the same thing, so the controller shortcuts are learnable from the keys.
 */
object Layouts {

    fun forLayer(id: LayerId): KeyboardLayout = when (id) {
        LayerId.LETTERS -> letters
        LayerId.SYMBOLS -> symbols
        LayerId.SYMBOLS_2 -> symbols2
    }

    private fun t(c: String) = Key(KeyAction.Text(c), c)
    private fun s(c: String) = Key(KeyAction.Text(c, c), c)
    private fun row(chars: String, text: (String) -> Key = ::s) = chars.split(' ').map(text)

    private val backspace = Key(KeyAction.Backspace, "⌫", width = 1.5f, hint = "B")
    private val enter = Key(KeyAction.Enter, "return", width = 2f, hint = "RT")
    private val shift = Key(KeyAction.Shift, "⇧", width = 1.5f, hint = "LT")
    private val space = Key(KeyAction.Space, "space", width = 6f, hint = "X")
    private val left = Key(KeyAction.CaretLeft, "‹")
    private val right = Key(KeyAction.CaretRight, "›")
    private val hide = Key(KeyAction.Hide, "⌄")
    private val toSymbols = Key(KeyAction.Layer(LayerId.SYMBOLS), "?123", width = 1.5f, hint = "L1")
    private val toLetters = Key(KeyAction.Layer(LayerId.LETTERS), "ABC", width = 1.5f)
    private val toSymbols2 = Key(KeyAction.Layer(LayerId.SYMBOLS_2), "=\\<", width = 1.5f, hint = "R1")
    private val backToSymbols = Key(KeyAction.Layer(LayerId.SYMBOLS), "?123", width = 1.5f, hint = "L1")

    private val letters = KeyboardLayout(
        LayerId.LETTERS,
        listOf(
            KeyRow(row("q w e r t y u i o p", ::t) + backspace),
            KeyRow(row("a s d f g h j k l", ::t) + enter, indent = 0.5f),
            KeyRow(listOf(shift) + row("z x c v b n m", ::t) + row(", . ?")),
            KeyRow(listOf(toSymbols, left, space, right, s("'"), hide)),
        ),
    )

    private val symbols = KeyboardLayout(
        LayerId.SYMBOLS,
        listOf(
            KeyRow(row("1 2 3 4 5 6 7 8 9 0") + backspace),
            KeyRow(row("@ # $ % & - + ( )") + enter, indent = 0.5f),
            KeyRow(listOf(toSymbols2) + row("* \" ' : ; ! ? / _ ~")),
            KeyRow(listOf(toLetters, left, space, right, s(","), s("."))),
        ),
    )

    private val symbols2 = KeyboardLayout(
        LayerId.SYMBOLS_2,
        listOf(
            KeyRow(row("` | • √ π ÷ × ¶ ∆ °") + backspace),
            KeyRow(row("£ € ¥ ^ = { } [ ]") + enter, indent = 0.5f),
            KeyRow(listOf(backToSymbols) + row("< > \\ © ® ™ ✓ … ¿ ¡")),
            KeyRow(listOf(toLetters, left, space, right, s(","), s("."))),
        ),
    )
}
