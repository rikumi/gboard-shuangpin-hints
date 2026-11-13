#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-2.1-or-later
"""Download pinned inputs, embed the release module, and sign both APKs identically."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
import zipfile


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def checked_download(config, output, local=None):
    source = Path(local) if local else output
    if not local and (not source.exists() or sha256(source) != config['sha256']):
        part = source.with_suffix('.part')
        with urllib.request.urlopen(config['url'], timeout=120) as response, part.open('wb') as stream:
            shutil.copyfileobj(response, stream)
        if sha256(part) != config['sha256']:
            part.unlink()
            raise RuntimeError('Downloaded input failed SHA-256 validation')
        part.replace(source)
    if sha256(source) != config['sha256']:
        raise RuntimeError('Input failed SHA-256 validation')
    return source.resolve()


def verify_signature(apksigner, apk, expected):
    result = subprocess.run([str(apksigner), 'verify', '--print-certs', str(apk)],
                            check=True, text=True, capture_output=True)
    digests = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', result.stdout)
    if digests != [expected]:
        raise RuntimeError('APK signing certificate does not match the persistent module key')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--module', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--gboard', type=Path)
    parser.add_argument('--lspatch', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    config = json.loads((root / 'ci/patch-inputs.json').read_text())
    work = root / 'build/patch'; work.mkdir(parents=True, exist_ok=True)
    gboard = checked_download(config['gboard'], work / 'gboard.apk', args.gboard)
    patcher = checked_download(config['lspatch'], work / 'lspatch.jar', args.lspatch)
    signing_path = os.environ.get('ANDROID_SIGNING_CONFIG')
    if signing_path:
        signing = json.loads(Path(signing_path).read_text())
    else:
        signing = {'storeFile': str(root / '.signing/debug.keystore'),
                   'storePassword': 'android', 'keyAlias': 'androiddebugkey', 'keyPassword': 'android'}
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    apksigner = sdk / 'build-tools/36.0.0/apksigner'
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    module = args.module.resolve()
    verify_signature(apksigner, module, config['moduleCertificateSha256'])
    # The pinned Google APK hash also protects its original signing block.
    subprocess.run([str(apksigner), 'verify', str(gboard)], check=True, stdout=subprocess.DEVNULL)
    patched_dir = work / 'output'; patched_dir.mkdir(exist_ok=True)
    for old in patched_dir.glob('*.apk'):
        old.unlink()
    command = [java, '-jar', str(patcher), '--force', '--sigbypasslv', '2',
               '--embed', str(module), '--keystore', str(Path(signing['storeFile']).resolve()),
               signing['storePassword'], signing['keyAlias'], signing['keyPassword'],
               '--output', str(patched_dir), str(gboard)]
    # The CLI requires password arguments: never log the command or attach its exception text.
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        # Do not echo command arguments or signing material on failure.
        print(result.stdout.replace(signing['storePassword'], '[redacted]').replace(signing['keyPassword'], '[redacted]'))
        raise SystemExit('LSPatch failed')
    outputs = list(patched_dir.glob('*.apk'))
    if len(outputs) != 1:
        raise RuntimeError('Expected exactly one patched Gboard APK')
    patched = outputs[0]
    verify_signature(apksigner, patched, config['moduleCertificateSha256'])
    with zipfile.ZipFile(patched) as archive:
        embedded = [name for name in archive.namelist() if name.startswith('assets/lspatch/modules/') and name.endswith('.apk')]
        if len(embedded) != 1 or hashlib.sha256(archive.read(embedded[0])).hexdigest() != sha256(module):
            raise RuntimeError('Patched APK does not embed this exact release module')
        if 'assets/lspatch/config.json' not in archive.namelist():
            raise RuntimeError('Missing LSPatch configuration')
    args.output.mkdir(parents=True, exist_ok=True)
    metadata = json.loads((module.parent / 'output-metadata.json').read_text())
    version = metadata['elements'][0]['versionName']
    module_out = args.output / f'gboard-shuangpin-hints-{version}-release.apk'
    patched_out = args.output / f'gboard-{config["gboard"]["version"]}-hints-{version}-lspatch.apk'
    shutil.copy2(module, module_out); shutil.copy2(patched, patched_out)
    (args.output / 'SHA256SUMS').write_text(''.join(f'{sha256(path)}  {path.name}\n' for path in (module_out, patched_out)))
    print(f'Prepared {module_out.name} and {patched_out.name}')


if __name__ == '__main__':
    main()
