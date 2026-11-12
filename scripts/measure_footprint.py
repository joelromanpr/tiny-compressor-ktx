#!/usr/bin/env python3
"""Report artifact sizes and, optionally, an app's measured APK delta."""

import argparse
import io
import json
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--aar", type=Path, default=Path("lib/build/outputs/aar/lib-release.aar"))
parser.add_argument("--apk", type=Path, default=Path("demo/build/outputs/apk/release/demo-release-unsigned.apk"))
parser.add_argument("--baseline-apk", type=Path, help="The same app/build settings without the library or its dependencies")
args = parser.parse_args()

with zipfile.ZipFile(args.aar) as archive:
    classes = archive.read("classes.jar")
    with zipfile.ZipFile(io.BytesIO(classes)) as jar:
        class_bytes = sum(entry.file_size for entry in jar.infolist() if entry.filename.endswith(".class"))
    report = {
        "aar": str(args.aar),
        "aarBytes": args.aar.stat().st_size,
        "classesJarBytes": len(classes),
        "uncompressedClassBytes": class_bytes,
        "packagedNativeLibraries": [entry.filename for entry in archive.infolist() if entry.filename.endswith(".so")],
        "apk": str(args.apk),
        "apkBytes": args.apk.stat().st_size,
        "notes": ["AAR sizes exclude transitive dependencies.", "APK bytes are the whole demo, not the library's added app size.", "APK size is not installed size or Play download size."],
    }
if args.baseline_apk:
    report["baselineApk"] = str(args.baseline_apk)
    report["baselineApkBytes"] = args.baseline_apk.stat().st_size
    report["apkDeltaBytes"] = report["apkBytes"] - report["baselineApkBytes"]
    report["notes"].append("A delta is meaningful only with identical app sources, assets, shrinker settings, toolchain, and build variant except library integration.")
print(json.dumps(report, indent=2))
