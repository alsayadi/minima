# Contributing to Minima OS

Thanks for your interest. Here's how to get involved.

## Before you start

- For anything non-trivial, **open an issue first** so we can discuss the approach.
- Check existing issues and PRs to avoid duplicate work.
- By contributing, you agree your contributions will be licensed under the MIT License (same as the project).

## Development setup

Requirements:

- Android Studio Hedgehog (2023.1) or newer
- JDK 17+
- Android SDK (API 34 target)
- An Android device or emulator running API 26+

Build:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

Minima is a multi-module Gradle project. When adding a feature, pick the right module:

- **`capability/`** — new action handlers (e.g. a new intent like "Spotify control")
- **`agent/`** — changes to intent classification, planning, routing
- **`data/`** — memory, context, proactive engine changes
- **`model/`** — LLM provider changes
- **`ui/`** — Compose screens, view models
- **`core/`** — shared data classes only
- **`app/`** — DI wiring, manifest, activity entry points

## Adding a new intent

1. Add the enum value to `core/.../Intent.kt::IntentType`
2. Create a capability class in `capability/.../yourname/YourCapability.kt` implementing `CapabilityProvider`
3. Register it in `capability/.../di/CapabilityModule.kt`
4. Add the route in `agent/.../planner/DeterministicPlanner.kt`
5. Add fallback patterns in `agent/.../classifier/DeterministicClassifier.kt`
6. Add LLM examples in `model/.../CloudModelProvider.kt` system prompt
7. Build, install, test with a real command

## Code style

- **Kotlin** with standard conventions
- **Jetpack Compose** for all UI
- **Hilt** for DI — no manual singletons
- **Coroutines + Flow** for async — no RxJava, no callbacks
- **Room** for persistence

Keep changes focused. Don't refactor unrelated code in the same PR.

## Commit messages

Use clear, imperative subject lines under 72 chars. Examples:

- `Add FlashlightCapability with torch mode toggle`
- `Fix command bar hidden when proactive cards overflow`
- `Voice: retry listening on "didn't catch that"`

Include a body only if the change needs context beyond the diff.

## Pull requests

- One logical change per PR
- Describe what and why in the body
- Reference related issues
- Include a screenshot/GIF if the change is visible
- Test on a real device before opening

## Testing

Right now Minima has no unit test coverage (it's on the roadmap). For now, manually test:

1. Install the APK on a real device
2. Try a few commands via text and voice
3. Verify the intent is classified correctly (`adb logcat -s TaskExecutor`)
4. Verify the capability executes
5. Verify nothing else regressed

If you add unit tests, put them under `moduleName/src/test/` following JUnit conventions.

## Questions?

Open a GitHub Discussion or file an issue. Thanks for helping build Minima.
