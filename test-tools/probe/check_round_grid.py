"""Runtime geometry regression: requires a running grid scene and patched probe APK."""
from pathlib import Path
import subprocess
import time
import statistics
import os
import shutil
from PIL import Image

sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk and os.environ.get('LOCALAPPDATA'):
    sdk = str(Path(os.environ['LOCALAPPDATA']) / 'Android' / 'Sdk')
ADB = os.environ.get('ADB') or shutil.which('adb')
if not ADB and sdk:
    ADB = str(Path(sdk) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb'))
if not ADB:
    raise SystemExit('Set ADB or ANDROID_HOME, or add adb to PATH')
OUT = Path('docs/runtime-tests')
def adb(*args):
    return subprocess.check_output([ADB, *args])
def pull(remote, local, package='studio.scenecam.probe'):
    (OUT / local).write_bytes(adb('exec-out', 'run-as', package, 'cat', remote))
def spacing(im, horizontal):
    n = im.width if horizontal else im.height
    hits = []
    for i in range(n):
        rgb = im.getpixel((i, 12) if horizontal else (12, i))[:3]
        if min(rgb) > 180:
            hits.append(i)
    groups = []
    for i in hits:
        if not groups or i > groups[-1][-1] + 1:
            groups.append([])
        groups[-1].append(i)
    centers = [statistics.mean(g) for g in groups]
    assert len(centers) >= 3, centers
    return statistics.median(b-a for a,b in zip(centers, centers[1:]))
def measure(name, expect_square=True):
    with Image.open(OUT / name) as im:
        dx, dy = spacing(im, True), spacing(im, False)
        ratio = dy/dx
        text = f'{name}: cells {dx:.2f} x {dy:.2f} px; ratio={ratio:.4f}'
        print(text, flush=True)
        report.append(text)
        if expect_square:
            assert abs(ratio-1) < .025, text
        else:
            assert abs(ratio-1) > .5, 'Baseline did not reproduce stretch'

report = []
measure('v9-grid-before.png', False)
cases = [(False,640,480,90), (False,640,480,0), (False,640,480,180),
         (False,640,480,270), (False,1280,720,90), (False,640,360,90),
         (True,640,480,90), (True,1280,720,90)]
for index,(camera2,w,h,angle) in enumerate(cases):
    adb('shell','am','force-stop','studio.scenecam.probe')
    remote = f'round-grid-camera{2 if camera2 else 1}-{w}x{h}-{angle}.png'
    adb('shell','run-as','studio.scenecam.probe','rm','-f','files/'+remote)
    adb('shell','am','start','-W','-n','studio.scenecam.probe/.RoundGridActivity',
        '--ez','camera2',str(camera2).lower(),'--ei','width',str(w),'--ei','height',str(h),
        '--ei','angle',str(angle),'--ez','encode',str(index==0).lower())
    for attempt in range(30):
        time.sleep(.5)
        listing = adb('shell','run-as','studio.scenecam.probe','ls','files').decode()
        if remote in listing and (index!=0 or 'ENCODE_DECODE_OK' in adb('logcat','-d','-s','RoundGrid').decode()):
            break
    else:
        raise AssertionError('Timeout '+remote)
    name = 'v9-'+remote
    pull('files/'+remote,name)
    measure(name)
    if index==0:
        pull('files/round-grid.mp4','v9-round-grid.mp4')
        pull('files/round-grid.mp4.png','v9-round-grid-decoded.png')
        measure('v9-round-grid-decoded.png')
pull('files/square-grid.png','v9-square-grid.png','studio.scenecam')
(OUT/'v9-grid-runtime.log').write_bytes(adb('logcat','-d','-s','RoundGrid','SceneCam','AndroidRuntime'))
(OUT/'v9-grid-camera-service.txt').write_bytes(adb('shell','dumpsys','media.camera'))
(OUT/'v9-grid-privacy.txt').write_bytes(adb('shell','dumpsys','sensor_privacy'))
(OUT/'v9-grid-results.txt').write_text('\n'.join(report)+'\nALL_GRID_CHECKS_PASSED\n',encoding='utf-8')
