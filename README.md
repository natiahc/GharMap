# GharMap

**Walk. Scan. Plan.**

An independent Android-first room-capture app with a shared C++17 geometry engine
and a native iPhone implementation. Basic plans are free. Projects can contain
multiple floors without a paid floor-count limit.

> **Development prototype, not a CubiCasa-equivalent release.** Assisted corner
> capture and experimental convex wall-plane outlines are implemented. Fully
> automatic whole-home reconstruction, inter-room alignment and measurement
> accuracy have not been validated. All output is labelled unverified.

## Implemented

| Capability | Android | iOS |
|---|---|---|
| Local properties, rooms and dynamic floor list | Yes | Yes |
| Camera and motion tracking without requiring LiDAR | ARCore-supported devices | ARKit world-tracking devices |
| Assisted room corners | Native ARCore floor hit tests | ARKit horizontal-plane raycasts |
| Experimental convex outline from wall planes | Yes | Yes |
| Local walkthrough recording + sampled pose/intrinsics metadata | ARCore MP4 dataset + JSONL | RGB MP4 + JSONL |
| Measured rectangle entry when AR is unavailable | Yes | Yes |
| Coordinate editing / draggable corners | Yes | Not yet |
| Dimensioned SVG | Yes | Yes |
| Multi-page floor PDF / project JSON export | Yes | Not yet |
| Automatic stitching, openings and Vastu reports | Not yet | Not yet |

Separate room scans are deliberately **not** overlaid as if they were aligned.
Adding floors does not establish geometric alignment between them. Phones still
have storage/performance limits; unsupported phones use measured entry.

## Android build

Requirements: JDK 17; Android SDK platform 35; NDK `27.2.12479018`; CMake `3.22.1`.
Gradle 8.9 is pinned through the included Gradle wrapper. Android Gradle Plugin is
8.7.3 and ARCore is pinned to 1.48.0 (including its matching native header).

1. Open this repository in Android Studio and install the SDK components above.
2. Set `sdk.dir` in an untracked `local.properties` file if Studio does not do so.
3. Run:

```sh
./gradlew :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android: 8.0/API 26. ABIs: arm64-v8a, armeabi-v7a, x86_64. AR is optional;
camera permission and Google Play Services for AR are checked at scan time.
Use the **Android and native checks** workflow under GitHub Actions for a build
artifact when its APK-build step succeeds. Debug APKs are development builds,
not Play Store releases.

## iPhone build

Requires macOS, Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```sh
brew install xcodegen
cd ios
xcodegen generate
open GharMap.xcodeproj
```

Select your signing team and physical iPhone, then Run. Minimum iOS: 16.0.
LiDAR is not required; check ARKit world-tracking support at runtime. The iOS CI
workflow builds for the simulator without signing. **Simulator compilation does
not test camera scanning.** Installing on an iPhone requires developer signing.

## Try the Android workflow

1. Create a property; add/select a floor.
2. Choose **Scan a room**, grant camera access, and install AR services if requested.
3. Move slowly across textured surfaces until tracking is ready.
4. Optionally start a local walkthrough recording.
5. Aim the centre at visible floor corners and mark them in perimeter order.
   Alternatively, try the experimental wall-plane outline after observing all walls.
6. Review, name and save the **unverified** room. Correct coordinates as needed.
7. Export its SVG or a multi-page floor report. Each room remains separately drawn.

On an unsupported phone, use **Enter measured room** and the coordinate editor.
No example house or synthetic scan is presented as a real captured property.

## Core verification

```sh
bash scripts/test_core.sh
# Or with CMake installed:
cmake -S core -B core/build-cmake
cmake --build core/build-cmake
ctest --test-dir core/build-cmake --output-on-failure
```

The script runs polygon, area, invalid geometry, unbounded-wall and SVG escaping
checks under AddressSanitizer/UBSan. Restricted/ptraced containers may need
`ASAN_OPTIONS=detect_leaks=0`; GitHub Linux CI runs with normal leak detection.
These tests establish geometry behaviour, not real-device measurement accuracy.

## Design and next work

- [Architecture and limitations](docs/ARCHITECTURE.md)
- [Real-device acceptance plan](docs/DEVICE_TEST_PLAN.md)
- [Third-party notices](third_party/NOTICE.md)

Priority: validate one-room capture on real phones; stabilize session recovery;
then implement shared-coordinate multi-room capture and verified floor stitching.
Android NDK is Android-specific. The C++ core is shared; iOS uses ARKit and an
Objective-C++ bridge compiled with Apple's toolchain.

No GharMap backend, subscription, ads or scan uploads are implemented. Home images
and recordings remain local unless the user explicitly exports them. Collect scans
only with occupants' consent and never commit private scans to this public repo.
