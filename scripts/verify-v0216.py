"""Verify 0.2.16 changes: undo clears sets, alarm fires after process death, resume stress."""
import re, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"D:\Projects\APPdevelopment\curious-training-aid\.android-test\sdk\platform-tools\adb.exe"
S = 'emulator-5556'
OUT = Path(r"D:\Projects\APPdevelopment\curious-training-aid\.android-test\reports\v0216")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*a, check=True):
    return subprocess.run([ADB, '-s', S, *a], check=check, capture_output=True,
                          text=True, encoding='utf-8', errors='replace').stdout


def dump(name):
    time.sleep(0.4)
    adb('shell', 'uiautomator', 'dump', '/sdcard/t.xml')
    adb('pull', '/sdcard/t.xml', str(OUT / f'{name}.xml'))
    return ET.parse(OUT / f'{name}.xml').getroot()


def texts(r): return [n.get('text', '') for n in r.iter('node')]
def bounds(n): return tuple(map(int, re.findall(r'\d+', n.get('bounds'))))
def center(n):
    b = bounds(n); return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def tapnode(n):
    x, y = center(n); adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(0.7)


def tap(r, text):
    ns = [n for n in r.iter('node') if n.get('text') == text]
    assert ns, f'not found: {text} / {[t for t in texts(r) if t]}'
    tapnode(ns[-1])


def set_blocks(r, label='1'):
    return [n for n in r.iter('node') if n.get('text') == label
            and n.get('class') == 'android.widget.TextView']


def notifications():
    return adb('shell', 'dumpsys', 'notification', '--noredact', 'com.haoqi.training')


def titles():
    return re.findall(r'android\.title=String \((.*?)\)', notifications())


def crash():
    return adb('shell', 'logcat', '-d', '-b', 'crash')


def app_pid():
    return adb('shell', 'pidof', 'com.haoqi.training', check=False).strip()


def add_exercise(name, rest):
    tap(dump('pre-add'), '添加动作')
    r = dump('dialog')
    fields = [n for n in r.iter('node') if n.get('class') == 'android.widget.EditText']
    tapnode(fields[0])
    adb('shell', 'input', 'text', name)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(0.4)
    r = dump('dialog-filled')
    rest_field = next(n for n in r.iter('node') if n.get('text') == '90')
    tapnode(rest_field)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(2):
        adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    adb('shell', 'input', 'text', str(rest))
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(0.4)
    tap(dump('dialog-ready'), '保存')
    time.sleep(0.8)


def skip_rest():
    adb('shell', 'am', 'startservice', '-n', 'com.haoqi.training/.timer.RestTimerService',
        '-a', 'com.haoqi.training.action.REST_SKIP', check=False)
    time.sleep(1.0)


def unlock_and_open():
    adb('shell', 'input', 'keyevent', '224')
    time.sleep(0.3)
    adb('shell', 'input', 'keyevent', '82')
    time.sleep(0.4)
    adb('shell', 'am', 'start', '-n', 'com.haoqi.training/.MainActivity', check=False)
    time.sleep(1.2)


def main():
    adb('root')
    time.sleep(2)
    adb('wait-for-device')
    adb('shell', 'input', 'keyevent', '224')
    time.sleep(0.5)
    adb('shell', 'input', 'keyevent', '82')
    time.sleep(0.5)
    adb('shell', 'input', 'swipe', '540', '1800', '540', '400', '250')
    time.sleep(0.8)
    adb('shell', 'cmd', 'statusbar', 'collapse')
    adb('shell', 'pm', 'clear', 'com.haoqi.training')
    adb('shell', 'pm', 'grant', 'com.haoqi.training', 'android.permission.POST_NOTIFICATIONS')
    adb('shell', 'logcat', '-c')
    adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
    time.sleep(1.5)

    # ---------- 1. undo day completion resets all sets ----------
    add_exercise('AAA', rest=5)
    r = dump('after-add')
    blocks = set_blocks(r)
    assert len(blocks) == 1, texts(r)
    tapnode(blocks[0])            # set 1 done -> rest starts
    time.sleep(1.0)
    assert any('休息中' in t for t in titles()), titles()
    skip_rest()
    r = dump('after-first-set')
    blocks = set_blocks(r, '2')
    tapnode(blocks[0])            # set 2 done -> rest starts
    time.sleep(1.0)
    assert any('休息中' in t for t in titles()), titles()
    skip_rest()

    r = dump('before-complete')
    tap(r, '完成')
    r = dump('completed')
    assert '撤销' in texts(r), texts(r)
    tap(r, '撤销')
    r = dump('after-undo')
    assert '完成' in texts(r), texts(r)

    # both sets should be not-done again: tapping set 1 and set 2 starts a rest each
    blocks = set_blocks(r)
    tapnode(blocks[0])
    time.sleep(1.0)
    rest1 = any('休息中' in t for t in titles())
    skip_rest()
    r = dump('after-undo-tap1')
    blocks = set_blocks(r, '2')
    tapnode(blocks[0])
    time.sleep(1.0)
    rest2 = any('休息中' in t for t in titles())
    skip_rest()
    print('undo reset: set1 starts rest =', rest1, ', set2 starts rest =', rest2)
    assert rest1 and rest2, 'undo did not reset all sets'

    # ---------- 2. alarm fires after the process is killed ----------
    add_exercise('BBB', rest=30)
    r = dump('alarm-setup')
    blocks = set_blocks(r)
    # BBB is the second card -> second '1' block
    tapnode(blocks[1])
    time.sleep(1.2)
    assert any('休息中' in t and 'BBB' in t for t in titles()), titles()
    adb('shell', 'input', 'keyevent', '3')  # HOME
    time.sleep(1.0)
    pid = app_pid()
    adb('shell', 'kill', '-9', pid, check=False)
    time.sleep(1.0)
    print('killed', pid, '->', app_pid())
    time.sleep(32.0)
    n = notifications()
    print('notifications after alarm:', [t for t in titles() if t])
    print('alert channel present:', 'rest_timer_alert_v1' in n)
    assert any('休息结束' in t for t in titles()), titles()
    assert 'rest_timer_alert_v1' in n, 'alert notification is not on the alarm channel'
    assert not any('休息中' in t for t in titles()), 'ongoing countdown notification should be replaced'

    # ---------- 3. resume from lock screen stress ----------
    adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
    time.sleep(1.5)
    # start a rest so the resume path has to resync the service/notification
    r = dump('stress-setup')
    blocks = set_blocks(r, '2')
    tapnode(blocks[1])
    time.sleep(1.0)
    assert any('休息中' in t for t in titles()), titles()

    for i in range(6):
        adb('shell', 'input', 'keyevent', '26')  # screen off
        time.sleep(2.0)
        unlock_and_open()
        time.sleep(1.0)
        pid = app_pid()
        assert pid, f'iteration {i}: app died'
    print('resume x6 ok, pid', app_pid())

    # also kill while locked and reopen (cold start path)
    adb('shell', 'input', 'keyevent', '26')
    time.sleep(1.0)
    pid = app_pid()
    adb('shell', 'kill', '-9', pid, check=False)
    time.sleep(1.5)
    unlock_and_open()
    assert app_pid(), 'app did not survive cold-start after kill'

    c = crash()
    assert 'FATAL' not in c, c[-3000:]
    print('CRASH LOG CLEAN')
    print('ALL PASS')


if __name__ == '__main__':
    main()
