#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-2.1-or-later
"""Allocate the next release without generating a recursive version-bump push."""
import json
import os
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
base = json.loads((root / 'version.json').read_text())
parse = lambda text: tuple(map(int, text.split('.')))
versions = [parse(tag[1:]) for tag in subprocess.check_output(
    ['git', 'tag', '--list', 'v*'], cwd=root, text=True).splitlines()
    if re.fullmatch(r'v\d+\.\d+\.\d+', tag)]
version = parse(base['name'])
if versions:
    latest = max(versions)
    version = max(version, (latest[0], latest[1], latest[2] + 1))
name = '.'.join(map(str, version))
code = max(base['code'], version[0] * 1000000 + version[1] * 10000 + version[2] + 1)
values = {'version': name, 'code': str(code), 'tag': 'v' + name}
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(''.join(f'{key}={value}\n' for key, value in values.items()))
print(json.dumps(values))
