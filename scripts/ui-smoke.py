"""Exercise the installed release APK through Android's real UI hierarchy (no app mocks)."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', default='emulator-5556')
p.add_argument('--reports', default='.android-test/reports/ui-smoke')
a = p.parse_args()
out = Path(a.reports)
out.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.run([a.adb, '-s', a.serial, *args], check=True, capture_output=True, text=True, encoding='utf-8').stdout

def dump(label):
    adb('shell', 'uiautomator', 'dump', '/sdcard/smoke.xml')
    target = out / f'{label}.xml'
    adb('pull', '/sdcard/smoke.xml', str(target))
    return ET.parse(target).getroot()

def assert_text(root, text):
    assert any(text in n.get('text', '') for n in root.iter('node')), f'Missing rendered text: {text}'

def tap(root, text=None, desc=None):
    matches = [n for n in root.iter('node') if (text is not None and n.get('text') == text) or (desc is not None and n.get('content-desc') == desc)]
    assert matches, f'Missing control: {text or desc}'
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', matches[-1].get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(0.6)

def alive():
    assert adb('shell', 'pidof', 'com.haoqi.training').strip(), 'App process exited'
    assert 'FATAL EXCEPTION' not in adb('logcat', '-b', 'crash', '-d'), 'Fatal exception recorded'

adb('logcat', '-c')
r=dump('training'); assert_text(r, '循环第1天')
for day in (2, 3):
    if not any(n.get('text') == f'第{day}天' for n in r.iter('node')):
        tap(r, text='添加一天')
        r=dump(f'added-day{day}')
    tap(r, text=f'第{day}天')
    r=dump(f'day{day}'); assert_text(r, f'循环第{day}天')
tap(r, text='完成')
r=dump('completed'); assert_text(r, '循环第3天已练完')
tap(r, text='第1天')
r=dump('back-day1'); assert_text(r, '循环第1天')
tap(r, text='添加动作')
r=dump('exercise-editor'); assert_text(r, '动作名称'); assert_text(r, '重量'); assert_text(r, '每组次数')
adb('shell', 'input', 'keyevent', '4')
r=dump('close-editor'); tap(r, text='饮食')
r=dump('diet'); assert_text(r, '今日热量'); assert_text(r, '早餐')
tap(r, text='添加早餐食物')
r=dump('food-choice'); assert_text(r, '手动录入'); tap(r, text='手动录入')
r=dump('food-editor'); assert_text(r, '食物名称')
adb('shell', 'input', 'keyevent', '4')
r=dump('close-food'); tap(r, desc='设置')
r=dump('settings'); assert_text(r, '个人资料'); assert_text(r, '保存资料')
adb('shell', 'input', 'keyevent', '4')
r=dump('settings-back'); assert_text(r, '今日热量')
alive()
print('PASS: release UI smoke: training/day selection/manual completion/exercise dialog/diet/manual food dialog/settings/back; no fatal exceptions')
