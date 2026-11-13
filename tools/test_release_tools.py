#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-2.1-or-later
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest import mock
import urllib.error
import contextlib
import io

TOOLS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('patch_gboard', TOOLS / 'patch_gboard.py')
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)


class ReleaseVersionTest(unittest.TestCase):
    def version(self, base, tags, previous_code=0):
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
            return json.loads(subprocess.check_output(['python3', str(root / 'tools/release_version.py')], cwd=root, text=True, env={**os.environ, 'PREVIOUS_RELEASE_CODE': str(previous_code)}))

    def test_first_release_uses_base(self):
        self.assertEqual(self.version('0.1.18', [])['tag'], 'v0.1.18')

    def test_existing_release_advances_without_a_source_bump(self):
        actual = self.version('0.1.18', ['v0.1.17', 'v0.1.18'])
        self.assertEqual(actual, {'tag': 'v0.1.19', 'version': '0.1.19', 'code': '10020'})

    def test_preview_versions_cannot_skip_a_release_and_nonrelease_tags_are_ignored(self):
        self.assertEqual(self.version('0.2.0', ['v0.1.18', 'v99.0.0-preview'])['tag'], 'v0.1.19')
        self.assertEqual(self.version('0.1.27', ['v0.1.24'])['tag'], 'v0.1.25')

    def test_install_code_advances_once_even_after_higher_preview_code(self):
        actual=self.version('0.1.27', ['v0.1.25'], 10028)
        self.assertEqual(actual, {'tag':'v0.1.26','version':'0.1.26','code':'10029'})


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

    def test_unavailable_public_download_keeps_module_and_skips_lspatch(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'tools').mkdir();(root/'ci').mkdir()
            (root/'ci/patch-inputs.json').write_text(json.dumps({'gboard':{},'lspatch':{},'moduleCertificateSha256':'fixture'}))
            module=root/'module.apk';module.write_bytes(b'module')
            (root/'output-metadata.json').write_text(json.dumps({'elements':[{'versionName':'0.1.27'}]}))
            output=root/'dist'
            with mock.patch.object(patch,'__file__',str(root/'tools/patch_gboard.py')), \
                 mock.patch.object(patch,'verify_signature'), \
                 mock.patch.object(patch,'checked_download',side_effect=urllib.error.URLError('unavailable')), \
                 mock.patch.dict(os.environ,{'ANDROID_HOME':'/unused-sdk'}), \
                 mock.patch('sys.argv',['patch_gboard','--module',str(module),'--output',str(output),'--skip-unavailable']), \
                 contextlib.redirect_stdout(io.StringIO()):
                patch.main()
            self.assertEqual([f.name for f in output.glob('*.apk')],['gboard-shuangpin-hints-0.1.27-release.apk'])
            self.assertEqual(list(output.glob('*.apks')),[])
            self.assertEqual((output/'gboard-shuangpin-hints-0.1.27-release.apk').read_bytes(),b'module')
            self.assertIn('gboard-shuangpin-hints-0.1.27-release.apk',(output/'SHA256SUMS').read_text())

    def test_gboard16_base_is_rejected_while_module_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'tools').mkdir();(root/'ci').mkdir()
            (root/'ci/patch-inputs.json').write_text(json.dumps({'gboard':{},'lspatch':{},'moduleCertificateSha256':'fixture'}))
            module=root/'module.apk';module.write_bytes(b'module')
            (root/'output-metadata.json').write_text(json.dumps({'elements':[{'versionName':'0.1.27'}]}))
            source=root/'gboard.apk'
            import zipfile
            with zipfile.ZipFile(source,'w') as archive:archive.writestr('AndroidManifest.xml',b'fixture')
            output=root/'dist'
            with mock.patch.object(patch,'__file__',str(root/'tools/patch_gboard.py')), \
                 mock.patch.object(patch,'verify_signature'), \
                 mock.patch.object(patch,'checked_download',return_value=source), \
                 mock.patch.object(patch.subprocess,'check_output',return_value="package: versionName='16.6.3'\nminSdkVersion:'26'"), \
                 mock.patch.object(patch.subprocess,'run') as run, \
                 mock.patch.dict(os.environ,{'ANDROID_HOME':'/unused-sdk'}), \
                 mock.patch('sys.argv',['patch_gboard','--module',str(module),'--output',str(output),'--skip-unavailable']), \
                 contextlib.redirect_stdout(io.StringIO()):
                patch.main()
            run.assert_not_called()
            self.assertEqual(list(output.glob('*lspatch*')),[])
            self.assertEqual((output/'gboard-shuangpin-hints-0.1.27-release.apk').read_bytes(),b'module')

    def test_gboard_build_input_is_a_pinned_direct_original_package_mirror(self):
        config=json.loads((TOOLS.parent/'ci/patch-inputs.json').read_text())['gboard']
        self.assertNotIn('apkmirror.com',config['url'])
        self.assertNotIn('downloader',config)
        self.assertTrue(config['url'].startswith('https://github.com/'))
        self.assertTrue(config['url'].endswith('.apk'))
        self.assertNotIn('beta',config['version'])
        self.assertEqual(len(config['sha256']),64)
        self.assertTrue(config['version'].startswith('18.'))

    def test_gboard18_below_android12l_is_allowed(self):
        import zipfile
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'tools').mkdir();(root/'ci').mkdir()
            (root/'ci/patch-inputs.json').write_text(json.dumps({'gboard':{'version':'18.6.3'},'lspatch':{},'moduleCertificateSha256':'fixture'}))
            module=root/'module.apk';module.write_bytes(b'module')
            (root/'output-metadata.json').write_text(json.dumps({'elements':[{'versionName':'0.1.27'}]}))
            source=root/'gboard.apk'
            with zipfile.ZipFile(source,'w') as archive:archive.writestr('AndroidManifest.xml',b'fixture')
            output=root/'dist'
            def command(args,**kwargs):
                if '-jar' in args:
                    dest=root/'build/patch/output/patched.apk'
                    with zipfile.ZipFile(dest,'w') as archive:
                        archive.writestr('assets/lspatch/modules/module.apk',b'module')
                        archive.writestr('assets/lspatch/config.json',b'{}')
                return mock.Mock(returncode=0,stdout='')
            with mock.patch.object(patch,'__file__',str(root/'tools/patch_gboard.py')), \
                 mock.patch.object(patch,'verify_signature'), \
                 mock.patch.object(patch,'checked_download',return_value=source), \
                 mock.patch.object(patch.subprocess,'check_output',side_effect=["package: name='com.google.android.inputmethod.latin' versionName='18.6.3'\nminSdkVersion:'26'",'f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83']), \
                 mock.patch.object(patch.subprocess,'run',side_effect=command), \
                 mock.patch.dict(os.environ,{'ANDROID_HOME':'/unused-sdk'}), \
                 mock.patch('sys.argv',['patch_gboard','--module',str(module),'--output',str(output),'--skip-unavailable']), \
                 contextlib.redirect_stdout(io.StringIO()):
                patch.main()
            self.assertEqual(len(list(output.glob('*lspatch.apk'))),1)


if __name__ == '__main__':
    unittest.main()
