#!/usr/bin/env python3
"""Regenerate docs/screenshots on a connected Android handheld. Drives only the PadKeys setup screen and
aborts if anything else comes to the foreground."""
import os, re, subprocess, sys, time

PKG = "com.kamaal.padkeys"
OUT = os.path.join(os.path.dirname(__file__), "..", "docs", "screenshots")

def adb(*args, out=False):
    r = subprocess.run(["adb", *args], capture_output=True, text=not out)
    return r.stdout

def sh(cmd): return adb("shell", cmd)

def foreground_ok():
    top = sh("dumpsys activity activities | grep -m1 topResumedActivity")
    if PKG not in top:
        sys.exit(f"aborting: foreground is not {PKG}: {top.strip()}")

def ime_shown(): return "mInputShown=true" in sh("dumpsys input_method | grep -m1 mInputShown")

def nodes():
    sh("uiautomator dump /sdcard/padkeys-ui.xml >/dev/null")
    return re.findall(r"<node [^>]*>", sh("cat /sdcard/padkeys-ui.xml"))

def center(pred):
    for n in nodes():
        if pred(n):
            x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
            return (x1 + x2) // 2, (y1 + y2) // 2
    sys.exit("element not found")

def find(pred):
    for n in nodes():
        if pred(n):
            return n
    return None

def bounds(n):
    return tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))

def tap(pred):
    foreground_ok()
    x, y = center(pred)
    sh(f"input tap {x} {y}")

def tap_card(name):
    """Theme cards live in a horizontal scroller; swipe it until the card is fully on screen."""
    pred = theme_card(name)
    width = bounds(nodes()[0])[2]  # root node = current (rotated) screen width; `wm size` is the panel's
    foreground_ok()
    sh("input swipe 960 200 960 900 250"); time.sleep(0.6)  # scroll the page back to the top
    for direction in [1, -1]:
        for _ in range(6):
            n = find(pred)
            if n:
                x1, y1, x2, y2 = bounds(n)
                if x1 >= 0 and x2 <= width and y1 >= 0:
                    foreground_ok()
                    sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
                    return
            row = find(lambda n: 'class="android.widget.HorizontalScrollView"' in n)
            if not row:
                sys.exit("theme picker not found")
            x1, y1, x2, y2 = bounds(row)
            y = (y1 + y2) // 2
            a, b = (x2 - 50, x1 + 50) if direction == 1 else (x1 + 50, x2 - 50)
            foreground_ok()
            sh(f"input swipe {a} {y} {b} {y} 300"); time.sleep(0.5)
    sys.exit(f"theme {name} not found")

def keys(*ks):
    foreground_ok()
    sh("input keyevent " + " ".join("KEYCODE_" + k for k in ks))
    time.sleep(0.4)

def hide_ime():
    if ime_shown():
        sh("input keyevent KEYCODE_BACK")  # with the IME up, BACK only closes the keyboard
        time.sleep(0.8)

def shot(name):
    foreground_ok()
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", out=True))

is_field = lambda n: 'class="android.widget.EditText"' in n
theme_card = lambda name: (lambda n: f'content-desc="{name} theme"' in n)

def focus_field():
    for _ in range(3):
        tap(is_field)
        for _ in range(10):
            time.sleep(0.3)
            if ime_shown():
                time.sleep(0.6)  # let the show animation finish
                return
    sys.exit("keyboard did not open")

os.makedirs(OUT, exist_ok=True)
sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1"); time.sleep(2.5)
foreground_ok(); hide_ime()

tap_card("Midnight"); time.sleep(0.5); shot("setup")
focus_field()
keys("MOVE_END"); sh("input keycombination 113 29"); keys("DEL")
sh("input text 'Typing%swith%sthe%ssticks'")
keys("DPAD_RIGHT", "DPAD_UP"); shot("keyboard-midnight")
keys("BUTTON_L1"); shot("keyboard-numbers")
keys("BUTTON_R1"); shot("keyboard-symbols"); keys("BUTTON_R1")

for name in ["Paper", "Terminal", "Sakura", "OLED", "Ember", "Glacier"]:
    hide_ime()
    tap_card(name); time.sleep(0.5)
    focus_field(); keys("DPAD_LEFT")
    shot("keyboard-" + name.lower())

hide_ime(); tap_card("Midnight"); time.sleep(0.3)
print("ok")
