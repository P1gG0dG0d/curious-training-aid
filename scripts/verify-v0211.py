"""Verify 0.2.11: pressing power while the rest alert rings silences it and drops the notification."""
import argparse, re, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path

p = argparse.ArgumentParser(); p.add_argument('--adb', required=True); a = p.parse_args()
out = Path('.android-test/reports/v0211'); out.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.run([a.adb, '-s', 'emulator-5556', *args], check=True,
                          capture_output=True, text=True, encoding='utf-8').stdout

def dump(name):
    for attempt in range(4):
        adb('shell', 'rm', '-f', '/sdcard/v.xml')
        adb('shell', 'uiautomator', 'dump', '/sdcard/v.xml')
        probe = subprocess.run([a.adb, '-s', 'emulator-5556', 'shell', 'ls', '/sdcard/v.xml'],
                               capture_output=True, text=True)
        if '/sdcard/v.xml' not in probe.stdout:
            # screen off or keyguard: wake and retry instead of reading a stale file
            adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
            time.sleep(0.6)
            adb('shell', 'wm', 'dismiss-keyguard')
            time.sleep(0.6)
            continue
        adb('pull', '/sdcard/v.xml', str(out / f'{name}.xml'))
        return ET.parse(out / f'{name}.xml').getroot()
    raise AssertionError(f'could not capture UI state: {name}')

def texts(r): return [n.get('text', '') for n in r.iter('node')]

def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    return (x1 + x2) // 2, (y1 + y2) // 2

def tapnode(n):
    x, y = center(n); adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(0.5)

def tap(r, text=None, desc=None):
    ns = [n for n in r.iter('node') if (text and n.get('text') == text) or (desc and n.get('content-desc') == desc)]
    assert ns, f'not found: {text or desc} / {texts(r)}'
    tapnode(ns[0])

def hide_keyboard():
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK'); time.sleep(0.4)

def display_state():
    m = re.search(r'mWakefulness=(\w+)', adb('shell', 'dumpsys', 'power'))
    return m.group(1) if m else 'unknown'

def ringing():
    return '休息结束' in adb('shell', 'dumpsys', 'notification', '--noredact')

def service_running():
    return 'RestTimerService' in adb('shell', 'dumpsys', 'activity', 'services', 'com.haoqi.training')

def launch():
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    time.sleep(0.6)
    adb('shell', 'am', 'force-stop', 'com.haoqi.training')
    adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
    time.sleep(1.5)

def scroll_until(label, attempts=8):
    for i in range(attempts):
        r = dump(f'scroll-{i}')
        if label in texts(r):
            return r
        adb('shell', 'input', 'swipe', '540', '1800', '540', '900', '250')
        time.sleep(0.6)
    raise AssertionError(f'{label} not reachable: {texts(r)}')

def create_exercise():
    r = scroll_until('添加动作')
    tap(r, text='添加动作')
    r = dump('dialog')
    fields = [n for n in r.iter('node') if n.get('class') == 'android.widget.EditText']
    tapnode(fields[0]); adb('shell', 'input', 'text', 'POWER'); hide_keyboard()
    r = dump('dialog2')
    rest = next(n for n in r.iter('node') if n.get('text') == '90')
    tapnode(rest)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    adb('shell', 'input', 'text', '2')
    hide_keyboard()
    tap(dump('dialog3'), text='保存')
    time.sleep(0.6)

def arm_alert(set_index, sleep_immediately=False):
    r = scroll_until('POWER')
    nodes = [n for n in r.iter('node') if n.get('text') == set_index and n.get('class') == 'android.widget.TextView']
    assert nodes, f'set {set_index} not visible'
    tapnode(nodes[-1])
    if sleep_immediately:
        adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    deadline = time.time() + 20
    while time.time() < deadline and not ringing():
        time.sleep(0.3)
    assert ringing(), f'alert never rang for set {set_index}'

adb('shell', 'pm', 'clear', 'com.haoqi.training')
adb('shell', 'pm', 'grant', 'com.haoqi.training', 'android.permission.POST_NOTIFICATIONS')
launch()
if 'POWER' not in texts(dump('start')):
    create_exercise()

# --- case 1: screen on while ringing, press power ---
arm_alert('1')
print('case 1: ringing with wakefulness =', display_state())
adb('shell', 'input', 'keyevent', 'KEYCODE_POWER')
time.sleep(2.0)
assert not ringing(), 'notification still present after power press'
assert not service_running(), 'ring service still running after power press'
print('case 1 ok: silenced; wakefulness after press =', display_state())

if 'Asleep' in display_state() or 'Dozing' in display_state():
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'); time.sleep(1.5)

# --- case 2: screen off before the alert rings, then press power ---
arm_alert('2', sleep_immediately=True)
print('case 2: alert rang with the screen off, wakefulness =', display_state())
adb('shell', 'input', 'keyevent', 'KEYCODE_POWER')
time.sleep(2.5)
assert not ringing(), 'notification still present after power press with screen off'
assert not service_running(), 'service still running after power press with screen off'
print('case 2 ok: silenced; wakefulness after press =', display_state())
print('PASS: power button silences the rest alert in both screen states')
