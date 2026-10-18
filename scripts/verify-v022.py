exec(open('scripts/ui-v022.py',encoding='utf-8').read().split("adb('logcat','-c')")[0])
adb('logcat','-c')
r=dump('before-start');tap(r,text='1');r=dump('running');assert any(n.get('text')=='跳过' for n in r.iter('node'))
notes=adb('shell','dumpsys','notification','--noredact');assert re.search(r'NotificationRecord\([^\n]*pkg=com\.haoqi\.training[^\n]*id=1001',notes),'Timer notification missing before skip'
tap(r,text='跳过');time.sleep(2)
notes=adb('shell','dumpsys','notification','--noredact');assert not re.search(r'NotificationRecord\([^\n]*pkg=com\.haoqi\.training[^\n]*id=1001',notes),'Timer notification remains after skip'
r=dump('stopped');assert not any(n.get('text')=='跳过' for n in r.iter('node')); print('PASS: running notification exists; App skip removes notification and countdown')
tap(r,desc='设置')
for i in range(4):
    r=dump('settings')
    fields=[n for n in r.iter('node') if n.get('class')=='android.widget.EditText' and 'API Key' in ''.join(x.get('text','') for x in n.iter())]
    if fields: break
    adb('shell','input','swipe','540','1700','540','650','400')
assert fields,'API Key field not visible'
assert fields[0].get('password')=='false','API Key still password field';click(fields[0])
info=adb('shell','dumpsys','input_method');(out/'input-method.txt').write_text(info,encoding='utf-8')
print('API KEY',[(n.get('text'),n.get('password')) for n in fields])
print('\n'.join(line for line in info.splitlines() if 'inputType=' in line))
assert 'FATAL EXCEPTION' not in adb('logcat','-b','crash','-d')
