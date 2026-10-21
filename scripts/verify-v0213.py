"""Verify 0.2.13: the macro estimate runs once on save, only when name/portion changed and numbers were not edited."""
import argparse, re, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path

p = argparse.ArgumentParser(); p.add_argument('--adb', required=True); a = p.parse_args()
out = Path('.android-test/reports/v0213'); out.mkdir(parents=True, exist_ok=True)
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
    assert hits, f'field {value!r} missing: {[n.get("text") for n in edit_texts(r)]}'
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

# point the app at the local stub
adb('shell', 'pm', 'clear', 'com.haoqi.training')
adb('shell', 'pm', 'grant', 'com.haoqi.training', 'android.permission.POST_NOTIFICATIONS')
adb('root'); time.sleep(2.0)
launch()
prefs = '/data/data/com.haoqi.training/shared_prefs/ai_config.xml'
adb('shell', 'am', 'force-stop', 'com.haoqi.training'); time.sleep(0.5)
xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
       "    <string name=\"api_key\">test-key</string>\n"
       "    <string name=\"base_url\">http://10.0.2.2:8765</string>\n"
       "    <string name=\"model\">deepseek-flash</string>\n</map>\n")
tmp = out / 'ai_config.xml'; tmp.write_text(xml, encoding='utf-8')
adb('push', str(tmp), '/data/local/tmp/ai_config.xml')
adb('shell', 'cp', '/data/local/tmp/ai_config.xml', prefs)
adb('shell', 'chmod', '660', prefs)
adb('shell', 'restorecon', prefs)
adb('shell', 'chown', '$(stat -c %u:%g /data/data/com.haoqi.training)', prefs)
launch()

# 1) typing alone must not call the AI
open_manual_entry()
r = dump('manual')
tapnode(edit_texts(r)[0]); adb('shell', 'input', 'text', 'Rice'); hide_keyboard()
time.sleep(2.5)
assert calls() == 0, f'AI was called while typing ({calls()})'
print('typing did not call the AI')

# 2) pressing save once must trigger exactly one estimate and then store the entry
tap(dump('before-save'), text='保存')
deadline = time.time() + 15
while time.time() < deadline and calls() < 1:
    time.sleep(0.4)
assert calls() == 1, f'expected exactly one AI call on save, got {calls()}'
time.sleep(1.5)
r = dump('after-save')
assert '手动录入 · 早餐' not in texts(r), 'dialog did not close after a successful estimate'
assert any('Rice' in t for t in texts(r)), f'entry not added: {texts(r)}'
print('save triggered exactly one estimate and stored the entry')

# 说明：这里只覆盖「保存时调用一次」这一新行为。
# 「用户手改过数字就不再估算」的守卫在 0.2.12 已端到端验证过（scripts/verify-v0212.py），
# 本次没有改动那段逻辑；kcal 输入框不会被 uiautomator 导出，单独复测脚本易受点击坐标影响。
print('PASS: typing never calls the AI, one save triggers exactly one estimate and stores the entry')
