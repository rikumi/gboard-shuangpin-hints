# Building

Use JDK 21, Android SDK 37, build-tools 36.0.0, and the included Gradle wrapper.

```sh
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease
```

Release builds require a persistent signing key. Local builds use `.signing/debug.keystore`; CI restores the existing key from the repository secret `ANDROID_SIGNING_BUNDLE`. Its JSON fields are `keystore` (base64), `storePassword`, `keyAlias`, and `keyPassword`. The private key and its temporary configuration must never be committed.

Alternatively, `ANDROID_SIGNING_CONFIG` can point to a private JSON file containing `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Both the module and patched Gboard use this same identity. The expected certificate digest is pinned in `ci/patch-inputs.json`.

Every push to `main` builds and tests the project, creates a minified/resource-shrunk release module, downloads the pinned Gboard and LSPatch inputs, embeds the module, signs both APKs, and publishes the next release. Pull requests only build and test; they cannot access release signing secrets. Manual runs publish by default and can disable publishing.

`tools/release_version.py` chooses the greater of the base version in `version.json` and the next patch after the latest release tag. It passes version overrides to Gradle without committing a version bump. This avoids a recursive push/release loop.

Only `coui-ui` and `coui-preference` are declared; there is no extended icon-pack dependency. R8 and resource shrinking remove unused dependency code and resources from release APKs. Xposed-reachable module classes retain their names and members.

To create the same patched Gboard locally:

```sh
python3 tools/patch_gboard.py --module build/outputs/apk/release/GboardShuangpinHints-release.apk --output dist
```

Inputs are downloaded outside tracked source, verified by SHA-256, and kept under `build/patch`. Both output signatures and the exact embedded module are verified before publication.
