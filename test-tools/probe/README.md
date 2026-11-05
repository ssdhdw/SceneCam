# Independent receiver and runtime probes

This Android module (`:probe`, package `studio.scenecam.probe`) deliberately has no Telegram dependency and no provider visibility queries. It exercises application startup, Java camera/audio calls, and real OpenGL camera consumption. It does not install or implement an Xposed framework.

## Build and load the module

Build SceneCam and the probe with JDK 17:

```powershell
.\gradlew.bat :app:assembleDebug :probe:assembleDebug
```

On a non-root test emulator, an official JingMatrix/LSPatch CLI can embed the SceneCam APK into the probe:

```powershell
java -jar lspatch.jar -d -f -m app/build/outputs/apk/debug/app-debug.apk -o patched test-tools/probe/build/outputs/apk/debug/probe-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r patched/probe-debug-487-lspatched.apk
adb shell am force-stop studio.scenecam.probe
adb shell am start -W -n studio.scenecam.probe/.ProbeActivity
```

The output filename depends on the LSPatch version. `-d` enables run-as for retrieving artifacts. Supply a Java version compatible with the particular LSPatch CLI; it need not be the JDK used by Gradle. LSPatch and patched third-party APKs are not bundled. Never infer full system LSPosed compatibility from an embedded-module test.

Start a scene in SceneCam first so its automatic URI grants establish provider visibility. Startup Toasts must come from the module's framework-loaded hooks, not from the probe itself.

## Camera controls

- **Read Scene URI:** retrieve the published JPEG through ContentResolver.
- **Open Camera1:** TextureView preview and one-shot NV21 callback saved as `files/callback.jpg`.
- Long-press **Open Camera1** while open: buffer callbacks, autofocus, and JPEG takePicture saved as `files/camera1-photo.jpg`.
- **Open Camera2 YUV:** native ImageReader filled by the virtual ImageWriter; saves `files/camera2-callback.jpg` and checks capture metadata.
- Long-press **Open Camera2 YUV:** Executor open, SessionConfiguration, and JPEG image capture saved as `files/virtual-jpeg.jpg`.

For a no-hardware test, enable camera sensor privacy on a disposable emulator, inspect `adb shell dumpsys media.camera`, and restore the switch afterward. A successful module load is not evidence of actual image replacement; inspect saved pixels.

## Square-grid round-video regression

`studio.scenecam.GridFixtureProbe`, in the app's androidTest sources, creates a 720 x 1280 image source with 80 x 80 square cells and a control circle. It replaces the emulator's saved scene configuration. The current app Gradle instrumentation runner selects this fixture runner.

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w studio.scenecam.test/studio.scenecam.GridFixtureProbe
```

Then start the scene from SceneCam. `RoundGridActivity` exercises Camera1/Camera2 -> SurfaceTexture/OES -> square OpenGL crop. Supported extras are `camera2` (boolean), `width`, `height`, `angle` (integers), and `encode` (boolean). Encoding writes a real AVC MP4 and a decoded control PNG.

```sh
adb shell am start -W -n studio.scenecam.probe/.RoundGridActivity --ei width 640 --ei height 480 --ei angle 90 --ez encode true
```

`check_round_grid.py` requires Python and Pillow. It locates ADB through the ADB environment variable, PATH, ANDROID_HOME/ANDROID_SDK_ROOT, or the standard Windows SDK directory. It runs multiple sizes/orientations, retrieves artifacts with run-as, and measures white grid-line spacing with a 2.5% tolerance. It also checks the recorded/decoded video. The checked-in before-fix PNG is the baseline. The grid scene must already be running and the probe must contain the current module. This is an independent reproduction of the investigated Telegram graphics path, not a logged-in Telegram test.

## Audio controls

The receiver intentionally has no RECORD_AUDIO manifest permission.

- Long-press **Read Scene URI**: virtual AudioRecord PCM16 16 kHz, float/stereo 44.1 kHz, direct ByteBuffer 48 kHz, timestamp, and stopped-state checks. Saves `files/virtual-mic.pcm`.
- Long-press **Recreate Activity**: Java AudioTrack plays an 880 Hz tone. With a device-playback source enabled, record while it plays to inspect the mix.

App instrumentation `AudioFeatureProbe` covers file loop/gain/mute, HTTP input, and denied physical microphone. Its HTTP fixture expects `tone.wav` at `http://127.0.0.1:18765/tone.wav`, reachable through adb reverse. `MicFeatureProbe` separately tests actual microphone open/close and requires explicit permission plus a usable physical/emulated input. Select the corresponding testInstrumentationRunner before building/running either one. Restore permissions and privacy switches after tests.

## Other entry points

`FeatureProbe` and `GestureProbe` in app androidTest cover scene/editor behavior. `EarlyLoadProbe` and `FrameScaleProbe` are app_process entry points for early hook construction and bitmap scaling. EarlyLoadProbe needs a D8-converted Xposed API stub on its test classpath for interface resolution only; do not embed those stubs in the production module.

See [VALIDATION.md](../../VALIDATION.md) for recorded observations and limitations. Generated logs and most development media are intentionally ignored; only curated synthetic grid evidence is published.
