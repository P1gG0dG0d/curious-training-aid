"""Verify v3 upgrade data, daily activity persistence and foreground date rollover on emulator only."""
import argparse, subprocess, time, re, xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--adb',required=True); a=p.parse_args()
out=Path('.android-test/reports/v026'); out.mkdir(parents=True,exist_ok=True)
def adb(*args): return subprocess.run([a.adb,'-s','emulator-5556',*args],check=True,capture_output=True,text=True,encoding='utf-8').stdout
def dump(name):
    adb('shell','uiautomator','dump','/sdcard/v026.xml'); adb('pull','/sdcard/v026.xml',str(out/(name+'.xml')))
    return ET.parse(out/(name+'.xml')).getroot()
def texts(r): return [n.get('text','') for n in r.iter('node')]
def tapnode(n):
    x,y,z,w=map(int,re.findall(r'\d+',n.get('bounds'))); adb('shell','input','tap',str((x+z)//2),str((y+w)//2)); time.sleep(.4)
def tap(r,text=None,desc=None):
    ns=[n for n in r.iter('node') if (text and n.get('text')==text) or (desc and n.get('content-desc')==desc)]
    assert ns,(text,desc,texts(r)); tapnode(ns[-1])
def diet():
    r=dump('nav'); tap(r,text='饮食'); return dump('diet')
def field(r): return next(n for n in r.iter('node') if n.get('class')=='android.widget.EditText')
def fill(r,value):
    tapnode(field(r)); adb('shell','input','keyevent','KEYCODE_MOVE_END')
    for _ in range(12): adb('shell','input','keyevent','KEYCODE_DEL')
    adb('shell','input','text',value); adb('shell','input','keyevent','4')
adb('shell','am','start','-W','-n','com.haoqi.training/.MainActivity')
r=dump('training'); assert 'ChestDay' in texts(r),'training name lost'
r=diet(); assert '今日活动消耗' in texts(r); assert float(field(r).get('text')) in (0,456)
assert any('摄入 230' in t for t in texts(r)),texts(r)
fill(r,'456'); r=dump('edited'); tap(r,text='保存今日活动消耗')
adb('shell','am','force-stop','com.haoqi.training'); adb('shell','am','start','-W','-n','com.haoqi.training/.MainActivity')
r=diet(); assert float(field(r).get('text'))==456,'activity not persisted'
tap(r,desc='设置'); r=dump('settings'); assert not any(t in ('活动消耗 kcal','活动代谢 kcal/天') for t in texts(r)),texts(r)
adb('shell','input','keyevent','4'); r=dump('back')
# Save/restore emulator wall clock and automatic time; never touch host time.
auto=adb('shell','settings','get','global','auto_time').strip()
epoch=int(adb('shell','date','+%s').strip()); mono=time.monotonic()
try:
    adb('shell','settings','put','global','auto_time','0')
    adb('shell','date','-s','@'+str(epoch+172800)); time.sleep(2)
    r=dump('tomorrow'); assert float(field(r).get('text'))==0,'previous activity leaked into tomorrow'
    assert any('摄入 0' in t for t in texts(r)),texts(r)
    fill(r,'123'); r=dump('tomorrow-edit'); tap(r,text='保存今日活动消耗')
    adb('shell','date','-s','@'+str(epoch+int(time.monotonic()-mono))); time.sleep(2)
    r=dump('restored-today'); assert any('今日活动 456 kcal' in t for t in texts(r)),'today activity overwritten'
    assert any('摄入 230' in t for t in texts(r)),'yesterday food deleted'
finally:
    adb('shell','date','-s','@'+str(epoch+int(time.monotonic()-mono)))
    adb('shell','settings','put','global','auto_time',auto)
assert adb('shell','pidof','com.haoqi.training').strip()
print('PASS: v3 upgrade preserved food/training; activity persists; settings removed; foreground day switch isolates activity/intake and preserves old data')
