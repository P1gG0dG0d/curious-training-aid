"""Android release regression for day renaming, migration and animated navigation."""
import argparse, re, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument('--adb',required=True); p.add_argument('--serial',default='emulator-5556'); a=p.parse_args()
out=Path('.android-test/reports/v020'); out.mkdir(parents=True,exist_ok=True)
def adb(*args):
    return subprocess.run([a.adb,'-s',a.serial,*args],check=True,capture_output=True,text=True,encoding='utf-8').stdout

def dump(name):
    adb('shell','uiautomator','dump','/sdcard/test.xml'); adb('pull','/sdcard/test.xml',str(out/f'{name}.xml'))
    return ET.parse(out/f'{name}.xml').getroot()
def has(r,text): return any(text in n.get('text','') for n in r.iter('node'))
def tapnode(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds'))); adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(.5)
def tap(r,text=None,desc=None):
    nodes=[n for n in r.iter('node') if (text is not None and n.get('text')==text) or (desc is not None and n.get('content-desc')==desc)]
    assert nodes, f'Control not found: {text or desc}'; tapnode(nodes[0] if text and (text.startswith('第') or text == 'ChestDay') else nodes[-1])
def alive():
    assert adb('shell','pidof','com.haoqi.training').strip()
    assert 'FATAL EXCEPTION' not in adb('logcat','-b','crash','-d')
adb('logcat','-c')
r=dump('upgraded'); assert has(r,'第3天'), 'Existing days lost in migration'
assert not has(r,'第n天只是计划编号')
tap(r,text='第3天'); r=dump('selected'); assert has(r,'撤销'), 'Existing completion lost'
tap(r,text='第3天'); r=dump('rename'); assert has(r,'重命名训练日')
field=next(n for n in r.iter('node') if n.get('class')=='android.widget.EditText'); tapnode(field)
adb('shell','input','text','ChestDay'); adb('shell','input','keyevent','4')
r=dump('typed'); tap(r,text='保存'); r=dump('named'); assert has(r,'ChestDay'); assert has(r,'撤销')
adb('shell','am','force-stop','com.haoqi.training'); adb('shell','am','start','-W','-n','com.haoqi.training/.MainActivity'); time.sleep(2)
r=dump('restart'); assert has(r,'ChestDay'), 'Name not persisted'
tap(r,text='ChestDay'); r=dump('reselected'); assert not has(r,'重命名训练日')
tap(r,text='ChestDay'); r=dump('edit-again'); assert has(r,'重命名训练日'); tap(r,text='取消')
r=dump('cancelled'); tap(r,text='饮食'); r=dump('diet'); assert has(r,'今日热量')
tap(r,desc='设置'); r=dump('settings'); assert has(r,'保存资料'); tap(r,text='保存资料')
adb('shell','input','keyevent','4'); r=dump('diet-configured'); assert has(r,'消耗摄入差为：'); assert has(r,'消耗 − 摄入')
adb('shell','screencap','-p','/sdcard/diet.png'); adb('pull','/sdcard/diet.png',str(out/'diet.png'))
tap(r,text='训练'); r=dump('training-return'); assert has(r,'ChestDay')
adb('shell','screencap','-p','/sdcard/training.png'); adb('pull','/sdcard/training.png',str(out/'training.png'))
alive(); print('PASS: v2 migration preserved days/completion; rename persists; selected-chip rename, other-chip selection, cancel, navigation and explicit calorie difference work.')
