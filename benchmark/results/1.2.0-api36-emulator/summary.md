Measured on Google sdk_gphone64_arm64, Android API 36.
Revision: `443a3ee28353f94f360291eed72964cbcc0e28d5`. Debug build, 2 warmups and 5 measured runs per row.

| Input | Engine | Output | Dimensions | Median ms | P90 ms | RGB PSNR dB¹ | Observed process PSS MiB² |
| --- | --- | ---: | --- | ---: | ---: | ---: | ---: |
| earth-photo | platform-fixed | 188,643 B | 1280 × 1280 | 78.15 | 117.25 | 36.79 | 76.7 |
| earth-photo | library-fixed | 188,643 B | 1280 × 1280 | 80.07 | 91.99 | 36.79 | 52.5 |
| earth-photo | library-100kb | 91,932 B | 1280 × 1280 | 127.64 | 130.12 | 32.47 | 56.9 |
| earth-webp | platform-fixed | 142,210 B | 1280 × 1280 | 179.41 | 202.42 | 37.46 | 58.7 |
| earth-webp | library-fixed | 142,210 B | 1280 × 1280 | 182.02 | 187.71 | 37.46 | 64.1 |
| earth-webp | library-100kb | 89,564 B | 1280 × 1280 | 423.01 | 672.39 | 34.48 | 61.1 |
| screenshot | platform-fixed | 23,906 B | 720 × 1280 | 80.07 | 88.05 | identical | 50.0 |
| screenshot | library-fixed | 23,906 B | 720 × 1280 | 66.30 | 79.76 | identical | 50.1 |
| screenshot | library-100kb | 23,906 B | 720 × 1280 | 67.20 | 70.27 | identical | 50.0 |
| transparent | platform-fixed | 74,608 B | 1280 × 853 | 78.84 | 83.80 | identical | 52.6 |
| transparent | library-fixed | 74,608 B | 1280 × 853 | 82.24 | 86.99 | identical | 50.8 |
| transparent | library-100kb | 74,608 B | 1280 × 853 | 76.26 | 81.05 | identical | 51.0 |
| panorama | platform-fixed | 14,971 B | 1280 × 128 | 4.78 | 7.78 | 34.78 | 46.5 |
| panorama | library-fixed | 14,971 B | 1280 × 128 | 10.43 | 11.89 | 34.78 | 47.1 |
| panorama | library-100kb | 14,971 B | 1280 × 128 | 6.64 | 8.54 | 34.78 | 46.7 |
| rotated-photo | platform-fixed | 188,808 B | 1280 × 1280 | 74.17 | 74.72 | 36.80 | 55.9 |
| rotated-photo | library-fixed | 188,808 B | 1280 × 1280 | 77.68 | 84.01 | 36.80 | 57.4 |
| rotated-photo | library-100kb | 92,122 B | 1280 × 1280 | 120.74 | 155.74 | 32.46 | 56.5 |

¹ Compared at each output's dimensions after compositing transparency over white. This does not measure detail lost by downscaling; see the methodology before comparing rows.

² Maximum observed whole-process PSS in a separate run, sampled about every 25 ms. This is neither per-operation allocation nor guaranteed peak memory.
