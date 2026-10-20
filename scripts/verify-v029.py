"""Verify 0.2.9: editing an existing exercise updates the card, notification text ticks,
and the lifted drag card keeps rounded corners."""
import argparse, re, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser(); p.add_argument('--adb', required=True); a = p.parse_args()
out = Path('.android-test/reports/v029'); out.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.run([a.adb, '-s', 'emulator-5556', *args], check=True,
                          capture_output=True, text=True, encoding='utf-8').stdout

def dump(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/v029.xml')
    adb('pull', '/sdcard/v029.xml', str(out / f'{name}.xml'))
    return ET.parse(out / f'{name}.xml').getroot()

def texts(r): return [n.get('text', '') for n in r.iter('node')]

def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    return (x1 + x2) // 2, (y1 + y2) // 2

def tapnode(n):
    x, y = center(n)
    adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(0.5)

def tap(r, text=None, desc=None):
    ns = [n for n in r.iter('node')
          if (text is not None and n.get('text') == text) or (desc is not None and n.get('content-desc') == desc)]
    assert ns, f'not found: {text or desc} / {texts(r)}'
    tapnode(ns[0])

def hide_keyboard():
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK'); time.sleep(0.5)

def launch():
    adb('shell', 'am', 'force-stop', 'com.haoqi.training')
    adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
    time.sleep(1.5)

launch()
r = dump('start')
assert any(t in ('CCC', 'AAA', 'BBB') for t in texts(r)), texts(r)

# --- 1. edit an existing exercise: weight 20 -> 42 ---
tap(r, desc='编辑动作')
r = dump('dialog')
fields = [n for n in r.iter('node') if n.get('class') == 'android.widget.EditText']
assert len(fields) == 5, [n.get('text') for n in fields]
weight = fields[3]
before = weight.get('text')
tapnode(weight)
adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
for _ in range(len(before) + 1):
    adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
adb('shell', 'input', 'text', '42')
hide_keyboard()
r = dump('typed')
# also give this exercise a 60s rest so the notification text can be sampled twice
rest_field = [n for n in r.iter('node') if n.get('class') == 'android.widget.EditText'][2]
tapnode(rest_field)
adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
for _ in range(len(rest_field.get('text') or '') + 1):
    adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
adb('shell', 'input', 'text', '60')
hide_keyboard()
tap(dump('typed-rest'), text='保存')
time.sleep(1.0)
r = dump('saved')
assert '42 kg' in texts(r), f'card did not refresh after edit: {texts(r)}'
launch()
r = dump('restart')
assert '42 kg' in texts(r), f'edit not persisted: {texts(r)}'
print(f'edit ok: {before} kg -> 42 kg, persisted across restart')

# --- 2. notification text must tick ---
first_set = next(n for n in r.iter('node') if n.get('text') == '1'
                 and n.get('class') == 'android.widget.TextView')
tapnode(first_set)
time.sleep(1.2)
def notif_text():
    state = adb('shell', 'dumpsys', 'notification', '--noredact')
    lines = [l.strip() for l in state.splitlines() if '剩余' in l]
    return lines[-1] if lines else ''
t1 = notif_text(); time.sleep(2.0); t2 = notif_text()
if not (t1 and t2 and t1 != t2):
    state = adb('shell', 'dumpsys', 'notification', '--noredact')
    print('DEBUG service:', 'RestTimerService' in adb('shell', 'dumpsys', 'activity', 'services', 'com.haoqi.training'))
    print('DEBUG rest lines:', [l.strip() for l in state.splitlines() if '休息' in l][:5])
    print('DEBUG sample:', [l.strip() for l in state.splitlines() if 'android.text=' in l][:5])
assert t1 and t2 and t1 != t2, f'notification text not counting down: {t1!r} {t2!r}'
print(f'notification text ticks: {t1.strip()} -> {t2.strip()}')

# --- 3. dragged card keeps rounded corners (screenshot for visual check) ---
adb('shell', 'cmd', 'statusbar', 'expand-notifications'); time.sleep(0.8)
adb('shell', 'cmd', 'statusbar', 'collapse'); time.sleep(0.5)
launch()
r = dump('drag-start')
source = next(n for n in r.iter('node') if n.get('text') == 'AAA')
sx, sy = center(source)
adb('shell', 'input', 'motionevent', 'DOWN', str(sx), str(sy))
time.sleep(0.9)
for step in range(1, 7):
    adb('shell', 'input', 'motionevent', 'MOVE', str(sx), str(sy - step * 25)); time.sleep(0.08)
adb('shell', 'screencap', '-p', '/sdcard/drag.png')
adb('pull', '/sdcard/drag.png', str(out / 'dragging.png'))
adb('shell', 'input', 'motionevent', 'UP', str(sx), str(sy - 150))
time.sleep(0.6)
print('drag screenshot saved; verification complete')
