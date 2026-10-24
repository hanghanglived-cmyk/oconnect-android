# Actual validation status

Recorded 2026-10-24. This file separates executed checks from pending checks.

## Executed

- Fork fetched from `hanghanglived-cmyk/oconnect-android`.
- Exact upstream release baseline checked out: `v1.17.0` →
  `cd1d747cc3a321eeeee5b0e54d73a8062e77a1d4`.
- Pinned OpenConnect and stoken submodules fetched; no native source changes.
- Microsoft JDK 17.0.20.1 and Gradle 8.13 distributions verified against their
  vendors' SHA256 checksums.
- `AppWhitelistConfigTest`: **10 tests passed** with real JUnit 4.13.2 and
  `javac --release 11` on JDK 17. These exercise disabled mode, empty lists,
  malformed names, missing packages, immutable snapshots, fresh connection
  application, and whether changes require a TUN restart.
- 144 Android manifest/resource XML files parsed successfully.
- Manifest checks: independent provider authority, backup disabled, no
  `QUERY_ALL_PACKAGES` permission, and explicit launcher intent visibility.
- Original release APK verified against its published SHA256:
  `6eb30e907f38554807d34407667be4b78d364da3b59003c90ec5032968afa6ca`.
- Its 16 native libraries imported unchanged for the Windows build. All ELF
  PT_LOAD segments checked for at least 16 KB alignment across all four ABIs.
- `git diff --check` passed.

## In progress / not yet passed

- Full Gradle `testDebugUnitTest lintDebug assembleDebug`: in progress while
  official SDK packages and Maven dependencies download. No build success or
  modified APK is claimed at this checkpoint.
- Full NDK build from source: Linux Actions workflow prepared; not yet verified.
- `AppWhitelistStoreTest` persistence/profile-isolation test is an Android
  instrumentation test. It has not been executed on a device.
- TUN establishment, active-rule rebuild, authentication after rebuild,
  network changes, lockscreen, per-app IPv4/IPv6 and DNS routing: not tested
  on a device. See the Android 16 checklist in `app-whitelist.md`.

The downloaded upstream APK is only a native-engine input; it is not the
modified deliverable. Only a generated and verified WL APK should be delivered
as the implementation result.
