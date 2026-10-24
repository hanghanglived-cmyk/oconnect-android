# OConnect WL: per-profile application whitelist

This is a test fork of OConnect, not an official upstream release.

## Baseline and scope

- Upstream release: `v1.17.0`, commit `cd1d747cc3a321eeeee5b0e54d73a8062e77a1d4`.
- OpenConnect: `8b702bf2dbaf11302ed98629214b1df5d50a12aa` (v9.21).
- stoken: `bc25aa48922064881f17201c6302ab2640e36763` (0.93).
- Development branch: `feature/app-whitelist`, in the user's fork only.
- Application ID: `io.github.hanghanglived.oconnect.whitelist`.
- Launcher label: **OConnect WL**. Version: `1.17.0-whitelist.1`.
- Minimum API 23, compile/target API 36; same as the original release.

The native engine, authentication, DTLS, DPD, IP routes, DNS settings,
network-change receiver, foreground service type, and screen/keepalive policy
are retained. Package-scoped actions are renamed solely to isolate this fork.
No root, iptables, DNS-based application filtering, or new third-party SDK is used.

## Source review

`OpenConnectManagementThread.runVPN()` obtains a Builder after CSTP setup,
calls `setIPInfo()`, then establishes the Android interface. `setupTunFD()`
passes that interface to libopenconnect. Application rules are inserted between
IP configuration and `establish()`, outside the native protocol implementation.

The original network reconnect calls `pause()` on libopenconnect and resumes
its mainloop using the same TUN. Rules consequently remain attached to that
interface across network changes. A new connection loads a fresh profile
snapshot before creating its interface.

Saving active rules is a separate, narrow restart path: persist both fields,
cancel the existing management thread, wait for its `threadDone()` callback
(after native destruction and TUN closure), and start the normal connection
path. The foreground notification stays active during this transition. Do not
replace this with `reconnect()`: that method does not recreate Android rules.
Saving changes to a profile currently authenticating or stopping is rejected
with a retry message. Revoke, Stop, or service destruction cancels a pending
rule restart. A reconnect can require authentication again.

The original KeepAlive component sends DNS probes from the VPN application's
own UID. Whitelist mode also permits this internal UID so those probes keep
using the tunnel. It does not count toward the minimum one user-selected app.
OpenConnect transport sockets continue to use the original `protect()` callback.

## Use

Open a VPN profile, then **App Split Tunneling / 应用分流**.
Select apps, enable the switch, and save. The switch defaults to off.
Search matches labels or package names. Clear requires confirmation and turns
off the draft switch; changes take effect only after Save.

Only launchable applications for the current Android user are offered. A
manifest `<queries>` MAIN/LAUNCHER intent provides Android 11+ visibility;
`QUERY_ALL_PACKAGES` and usage/accessibility permissions are not requested.
Headless services and apps in another user/work profile are not included in
this first version. Selection is stored per profile in the existing private
SharedPreferences file. It is not shared with the official installation.

Uninstalled, disabled, malformed, or inaccessible packages are skipped and a
count is shown. If none remain when connecting, no VPN interface is established;
the mode never silently falls back to all-app VPN. Android's platform API
implements UID rules: apps sharing a UID can share routing behavior.

Allowed apps remain subject to the existing profile and gateway routes;
the whitelist does not convert a server split tunnel to a full tunnel.
Unselected apps use the default network unless Android's **Block connections
without VPN**, enterprise policy, or another system restriction prevents it.
Only an allowlist is implemented: Android forbids combining allowed and
disallowed application rules on a Builder.

## Coexistence and privacy

The package ID, remote API permission, provider authority, and internal intent
names are separate from official OConnect. No signature key for the official
app is needed. Installing this app does not migrate or delete official data.
Android permits only one active VPN per user: connecting WL switches the active
VPN and may stop the official connection after the user grants system consent.

Legacy ACRA automatic crash upload is disabled and its old endpoint removed.
Cloud backup is disabled. New diagnostics record counts, never selected package
lists or VPN credentials. The upstream update checker is disabled for this fork
so it cannot recommend an incompatible official package.

## Build on Linux

The native Makefile requires a **Linux x86_64 NDK host toolchain**. A Windows
NDK alone is insufficient. Use Linux, a Linux CI runner, or a separately
configured WSL2 environment; installing WSL/rebooting a user's PC is not part
of this change.

Requirements: JDK 17, Gradle wrapper 8.13, AGP 8.13.2, Android platform 36,
build-tools 35.0.0, NDK r27c (`27.2.12479018`), GNU make, Autoconf,
Automake, Libtool, pkg-config, gettext/autopoint, and normal C build tools.

