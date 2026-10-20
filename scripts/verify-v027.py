"""Verify 0.2.7: notification countdown keeps ticking while backgrounded, and the
'all sets done' inline hint is gone. Emulator only."""
import argparse, re, subprocess, time, hashlib
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser(); p.add_argument('--adb', required=True); a = p.parse_args()
out = Path('.android-test/reports/v027'); out.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.run([a.adb, '-s', 'emulator-5556', *args], check=True,
                          capture_output=True, text=True, encoding='utf-8').stdout

def dump(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/v027.xml')
    adb('pull', '/sdcard/v027.xml', str(out / f'{name}.xml'))
    return ET.parse(out / f'{name}.xml').getroot()

def texts(r): return [n.get('text', '') for n in r.iter('node')]

def tapnode(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.6)

def setblock(r, index):
    """The set square shows only its number; pick the smallest node with that text."""
    ns = [n for n in r.iter('node') if n.get('text') == str(index)
          and n.get('class') == 'android.widget.TextView']
    assert ns, f'set {index} not found: {texts(r)}'
    return min(ns, key=lambda n: len(n.get('bounds')))

def shot(name):
    adb('shell', 'screencap', '-p', '/sdcard/v027.png')
    adb('pull', '/sdcard/v027.png', str(out / f'{name}.png'))

def tap(r, text):
    ns = [n for n in r.iter('node') if n.get('text') == text]
    assert ns, f'control not found: {text} / {texts(r)}'
    tapnode(ns[-1])

# Fresh data keeps the set toggles deterministic (all four start unchecked).
adb('shell', 'pm', 'clear', 'com.haoqi.training')
adb('shell', 'pm', 'grant', 'com.haoqi.training', 'android.permission.POST_NOTIFICATIONS')
adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
time.sleep(1.5)

r = dump('empty')
assert any('这一天还没有动作' in t for t in texts(r)), texts(r)
tap(r, '添加动作')
r = dump('dialog')
tapnode(next(n for n in r.iter('node') if n.get('class') == 'android.widget.EditText'))
adb('shell', 'input', 'text', 'TimerTest')
adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
time.sleep(0.4)
tap(dump('typed'), '保存')
r = dump('saved')
assert any(t == 'TimerTest' for t in texts(r)), texts(r)

# Tap all four sets; the layout shifts once the rest banner appears, so re-dump each time.
for index in (1, 2, 3, 4):
    tapnode(setblock(dump(f'set{index}'), index))

r = dump('all-done')
assert not any('全部组已完成' in t or '记得点上方' in t for t in texts(r)), \
    f'hint still shown: {texts(r)}'
assert any('休息中' in t for t in texts(r)), 'rest timer did not start'
shot('all-sets-done')

# Background the app, then check the notification itself renders a chronometer.
adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
time.sleep(2.0)
state = adb('shell', 'dumpsys', 'notification', '--noredact')
record = [l.strip() for l in state.splitlines() if 'chronometer' in l.lower()]
assert record, 'no chronometer flag on the rest notification'
assert any('android.showChronometer=Boolean (true)' in l for l in record), record
assert any('android.chronometerCountDown=Boolean (true)' in l for l in record), record
when = [int(m) for l in state.splitlines() if 'mWhen=' in l or ' when=' in l for m in re.findall(r'\d{13}', l)]
assert when and max(when) > int(time.time() * 1000), f'chronometer base not in the future: {when}'

# Two shade screenshots six seconds apart: only SystemUI can change them now.
adb('shell', 'cmd', 'statusbar', 'expand-notifications')
time.sleep(1.5)
shot('shade-t0')
time.sleep(6)
shot('shade-t6')
def top_hash(name):
    im = Image.open(out / f'{name}.png').convert('L').crop((0, 0, 1080, 700))
    return hashlib.sha256(im.tobytes()).hexdigest()
h0, h6 = top_hash('shade-t0'), top_hash('shade-t6')
assert h0 != h6, 'notification area did not change while app was backgrounded'
adb('shell', 'cmd', 'statusbar', 'collapse')
print(f'PASS: hint removed; notification chronometer present and the shade kept ticking '
      f'({h0[:12]} -> {h6[:12]}); rest timer state = {[t for t in texts(r) if "休息中" in t]}')
