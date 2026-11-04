package com.kamaal.padkeys.input

import android.view.KeyEvent
import android.view.MotionEvent

/** All remappable controller buttons, axes, and timing live here. */
object ControllerBindings {
    enum class Command { LEFT_TYPE, RIGHT_TYPE, ACTIVE_TYPE, BACKSPACE, SPACE, SHIFT, SYMBOLS, SYMBOLS_2, ENTER, SELECT, UP, DOWN, LEFT, RIGHT }
    val buttons = mapOf(
        KeyEvent.KEYCODE_BUTTON_L2 to Command.SHIFT,
        KeyEvent.KEYCODE_BUTTON_R2 to Command.ENTER,
        KeyEvent.KEYCODE_BUTTON_THUMBL to Command.LEFT_TYPE,
        KeyEvent.KEYCODE_BUTTON_THUMBR to Command.RIGHT_TYPE,
        KeyEvent.KEYCODE_BUTTON_A to Command.ACTIVE_TYPE,
        KeyEvent.KEYCODE_BUTTON_B to Command.BACKSPACE,
        KeyEvent.KEYCODE_BUTTON_X to Command.SPACE,
        KeyEvent.KEYCODE_BUTTON_Y to Command.SHIFT,
        KeyEvent.KEYCODE_BUTTON_L1 to Command.SYMBOLS,
        KeyEvent.KEYCODE_BUTTON_R1 to Command.SYMBOLS_2,
        KeyEvent.KEYCODE_BUTTON_START to Command.ENTER,
        KeyEvent.KEYCODE_BUTTON_SELECT to Command.SELECT,
        KeyEvent.KEYCODE_DPAD_UP to Command.UP,
        KeyEvent.KEYCODE_DPAD_DOWN to Command.DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to Command.LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to Command.RIGHT,
    )
    val leftStick = intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
    val rightStick = intArrayOf(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)
    val leftTriggers = intArrayOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE)
    val rightTriggers = intArrayOf(MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS)
    const val STICK_ENGAGE = .5f
    const val STICK_RELEASE = .3f
    const val STICK_DELAY = 300L
    const val STICK_SLOW_REPEAT = 140L
    const val STICK_FAST_REPEAT = 60L
    const val TRIGGER_PRESS = .55f
    const val TRIGGER_RELEASE = .35f
    const val DPAD_DELAY = 350L
    const val DPAD_REPEAT = 70L
    const val DELETE_DELAY = 400L
    const val DELETE_REPEAT = 50L
    const val DELETE_FAST_REPEAT = 25L
    const val SELECT_LONG_PRESS = 500L
}
