#!/usr/bin/env python3
"""Build the native Material 3 module and verify its debug APK signature."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VERSION = json.loads((ROOT / 'version.json').read_text())
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or (Path.home() / ('Library/Android/sdk' if os.sys.platform == 'darwin' else 'Android/Sdk')))
JAVA = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or 'java').resolve().parent
TOOLS = SDK / 'build-tools' / os.environ.get('ANDROID_BUILD_TOOLS', '36.0.0')
API = ROOT / 'libs/xposed-api-82.jar'
BUILD = ROOT / 'build'
env = dict(os.environ, JAVA_HOME=str(JAVA.parent), ANDROID_HOME=str(SDK))

def run(*args):
    subprocess.run([str(a) for a in args], check=True, env=env, cwd=ROOT)

def main():
    API.parent.mkdir(exist_ok=True)
    if not API.exists():
        urllib.request.urlretrieve('https://maven.aliyun.com/repository/public/de/robv/android/xposed/api/82/api-82.jar', API)
    assert hashlib.sha256(API.read_bytes()).hexdigest() == 'f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25', 'Xposed API checksum mismatch'
    signing = ROOT / '.signing/debug.keystore'
    if not signing.exists():
        signing.parent.mkdir(exist_ok=True)
        run(JAVA / 'keytool', '-genkeypair', '-keystore', signing, '-storepass', 'android', '-keypass', 'android',
            '-alias', 'androiddebugkey', '-dname', 'CN=Gboard Hints Debug', '-keyalg', 'RSA', '-validity', '10000')
    gradle = os.environ.get('GRADLE_BIN', str(ROOT / 'gradlew'))
    run(gradle, 'assembleDebug', '--console=plain')
    directory = BUILD / 'outputs/apk/debug'
    metadata = json.loads((directory / 'output-metadata.json').read_text())
    release = metadata['elements'][0]
    assert release['versionCode'] == VERSION['code'] and release['versionName'] == VERSION['name']
    output = BUILD / f'gboard-shuangpin-hints-{VERSION["name"]}-debug.apk'
    shutil.copy2(directory / release['outputFile'], output)
    run(TOOLS / 'apksigner', 'verify', '--verbose', output)
    print(output)
    print('SHA-256:', hashlib.sha256(output.read_bytes()).hexdigest())

if __name__ == '__main__': main()
