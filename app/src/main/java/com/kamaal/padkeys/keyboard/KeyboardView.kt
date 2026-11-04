package com.kamaal.padkeys.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Split keyboard with two floating cursors, one per stick.
 *
 * Geometry is kept in key units (1 = one standard key) and converted to pixels at draw time.
 * Both cursors can roam the whole keyboard independently; they start on the home keys (F and J).
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Called when a key is tapped by touch. */
    var onKeyTapped: ((Key) -> Unit)? = null

    // ---- model --------------------------------------------------------------------------------

    private class Cell(val key: Key, val row: Int, val x: Float, val w: Float) {
        val cx get() = x + w / 2f
    }

    private class Cursor(var x: Float, var y: Float) {
        var cell: Cell? = null
        var pressedAt = 0L
    }

    private var layout: KeyboardLayout? = null
    private var cells: List<Cell> = emptyList()
    private var rowCount = 0
    private var unitsWide = 1f

    private var shift = ShiftState.OFF
    private var activeSide = CursorSide.LEFT
    private var cursorsVisible = true

    private val leftCursor = Cursor(-1f, -1f)
    private val rightCursor = Cursor(-1f, -1f)

    private var touchCell: Cell? = null
    private var touchPressedAt = 0L

    private fun cursor(side: CursorSide) = if (side == CursorSide.LEFT) leftCursor else rightCursor

    // ---- public API (contract) ----------------------------------------------------------------

    fun setLayout(layout: KeyboardLayout) {
        this.layout = layout
        val built = ArrayList<Cell>()
        unitsWide = layout.rows.maxOfOrNull { r -> r.indent + r.keys.sumOf { it.width.toDouble() }.toFloat() } ?: 1f
        layout.rows.forEachIndexed { r, row ->
            var x = row.indent
            for (key in row.keys) {
                built += Cell(key, r, x, key.width)
                x += key.width
            }
        }
        cells = built
        rowCount = layout.rows.size

        if (leftCursor.x < 0f) placeOn(leftCursor, "f", CursorSide.LEFT)
        if (rightCursor.x < 0f) placeOn(rightCursor, "j", CursorSide.RIGHT)
        clamp(CursorSide.LEFT)
        clamp(CursorSide.RIGHT)
        resolve(CursorSide.LEFT)
        resolve(CursorSide.RIGHT)
        if (width > 0) computeOrigin()
        requestLayout()
        invalidate()
    }

    fun setShiftState(state: ShiftState) {
        if (shift != state) {
            shift = state
            invalidate()
        }
    }

    /** Continuous move in key units (1f = one key width/height). Called every frame from stick input. */
    fun nudgeCursor(side: CursorSide, dx: Float, dy: Float) {
        val c = cursor(side)
        c.x += dx
        c.y += dy
        clamp(side)
        resolve(side)
        invalidate()
    }

    /**
     * Discrete move of [side]'s cursor to the neighbouring key. dCol/dRow are -1, 0 or 1. Cursors roam the
     * whole keyboard; at an edge the cursor stays put. Returns [side].
     */
    fun stepCursor(side: CursorSide, dCol: Int, dRow: Int): CursorSide {
        val c = cursor(side)
        val from = c.cell ?: resolve(side) ?: return side
        var target: Cell? = null
        if (dCol != 0) {
            val row = cells.filter { it.row == from.row }
            target = row.getOrNull(row.indexOf(from) + dCol)
            if (target == null && dRow == 0) return side
        }
        if (dRow != 0) {
            val base = target ?: from
            val r = base.row + dRow
            if (r in 0 until rowCount) {
                // Keep the column: pick the key in the next row under the cursor's x.
                val x = if (target == null) c.x else base.cx
                target = cells.filter { it.row == r }.minByOrNull { distanceX(it, x) }
            }
        }
        target ?: return side
        c.x = if (dCol == 0 && c.x in target.x..(target.x + target.w)) c.x else target.cx
        c.y = target.row + 0.5f
        clamp(side)
        resolve(side)
        activeSide = side
        invalidate()
        return side
    }

    /** Put both cursors back on the home keys (F and J) and make the left one active. */
    fun resetCursors() {
        placeOn(leftCursor, "f", CursorSide.LEFT)
        placeOn(rightCursor, "j", CursorSide.RIGHT)
        clamp(CursorSide.LEFT)
        clamp(CursorSide.RIGHT)
        resolve(CursorSide.LEFT)
        resolve(CursorSide.RIGHT)
        activeSide = CursorSide.LEFT
        invalidate()
    }

    /** Key currently under the given cursor, or null if none. */
    fun keyUnder(side: CursorSide): Key? = (cursor(side).cell ?: resolve(side))?.key

    /** Marks which cursor A-button presses target; drawn more prominently. */
    fun setActiveSide(side: CursorSide) {
        if (activeSide != side) {
            activeSide = side
            invalidate()
        }
    }

    /** Visual press feedback for the key under [side]. */
    fun showPress(side: CursorSide) {
        cursor(side).pressedAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    /** Hide cursors when the user is typing by touch; show again on controller input. */
    fun setCursorsVisible(visible: Boolean) {
        if (cursorsVisible != visible) {
            cursorsVisible = visible
            invalidate()
        }
    }

    // ---- geometry helpers ---------------------------------------------------------------------

    private fun distanceX(cell: Cell, x: Float) = when {
        x < cell.x -> cell.x - x
        x > cell.x + cell.w -> x - (cell.x + cell.w)
        else -> 0f
    } + abs(cell.cx - x) * 0.001f // tie-break toward the closer centre

    private fun placeOn(c: Cursor, label: String, side: CursorSide) {
        val cell = cells.firstOrNull { it.key.label == label }
            ?: cells.filter { it.row == rowCount / 2 }.let { it.getOrNull(if (side == CursorSide.LEFT) 0 else it.size - 1) }
            ?: return
        c.x = cell.cx
        c.y = cell.row + 0.5f
    }

    private fun clamp(side: CursorSide) {
        val c = cursor(side)
        c.x = c.x.coerceIn(0f, unitsWide)
        c.y = c.y.coerceIn(0f, max(0f, rowCount - 0.001f))
    }

    private fun resolve(side: CursorSide): Cell? {
        val c = cursor(side)
        val row = c.y.toInt().coerceIn(0, max(0, rowCount - 1))
        val cell = cells.filter { it.row == row }.minByOrNull { distanceX(it, c.x) }
        c.cell = cell
        return cell
    }

    // ---- measurement & drawing ----------------------------------------------------------------

    private val dp = resources.displayMetrics.density
    private val legendH = 22 * dp
    private val pad = 6 * dp
    private val gap = 5 * dp
    private val rowH = 44 * dp
    private val maxUnit = 66 * dp
    private val radius = 9 * dp

    private var unitPx = 0f
    private var originX = 0f
    private var originY = 0f

    /** Space reserved under the keys for the system's IME nav bar (back / keyboard switcher). */
    private var bottomInset = 0

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        if (bottom != bottomInset) {
            bottomInset = bottom
            requestLayout()
        }
        return insets
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (legendH + pad * 2 + rowCount * rowH).toInt() + bottomInset
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = computeOrigin()

    private fun computeOrigin() {
        unitPx = min((width - pad * 2) / unitsWide, maxUnit)
        originX = (width - unitPx * unitsWide) / 2f
        originY = legendH + pad
    }

    private val bgPaint = Paint().apply { color = 0xFF111317.toInt() }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF23262E.toInt() }
    private val modPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1D23.toInt() }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE9EBF0.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7D8494.toInt()
        textAlign = Paint.Align.RIGHT
        textSize = 9.5f * dp
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7D8494.toInt()
        textSize = 11f * dp
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private var theme = Themes.default
    private val leftColor get() = theme.leftCursor.toInt()
    private val rightColor get() = theme.rightCursor.toInt()

    fun setTheme(theme: KeyboardTheme) {
        this.theme = theme
        bgPaint.color = theme.background.toInt()
        keyPaint.color = theme.key.toInt()
        modPaint.color = theme.modKey.toInt()
        val family = if (theme.monospace) "monospace" else "sans-serif"
        labelPaint.typeface = Typeface.create(family, Typeface.NORMAL)
        val medium = if (theme.monospace) Typeface.create("monospace", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        hintPaint.typeface = medium
        legendPaint.typeface = medium
        invalidate()
    }

    init {
        setTheme(Themes.default)
    }

    private val rect = RectF()

    private fun isModifier(key: Key) = key.action !is KeyAction.Text && key.action !is KeyAction.Space

    private fun cellRect(cell: Cell, out: RectF) {
        val l = originX + cell.x * unitPx
        val t = originY + cell.row * rowH
        out.set(l + gap / 2, t + gap / 2, l + cell.w * unitPx - gap / 2, t + rowH - gap / 2)
    }

    override fun onDraw(canvas: Canvas) {
        if (unitPx == 0f) computeOrigin()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawLegend(canvas)

        val now = SystemClock.uptimeMillis()
        var animating = false


        for (cell in cells) {
            cellRect(cell, rect)

            var pressT = 0f
            for (side in CursorSide.entries) {
                val c = cursor(side)
                if (c.cell === cell) pressT = max(pressT, pressProgress(c.pressedAt, now))
            }
            if (touchCell === cell) pressT = max(pressT, if (touchPressedAt > 0) 1f else 0f)
            if (pressT > 0f) {
                animating = animating || touchCell !== cell
                val inset = 2.5f * dp * pressT
                rect.inset(inset, inset)
            }

            val shiftKey = cell.key.action is KeyAction.Shift
            val paint = when {
                shiftKey && shift != ShiftState.OFF -> fillPaint.apply { color = (if (shift == ShiftState.CAPS) theme.capsFill else theme.shiftOn).toInt() }
                isModifier(cell.key) -> modPaint
                else -> keyPaint
            }
            canvas.drawRoundRect(rect, radius, radius, paint)

            if (cursorsVisible) drawHighlights(canvas, cell, pressT)

            drawLabel(canvas, cell, shiftKey)
        }

        if (animating) postInvalidateOnAnimation()
    }

    private fun pressProgress(at: Long, now: Long): Float {
        val dt = now - at
        if (at == 0L || dt > PRESS_MS) return 0f
        return 1f - dt / PRESS_MS.toFloat()
    }

    private fun drawHighlights(canvas: Canvas, cell: Cell, pressT: Float) {
        val both = leftCursor.cell === cell && rightCursor.cell === cell
        // Inactive first so the active ring sits on top; when both share a key, nest the inactive ring inside.
        for (side in listOf(activeSide.let { if (it == CursorSide.LEFT) CursorSide.RIGHT else CursorSide.LEFT }, activeSide)) {
            if (cursor(side).cell !== cell) continue
            val active = side == activeSide
            val color = if (side == CursorSide.LEFT) leftColor else rightColor
            fillPaint.color = withAlpha(color, (if (active) 0x40 else 0x22) + (0x50 * pressT).toInt())
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            ringPaint.color = withAlpha(color, if (active) 0xFF else 0x99)
            ringPaint.strokeWidth = (if (active) 2.5f else 1.5f) * dp
            val i = ringPaint.strokeWidth / 2 + if (both && !active) 4f * dp else 0f
            rect.inset(i, i)
            canvas.drawRoundRect(rect, radius - i, radius - i, ringPaint)
            rect.inset(-i, -i)
        }
    }

    private fun drawLabel(canvas: Canvas, cell: Cell, shiftKey: Boolean) {
        val key = cell.key
        val action = key.action
        val label = when {
            action is KeyAction.Text && shift != ShiftState.OFF -> action.shifted
            else -> key.label
        }
        labelPaint.textSize = when {
            label.length > 3 -> 13f * dp
            action is KeyAction.Text -> 19f * dp
            else -> 17f * dp
        }
        labelPaint.color = when {
            shiftKey && shift == ShiftState.CAPS -> theme.capsLabel.toInt()
            action is KeyAction.Space -> theme.quietLabel.toInt()
            else -> theme.label.toInt()
        }
        val baseline = rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2
        canvas.drawText(label, rect.centerX(), baseline, labelPaint)

        key.hint?.let {
            hintPaint.color = if (shiftKey && shift == ShiftState.CAPS) withAlpha(theme.capsLabel.toInt(), 0x99) else theme.hint.toInt()
            canvas.drawText(it, rect.right - 6 * dp, rect.top + 12 * dp, hintPaint)
        }
    }

    private fun drawLegend(canvas: Canvas) {
        val y = legendH / 2 + pad / 2 - (legendPaint.descent() + legendPaint.ascent()) / 2
        var x = originX.coerceAtLeast(pad) + 4 * dp
        fun item(glyph: String, text: String, color: Int? = null) {
            legendPaint.color = color ?: theme.legend.toInt()
            canvas.drawText(glyph, x, y, legendPaint)
            x += legendPaint.measureText(glyph) + 4 * dp
            legendPaint.color = theme.hint.toInt()
            canvas.drawText(text, x, y, legendPaint)
            x += legendPaint.measureText(text) + 14 * dp
        }
        item("A", "type")
        item("LT", "shift")
        item("RT", "enter")
        item("B", "delete")
        item("X", "space")
        item("L1", "123")
        item("R1", "#+=")
        item("⧉", "hold to close")
    }

    // ---- touch --------------------------------------------------------------------------------

    private val repeatBackspace = object : Runnable {
        override fun run() {
            val cell = touchCell ?: return
            onKeyTapped?.invoke(cell.key)
            postDelayed(this, 50)
        }
    }

    /** Only the first finger down drives the keyboard; extra fingers are ignored. */
    private var touchPointer = -1

    /** Backspace deletes as soon as a finger lands on it (by tap or slide), then repeats while held. */
    private fun enterTouchCell(cell: Cell?) {
        removeCallbacks(repeatBackspace)
        touchCell = cell
        touchPressedAt = if (cell != null) SystemClock.uptimeMillis() else 0L
        if (cell?.key?.action is KeyAction.Backspace) {
            performHapticFeedback(KEYBOARD_TAP)
            onKeyTapped?.invoke(cell.key)
            postDelayed(repeatBackspace, 400)
        }
        invalidate()
    }

    private fun releaseTouch(x: Float, y: Float, commit: Boolean) {
        removeCallbacks(repeatBackspace)
        val cell = touchCell
        touchCell = null
        touchPressedAt = 0L
        touchPointer = -1
        if (commit && cell != null && cell.key.action !is KeyAction.Backspace && cellAt(x, y) === cell) {
            performHapticFeedback(KEYBOARD_TAP)
            onKeyTapped?.invoke(cell.key)
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchPointer = event.getPointerId(0)
                enterTouchCell(cellAt(event.x, event.y))
            }
            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(touchPointer)
                if (i >= 0) {
                    val now = cellAt(event.getX(i), event.getY(i))
                    if (now !== touchCell) enterTouchCell(now)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                if (event.getPointerId(i) == touchPointer) releaseTouch(event.getX(i), event.getY(i), commit = true)
            }
            MotionEvent.ACTION_UP -> {
                val i = event.findPointerIndex(touchPointer)
                if (i >= 0) releaseTouch(event.getX(i), event.getY(i), commit = true) else releaseTouch(0f, 0f, commit = false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> releaseTouch(0f, 0f, commit = false)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun cellAt(px: Float, py: Float): Cell? {
        if (unitPx == 0f) return null
        val ux = (px - originX) / unitPx
        val row = ((py - originY) / rowH).toInt()
        if (py < originY || row !in 0 until rowCount) return null
        return cells.firstOrNull { it.row == row && ux >= it.x && ux < it.x + it.w }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(repeatBackspace)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val PRESS_MS = 140L
        val KEYBOARD_TAP = android.view.HapticFeedbackConstants.KEYBOARD_TAP
        fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }
}
