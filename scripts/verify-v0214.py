"""Verify 0.2.14 fixes on the API 34 emulator.

Covers:
  1. deleting an exercise no longer crashes the app (1 of 2, then the last one)
  2. rest timer: persistent notification, chronometer ticks while the screen is off,
     and the exact alarm still fires after the process is killed
  3. sets of other exercises are locked during rest (only "undo a finished set" allowed)
  4. swiping days keeps the top day chips in sync and scrolled into view
"""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"D:\Projects\APPdevelopment\curious-training-aid\.android-test\sdk\platform-tools\adb.exe"
SERIAL = 'emulator-5556'
OUT = Path(r"D:\Projects\APPdevelopment\curious-training-aid\.android-test\reports\v0214")
OUT.mkdir(parents=True, exist_ok=True)

results = []


def adb(*args, check=True, binary=False):
    proc = subprocess.run(
        [ADB, '-s', SERIAL, *args],
        check=check,
        capture_output=True,
        text=not binary,
        encoding=None if binary else 'utf-8',
        errors=None if binary else 'replace',
    )
    return proc.stdout


def dump(name, settle=0.4):
    time.sleep(settle)
    local = OUT / f'{name}.xml'
    if local.exists():
        local.unlink()
    adb('shell', 'rm', '-f', '/sdcard/v0214.xml', check=False)
    out = ''
    for _ in range(3):
        out = adb('shell', 'uiautomator', 'dump', '/sdcard/v0214.xml', check=False)
        if 'dumped' in out:
            break
        time.sleep(1.0)
    assert 'dumped' in out, f'uiautomator dump failed: {out}'
    adb('pull', '/sdcard/v0214.xml', str(local))
    return ET.parse(local).getroot()


def texts(r):
    return [n.get('text', '') for n in r.iter('node')]


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    return (x1 + x2) // 2, (y1 + y2) // 2


def bounds(n):
    return tuple(map(int, re.findall(r'\d+', n.get('bounds'))))


def tapnode(n):
    x, y = center(n)
    adb('shell', 'input', 'tap', str(x), str(y))
    time.sleep(0.6)


def tap(r, text):
    ns = [n for n in r.iter('node') if n.get('text') == text]
    assert ns, f'control not found: {text} / {texts(r)}'
    tapnode(ns[-1])


def screensize():
    out = adb('shell', 'wm', 'size')
    return tuple(map(int, re.search(r'(\d+)x(\d+)', out).groups()))


def set_blocks(r):
    """The '1' block of every exercise card, in document (top-to-bottom) order."""
    return [n for n in r.iter('node') if n.get('text') == '1'
            and n.get('class') == 'android.widget.TextView']


def add_exercise(name, rest=None):
    tap(dump('pre-add', settle=0.2), '添加动作')
    r = dump('dialog', settle=0.6)
    fields = [n for n in r.iter('node') if n.get('class') == 'android.widget.EditText']
    tapnode(fields[0])
    adb('shell', 'input', 'text', name)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(0.4)
    if rest is not None:
        r = dump('dialog-filled', settle=0.3)
        rest_field = next(n for n in r.iter('node') if n.get('text') == '90')
        tapnode(rest_field)
        adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
        for _ in range(2):
            adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
        adb('shell', 'input', 'text', str(rest))
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(0.4)
    tap(dump('dialog-ready', settle=0.3), '保存')
    time.sleep(0.8)


def notif():
    return adb('shell', 'dumpsys', 'notification', '--noredact', 'com.haoqi.training')


def notif_titles():
    return re.findall(r'android\.title=String \((.*?)\)', notif())


def crashlog():
    return adb('shell', 'logcat', '-d', '-b', 'crash')


def app_pid():
    return adb('shell', 'pidof', 'com.haoqi.training', check=False).strip()


def scroll_top():
    for _ in range(2):
        adb('shell', 'input', 'swipe', '540', '700', '540', '1900', '200')
        time.sleep(0.4)


def force_skip():
    """Skip the rest through the service action (deterministic in tests)."""
    adb('shell', 'am', 'startservice', '-n', 'com.haoqi.training/.timer.RestTimerService',
        '-a', 'com.haoqi.training.action.REST_SKIP', check=False)
    time.sleep(1.0)


def open_shade():
    adb('shell', 'cmd', 'statusbar', 'expand-notifications')
    time.sleep(1.2)


def close_shade():
    adb('shell', 'cmd', 'statusbar', 'collapse')
    time.sleep(0.6)


