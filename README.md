# OConnect

> **OConnect WL test fork:** per-profile application allowlists, separate package
> ID, and unchanged v1.17.0 native engine. See [usage, builds and device checks](docs/app-whitelist.md)
> and [actual validation status](docs/app-whitelist-validation.md). This branch
> is not an official upstream release.

[![License: GPL v2](https://img.shields.io/badge/license-GPL--2.0-blue.svg)](COPYING)
[![Android](https://img.shields.io/badge/platform-Android-3DDC84.svg)](app/build.gradle)
[![Latest release](https://img.shields.io/github/v/release/pengyue-polaron/openconnect-next-android)](https://github.com/pengyue-polaron/openconnect-next-android/releases/latest)

OConnect is a modern Android client for
[OpenConnect](https://www.infradead.org/openconnect/)-compatible SSL VPN
gateways, including Cisco AnyConnect-compatible servers and ocserv.

This repository is a maintained fork of the original OpenConnect for Android
codebase. It keeps the existing VPN core while updating the Android build,
the Material interface, onboarding, saved-credential login, localization, and
project documentation.

## Status

- Distribution: GitHub Releases only for now.
- Stores: not published on Google Play, F-Droid, or other app stores yet.
- F-Droid: planned; see [docs/fdroid.md](docs/fdroid.md).
- App name: **OConnect**.
- Android application ID: `io.pengyue.oconnect`.

## Features

- OpenConnect-compatible SSL VPN connections without root.
- Profile-based setup for organization, school, and self-hosted VPN gateways.
- Saved credentials and automatic login for repeat connections.
- RSA SecurID and TOTP software token support.
- Connection log, status view, byte counters, local IP details, and reconnect
  handling.
- Android Quick Settings tile with a selectable default profile, last-used
  behavior, or an app-selection fallback.
- Material 3 UI with light and dark mode.
- English, Simplified Chinese, and Traditional Chinese copy for the refreshed
  onboarding, settings, FAQ, and connection flows.

## Screenshots

<p>
  <img src="screenshots/verification/profiles-empty-new-icon.png" width="220" alt="First VPN setup screen">
  <img src="screenshots/verification/add-profile-blue-polish.png" width="220" alt="Add profile dialog">
  <img src="screenshots/verification/profile-list-blue-polish.png" width="220" alt="Profile list">
</p>

<p>
  <img src="screenshots/verification/login-dialog-password-toggle.png" width="220" alt="Login dialog with password visibility controls">
  <img src="screenshots/verification/profile-list-dark.png" width="220" alt="Dark mode profile list">
  <img src="screenshots/verification/profile-list-blue-polish-zh-cn.png" width="220" alt="Simplified Chinese profile list">
</p>

<p>
  <img src="screenshots/verification/quick-settings-tile-zh-cn.png" width="220" alt="Android Quick Settings VPN tile">
  <img src="screenshots/verification/settings-redesign/13-profile-main-final.png" width="220" alt="Redesigned VPN profile settings">
  <img src="screenshots/verification/settings-redesign/07-log-empty.png" width="220" alt="Connection log empty state">
  <img src="screenshots/verification/settings-redesign/08-faq-collapsed.png" width="220" alt="Collapsible FAQ">
</p>

## Install

Download the latest APK from
[GitHub Releases](https://github.com/pengyue-polaron/openconnect-next-android/releases/latest)
and install it on your Android device.

Current release APKs are published directly from this repository while store
distribution is being prepared. If you install an APK manually, Android will
not receive updates from an app store automatically.

## Basic Use

1. Open the app.
2. Choose **Add VPN profile**.
3. Enter the VPN gateway address from your organization.
4. Tap the profile row to connect.
5. Complete the login, certificate, group, or token prompts sent by the VPN
   server.
6. Open the **Log** tab if a connection fails.

The profile editor keeps the common profile name and server address on the
first screen. Certificates, private keys, software tokens, and saved login
behavior are grouped under Authentication. Split tunneling, reported OS, CSD,
PFS, XML POST, and DPD options remain available under Advanced.

General Settings keeps everyday VPN behavior and the Quick Settings default on
the first screen. Logging and legacy device compatibility options are grouped
under Advanced.

## Automatic Login

Automatic login is the passwordless flow previously exposed as **Batch mode**.
It reuses credentials saved from a normal login prompt.

- **Ask every time**: always show the VPN server login prompt.
- **Use saved credentials when available**: reuse saved fields and ask only for
  missing or changed prompts.
- **Use saved credentials only**: never show login prompts. If required data is
  missing, the connection stops so the profile can be updated.

## Build From Source

### Requirements

- JDK 17 or newer.
- Android SDK with platform tools.
- Android NDK r27c (`ndk;27.2.12479018`).
- GNU build tools (`make`, Autoconf, Automake, Libtool, pkg-config).
- Git submodules initialized.

### Build

```bash
git clone https://github.com/pengyue-polaron/openconnect-next-android.git
cd openconnect-next-android
git submodule update --init --recursive
make -C external install
./gradlew assembleDebug
```

The native build pins OpenConnect v9.21, stoken v0.93, curl 8.21.0, and the
cryptographic dependencies declared by OpenConnect's Android build. Native
executables and shared libraries are linked for 16 KB Android page sizes.
Override the NDK location when needed:

```bash
make -C external install ANDROID_NDK="$ANDROID_HOME/ndk/27.2.12479018"
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the current verification command:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## F-Droid

F-Droid inclusion is not automatic. The app needs source metadata, a buildable
tagged release, and a merge request to the `fdroiddata` repository. The current
preparation notes and checklist live in [docs/fdroid.md](docs/fdroid.md).

The Android application ID has been migrated to `io.pengyue.oconnect`
so this fork can be submitted separately from existing OpenConnect packages in
the F-Droid ecosystem. Because this replaces the previous
`io.pengyue.openconnectnext` ID, OConnect installs as a separate app instead of
upgrading an existing OpenConnect Next installation.

## Contributing

Issues and pull requests are welcome. Useful contributions include:

- Android compatibility fixes.
- VPN server compatibility reports.
- UI and accessibility polish.
- Translation improvements.
- F-Droid packaging work.
- Reproducible build and release signing improvements.

When reporting a connection issue, include the Android version, device model,
VPN gateway type if known, and relevant Log tab output. Do not include
passwords, private keys, tokens, or organization secrets.

## Security

This is VPN software and can route device traffic through a configured server.
Only install APKs from a source you trust, and only connect to VPN gateways you
control or are authorized to use.

Please report security-sensitive issues privately to the repository owner before
opening a public issue.

## License

OConnect is released under the GPLv2 license. See [COPYING](COPYING)
and [doc/LICENSE.txt](doc/LICENSE.txt).

Much of the Java code was derived from OpenVPN for Android by Arne Schwabe.
This package also includes OpenConnect, GnuTLS, GMP, Nettle, Libxml2, OATH
Toolkit, stoken, LibTomCrypt, and cURL components.
