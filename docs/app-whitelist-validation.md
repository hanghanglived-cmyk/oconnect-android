# Actual validation results

Recorded 2026-10-24. Tested application/build source commit:
`ded82c1ec2bcf8affb51c1abecb3bd2e064760f6`.

## Successful builds

- Windows: **BUILD SUCCESSFUL**, 80 tasks executed, using JDK 17.0.20.1,
  Gradle 8.13, Android platform 36 revision 2 and build-tools 35.0.0.
- Commands: `:app:clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.
- Paths temporarily mapped to an unused ASCII-only drive; mapping removed
  after the build. Neither the checkout nor SDK was relocated.
- Windows APK preserves the original release's 16 native binaries,
  verified byte-for-byte against the SHA256-verified upstream v1.17.0 APK.
- Linux Actions: **success**, including NDK `27.2.12479018` full native
  source build, all four ABIs, unit tests, lint, main/test APK compilation.
- [Successful Actions run](https://github.com/hanghanglived-cmyk/oconnect-android/actions/runs/40052487704).
- [APK artifact](https://github.com/hanghanglived-cmyk/oconnect-android/actions/runs/40052487704/artifacts/12544248491).
- [Diagnostics artifact](https://github.com/hanghanglived-cmyk/oconnect-android/actions/runs/40052487704/artifacts/12544457355).

Local and CI builds use different debug signing keys. Select one source for
installation; do not assume they can update each other. Neither is a
production-signed release.

## Tests and checks

Gradle JUnit results: **18 tests, zero failures/errors**:

| Suite | Tests |
| --- | ---: |
| AppWhitelistConfigTest | 10 |
| QuickSettingsProfileSelectionTest | 4 |
| StreamUtilsTest | 1 |
| VersionComparatorTest | 3 |

Whitelist tests cover disabled mode, empty lists, malformed names, missing
packages, immutable snapshots, fresh connection rules and restart decisions.
Lint: **zero errors**, 189 non-fatal local warnings. Main categories include
unused resources, overdraw, RTL, indentation, locale and SharedPreferences
style. This is not a warning-free codebase. `AppWhitelistStoreTest` and existing
device tests compiled; none were executed on an Android device.

Additional checks:

- Exact baseline `v1.17.0` → `cd1d747cc3a321eeeee5b0e54d73a8062e77a1d4`.
- Fork relationship and authenticated owner `hanghanglived-cmyk` verified.
- OpenConnect `8b702bf2dbaf11302ed98629214b1df5d50a12aa` and stoken
  `bc25aa48922064881f17201c6302ab2640e36763` fetched; protocol sources unchanged.
- Vendor checksums verified for JDK, Gradle, SDK archives and original APK.
- 144 manifest/resource XML files parsed; `git diff --check` passed.
- No `QUERY_ALL_PACKAGES`; separate provider/permission; backup disabled.
- Main APK CRC, dex, native payload, signature and metadata verified.
  All 16 native ELF files have PT_LOAD alignment >= 16 KB.
- `apksigner verify`: v1/v2 valid, one debug signer.
- `aapt`: `io.github.hanghanglived.oconnect.whitelist`, version
  `1.17.0-whitelist.1` / code `1130`, min API 23, target API 36,
  label `OConnect WL`; arm64-v8a, armeabi-v7a, x86 and x86_64.
- No keystores or VPN credentials committed. No upstream PR created.

## Primary local deliverable

`OConnect-WL-v1.17.0-whitelist.1-debug.apk`: 24,202,187 bytes.

SHA256:

```text
fd0357b596aed035ac2c4e9ce543e57b5e0c9906aceea3bea3777d7ba61cc935
```

Debug certificate SHA256:
`632f2ee1bde88f05ed779b9d3b8fd3023711ccd0ea3ba8f08597389ef9f61762`.

## Device checks remain outstanding

`adb devices -l` showed no attached devices. Installation, live VPN,
Android 16/Samsung lockscreen, network switching, live TUN replacement,
per-app routing, DNS and persistence instrumentation were not tested on a device.
No VPN credentials were requested. Use the device checklist before replacing
the user's stable everyday connection.

## Build issues resolved

- Replaced setup action's removed `tools` package with supported platform tools.
- LZ4 installation now uses the Android sysroot `BINDIR`, not `/usr/local/bin`.
- Kept recursive ABI builds serial to avoid shared download-file races.
- Windows AIDL/AAPT encoding failures resolved with temporary ASCII paths.
  Disabling AGP's initial path check alone was insufficient; no system locale
  change was needed.
