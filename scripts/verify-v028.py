"""Verify 0.2.8: long-press drag reorder (persisted) and swipe-to-dismiss stops the ring."""
import argparse, re, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path

p = argparse.ArgumentParser(); p.add_argument('--adb', required=True); a = p.parse_args()
out = Path('.android-test/reports/v028'); out.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.run([a.adb, '-s', 'emulator-5556', *args], check=True,
                          capture_output=True, text=True, encoding='utf-8').stdout

def dump(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/v028.xml')
    adb('pull', '/sdcard/v028.xml', str(out / f'{name}.xml'))
    return ET.parse(out / f'{name}.xml').getroot()

def texts(r): return [n.get('text', '') for n in r.iter('node')]

def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    return (x1 + x2) // 2, (y1 + y2) // 2

def tapnode(n):
    x, y = center(n)
    adb('shell', 'input', 'tap', str(x), str(y))
    time.sleep(0.5)

def tap(r, text):
    ns = [n for n in r.iter('node') if n.get('text') == text]
    assert ns, f'control not found: {text} / {texts(r)}'
    tapnode(ns[-1])

def replace_field(node, value, digits):
    tapnode(node)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(digits):
        adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    adb('shell', 'input', 'text', value)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(0.4)

def add_exercise(name, rest):
    tap(dump(f'before-add-{name}'), '添加动作')
    r = dump(f'dialog-{name}')
    fields = [n for n in r.iter('node') if n.get('class') == 'android.widget.EditText']
    tapnode(fields[0])
    adb('shell', 'input', 'text', name)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(0.4)
    r = dump(f'dialog-filled-{name}')
    rest_field = next(n for n in r.iter('node') if n.get('text') == '90')
    replace_field(rest_field, str(rest), 2)
    tap(dump(f'dialog-ready-{name}'), '保存')

def order(r):
    return [t for t in texts(r) if t in ('AAA', 'BBB', 'CCC')]

adb('shell', 'pm', 'clear', 'com.haoqi.training')
adb('shell', 'pm', 'grant', 'com.haoqi.training', 'android.permission.POST_NOTIFICATIONS')
adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
time.sleep(1.5)

for name in ('AAA', 'BBB', 'CCC'):
    add_exercise(name, 3)

r = dump('created')
assert order(r) == ['AAA', 'BBB', 'CCC'], order(r)

# Long-press the third card and drag it above the first one.
source = next(n for n in r.iter('node') if n.get('text') == 'CCC')
target = next(n for n in r.iter('node') if n.get('text') == 'AAA')
sx, sy = center(source)
tx, ty = center(target)
adb('shell', 'input', 'motionevent', 'DOWN', str(sx), str(sy))
time.sleep(1.0)
steps = 10
for step in range(1, steps + 1):
    adb('shell', 'input', 'motionevent', 'MOVE', str(sx), str(sy + (ty - 40 - sy) * step // steps))
    time.sleep(0.08)
adb('shell', 'input', 'motionevent', 'UP', str(sx), str(ty - 40))
time.sleep(1.0)

r = dump('reordered')
assert order(r) == ['CCC', 'AAA', 'BBB'], f'drag reorder failed: {order(r)}'

adb('shell', 'am', 'force-stop', 'com.haoqi.training')
adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
time.sleep(1.5)
r = dump('restarted')
assert order(r) == ['CCC', 'AAA', 'BBB'], f'order not persisted: {order(r)}'

# Start a 3s rest on CCC (top card), wait for the ring, then swipe the finished notification away.
first_set = next(n for n in r.iter('node') if n.get('text') == '1'
                 and n.get('class') == 'android.widget.TextView')
tapnode(first_set)
time.sleep(4.5)
adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
time.sleep(1.0)
state = adb('shell', 'dumpsys', 'notification', '--noredact')
assert '休息结束' in state, 'finished notification missing'
assert 'RestTimerService' in adb('shell', 'dumpsys', 'activity', 'services', 'com.haoqi.training')

adb('shell', 'cmd', 'statusbar', 'expand-notifications')
time.sleep(1.5)
shade = dump('shade-ringing')
node = next(n for n in shade.iter('node') if '休息结束' in n.get('text', ''))
x, y = center(node)
x1 = int(re.findall(r'\d+', node.get('bounds'))[2])
adb('shell', 'input', 'swipe', str(x1 - 30), str(y), '40', str(y), '250')
time.sleep(2.0)
adb('shell', 'cmd', 'statusbar', 'collapse')
time.sleep(0.5)

state = adb('shell', 'dumpsys', 'notification', '--noredact')
assert '休息结束' not in state, 'finished notification still present after swipe'
services = adb('shell', 'dumpsys', 'activity', 'services', 'com.haoqi.training')
assert 'RestTimerService' not in services, 'ring service still running after swipe'
print('PASS: long-press drag reorder persists (CCC,AAA,BBB); swiping the finished notification '
      'removed it and destroyed the ringing service')
