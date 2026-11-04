package com.kamaal.padkeys.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.kamaal.padkeys.input.GamepadController
import com.kamaal.padkeys.keyboard.*

class PadKeysImeService : InputMethodService() {
    private var keyboard: KeyboardView? = null
    private var controller: GamepadController? = null
    private var layer = LayerId.LETTERS
    private var shift = ShiftState.OFF
    private var lastShiftPress = 0L
    private var manualShift = false
    private var inputVisible = false

    override fun onCreateInputView(): View {
        controller?.reset()
        return KeyboardView(this).also { view ->
            keyboard = view
            view.setLayout(Layouts.forLayer(layer))
            view.setTheme(Themes.load(this))
            view.setShiftState(shift)
            view.onKeyTapped = { key ->
                controller?.reset()
                view.setCursorsVisible(false)
                handle(key.action)
            }
            controller = GamepadController(view, ::handle) { target ->
                switchLayer(if (layer == target) LayerId.LETTERS else target)
            }
        }
    }
    override fun onEvaluateFullscreenMode() = false
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true // A connected gamepad must not suppress the software keyboard.
    }
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        controller?.reset()
        if (!restarting) {
            layer = LayerId.LETTERS; shift = ShiftState.OFF; manualShift = false; lastShiftPress = 0L
        }
        keyboard?.setLayout(Layouts.forLayer(layer))
        updateAutoCaps()
    }
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputVisible = true
        keyboard?.setTheme(Themes.load(this)) // picked up live from the settings screen
        keyboard?.setShiftState(shift)
        Log.i(TAG, "input view shown inputType=${info?.inputType} imeOptions=${info?.imeOptions}")
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        inputVisible = false; controller?.reset()
        Log.i(TAG, "input view hidden")
        super.onFinishInputView(finishingInput)
    }
    override fun onWindowHidden() {
        inputVisible = false; controller?.reset()
        super.onWindowHidden()
    }
    override fun onFinishInput() {
        inputVisible = false; controller?.reset()
        super.onFinishInput()
    }
    override fun onDestroy() { controller?.reset(); super.onDestroy() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (inputVisible && controller?.onKey(event) == true) true else super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (inputVisible && controller?.onKey(event) == true) true else super.onKeyUp(keyCode, event)
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        Log.d(TAG, "onGenericMotionEvent source=${event.source} visible=$inputVisible")
        return if (inputVisible && controller?.onMotion(event) == true) true else super.onGenericMotionEvent(event)
    }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoCaps()
    }
    private fun handle(action: KeyAction) {
        val connection = currentInputConnection ?: return
        Log.d(TAG, "action ${action.javaClass.simpleName}")
        when (action) {
            is KeyAction.Text -> {
                connection.commitText(if (shift == ShiftState.OFF) action.text else action.shifted, 1)
                if (shift == ShiftState.ON) { shift = ShiftState.OFF; manualShift = false }
                lastShiftPress = 0L
            }
            KeyAction.Space -> { connection.commitText(" ", 1); lastShiftPress = 0L }
            KeyAction.Backspace -> deleteBackward()
            KeyAction.Enter -> enter()
            KeyAction.Shift -> {
                val now = SystemClock.uptimeMillis()
                shift = when {
                    shift == ShiftState.CAPS -> ShiftState.OFF
                    lastShiftPress != 0L && now - lastShiftPress <= 350 -> ShiftState.CAPS
                    shift == ShiftState.OFF -> ShiftState.ON
                    else -> ShiftState.OFF
                }
                lastShiftPress = now; manualShift = true
            }
            is KeyAction.Layer -> switchLayer(action.target)
            KeyAction.CaretLeft -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            KeyAction.CaretRight -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyAction.Hide -> requestHideSelf(0)
        }
        keyboard?.setShiftState(shift)
        if (action is KeyAction.Text || action == KeyAction.Space || action == KeyAction.Enter || action == KeyAction.Backspace) {
            keyboard?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    private fun switchLayer(target: LayerId) { layer = target; keyboard?.setLayout(Layouts.forLayer(layer)) }
    private fun updateAutoCaps() {
        if (shift != ShiftState.CAPS && !manualShift) {
            val type = currentInputEditorInfo?.inputType ?: 0
            val flags = type and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
            shift = if (type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT && flags != 0 &&
                (currentInputConnection?.getCursorCapsMode(type) ?: 0) != 0) ShiftState.ON else ShiftState.OFF
        }
        keyboard?.setShiftState(shift)
    }
    private fun deleteBackward() {
        val connection = currentInputConnection ?: return
        if (!connection.getSelectedText(0).isNullOrEmpty()) {
            connection.commitText("", 1)
        } else if (!connection.deleteSurroundingTextInCodePoints(1, 0)) {
            // Some editors only implement UTF-16 deletion. Never split a surrogate pair.
            val before = connection.getTextBeforeCursor(2, 0)
            val count = if (before != null && before.length >= 2 &&
                Character.isSurrogatePair(before[before.length - 2], before[before.length - 1])) 2 else 1
            connection.deleteSurroundingText(count, 0)
        }
    }
    private fun enter() {
        val info = currentInputEditorInfo
        val connection = currentInputConnection ?: return
        if (info != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0) {
            val action = if (info.actionLabel != null) info.actionId else info.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                if (connection.performEditorAction(action)) return
            }
        }
        if (info != null && info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) {
            connection.commitText("\n", 1)
        } else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }
    companion object { private const val TAG = "PadKeys" }
}
