"""Verify 0.2.12: editing name/portion auto-fills macros via AI, but hand-edited numbers are kept.

uiautomator does not export the kcal field on this dialog, so the checks use the
protein/carbs/fat fields and the request log instead of the kcal box position.
"""
import argparse, re, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path

p = argparse.ArgumentParser(); p.add_argument('--adb', required=True); a = p.parse_args()
out = Path('.android-test/reports/v0212'); out.mkdir(parents=True, exist_ok=True)
LOG = Path('.android-test/reports/fake-llm-calls.log')

def adb(*args):
    return subprocess.run([a.adb, '-s', 'emulator-5556', *args], check=True,
                          capture_output=True, text=True, encoding='utf-8').stdout

def dump(name):
    for _ in range(4):
        adb('shell', 'rm', '-f', '/sdcard/v.xml')
        adb('shell', 'uiautomator', 'dump', '/sdcard/v.xml')
        probe = subprocess.run([a.adb, '-s', 'emulator-5556', 'shell', 'ls', '/sdcard/v.xml'],
                               capture_output=True, text=True)
        if '/sdcard/v.xml' not in probe.stdout:
            adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'); time.sleep(0.6)
            adb('shell', 'wm', 'dismiss-keyguard'); time.sleep(0.6)
            continue
        adb('pull', '/sdcard/v.xml', str(out / f'{name}.xml'))
        return ET.parse(out / f'{name}.xml').getroot()
    raise AssertionError(f'cannot dump: {name}')

def texts(r): return [n.get('text', '') for n in r.iter('node')]

def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    return (x1 + x2) // 2, (y1 + y2) // 2

def tapnode(n):
    x, y = center(n); adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(0.4)

def tap(r, text=None, desc=None):
    ns = [n for n in r.iter('node') if (text and n.get('text') == text) or (desc and n.get('content-desc') == desc)]
    assert ns, f'not found: {text or desc} / {texts(r)}'
    tapnode(ns[0])

def hide_keyboard():
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK'); time.sleep(0.4)

def edit_texts(r): return [n for n in r.iter('node') if n.get('class') == 'android.widget.EditText']

def field_by_text(r, value):
    hits = [n for n in edit_texts(r) if (n.get('text') or '') == value]
    assert hits, f'field with text {value!r} not found: {[n.get("text") for n in edit_texts(r)]}'
    return hits[0]

def rewrite(node, value, clears):
    tapnode(node)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(clears):
        adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    adb('shell', 'input', 'text', value)
    hide_keyboard()

def calls():
    return len(LOG.read_text(encoding='utf-8').splitlines()) if LOG.exists() else 0

def wait_calls(target, seconds=15):
    deadline = time.time() + seconds
    while time.time() < deadline and calls() < target:
        time.sleep(0.4)
    return calls()

def launch():
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard'); time.sleep(0.5)
    adb('shell', 'am', 'force-stop', 'com.haoqi.training')
    adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
    time.sleep(1.5)

def open_manual_entry():
    r = dump('diet-open'); tap(r, text='饮食'); time.sleep(1.0)
    for _ in range(3):
        r = dump('diet-scroll')
        if '添加早餐食物' in texts(r):
            break
        adb('shell', 'input', 'swipe', '540', '1700', '540', '900', '250'); time.sleep(0.6)
    tap(r, text='添加早餐食物'); time.sleep(0.7)
    tap(dump('choice'), text='手动录入'); time.sleep(0.9)

# 0) point the app at the local stub so no real API is called
adb('shell', 'pm', 'clear', 'com.haoqi.training')
adb('shell', 'pm', 'grant', 'com.haoqi.training', 'android.permission.POST_NOTIFICATIONS')
adb('root'); time.sleep(2.0)
launch()
prefs = '/data/data/com.haoqi.training/shared_prefs/ai_config.xml'
adb('shell', 'am', 'force-stop', 'com.haoqi.training'); time.sleep(0.5)
xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
       "<map>\n"
       "    <string name=\"api_key\">test-key</string>\n"
       "    <string name=\"base_url\">http://10.0.2.2:8765</string>\n"
       "    <string name=\"model\">deepseek-flash</string>\n"
       "</map>\n")
tmp = out / 'ai_config.xml'
tmp.write_text(xml, encoding='utf-8')
adb('push', str(tmp), '/data/local/tmp/ai_config.xml')
adb('shell', 'cp', '/data/local/tmp/ai_config.xml', prefs)
adb('shell', 'chmod', '660', prefs)
adb('shell', 'restorecon', prefs)
adb('shell', 'chown', '$(stat -c %u:%g /data/data/com.haoqi.training)', prefs)
launch()

# 1) typing a name should auto-fill the macros
open_manual_entry()
r = dump('manual')
name_field = edit_texts(r)[0]
tapnode(name_field); adb('shell', 'input', 'text', 'Rice'); hide_keyboard()
assert wait_calls(1) >= 1, 'no AI request after typing the name'
time.sleep(1.5)
r = dump('after-name')
filled = [n.get('text') for n in edit_texts(r)]
print('auto-filled after name:', filled)

# 2) changing the portion must trigger another estimate and change the numbers
before = calls()
portion = field_by_text(dump('portion-edit'), '1 份')
rewrite(portion, '200g', 6)
assert wait_calls(before + 1) > before, 'portion change did not trigger a new estimate'
time.sleep(1.5)
after_portion = [n.get('text') for n in edit_texts(dump('after-portion'))]
print('auto-filled after portion:', after_portion)
assert after_portion != filled, 'numbers did not change after the portion changed'

# 3) once the user edits a number, AI must not be called again nor overwrite it
before = calls()
r = dump('edit-protein')
protein_value = edit_texts(r)[3].get('text')
rewrite(edit_texts(r)[3], '77', len(protein_value or '') + 2)
r = dump('edit-name')
name_node = edit_texts(r)[0]
tapnode(name_node); adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
adb('shell', 'input', 'text', 'X'); hide_keyboard()
time.sleep(3.5)
assert calls() == before, f'AI called although the user edited a number ({before} -> {calls()})'
final = [n.get('text') for n in edit_texts(dump('final'))]
assert '77' in final, f'hand-edited value was overwritten: {final}'
print('hand-edited numbers kept:', final)
print('PASS: auto-estimate on name/portion change, skipped after manual edits')
