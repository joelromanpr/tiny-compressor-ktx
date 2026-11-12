Measured on Google sdk_gphone64_arm64, Android API 36.
Revision: `cf7b53fa3dd4341135673e91aef3ed898ade3f0e`. Debug build, 2 warmups and 5 measured runs per row.

| Input | Engine | Output | Dimensions | Median ms | P90 ms | RGB PSNR dB¹ | Observed process PSS MiB² |
| --- | --- | ---: | --- | ---: | ---: | ---: | ---: |
| earth-photo | platform-fixed | 188,643 B | 1280 × 1280 | 56.19 | 58.54 | 36.79 | 70.4 |
| earth-photo | library-fixed | 188,643 B | 1280 × 1280 | 57.48 | 57.67 | 36.79 | 118.5 |
| earth-photo | library-100kb | 91,932 B | 1280 × 1280 | 85.14 | 92.72 | 32.47 | 51.8 |
| earth-webp | platform-fixed | 142,210 B | 1280 × 1280 | 128.92 | 129.31 | 37.46 | 53.3 |
| earth-webp | library-fixed | 142,210 B | 1280 × 1280 | 132.47 | 196.01 | 37.46 | 53.4 |
| earth-webp | library-100kb | 89,564 B | 1280 × 1280 | 280.05 | 284.70 | 34.48 | 57.0 |
| screenshot | platform-fixed | 23,906 B | 720 × 1280 | 52.64 | 53.34 | identical | 45.8 |
| screenshot | library-fixed | 23,906 B | 720 × 1280 | 52.63 | 53.56 | identical | 46.0 |
| screenshot | library-100kb | 23,906 B | 720 × 1280 | 53.96 | 54.87 | identical | 46.0 |
| transparent | platform-fixed | 74,608 B | 1280 × 853 | 56.35 | 57.09 | identical | 46.6 |
| transparent | library-fixed | 74,608 B | 1280 × 853 | 58.23 | 60.53 | identical | 46.6 |
| transparent | library-100kb | 74,608 B | 1280 × 853 | 57.85 | 58.17 | identical | 46.7 |
| panorama | platform-fixed | 14,971 B | 1280 × 128 | 4.58 | 4.81 | 34.78 | 43.2 |
| panorama | library-fixed | 14,971 B | 1280 × 128 | 4.95 | 5.08 | 34.78 | 43.4 |
| panorama | library-100kb | 14,971 B | 1280 × 128 | 5.06 | 5.29 | 34.78 | 43.8 |
| rotated-photo | platform-fixed | 188,808 B | 1280 × 1280 | 62.24 | 64.55 | 36.80 | 49.4 |
| rotated-photo | library-fixed | 188,808 B | 1280 × 1280 | 64.75 | 66.07 | 36.80 | 50.2 |
| rotated-photo | library-100kb | 92,122 B | 1280 × 1280 | 93.12 | 94.97 | 32.46 | 51.5 |

¹ Compared at each output's dimensions after compositing transparency over white. This does not measure detail lost by downscaling; see the methodology before comparing rows.

² Maximum observed whole-process PSS in a separate run, sampled about every 25 ms. This is neither per-operation allocation nor guaranteed peak memory.
