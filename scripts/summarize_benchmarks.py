#!/usr/bin/env python3
"""Render the recorded measurements without adding performance claims."""

import json
import sys
from pathlib import Path

report = json.loads(Path(sys.argv[1]).read_text())
device = report["device"]
print(f"Measured on {device['manufacturer']} {device['model']}, Android API {device['api']}.")
print(f"Revision: `{report['revision']}`. Debug build, {report['warmups']} warmups and {report['iterations']} measured runs per row.")
print()
print("| Input | Engine | Output | Dimensions | Median ms | P90 ms | RGB PSNR dB¹ | Observed process PSS MiB² |")
print("| --- | --- | ---: | --- | ---: | ---: | ---: | ---: |")
for row in report["results"]:
    quality = row["quality"]
    psnr = "identical" if quality["identicalRgbOverWhite"] else f"{quality['rgbPsnrDbOverWhite']:.2f}"
    print(f"| {row['fixture']} | {row['engine']} | {row['outputBytes']:,} B | {row['outputWidth']} × {row['outputHeight']} | {row['medianMs']:.2f} | {row['p90Ms']:.2f} | {psnr} | {row['memory']['observedProcessPssMaxKiB'] / 1024:.1f} |")
print()
print("¹ Compared at each output's dimensions after compositing transparency over white. This does not measure detail lost by downscaling; see the methodology before comparing rows.")
print()
print("² Maximum observed whole-process PSS in a separate run, sampled about every 25 ms. This is neither per-operation allocation nor guaranteed peak memory.")