```sh
git clone --branch feature/app-whitelist --recurse-submodules \
  https://github.com/hanghanglived-cmyk/oconnect-android.git
cd oconnect-android
sdkmanager 'platforms;android-36' 'build-tools;35.0.0' 'ndk;27.2.12479018'
make -C external install ANDROID_NDK="$ANDROID_HOME/ndk/27.2.12479018"
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. Native libraries must
exist for all packaged ABIs; a Java-only APK is not a usable VPN client.
The Actions workflow checks native payloads, signature and package identity,
and uploads the standalone APK, checksum, reports, and build logs. A failed
native dependency download or build fails the job visibly; it is not hidden
by producing a native-library-free artifact.

The workflow runs on pushes to the development branch or manual dispatch.
It builds all four original ABIs with 16 KB native page-size linker flags.
See **Actions → App whitelist debug APK → successful run →
oconnect-whitelist-debug**. Artifact ZIP contents include the APK.
No successful workflow run or downloadable APK is implied by this document;
check `app-whitelist-validation.md` for actual results.

### Windows alternative using the original release's native engine

To compile Java/resources on Windows without rebuilding Linux-hosted native
dependencies, download `oconnect-v1.17.0-debug.apk` from the original
`pengyue-polaron/oconnect-android` GitHub **v1.17.0** release. Its published
SHA256 is `6eb30e907f38554807d34407667be4b78d364da3b59003c90ec5032968afa6ca`.
Run `python scripts/import-release-native.py /path/to/oconnect-v1.17.0-debug.apk`.
The script verifies that exact digest before importing the 16 unchanged native
libraries, refuses to replace different local libraries, and keeps `.so` files
out of Git. Initialize the pinned source submodules for the JNI Java wrappers,
install the Windows SDK platform/build tools, set `JAVA_HOME` to JDK 17 and
`ANDROID_HOME` to that SDK, then run `gradlew.bat testDebugUnitTest lintDebug assembleDebug`.
This reuses upstream native binaries; it is not a local NDK/source rebuild.
The source-building CI path above remains available for full reproducibility.

## Signing and installation

Debug and release builds are different. The debug APK uses a development key;
no production release has been signed. Never commit keystores or passwords.
Ephemeral CI runners may generate different debug keys between runs. An update
requires the same package and signing key; otherwise Android refuses it. Do not
uninstall an existing WL installation without first arranging to preserve its
configuration. A stable release key can later be supplied through a separately
approved secure signing process.

On Samsung: download the artifact from your own successful Actions run,
extract the APK, permit installation for the chosen file/browser app, and
install **OConnect WL**. Keep the official app installed. Create a profile
manually in WL; do not send credentials to GitHub or chat. Start with the
whitelist off and validate ordinary connectivity before enabling it.

## Android 16 / Samsung Galaxy S26 device checklist

All checks require an actual device and a working authorized VPN account.
Use a controlled IP-check endpoint or gateway-side observation to compare
egress; the VPN key icon alone does not prove per-app routing.

- [ ] Install alongside official OConnect; verify separate data and provider.
- [ ] With whitelist off, compare baseline connection, DNS and routes.
- [ ] Select at least one app; verify selected and unselected IPv4/IPv6 egress.
- [ ] Test a DNS lookup in each group without changing original DNS options.
- [ ] Confirm the empty switch/save and all-uninstalled connection cases block.
- [ ] Uninstall one selected app; verify warning and surviving app rules.
- [ ] Disconnect/reconnect and force-stop/relaunch; verify profile persistence.
- [ ] Use two VPN profiles with distinct selections; verify isolation.
- [ ] Switch Wi-Fi to mobile data and back; verify rules and DTLS reconnect.
- [ ] Lock for 5, 30 and 60 minutes; check connection and keepalive on resume.
- [ ] Change/save rules while connected; verify old session exits before new
      interface, original authentication UI appears if needed, and new egress.
- [ ] Press Stop or revoke VPN access during restart; verify no unwanted restart.
- [ ] Disable/save whitelist; verify original all-app routing behavior returns.
- [ ] Rotate the selection page; verify draft selection and search usability.
- [ ] Check light/dark themes, Chinese/English and large font sizes.
- [ ] Check notification permission and battery settings with original defaults.
- [ ] Check Android lockdown interaction; leave it off when testing direct apps.

No device stability claim should be made until these results are recorded.

## License and maintenance

Keep `COPYING`, `doc/LICENSE.txt`, and bundled notices. The application is
GPLv2-or-later with the original OpenSSL linking exception. OpenConnect and
stoken retain their LGPL notices; other bundled libraries have their own
licenses, including the crypto and compression dependencies fetched by the
unchanged native build. Provide corresponding source and pinned submodule
revisions alongside distributed binaries; preserve any applicable relinking
requirements. Do not treat the legacy dependency summary as a new legal audit.

For updates, rebase onto an explicit upstream release, inspect any new
`establish()` or reconnect paths, rerun tests and APK checks, and repeat device
checks before replacing the stable build. Keep signing material outside Git.
