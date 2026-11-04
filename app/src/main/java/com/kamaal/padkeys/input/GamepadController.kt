package com.kamaal.padkeys.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.kamaal.padkeys.input.ControllerBindings.Command
import com.kamaal.padkeys.keyboard.*
import kotlin.math.*

/** Main-thread controller state. Multiple representations of one control share a logical latch. */
class GamepadController(
    private val view: KeyboardView,
    private val action: (KeyAction) -> Unit,
    /** Switch to [LayerId], or back to letters if it's already showing. */
    private val toggleLayer: (LayerId) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var activeSide = CursorSide.LEFT
    private val held = mutableSetOf<Int>()
    private val directions = mutableMapOf<Command, MutableSet<String>>()
    private val repeats = mutableMapOf<Command, Runnable>()
    private val triggerSources = arrayOf(mutableSetOf<String>(), mutableSetOf())
    private val triggerDown = booleanArrayOf(false, false)
    private val triggerRelease = arrayOfNulls<Runnable>(2)
    private var selectLong = false
    private var selectTask: Runnable? = null
    private class StickState(val side: CursorSide) {
        var x = 0f
        var y = 0f
        var magnitude = 0f
        var col = 0
        var row = 0
        var repeat: Runnable? = null
        val engaged get() = col != 0 || row != 0
    }
    private val sticks = arrayOf(StickState(CursorSide.LEFT), StickState(CursorSide.RIGHT))
    private var lastMotionLog = 0L

    fun onKey(event: KeyEvent): Boolean {
        val command = ControllerBindings.buttons[event.keyCode] ?: return false
        // DPAD from a physical keyboard should keep editing text; synthetic adb events have id -1.
        if (event.deviceId != -1 && !isController(event.device, event.source)) return false
        view.setCursorsVisible(true)
        val down = event.action == KeyEvent.ACTION_DOWN
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        Log.d("PadKeys", "key ${KeyEvent.keyCodeToString(event.keyCode)} ${if (down) "down" else "up"} source=${event.source}")
        if (down && !held.add(event.keyCode)) return true
        if (!down) held.remove(event.keyCode)
        when {
            event.keyCode == KeyEvent.KEYCODE_BUTTON_L2 -> trigger(0, "key", down)
            event.keyCode == KeyEvent.KEYCODE_BUTTON_R2 -> trigger(1, "key", down)
            command in directionCommands -> direction(command, "key", down)
            command == Command.SELECT -> select(down, event.isCanceled)
            down -> {
                perform(command)
                if (command in repeatCommands) startRepeat(command)
            }
            else -> stopRepeat(command)
        }
        return true
    }

    fun onMotion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        view.setCursorsVisible(true)
        val now = SystemClock.uptimeMillis()
        if (now - lastMotionLog > 500) {
            Log.d("PadKeys", "joystick delivered axes X=${event.getAxisValue(MotionEvent.AXIS_X)} Y=${event.getAxisValue(MotionEvent.AXIS_Y)} Z=${event.getAxisValue(MotionEvent.AXIS_Z)} RZ=${event.getAxisValue(MotionEvent.AXIS_RZ)}")
            lastMotionLog = now
        }
        updateStick(sticks[0], event.getAxisValue(ControllerBindings.leftStick[0]), event.getAxisValue(ControllerBindings.leftStick[1]))
        updateStick(sticks[1], event.getAxisValue(ControllerBindings.rightStick[0]), event.getAxisValue(ControllerBindings.rightStick[1]))
        analogTrigger(0, ControllerBindings.leftTriggers.maxOf { event.getAxisValue(it) })
        analogTrigger(1, ControllerBindings.rightTriggers.maxOf { event.getAxisValue(it) })
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        direction(Command.LEFT, "hat", hx < -.5f); direction(Command.RIGHT, "hat", hx > .5f)
        direction(Command.UP, "hat", hy < -.5f); direction(Command.DOWN, "hat", hy > .5f)
        return true
    }

    private fun analogTrigger(index: Int, value: Float) {
        val was = "axis" in triggerSources[index]
        val down = if (was) value >= ControllerBindings.TRIGGER_RELEASE else value > ControllerBindings.TRIGGER_PRESS
        trigger(index, "axis", down)
    }

    private fun trigger(index: Int, source: String, down: Boolean) {
        val sources = triggerSources[index]
        if (down) sources.add(source) else sources.remove(source)
        if (sources.isNotEmpty()) {
            triggerRelease[index]?.let(handler::removeCallbacks)
            triggerRelease[index] = null
            if (!triggerDown[index]) {
                triggerDown[index] = true
                // Triggers are modifiers, not typing: LT = shift, RT = enter.
                perform(if (index == 0) Command.SHIFT else Command.ENTER)
            }
        } else if (triggerDown[index] && triggerRelease[index] == null) {
            // One frame of release grace covers Android key/axis ordering for the same pull.
            val task = Runnable { triggerDown[index] = false; triggerRelease[index] = null }
            triggerRelease[index] = task
            handler.postDelayed(task, 32)
        }
    }

    private fun direction(command: Command, source: String, down: Boolean) {
        val sources = directions.getOrPut(command) { mutableSetOf() }
        val was = sources.isNotEmpty()
        if (down) sources.add(source) else sources.remove(source)
        if (!was && sources.isNotEmpty()) { perform(command); startRepeat(command) }
        if (was && sources.isEmpty()) stopRepeat(command)
    }
    private fun select(down: Boolean, canceled: Boolean) {
        if (down) {
            selectLong = false
            val task = Runnable { selectLong = true; action(KeyAction.Hide) }
            selectTask = task; handler.postDelayed(task, ControllerBindings.SELECT_LONG_PRESS)
        } else {
            selectTask?.let(handler::removeCallbacks); selectTask = null
            if (!selectLong && !canceled) toggleLayer(LayerId.SYMBOLS)
        }
    }
    private fun startRepeat(command: Command) {
        stopRepeat(command)
        val start = SystemClock.uptimeMillis()
        val task = object : Runnable {
            override fun run() {
                perform(command)
                if (repeats[command] !== this) return
                val delay = when {
                    command == Command.BACKSPACE && SystemClock.uptimeMillis() - start >= 1000 -> ControllerBindings.DELETE_FAST_REPEAT
                    command == Command.BACKSPACE -> ControllerBindings.DELETE_REPEAT
                    else -> ControllerBindings.DPAD_REPEAT
                }
                handler.postDelayed(this, delay)
            }
        }
        repeats[command] = task
        handler.postDelayed(task, if (command == Command.BACKSPACE) ControllerBindings.DELETE_DELAY else ControllerBindings.DPAD_DELAY)
    }
    private fun stopRepeat(command: Command) { repeats.remove(command)?.let(handler::removeCallbacks) }
    private fun perform(command: Command) {
        when (command) {
            Command.LEFT_TYPE -> type(CursorSide.LEFT)
            Command.RIGHT_TYPE -> type(CursorSide.RIGHT)
            Command.ACTIVE_TYPE -> type(activeSide)
            Command.BACKSPACE -> action(KeyAction.Backspace)
            Command.SPACE -> action(KeyAction.Space)
            Command.SHIFT -> action(KeyAction.Shift)
            Command.SYMBOLS -> toggleLayer(LayerId.SYMBOLS)
            Command.SYMBOLS_2 -> toggleLayer(LayerId.SYMBOLS_2)
            Command.ENTER -> action(KeyAction.Enter)
            Command.UP -> step(0, -1)
            Command.DOWN -> step(0, 1)
            Command.LEFT -> step(-1, 0)
            Command.RIGHT -> step(1, 0)
            Command.SELECT -> Unit
        }
    }
    private fun type(side: CursorSide) {
        setActive(side)
        view.keyUnder(side)?.let { view.showPress(side); action(it.action) }
    }
    private fun step(col: Int, row: Int) { setActive(view.stepCursor(activeSide, col, row)) }
    private fun setActive(side: CursorSide) { activeSide = side; view.setActiveSide(side) }
    private fun updateStick(stick: StickState, x: Float, y: Float) {
        val changed = stick.x != x || stick.y != y
        stick.x = x; stick.y = y; stick.magnitude = hypot(x, y)
        if (stick.engaged && stick.magnitude < ControllerBindings.STICK_RELEASE) {
            stopStick(stick)
            return
        }
        if (!stick.engaged && stick.magnitude <= ControllerBindings.STICK_ENGAGE) return
        val col = if (abs(x) >= abs(y)) if (x > 0) 1 else -1 else 0
        val row = if (col == 0) if (y > 0) 1 else -1 else 0
        if (changed) setActive(stick.side)
        if (stick.col == col && stick.row == row) return
        stopStick(stick)
        stick.col = col; stick.row = row
        stepStick(stick)
        val task = object : Runnable {
            override fun run() {
                if (stick.repeat !== this || !stick.engaged) return
                stepStick(stick)
                val deflection = ((stick.magnitude - ControllerBindings.STICK_ENGAGE) /
                    (1 - ControllerBindings.STICK_ENGAGE)).coerceIn(0f, 1f)
                val interval = (ControllerBindings.STICK_SLOW_REPEAT - deflection *
                    (ControllerBindings.STICK_SLOW_REPEAT - ControllerBindings.STICK_FAST_REPEAT)).toLong()
                handler.postDelayed(this, interval)
            }
        }
        stick.repeat = task
        handler.postDelayed(task, ControllerBindings.STICK_DELAY)
    }
    private fun stepStick(stick: StickState) {
        setActive(stick.side)
        view.stepCursor(stick.side, stick.col, stick.row)
        Log.d("PadKeys", "stick ${stick.side} step=${stick.col},${stick.row} magnitude=${stick.magnitude}")
    }
    private fun stopStick(stick: StickState) {
        stick.repeat?.let(handler::removeCallbacks)
        stick.repeat = null; stick.col = 0; stick.row = 0
    }
    /** Called when the view's cursors go back to the home keys: A targets the left cursor again. */
    fun onCursorsReset() = setActive(CursorSide.LEFT)

    fun reset() {
        handler.removeCallbacksAndMessages(null); held.clear(); directions.clear(); repeats.clear()
        triggerSources.forEach { it.clear() }; triggerDown.fill(false); triggerRelease.fill(null)
        selectTask = null
        sticks.forEach { stopStick(it); it.x = 0f; it.y = 0f; it.magnitude = 0f }
    }
    private fun isController(device: InputDevice?, source: Int): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            (device?.vendorId == 0x2020 && device.productId == 0x0111)
    companion object {
        private val directionCommands = setOf(Command.UP, Command.DOWN, Command.LEFT, Command.RIGHT)
        private val repeatCommands = setOf(Command.BACKSPACE)
    }
}
