#!/usr/bin/env python3
"""Report artifact sizes and, optionally, an app's measured APK delta."""

import argparse
import io
import json
import re
import subprocess
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--aar", type=Path, default=Path("lib/build/outputs/aar/lib-release.aar"))
parser.add_argument("--apk", type=Path, default=Path("demo/build/outputs/apk/release/demo-release-unsigned.apk"))
parser.add_argument("--baseline-apk", type=Path, help="The same app/build settings without the library or its dependencies")
parser.add_argument("--dependency-only", action="store_true", help="Label the unshrunk minimal-app flavor comparison")
args = parser.parse_args()
if args.dependency_only and not args.baseline_apk:
    parser.error("--dependency-only requires --baseline-apk")
repo_root = Path(__file__).resolve().parents[1]
revision = subprocess.run(["git", "rev-parse", "HEAD"], cwd=repo_root, check=True, capture_output=True, text=True).stdout.strip()
if subprocess.run(["git", "status", "--porcelain"], cwd=repo_root, check=True, capture_output=True, text=True).stdout:
    revision += "-dirty"
version = re.search(r"^VERSION_NAME=(.+)$", (repo_root / "gradle.properties").read_text(), re.MULTILINE).group(1)

with zipfile.ZipFile(args.aar) as archive:
    classes = archive.read("classes.jar")
    with zipfile.ZipFile(io.BytesIO(classes)) as jar:
        class_bytes = sum(entry.file_size for entry in jar.infolist() if entry.filename.endswith(".class"))
    report = {
        "revision": revision,
        "libraryVersion": version,
        "measurementKind": "unshrunk-dependency-only-apk" if args.dependency_only else "artifact-and-whole-app",
        "aar": str(args.aar),
        "aarBytes": args.aar.stat().st_size,
        "classesJarBytes": len(classes),
        "uncompressedClassBytes": class_bytes,
        "packagedNativeLibraries": [entry.filename for entry in archive.infolist() if entry.filename.endswith(".so")],
        "apk": str(args.apk),
        "apkBytes": args.apk.stat().st_size,
        "notes": ["AAR sizes exclude transitive dependencies.", "APK bytes describe the whole supplied app, not an incremental cost by themselves.", "APK size is not installed size or Play download size."],
    }
if args.baseline_apk:
    report["baselineApk"] = str(args.baseline_apk)
    report["baselineApkBytes"] = args.baseline_apk.stat().st_size
    report["apkDeltaBytes"] = report["apkBytes"] - report["baselineApkBytes"]
    report["notes"].append("A delta is meaningful only with identical app sources, assets, shrinker settings, toolchain, and build variant except library integration.")
if args.dependency_only:
    report["shrinkingEnabled"] = False
    report["notes"].append("The two minimal-app release flavors share Activity source, assets, application ID, SDK settings, and build options; only withCompressor declares the library dependency.")
    report["notes"].append("The delta includes transitive dependencies without shrinking. Existing apps may already share them; this is not the cost after R8 optimization.")
print(json.dumps(report, indent=2))
