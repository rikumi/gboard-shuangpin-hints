#!/usr/bin/env python3
"""Static adapter audit against real APKs; does NOT claim on-device verification."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

class Dex:
    def __init__(self, data):
        self.data = data
        count, offset = self.pair(56)
        self.strings = []
        for i in range(count):
            p = self.u32(offset + i * 4)
            _, p = self.uleb(p)
            self.strings.append(data[p:data.index(b'\0', p)].decode('utf-8', 'replace'))
        count, offset = self.pair(64)
        self.types = [self.strings[self.u32(offset + i * 4)] for i in range(count)]
        count, offset = self.pair(72)
        self.protos = []
        for i in range(count):
            _, ret, args = struct.unpack_from('<III', data, offset + i * 12)
            params = [] if not args else [self.types[struct.unpack_from('<H', data, args + 4 + 2 * j)[0]] for j in range(self.u32(args))]
            self.protos.append((self.types[ret], params))
        count, offset = self.pair(80)
        self.fields = []
        for i in range(count):
            cls, typ, name = struct.unpack_from('<HHI', data, offset + i * 8)
            self.fields.append({'owner': self.types[cls], 'type': self.types[typ], 'name': self.strings[name]})
        count, offset = self.pair(88)
        self.methods = []
        for i in range(count):
            cls, proto, name = struct.unpack_from('<HHI', data, offset + i * 8)
            ret, args = self.protos[proto]
            self.methods.append({'owner': self.types[cls], 'name': self.strings[name], 'args': args, 'return': ret})
        self.classes = {}
        count, offset = self.pair(96)
        for i in range(count):
            cls, flags, parent, interfaces, _, _, content, _ = struct.unpack_from('<8I', data, offset + i * 32)
            info = {'access': flags, 'enumNames': [], 'name': self.types[cls], 'parent': None if parent == 0xffffffff else self.types[parent], 'fields': [], 'methods': [], 'interfaces': [] if not interfaces else [self.types[struct.unpack_from('<H', data, interfaces + 4 + 2 * j)[0]] for j in range(self.u32(interfaces))]}
            self.classes[info['name']] = info
            if not content: continue
            lengths = []
            for _ in range(4):
                n, content = self.uleb(content); lengths.append(n)
            for section, length in enumerate(lengths):
                index = 0
                for _ in range(length):
                    diff, content = self.uleb(content); index += diff
                    access, content = self.uleb(content)
                    record = dict(self.fields[index] if section < 2 else self.methods[index])
                    record['access'] = access
                    if section >= 2:
                        code, content = self.uleb(content); record['code_offset'] = code
                    info['fields' if section < 2 else 'methods'].append(record)
            if flags & 16384:
                for method in info['methods']:
                    code = method.get('code_offset', 0)
                    if method['name'] != '<clinit>' or not code: continue
                    units = self.u32(code + 12)
                    start = code + 16
                    for unit in range(max(0, units - 1)):
                        opcode = data[start + unit * 2]
                        if opcode == 26:
                            index = struct.unpack_from('<H', data, start + (unit + 1) * 2)[0]
                        elif opcode == 27 and unit + 2 < units:
                            index = self.u32(start + (unit + 1) * 2)
                        else: continue
                        if index < len(self.strings) and self.strings[index] in ('DECODE', 'COMMIT', 'PRESS', 'DOWN', 'UP'):
                            info['enumNames'].append(self.strings[index])

    def u32(self, p): return struct.unpack_from('<I', self.data, p)[0]
    def pair(self, p): return struct.unpack_from('<II', self.data, p)
    def uleb(self, p):
        result = shift = 0
        while True:
            c = self.data[p]; p += 1; result |= (c & 127) << shift
            if c < 128: return result, p
            shift += 7

def separator_layouts(apk, sdk, resources):
    labels = re.findall(r'resource (0x[0-9a-f]+) string/[^\n]+\n\s+\(\) "Separator"', resources)
    assert labels, 'Missing native separator accessibility label'
    result = []
    with zipfile.ZipFile(apk) as archive:
        files = [name for name in archive.namelist() if name.endswith('.xml')
                 and any(struct.pack('<I', int(label, 16)) in archive.read(name) for label in labels)]
    for name in files:
        tree = subprocess.check_output([str(sdk / 'aapt2'), 'dump', 'xmltree', str(apk), '--file', name], text=True)
        lines = tree.splitlines()
        for index, line in enumerate(lines):
            match = re.match(r'^(\s*)E: softkey \(line=', line)
            if not match: continue
            indent = len(match.group(1)); end = index + 1
            while end < len(lines) and (not lines[end].lstrip().startswith('E:') or len(lines[end]) - len(lines[end].lstrip()) > indent): end += 1
            block = '\n'.join(lines[index:end])
            if not any('content_description=@' + label in block for label in labels): continue
            key_id = re.search(r'A: id=@(0x[0-9a-f]+)', block)
            for action in re.split(r'E: action \(line=\d+\)', block)[1:]:
                action = re.split(r'\n\s*E:', action, maxsplit=1)[0]
                attrs = dict(re.findall(r'A: (data|intention|keycode|type)=(.*?)\s*(?:\(Raw:.*)?$', action, re.M))
                attrs = {key: value.strip().strip('"') for key, value in attrs.items()}
                if attrs.get('data') == "'" and attrs.get('intention') == 'DECODE' and attrs.get('type') == 'PRESS':
                    result.append({'file': name, 'definitionId': key_id.group(1) if key_id else None,
                                   'keycode': attrs.get('keycode'), 'data': "'", 'intention': 'DECODE', 'phase': 'PRESS'})
    assert result, 'No native decode-apostrophe separator binding found'
    return result

def analyze(apk, sdk):
    classes, strings = {}, set()
    resources = subprocess.check_output([str(sdk / 'aapt2'), 'dump', 'resources', str(apk)], text=True)
    strings.update(re.findall(r'^\s+\(\) "([^"\n]*)"$', resources, re.M))
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                dex = Dex(z.read(name)); classes.update(dex.classes); strings.update(dex.strings)
    key = classes['Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;']
    metadata = []
    for f in key['fields']:
        if f['access'] & 8: continue
        fields = classes.get(f['type'], {}).get('fields', [])
        if any(g['type'] in ('[Ljava/lang/CharSequence;', '[Ljava/lang/String;') for g in fields): metadata.append(f)
    assert len(metadata) == 1, f'Metadata detection is ambiguous: {metadata}'
    bindings = [m for m in key['methods'] if m['args'] and m['args'][0] == metadata[0]['type'] and not m['access'] & 8]
    assert bindings, 'No metadata binding hook'
    callbacks = []
    processor = 'Lcom/google/android/apps/inputmethod/libs/pinyin/HmmPinyinQwertyDecodeProcessor;'
    while processor in classes:
        info = classes[processor]
        callbacks += [m for m in info['methods'] if m['args'] == ['Landroid/view/inputmethod/EditorInfo;', 'Z'] and not m['access'] & 8]
        processor = info['parent']
    assert callbacks, 'No typed Pinyin activation callback'
    assert 'Lcom/google/android/apps/inputmethod/libs/hmm/AbstractHmmDecodeProcessor;' in classes
    chinese = classes['Lcom/google/android/apps/inputmethod/libs/chinese/ime/hmm/AbstractHmmChineseDecodeProcessor;']
    preedit_updates = [m for m in chinese['methods'] if m['return'] == 'V' and m['args'] == ['J'] and m['access'] & 1 and not m['access'] & 8]
    preedit_caches = [f for f in chinese['fields'] if f['type'] == 'Ljava/lang/CharSequence;' and f['access'] & 64 and not f['access'] & 8]
    preedit_getters = [m for m in chinese['methods'] if m['return'] == 'Ljava/lang/CharSequence;' and not m['args'] and m['access'] & 4]
    preedit_readers = [m for m in chinese['methods'] if m['return'] == 'Ljava/lang/CharSequence;'
                       and not m['args'] and m['access'] & 4 and not m['access'] & 8]
    preedit_clear_notifications = [m for m in chinese['methods'] if m['return'] == 'V'
                                    and not m['args'] and not m['access'] & (8 | 1024)] if preedit_caches else []
    assert preedit_readers
    if preedit_caches: assert preedit_clear_notifications
    assert len(preedit_updates) == 1 and len(preedit_caches) <= 1
    assert preedit_caches or len(preedit_getters) == 1
    hmm = classes['Lcom/google/android/apps/inputmethod/libs/hmm/AbstractHmmDecodeProcessor;']
    preedit_resets = [m for m in hmm['methods'] if m['return'] == 'V' and m['args'] == ['Ljava/lang/CharSequence;'] and not m['access'] & 8]
    assert preedit_resets
    ime = classes['Lcom/google/android/libraries/inputmethod/ime/AbstractIme;']
    ime_activations = [m for m in ime['methods'] if m['return'] == 'V' and m['args'][:2] == ['Landroid/view/inputmethod/EditorInfo;', 'Z'] and not m['access'] & 8]
    assert len(ime_activations) == 1
    assert classes['Lcom/google/android/apps/inputmethod/libs/latin5/LatinIme;']['parent'] == ime['name']
    based = classes['Lcom/google/android/libraries/inputmethod/ime/processor/ProcessorBasedIme;']
    def inherited_dispatch(type_name, candidate):
        info = classes.get(type_name)
        if not info: return False
        for iface in info['interfaces']:
            if any(m['name'] == candidate['name'] and m['args'] == candidate['args'] and m['return'] == 'Z'
                   for m in classes.get(iface, {}).get('methods', [])): return True
        return inherited_dispatch(info['parent'], candidate)
    dispatches = [m for m in based['methods'] if m['return'] == 'Z' and len(m['args']) == 1
                  and inherited_dispatch(based['parent'], m)]
    assert len(dispatches) == 1, dispatches
    event = classes[dispatches[0]['args'][0]]
    action_arrays = []
    for field in event['fields']:
        if not field['type'].startswith('[L'): continue
        action = classes.get(field['type'][1:], {})
        constructors = [m for m in action.get('methods', []) if m['name'] == '<init>' and len(m['args']) == 3
                        and m['args'][0] == 'I' and m['args'][2] == 'Ljava/lang/Object;']
        if constructors: action_arrays.append((field, action, constructors))
    assert len(action_arrays) == 1
    action_field, action, constructors = action_arrays[0]
    intention = classes[constructors[0]['args'][1]]
    assert 'DECODE' in intention['enumNames']
    assert len([f for f in action['fields'] if f['type'] == 'I' and f['access'] & 1 and not f['access'] & 8]) == 1
    phases = [f for f in event['fields'] if 'PRESS' in classes.get(f['type'], {}).get('enumNames', [])]
    assert len(phases) == 1
    assert any(f['type'] == metadata[0]['type'] for f in event['fields'])
    assert 'id/key_pos_shift' in resources
    primary_paths = []
    for owner in based['fields']:
        for field in classes.get(owner['type'], {}).get('fields', []):
            if not field['access'] & 8 and (field['type'].endswith('/BaseDecodeProcessor;')
                    or field['type'] == 'Ljava/lang/Object;' or field['type'].startswith('[L')
                    or field['type'].startswith('L') and field['type'] in classes):
                primary_paths.append({'manager': owner, 'candidate': field, 'selection': 'runtime BaseDecodeProcessor instance; direct field preferred'})
    assert primary_paths, 'No primary decoder path'
    protected_handlers = []
    parent = 'Lcom/google/android/apps/inputmethod/libs/pinyin/HmmPinyinQwertyDecodeProcessor;'
    while parent in classes:
        info = classes[parent]
        protected_handlers.extend(m for m in info['methods'] if m['return'] == 'Z'
                                  and m['args'] == [event['name']] and m['access'] & 4 and not m['access'] & (8 | 1024))
        parent = info['parent']
    assert protected_handlers, 'No Chinese decoder fallback event entry'
    inline_uppercase = {'dispatch': dispatches[0], 'actions': action_field, 'constructor': constructors[0],
                        'phase': phases[0], 'intention': 'DECODE', 'primaryDecoderPaths': primary_paths, 'decoderEventHandlers': protected_handlers, 'identification': 'PRESS + DECODE apostrophe; no position ID required',
                        'nativeSeparatorLayouts': separator_layouts(apk, sdk, resources)}
    assert 'pinyin_scheme' in strings, 'Missing scheme preference'
    schemes = [s for s in ('shuangpin_flypy', 'shuangpin_ziranma', 'shuangpin_ms', 'shuangpin_abc', 'shuangpin_ziguang', 'shuangpin_jiajia') if s in strings]
    assert len(schemes) == 6, schemes
    lifecycle = {}
    service = 'Lcom/android/inputmethod/latin/LatinIME;'
    while service in classes:
        info = classes[service]
        for m in info['methods']:
            if m['name'] in ('onCreate', 'onStartInputView', 'onFinishInputView', 'onDestroy', 'onCurrentInputMethodSubtypeChanged', 'getCurrentInputConnection'):
                lifecycle.setdefault(m['name'], m['owner'])
        service = info['parent']
    assert service == 'Landroid/inputmethodservice/InputMethodService;', service
    env = dict(os.environ)
    certs = subprocess.check_output([str(sdk / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True, env=env)
    roots = re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', certs)
    assert 'f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83' in roots, 'Unexpected signing root'
    badging = subprocess.check_output([str(sdk / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
    package, code, version = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging).groups()
    assert package == 'com.google.android.inputmethod.latin'
    return {'file': apk.name, 'package': package, 'versionCode': int(code), 'version': version,
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'googleSigningRootVerified': True,
            'metadataField': metadata[0], 'bindingMethods': bindings, 'activationMethods': callbacks,
            'hmmBasePresent': True,
            'enginePreedit': {'updates': preedit_updates, 'readers': preedit_readers, 'clearNotifications': preedit_clear_notifications, 'cache': preedit_caches, 'fallbackGetter': preedit_getters if not preedit_caches else [], 'emptyTextResetMethods': preedit_resets},
            'imeActivation': ime_activations, 'inlineUppercase': inline_uppercase,
            'lifecycleOwners': lifecycle, 'schemes': schemes,
            'launcherActivity': re.search(r"launchable-activity: name='([^']+)'", badging).group(1), 'verification': 'static APK structure only; device test pending'}

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('apks', nargs='+', type=Path)
    parser.add_argument('--sdk-tools', type=Path, default=Path.home() / 'Library/Android/sdk/build-tools/36.0.0')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = [analyze(apk, args.sdk_tools) for apk in args.apks]
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    for item in report: print('PASS:', item['version'], 'Google signature and static hook targets')
