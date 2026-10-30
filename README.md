# CloneSpace

Parallel-space style app cloner built on the open-source BlackBox virtualization engine
(default: [ALEX5402/NewBlackbox](https://github.com/ALEX5402/NewBlackbox), Android 5–14+, Apache-2.0 header — verify the license of any fork you use).
No Binder/PackageManager hooking is written here.

## Build (cloud only)
1. Push this folder to a GitHub repo (the `.github` folder must be at the repo root).
2. **Actions → Build debug APK → Run workflow** (inputs: JDK, engine repo/ref/module, edition, extra SDK packages).
3. Download APKs from the run's **Artifacts**. Debug-signed; no keys.

The workflow clones the engine, drops the `cloneapp/` module into the engine's own Gradle project, and builds with the
**engine's own Gradle wrapper**, so the Android Gradle Plugin version always matches the engine.

## 32-bit / 64-bit
Two editions are built (arm64-v8a and armeabi-v7a, separate package ids). The app inspects each target's `lib/<abi>`
folders and shows "Unsupported: … use the 32-bit edition" when the app doesn't match the running edition.

## Permissions
The host declares the permissions clones usually request and asks for the runtime ones before the first launch.
`targetSdk` is 28 on purpose (legacy storage, normal permission behavior for virtualized apps).

## Code map
- `EngineBridge.java` — every engine call (name-based reflection, so small API differences between forks don't break compilation) + support checks.
- `MainActivity` clones list / launch / shortcut / delete · `PickAppActivity` clone an app · `LaunchProxyActivity` shortcut target.

## Limits
Split-APK-only apps, strong anti-tamper (many games/banking apps) and Google sign-in inside clones may fail; the engine's error is shown.
Replace the placeholder launcher icon before sharing.
