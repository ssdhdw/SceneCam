# Validation

This document distinguishes observed behavior from unsupported or untested claims. Build and runtime checks were performed during development; they are not a guarantee for every Android ROM or receiving application.

## Environment and build

- JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.10.1, compile SDK 36, target SDK 35, minimum SDK 28.
- `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleDebugAndroidTest`, and `:probe:assembleDebug` passed for the 0.9 work.
- Runtime: API 37 Google Play x86_64 Android emulator, JingMatrix LSPatch 1.2 build 487 debug.
- LSPatch embedded the separately built SceneCam module into the independent probe APK. This is a module-loading/hook test, not a physical system-wide LSPosed/LineageOS test.
- Physical Android 16/LineageOS and an authenticated Telegram round-video session were not tested.

Only curated synthetic grid artifacts are included in this public repository. Development-only raw device logs, unrelated screenshots, and third-party APKs remain excluded.

## 0.9: SurfaceTexture geometry regression

The public Telegram Android sources were inspected on November 5, 2026: [InstantCameraView](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java), [CameraSession](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/camera/CameraSession.java), [CameraController](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/camera/CameraController.java), and [Camera2Session](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/camera/Camera2Session.java). These links follow upstream master and may change.

The relevant path is Camera1 openRound or Camera2Session -> SurfaceTexture/OES -> oriented square crop in OpenGL -> MediaCodec. SceneCam's Camera1 implementation previously ignored setDisplayOrientation. Ordinary NV21/YUV tests did not exercise this consumer-side crop.

`GridFixtureProbe` creates a real 720 x 1280 PNG image with 80 x 80 square cells and a reference circle. The image travels through the normal image worker, scene compositor, JPEG provider, and FrameClient. `RoundGridActivity` receives actual OES buffers, applies the square texture-coordinate crop, and reads the rendered pixels. It has no Telegram dependency or package-specific hook.

| Case | Measured cell width x height | Height/width |
| --- | --- | --- |
| Camera1 640 x 480, 90 degrees, before fix | 44.5 x 79.0 px | 1.7753 |
| Camera1 640 x 480, 90 degrees, after fix | 44.5 x 44.5 px | 1.0000 |
| Same OES draw after AVC/MP4 encode/decode | 44.5 x 44.5 px | 1.0000 |
| Camera1 640 x 480, 0 / 180 degrees | 59.5 x 59.0 px | 0.9916 |
| Camera1 640 x 480, 270 degrees | 44.5 x 44.5 px | 1.0000 |
| Camera1 1280 x 720 / 640 x 360, 90 degrees | 44.5 x 44.0 px | 0.9888 |
| Camera2 640 x 480 | 59.5 x 59.0 px | 0.9916 |
| Camera2 1280 x 720 | 79.0 x 79.0 px | 1.0000 |

All corrected cases passed a 2.5% tolerance for pixel rounding. The actual recorded AVC video was decoded through MediaMetadataRetriever; this is more than a preview-only check. Camera sensor privacy was enabled, CameraService reported no active camera clients, and its event log contained no CONNECT. The collected runtime log had no FATAL EXCEPTION.

Artifacts: [input PNG](docs/runtime-tests/v9-square-grid.png), [before](docs/runtime-tests/v9-grid-before.png), [after](docs/runtime-tests/v9-round-grid-camera1-640x480-90.png), [decoded AVC frame](docs/runtime-tests/v9-round-grid-decoded.png), [MP4](docs/runtime-tests/v9-round-grid.mp4), [measurements](docs/runtime-tests/v9-grid-results.txt). Automation: [check_round_grid.py](test-tools/probe/check_round_grid.py).

This reproduces the relevant graphics behavior in an independent receiver. It does not assert end-to-end recording/upload in a logged-in Telegram client, or full emulation of native buffer-transform metadata.

## 0.8: audio observations

- A 150 ms 440 Hz WAV continued playing after 1.6 seconds, confirming file looping; RMS was approximately 8424.
- Gain at 25% reduced RMS to approximately 2107; disabling the source produced zero.
- An actual HTTP MediaExtractor source through a local test server produced RMS approximately 8504.
- Enabling a physical microphone without RECORD_AUDIO failed before creating AudioRecord.
- With permission granted separately, the physical microphone opened one recording configuration; disabling the source returned the count to zero.
- The receiving probe declares no RECORD_AUDIO permission. Virtual recording was checked with SceneCam's physical microphone permission denied and microphone sensor privacy enabled.
- Virtual PCM16 mono 16 kHz recording contained the 440 Hz file tone, with roughly one second of reads taking roughly one second.
- Builder-based 44.1 kHz stereo float and 48 kHz direct ByteBuffer reads, timestamp/state, and read-after-stop behavior passed. ByteBuffer position remained unchanged.
- Hooked 48 kHz AudioTrack playback at 880 Hz mixed with the 440 Hz source. DFT amplitudes in the recorded PCM were approximately 12000 at 440 Hz and 7999.5 at 880 Hz.

These tests cover Java AudioRecord/AudioTrack paths. Native recording/playback and MediaRecorder encoding were not validated as supported. Precise lip sync and true stereo mixing are not implemented.

## Earlier camera/editor observations

- Camera1 preview, normal/buffer callbacks, autofocus and JPEG takePicture, plus Camera2 YUV/JPEG ImageReader and capture results were exercised with camera sensor privacy enabled and no active hardware camera client.
- Camera1 callbacks were delivered to the opening thread's main Looper in the probe.
- Stopping the scene resulted in black output through existing virtual camera connections.
- Repeated Camera1/Camera2 open/close cycles were exercised without a fatal exception in the observed runs.
- Crop preservation, per-layer mode persistence, rotation, drag, and pinch were checked through instrumentation. Preview FPS and scene generation FPS are separate settings.
- Emulator cameras with different IDs were observed producing frames concurrently; duplicate layers for one camera shared the same frame connection. This is not a claim that every physical phone supports simultaneous cameras.
- Early hook construction before a main Looper exists was regression-tested; handler-dependent initialization is deferred.

## Reproduction tools

See [probe setup](test-tools/probe/README.md). Instrumentation runners are standalone development entry points rather than a conventional all-tests suite. Choose the runner deliberately, since some replace the emulator scene configuration or exercise a physical microphone with permission.

Pure Java NV21/YUV conversion checks can be run on Windows with `scripts/test-nv21.ps1` and JAVA_HOME set. Building/linting does not itself prove that LSPosed loaded the module or that a receiving app uses supported Java APIs.
