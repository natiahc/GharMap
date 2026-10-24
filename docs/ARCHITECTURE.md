# Architecture and current limits

## Android-first, native core

The Activity layer handles screens, runtime permissions, ARCore installation,
Android lifecycle, private project storage and Storage Access Framework exports.
Java is intentionally a thin platform shell, not the reconstruction engine.

The Android NDK library owns the ARCore C session, OpenGL ES camera background,
capture controls, camera intrinsics/poses, vertical-plane observations and hit tests.
All capture entry points are serialized with a mutex. Rendering stops before pause
or destruction. The GL thread makes session updates and geometry queries.

The platform-independent C++17 core validates polygons, calculates area/perimeter,
intersects wall half-planes and exports dimensioned SVG. It has no JNI, Android,
ARCore or UI dependency. iOS uses SwiftUI, ARKit world tracking, an Objective-C++
bridge to that same core, and AVAssetWriter for local RGB walkthrough video.
ARKit world tracking does not require LiDAR on supported devices. iOS currently
has a smaller editing/export feature set than Android; see the README matrix.

## Honest capture modes

1. Assisted AR: user aims at visible floor corners and marks them in perimeter order.
2. Experimental auto outline: intersections of tracked vertical ARCore planes produce
   a **convex-room hypothesis**. Open, inconsistent or degenerate observations yield
   no outline. Furniture planes can still cause false boundaries; user review is
   mandatory and output remains unverified.
3. User-measured rectangle/coordinate editor: available without AR tracking.

This is not dense video reconstruction, learned semantic wall detection, a room
segmentation model, or CubiCasa-equivalent automation. The ARCore Depth API is
enabled when supported, but dense depth fusion is not implemented. Automatic
doors/windows, robust loop closure and non-convex automatic outlines are pending.

## Coordinates and floors

ARCore world space is right-handed, Y up, metres. Plans project X/Z into 2D.
The AR origin is arbitrary and must not be treated as geographic north. Every room
capture has an independent origin. The app exports separate room drawings, rather
than inventing alignment between independent scans. Shared-frame room capture,
anchors, floor stitching and staircase registration are pending.

Floors are a dynamic array with no subscription, scan count or fixed floor cap.
Device storage, memory and performance remain finite. This does not establish
scalability to arbitrarily large buildings.

## Capture data

ARCore records a local MP4 dataset containing camera video and AR session data.
A sibling JSONL file stores sampled tracking-ready camera poses (column-major
camera-to-world matrices), intrinsics, image dimensions and monotonic timestamps.
Recording stops on pause and must not be represented as uninterrupted afterward.
MP4 dataset replay/reprocessing is a future feature; JSONL is not a complete replay
format. Completed recordings survive cancellation only if explicit discard does
not succeed; project deletion removes all contents. Process death can leave orphan
recordings inside the project directory; no automatic recovery UI exists yet.

## Privacy

No account, analytics, ads, cloud reconstruction, location permission or GharMap
backend is present. Files reside in app-private storage. Android backup is disabled.
Users choose export destinations using the system picker; exported files leave app
control. ARCore/Google Play Services has its own runtime behaviour and terms.
Capture homes only with the occupants' consent. Do not commit real scans or location
data to this public repository.

## Roadmap

- Validate recording, lifecycle and measurements on real ARCore devices.
- Persist/recover in-progress room outlines safely across process death.
- Fuse depth observations and learn/reconstruct wall boundaries on non-depth phones.
- Anchored shared-coordinate multi-room capture and verified multi-floor stitching.
- Openings, irregular walls, user-confirmed north and metric/imperial display.
- Real-device testing and editing/export parity for the iOS ARKit adapter.
- Optional, practitioner-configurable Vastu overlays; no health/wealth guarantees.

Broad device support is a goal. Unsupported AR devices use measured entry, not
fabricated automatic scans. Never market current output as survey/construction grade.