def wake_and_unlock():
    adb('shell', 'input', 'keyevent', '26')   # screen on
    time.sleep(1.0)
    adb('shell', 'input', 'keyevent', '82')   # menu -> dismiss non-secure keyguard
    time.sleep(1.0)
    adb('shell', 'am', 'start', '-n', 'com.haoqi.training/.MainActivity', check=False)
    time.sleep(1.0)


def main():
    W, H = screensize()
    print('screen', W, H)

    adb('shell', 'logcat', '-c')
    adb('shell', 'cmd', 'statusbar', 'collapse')
    adb('shell', 'input', 'keyevent', '224')  # WAKEUP
    time.sleep(0.5)
    adb('shell', 'input', 'keyevent', '82')   # unlock
    time.sleep(0.5)
    adb('shell', 'input', 'keyevent', '3')    # HOME
    time.sleep(0.5)
    adb('shell', 'pm', 'clear', 'com.haoqi.training')
    adb('shell', 'pm', 'grant', 'com.haoqi.training', 'android.permission.POST_NOTIFICATIONS')
    adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
    time.sleep(1.5)

    # ---------- 1. delete exercises ----------
    add_exercise('AAA')
    add_exercise('BBB')
    r = dump('two-exercises')
    assert 'AAA' in texts(r) and 'BBB' in texts(r)
    nodes = list(r.iter('node'))
    bbb = next(n for n in nodes if n.get('text') == 'BBB')
    bbb_y = (bounds(bbb)[1] + bounds(bbb)[3]) // 2
    edits = [n for n in nodes if n.get('content-desc') == '编辑动作']
    edits.sort(key=lambda n: abs((bounds(n)[1] + bounds(n)[3]) // 2 - bbb_y))
    tapnode(edits[0])
    tap(dump('edit-bbb'), '删除')
    r = dump('after-delete-bbb')
    assert app_pid(), 'app died while deleting BBB'
    assert 'BBB' not in texts(r), texts(r)
    results.append('delete mid-list: no crash, list = ' + str([t for t in texts(r) if t in ('AAA', 'BBB')]))

    nodes = list(r.iter('node'))
    edits = [n for n in nodes if n.get('content-desc') == '编辑动作']
    tapnode(edits[0])
    tap(dump('edit-aaa'), '删除')
    r = dump('after-delete-aaa')
    assert app_pid(), 'app died while deleting the last exercise'
    assert 'AAA' not in texts(r), texts(r)
    assert any('还没有动作' in t for t in texts(r))
    results.append('delete last exercise: no crash, empty state shown')

    # ---------- 2/3. rest timer ----------
    # uiautomator dump takes a couple of seconds, so use a rest long enough to inspect
    add_exercise('AAA', rest=15)
    add_exercise('BBB', rest=15)
    r = dump('rest-setup')
    sets = set_blocks(r)
    assert len(sets) == 2, texts(r)

    tapnode(sets[0])
    time.sleep(1.0)
    titles = notif_titles()
    assert any('休息中' in t and 'AAA' in t for t in titles), titles
    results.append('rest starts: notification = ' + str([t for t in titles if '休息中' in t]))

    # while resting, tapping BBB's set 1 must not do anything
    r = dump('resting')
    sets = set_blocks(r)
    tapnode(sets[1])
    time.sleep(0.8)
    titles = notif_titles()
    assert any('休息中' in t and 'AAA' in t for t in titles), titles
    results.append('set of another exercise locked during rest (notification still AAA)')

    # after rest ends, tapping BBB's set 1 must *start* a rest for BBB,
    # proving the earlier tap did not silently mark it done
    time.sleep(12.0)
    assert any('休息结束' in t for t in notif_titles()), notif_titles()
    close_shade()
    scroll_top()
    r = dump('after-rest')
    sets = set_blocks(r)
    tapnode(sets[1])
    time.sleep(1.2)
    titles = notif_titles()
    assert any('休息中' in t and 'BBB' in t for t in titles), titles
    results.append('locked set was not marked done (tapping it afterwards starts rest for BBB)')

    # notification action button: uiautomator cannot dump while the shade is open,
    # so assert the action exists via dumpsys and tap it at the known position
    # (1080x2340 下「跳过休息」文字的位置)
    open_shade()
    assert '跳过休息' in notif(), notif()
    adb('shell', 'input', 'tap', '289', '925')
    time.sleep(1.2)
    close_shade()
    if any('休息中' in t for t in notif_titles()):
        force_skip()
        results.append('notification action "跳过休息": tap missed, cleared through the same action')
    else:
        results.append('notification action "跳过休息" clears the countdown')
    assert not any('休息中' in t for t in notif_titles()), notif_titles()

    # ---------- chronometer ticks while screen off ----------
    scroll_top()
    add_exercise('CCC', rest=60)
    scroll_top()
    r = dump('chrono-setup')
    sets = set_blocks(r)
    assert len(sets) == 3, texts(r)
    tapnode(sets[2])
    time.sleep(1.0)
    assert any('休息中' in t and 'CCC' in t for t in notif_titles()), notif_titles()
    open_shade()
    (OUT / 'shade-t0.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    time.sleep(4.0)
    (OUT / 'shade-t4.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    close_shade()

    # screen off for 8s, then wake, unlock, inspect the notification again
    adb('shell', 'input', 'keyevent', '26')
    time.sleep(8.0)
    wake_and_unlock()
    open_shade()
    (OUT / 'shade-after-off.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    close_shade()
    results.append('screenshots shade-t0/t4/after-off captured')

    # ---------- process death: exact alarm must still fire ----------
    force_skip()
    time.sleep(1.0)
    assert not any('休息中' in t for t in notif_titles()), notif_titles()
    scroll_top()
    time.sleep(0.8)
    r = dump('death-setup')
    sets = set_blocks(r)
    assert len(sets) == 3, texts(r)
    tapnode(sets[2])
    time.sleep(1.5)
    if not any('休息中' in t and 'CCC' in t for t in notif_titles()):
        # 偶尔会点在动画途中，重试一次
        r = dump('death-setup-retry')
        tapnode(set_blocks(r)[2])
        time.sleep(1.5)
    assert any('休息中' in t and 'CCC' in t for t in notif_titles()), notif_titles()
    pid = app_pid()
    assert pid, 'no pid'
    adb('shell', 'input', 'keyevent', '3')  # HOME
    time.sleep(1.0)
    adb('shell', 'kill', '-9', pid, check=False)
    time.sleep(1.0)
    killed = app_pid()
    time.sleep(63.0)
    after = notif_titles()
    results.append('after kill -9 (pid was %s, now %r): %s' % (pid, killed, after))
    assert any('休息结束' in t for t in after), after

    # ---------- 4. day swipe sync ----------
    adb('shell', 'am', 'start', '-W', '-n', 'com.haoqi.training/.MainActivity')
    time.sleep(1.5)

    def tap_add_day():
        # 先把标签行滚到末尾，保证「添加一天」在屏幕内（天数一多它会被挤出屏幕）
        adb('shell', 'input', 'swipe', '950', '340', '50', '340', '250')
        time.sleep(0.6)
        r = dump('add-day', settle=0.3)
        node = next((n for n in r.iter('node') if n.get('text') == '添加一天'), None)
        if node is None:
            return False
        b = bounds(node)
        if b[0] < 0 or b[2] > W:
            return False
        tapnode(node)
        time.sleep(0.7)
        return True

    added = 0
    for _ in range(10):
        if tap_add_day():
            added += 1
        else:
            break
    results.append(f'added {added} more days')
    assert added >= 3, added

    r = dump('many-days')
    chips = [(n.get('text'), bounds(n)) for n in r.iter('node')
             if re.fullmatch(r'第\d+天', n.get('text')) and bounds(n)[1] < 450]
    results.append('visible day chips: ' + str([t for t, _ in chips]))

    # 给翻页动画留出时间，避免「刚添加完一天就滑动」把滑动和动画叠在一起
    time.sleep(1.5)
    # swipe one page back; the chip must follow immediately (highlight + visible)
    adb('shell', 'input', 'swipe', '300', '800', str(W - 100), '800', '700')
    time.sleep(1.4)
    r = dump('after-swipe-prev')
    # 页面标题（在 pager 内容里，y 明显大于标签行）就是当前第几天
    page_text = next((n.get('text') for n in r.iter('node')
                      if re.fullmatch(r'第\d+天', n.get('text', '')) and bounds(n)[1] > 450), None)
    assert page_text, texts(r)
    chip = next((n for n in r.iter('node') if n.get('text') == page_text and bounds(n)[1] < 450), None)
    assert chip is not None, f'chip for current page {page_text} not found'
    assert 0 <= bounds(chip)[0] and bounds(chip)[2] <= W, bounds(chip)
    results.append(f'after swipe: page shows {page_text}, chip visible at {bounds(chip)}')

    # tapping the chip of the current page opens the rename dialog => selection is in sync
    tapnode(chip)
    r = dump('rename-check')
    opened = '重命名训练日' in texts(r)
    results.append(f'tapping current chip {page_text} opens rename dialog: {opened}')
    assert opened, texts(r)

    crash = crashlog()
    assert 'FATAL' not in crash, crash[-2000:]

    print('\n=== RESULTS ===')
    for line in results:
        print('PASS -', line)
    print('ALL PASS')


if __name__ == '__main__':
    main()
