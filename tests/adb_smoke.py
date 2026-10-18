"""UI smoke test for a disposable emulator. Clears this app's data first.
Usage: ANDROID_SERIAL=emulator-5560 python3 tests/adb_smoke.py
Requires adb in PATH and an already installed debug APK.
"""
import os, re, subprocess, time, xml.etree.ElementTree as ET

serial = os.environ.get('ANDROID_SERIAL', 'emulator-5560')
def adb(*args):
    return subprocess.check_output(['adb', '-s', serial, *args], text=True)
def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/health-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/health-ui.xml'))
def tap_text(label):
    for attempt in range(9):
        for n in nodes().iter('node'):
            if n.get('text') == label:
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
                if x2 <= x1 or y2 <= y1:
                    continue
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                time.sleep(1)
                return
        if attempt < 4:
            adb('shell', 'input', 'swipe', '160', '510', '160', '220', '250')
        else:
            adb('shell', 'input', 'swipe', '160', '220', '160', '510', '250')
    raise AssertionError(f'Missing clickable text: {label}')
def visible(text):
    assert any(text in n.get('text', '') for n in nodes().iter('node')), f'Missing text: {text}'

adb('shell', 'pm', 'clear', 'com.cloudres.health')
adb('shell', 'am', 'start', '-W', '-n', 'com.cloudres.health/.MainActivity')
time.sleep(2)
visible('今日完成')
tap_text('训练')
visible('把每一次练好')
tap_text('动作')
visible('腿举机')
tap_text('知道了')
tap_text('全身 B')
visible('把每一次练好')
tap_text('饮食')
visible('食堂也能吃得明白')
tap_text('记录')
tap_text('＋ 记录体重 / 腰围')
root = nodes()
edits = [n for n in root.iter('node') if n.get('class') == 'android.widget.EditText']
assert len(edits) == 2
for node, value in zip(edits, ['60.5', '75']):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    adb('shell', 'input', 'text', value)
adb('shell', 'input', 'keyevent', '4')
tap_text('保存')
visible('60.5')
adb('shell', 'am', 'force-stop', 'com.cloudres.health')
adb('shell', 'am', 'start', '-W', '-n', 'com.cloudres.health/.MainActivity')
tap_text('记录')
visible('60.5')
# Show settings without changing alarms or exporting personal data.
tap_text('设置')
visible('计划与提醒')
assert 'FATAL EXCEPTION' not in adb('logcat', '-d', '-s', 'AndroidRuntime')
print('UI_SMOKE_OK: four tabs, exercise dialog, A/B selection, body save and process-restart persistence, settings')
