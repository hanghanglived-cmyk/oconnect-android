#!/usr/bin/env python3
"""Import unchanged, hash-verified v1.17.0 native libraries for a Windows build.

SPDX-License-Identifier: GPL-2.0-or-later
Download the APK from the upstream v1.17.0 GitHub release, then pass its local
path. This does not install/execute the upstream APK or change native sources.
"""
import argparse
import hashlib
from pathlib import Path
import zipfile

EXPECTED_SHA256 = "6eb30e907f38554807d34407667be4b78d364da3b59003c90ec5032968afa6ca"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
LIBRARIES = ("libopenconnect.so", "libstoken.so", "libcurl-bin.so", "libcsd-wrapper.so")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream_apk", type=Path)
    args = parser.parse_args()
    if hashlib.sha256(args.upstream_apk.read_bytes()).hexdigest() != EXPECTED_SHA256:
        raise SystemExit("Upstream APK digest mismatch; no native libraries imported")
    root = Path(__file__).resolve().parents[1] / "app/src/main/jniLibs"
    with zipfile.ZipFile(args.upstream_apk) as apk:
        # Validate every input before writing; never extract arbitrary ZIP paths.
        payloads = [(abi, lib, apk.read(f"lib/{abi}/{lib}"))
                    for abi in ABIS for lib in LIBRARIES]
    for abi, lib, payload in payloads:
        target = root / abi / lib
        if target.exists() and target.read_bytes() != payload:
            raise SystemExit(f"Existing {abi}/{lib} differs; refusing to overwrite it")
    for abi, lib, payload in payloads:
        target = root / abi / lib
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
    print("Verified upstream SHA256 and imported 16 unchanged native libraries")


if __name__ == "__main__":
    main()
