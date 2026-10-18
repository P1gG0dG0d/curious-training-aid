import argparse,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);a=p.parse_args()
out=Path('.android-test/reports/v022');out.mkdir(parents=True,exist_ok=True)
def adb(*args): return subprocess.run([a.adb,'-s','emulator-5556',*args],check=True,capture_output=True,text=True,encoding='utf-8').stdout
def dump(name):
    adb('shell','uiautomator','dump','/sdcard/test.xml');adb('pull','/sdcard/test.xml',str(out/f'{name}.xml'));return ET.parse(out/f'{name}.xml').getroot()
def click(n):
    b=list(map(int,re.findall(r'\d+',n.get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.5)
def tap(r,text=None,desc=None):
    ns=[n for n in r.iter('node') if (text is not None and n.get('text')==text) or(desc is not None and n.get('content-desc')==desc)];assert ns,(text,desc);click(ns[-1])
adb('logcat','-c')
r=dump('training');tap(r,text='添加动作');r=dump('exercise')
fields=[n for n in r.iter('node') if n.get('class')=='android.widget.EditText'];click(fields[0]);adb('shell','input','text','TimerTest');adb('shell','input','keyevent','4');r=dump('filled');tap(r,text='保存');r=dump('saved')
print('TRAINING TEXTS',[(n.get('text'),n.get('content-desc')) for n in r.iter('node') if n.get('text') or n.get('content-desc')])
