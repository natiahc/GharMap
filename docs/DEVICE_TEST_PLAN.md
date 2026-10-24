# Real-device acceptance plan

No real-device tests have been performed in the remote development environment.
Before calling this an alpha scanner, record model, Android version, ARCore version,
APK commit SHA, depth support, scan duration, reference measurements and failures.

## Required scenarios

- ARCore-supported non-ToF phone; depth-capable phone; unsupported AR device.
- Camera permission granted, denied and permanently denied; ARCore installation.
- Camera occupied by another app; background/foreground; incoming call; process death.
- Record a walkthrough, stop, inspect MP4 dataset and JSONL timestamps/poses.
- Pause recording; verify it stops and the UI no longer says it is recording.
- Low disk space and output write failures; cancellation and project deletion.
- Mark rectangle corners in both orders; compare each wall and area with laser/tape.
- Blank walls, mirrors, dim rooms, clutter, furniture mistaken for walls.
- Auto outline with three walls only: must fail instead of adding a missing wall.
- A convex room with all walls observed: review against a measured reference.
- L-shaped room: assisted mode may represent it; auto convex mode must not be
  accepted as a faithful representation of it.
- Add at least five floors, relaunch, rename, edit corners and export each floor.
- Confirm separately captured rooms are labelled independent and not auto-aligned.
- Cancel/export to local and cloud document providers; verify PDF/SVG dimensions.

## Accuracy reporting

Report absolute error and percentage error per wall, reference area vs estimated
area, tracking losses and retry rate. Set product acceptance thresholds based on
the measured distribution, not a single good scan. No accuracy percentage is claimed.
