package com.kamaal.padkeys

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kamaal.padkeys.keyboard.KeyboardTheme
import com.kamaal.padkeys.keyboard.KeyboardView
import com.kamaal.padkeys.keyboard.LayerId
import com.kamaal.padkeys.keyboard.Layouts
import com.kamaal.padkeys.keyboard.Themes
import kotlin.math.max

/** Setup + personalisation: default-keyboard status, theme picker with a live preview, and a test field. */
class SetupActivity : Activity() {

    private val dp by lazy { resources.displayMetrics.density }
    private fun Int.dp() = (this * dp).toInt()

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    private lateinit var defaultButton: Button
    private lateinit var preview: KeyboardView
    private val cards = mutableListOf<Pair<KeyboardTheme, ThemeCard>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(BG)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.setPadding(24.dp(), 16.dp(), 24.dp(), 16.dp())

        content.addView(header())
        content.addView(sectionLabel("Theme"))
        content.addView(themeRow())
        content.addView(sectionLabel("Try it"))
        content.addView(testField(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        preview = KeyboardView(this).apply {
            setLayout(Layouts.forLayer(LayerId.LETTERS))
            setTheme(Themes.load(context))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        content.addView(
            preview,
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 16.dp() },
        )

        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        // Edge-to-edge (target 35) means adjustResize no longer shrinks the window, so make room for the
        // keyboard ourselves and hide the preview while the real keyboard is up.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            val imeVisible = insets.isVisible(WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            preview.visibility = if (imeVisible) View.GONE else View.VISIBLE
            // The preview is gone, so the test field is now the last thing on the page: pin it above the keyboard.
            if (imeVisible) view.post { (view as ScrollView).fullScroll(View.FOCUS_DOWN); currentFocus?.requestFocus() }
            insets
        }
        setContentView(scroll)
        val current = Themes.load(this)
        select(current, save = false)
        // Start controller focus on the current theme (not the text field, which would open the keyboard).
        cards.first { it.first.id == current.id }.second.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus() // returning from the system IME picker
    }

    // ---- header -------------------------------------------------------------------------------

    private fun header(): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(TEXT)
        })
        titles.addView(TextView(this).apply {
            text = "A controller-first keyboard for Android handhelds"
            textSize = 13f
            setTextColor(MUTED)
        })
        row.addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        statusDot = View(this)
        row.addView(statusDot, LinearLayout.LayoutParams(8.dp(), 8.dp()).apply { marginEnd = 8.dp() })
        statusText = TextView(this).apply { textSize = 13f; setTextColor(MUTED) }
        row.addView(statusText, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = 12.dp() })

        enableButton = pillButton("Enable") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        defaultButton = pillButton("Set as default") {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }
        row.addView(enableButton)
        row.addView(defaultButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = 8.dp() })
        return row
    }

    private fun refreshStatus() {
        val manager = getSystemService(InputMethodManager::class.java)
        val enabled = manager.enabledInputMethodList.any { it.packageName == packageName }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        statusText.text = when {
            selected -> "Default keyboard"
            enabled -> "Enabled, not default"
            else -> "Not enabled"
        }
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (selected) 0xFF5FD08A.toInt() else 0xFFE0A84F.toInt())
        }
        enableButton.visibility = if (enabled) View.GONE else View.VISIBLE
        defaultButton.visibility = if (enabled && !selected) View.VISIBLE else View.GONE
    }

    private fun pillButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(TEXT)
        minHeight = 0; minimumHeight = 0
        setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
        stateListAnimator = null
        background = GradientDrawable().apply { cornerRadius = 18f * dp; setColor(SURFACE) }
        setOnClickListener { onClick() }
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(MUTED)
        setPadding(0, 18.dp(), 0, 8.dp())
    }

    // ---- themes -------------------------------------------------------------------------------

    private fun themeRow(): View {
        val row = LinearLayout(this)
        for (theme in Themes.all) {
            val card = ThemeCard(this, theme).apply {
                setOnClickListener { select(theme, save = true) }
            }
            cards += theme to card
            row.addView(card, LinearLayout.LayoutParams(120.dp(), 92.dp()).apply { marginEnd = 10.dp() })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(row)
        }
    }

    private fun select(theme: KeyboardTheme, save: Boolean) {
        if (save) Themes.save(this, theme)
        cards.forEach { (t, card) -> card.chosen = t.id == theme.id }
        preview.setTheme(theme)
    }

    private fun testField() = EditText(this).apply {
        hint = "Focus here and type with the controller"
        setHintTextColor(MUTED)
        setTextColor(TEXT)
        textSize = 16f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = 1
        maxLines = 3
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        background = GradientDrawable().apply { cornerRadius = 10f * dp; setColor(SURFACE) }
    }

    /** A miniature keyboard in the theme's colours. Focusable so it can be picked with the d-pad. */
    private class ThemeCard(context: Context, private val theme: KeyboardTheme) : View(context) {
        var chosen = false
            set(value) { field = value; invalidate() }

        private val d = context.resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * d
            typeface = Typeface.create(if (theme.monospace) "monospace" else "sans-serif-medium", Typeface.NORMAL)
        }
        private val r = RectF()

        init {
            isFocusable = true
            isClickable = true
            contentDescription = "${theme.name} theme"
            setOnFocusChangeListener { _, _ -> invalidate() }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val inset = 3 * d
            r.set(inset, inset, w - inset, h - inset)
            paint.color = theme.background.toInt()
            canvas.drawRoundRect(r, 12 * d, 12 * d, paint)

            // Two rows of mini keys, with the left and right cursors on the home keys.
            val cols = 5
            val gap = 4 * d
            val left = r.left + 10 * d
            val top = r.top + 10 * d
            val kw = (r.width() - 20 * d - gap * (cols - 1)) / cols
            val kh = 14 * d
            for (row in 0..1) for (col in 0 until cols) {
                val x = left + col * (kw + gap)
                val y = top + row * (kh + gap)
                val k = RectF(x, y, x + kw, y + kh)
                paint.color = (if (col == cols - 1) theme.modKey else theme.key).toInt()
                canvas.drawRoundRect(k, 3 * d, 3 * d, paint)
                val cursor = when {
                    row == 1 && col == 1 -> theme.leftCursor
                    row == 1 && col == 3 -> theme.rightCursor
                    else -> null
                }
                if (cursor != null) {
                    ring.color = cursor.toInt()
                    ring.strokeWidth = 1.5f * d
                    canvas.drawRoundRect(k, 3 * d, 3 * d, ring)
                }
            }

            text.color = theme.label.toInt()
            canvas.drawText(theme.name, left, r.bottom - 11 * d, text)

            if (chosen || isFocused) {
                ring.color = if (chosen) TEXT else MUTED
                ring.strokeWidth = (if (chosen) 2.5f else 1.5f) * d
                r.inset(-1.5f * d, -1.5f * d)
                canvas.drawRoundRect(r, 13.5f * d, 13.5f * d, ring)
            }
        }
    }

    private companion object {
        const val BG = 0xFF0E0F12.toInt()
        const val SURFACE = 0xFF1C1E24.toInt()
        const val TEXT = 0xFFECEEF2.toInt()
        const val MUTED = 0xFF8C93A3.toInt()
    }
}
