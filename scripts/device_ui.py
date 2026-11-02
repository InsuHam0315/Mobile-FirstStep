"""Small adb UI harness: inspect native views, select visible labels, and capture evidence."""
import argparse
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

ADB = str(pathlib.Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe')

def adb(*args):
    return subprocess.check_output([ADB, *args])

def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/firststep-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/firststep-ui.xml')).iter('node')

def tap(text, hold=False):
    for node in nodes():
        if node.get('text') == text and node.get('enabled') == 'true':
            x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
            x,y=str((x1+x2)//2),str((y1+y2)//2)
            if hold: adb('shell','input','swipe',x,y,x,y,'3300')
            else: adb('shell','input','tap',x,y)
            return
    raise RuntimeError('Visible enabled label not found: '+text)

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('action',choices=['dump','tap','hold','shot'])
    parser.add_argument('value',nargs='?')
    args=parser.parse_args()
    if args.action=='dump':
        for n in nodes():
            if n.get('text'): print(n.get('text'),n.get('bounds'),'enabled='+n.get('enabled'))
    elif args.action=='shot':
        path=pathlib.Path(args.value);path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(adb('exec-out','screencap','-p'))
    else: tap(args.value,args.action=='hold')
