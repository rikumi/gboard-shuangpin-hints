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


def download_stream(config, destination):
    with urllib.request.urlopen(config['url'], timeout=60) as response, destination.open('wb') as stream:
        shutil.copyfileobj(response, stream)


def checked_download(config, output, local=None):
    source = Path(local) if local else output
    expected=config.get('sha256')
    if not local and (not source.exists() or expected and sha256(source) != expected):
        part = source.with_suffix('.part')
        download_stream(config, part)
        if expected and sha256(part) != expected:
            part.unlink()
            raise RuntimeError('Downloaded input failed SHA-256 validation')
        part.replace(source)
    if expected and sha256(source) != expected:
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
    parser.add_argument('--inputs-config',type=Path)
    parser.add_argument('--lspatch', type=Path)
    parser.add_argument('--skip-unavailable', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    config = json.loads((args.inputs_config or root / 'ci/patch-inputs.json').read_text())
    work = root / 'build/patch'; work.mkdir(parents=True, exist_ok=True)
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
    args.output.mkdir(parents=True, exist_ok=True)
    metadata = json.loads((module.parent / 'output-metadata.json').read_text())
    version = metadata['elements'][0]['versionName']
    module_out = args.output / f'gboard-shuangpin-hints-{version}-release.apk'
    shutil.copy2(module,module_out)
    def sums(paths):
        (args.output/'SHA256SUMS').write_text(''.join(f'{sha256(path)}  {path.name}\n' for path in paths))
    try:
        source = checked_download(config['gboard'], work/'gboard-input', args.gboard)
        patcher = checked_download(config['lspatch'], work/'lspatch.jar', args.lspatch)
    except (OSError, RuntimeError) as error:
        if not args.skip_unavailable:raise
        sums([module_out]);print('LSPatch skipped: verified public input unavailable ('+type(error).__name__+'). Module APK prepared.')
        return
    inputs=work/'inputs';inputs.mkdir(exist_ok=True)
    splits=[]
    with zipfile.ZipFile(source) as archive:
        if 'AndroidManifest.xml' in archive.namelist():gboard=source
        else:
            names=[n for n in archive.namelist() if n.endswith('.apk')]
            if len({Path(n).name for n in names})!=len(names):raise RuntimeError('Duplicate bundle split names')
            for name in names:
                target=inputs/Path(name).name;target.write_bytes(archive.read(name))
                if target.name=='base.apk':gboard=target
                else:splits.append(target)
            if not any(Path(n).name=='base.apk' for n in names):raise RuntimeError('Bundle has no base APK')
    badging=subprocess.check_output([str(sdk/'build-tools/36.0.0/aapt2'),'dump','badging',str(gboard)],text=True)
    actual_name=re.search(r"versionName='([^']+)'",badging)
    package=re.search(r"package: name='([^']+)'",badging)
    if not actual_name or not actual_name[1].startswith(config['gboard'].get('version','18.')) or not actual_name[1].startswith('18.') or not package or package[1]!='com.google.android.inputmethod.latin':
        if not args.skip_unavailable:raise RuntimeError('LSPatch requires the selected Gboard 18 package')
        sums([module_out]);print('LSPatch skipped: downloaded APK is not Gboard 18.');return
    # Verify the original Google signing root in addition to the pinned archive hash.
    original_cert=subprocess.check_output([str(apksigner),'verify','--print-certs',str(gboard)],text=True)
    if 'f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83' not in original_cert:
        if not args.skip_unavailable:raise RuntimeError('Unexpected Gboard signing root')
        sums([module_out]);print('LSPatch skipped: Google signature verification failed.');return
    # Verify the original signing block before passing the APK to LSPatch.
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
    if splits:
        patched_out=args.output/f'gboard-{config["gboard"]["version"]}-hints-{version}-lspatch.apks'
        signed=work/'signed-splits';signed.mkdir(exist_ok=True)
        with zipfile.ZipFile(patched_out,'w',zipfile.ZIP_DEFLATED) as archive:
            archive.write(patched,'base.apk')
            for split in splits:
                split_cert=subprocess.check_output([str(apksigner),'verify','--print-certs',str(split)],text=True)
                if 'f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83' not in split_cert:
                    raise RuntimeError('Unexpected original split signing root')
                output=signed/split.name
                command=[str(apksigner),'sign','--ks',str(Path(signing['storeFile']).resolve()),'--ks-key-alias',signing['keyAlias'],
                         '--ks-pass','pass:'+signing['storePassword'],'--key-pass','pass:'+signing['keyPassword'],'--out',str(output),str(split)]
                result=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
                if result.returncode:raise RuntimeError('Split signing failed')
                verify_signature(apksigner,output,config['moduleCertificateSha256']);archive.write(output,split.name)
    else:
        patched_out=args.output/f'gboard-{config["gboard"]["version"]}-hints-{version}-lspatch.apk'
        shutil.copy2(patched,patched_out)
    sums([module_out,patched_out])
    print(f'Prepared {module_out.name} and {patched_out.name}')


if __name__ == '__main__':
    main()
