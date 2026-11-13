#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-2.1-or-later
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('patch_gboard', TOOLS / 'patch_gboard.py')
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)


class ReleaseVersionTest(unittest.TestCase):
    def version(self, base, tags):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root / 'tools').mkdir()
            shutil.copy2(TOOLS / 'release_version.py', root / 'tools')
            (root / 'version.json').write_text(json.dumps({'name': base, 'code': 10019}))
            def git(*args):
                subprocess.run(['git', *args], cwd=root, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            git('init', '-q'); git('config', 'user.name', 'Release Test'); git('config', 'user.email', 'test@example.invalid')
            git('add', '.'); git('commit', '-qm', 'fixture')
            for tag in tags:
                git('tag', tag)
            return json.loads(subprocess.check_output(['python3', str(root / 'tools/release_version.py')], cwd=root, text=True))

    def test_first_release_uses_base(self):
        self.assertEqual(self.version('0.1.18', [])['tag'], 'v0.1.18')

    def test_existing_release_advances_without_a_source_bump(self):
        actual = self.version('0.1.18', ['v0.1.17', 'v0.1.18'])
        self.assertEqual(actual, {'tag': 'v0.1.19', 'version': '0.1.19', 'code': '10020'})

    def test_higher_base_wins_and_nonrelease_tags_are_ignored(self):
        self.assertEqual(self.version('0.2.0', ['v0.1.18', 'v99.0.0-preview'])['tag'], 'v0.2.0')


class PinnedInputTest(unittest.TestCase):
    def test_verified_local_input_can_be_used_without_network(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'input'; path.write_bytes(b'pinned build input')
            config = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
            self.assertEqual(patch.checked_download(config, path, path), path.resolve())

    def test_modified_input_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'input'; path.write_bytes(b'tampered')
            with self.assertRaises(RuntimeError):
                patch.checked_download({'sha256': '0' * 64}, path, path)


if __name__ == '__main__':
    unittest.main()
