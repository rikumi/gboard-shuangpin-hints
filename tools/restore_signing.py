#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-2.1-or-later
"""Restore the repository's persistent signing bundle without logging secrets."""
import base64
import json
import os
from pathlib import Path

encoded = os.environ.get('ANDROID_SIGNING_BUNDLE', '')
if not encoded:
    raise SystemExit('Configure the ANDROID_SIGNING_BUNDLE repository secret; random release signing is disabled.')
bundle = json.loads(encoded)
directory = Path(os.environ['RUNNER_TEMP']) / 'gboard-hints-signing'
directory.mkdir(mode=0o700, exist_ok=True)
keystore = directory / 'module.keystore'
keystore.write_bytes(base64.b64decode(bundle.pop('keystore'), validate=True))
keystore.chmod(0o600)
bundle['storeFile'] = str(keystore)
for key in ('storePassword', 'keyAlias', 'keyPassword'):
    if not isinstance(bundle.get(key), str) or not bundle[key]:
        raise SystemExit(f'Missing signing field: {key}')
config = directory / 'signing.json'
config.write_text(json.dumps(bundle))
config.chmod(0o600)
with open(os.environ['GITHUB_ENV'], 'a') as output:
    output.write(f'ANDROID_SIGNING_CONFIG={config}\n')
