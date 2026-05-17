# Mobile Development (Stage 5a)

Runs the Remi app on iOS Simulator and Android Emulator with live reload against the local Spring Boot backend.

## Prereqs

| Tool | Version | How |
|------|---------|-----|
| macOS | Sonoma+ | (host OS) |
| Xcode | 15+ | Mac App Store |
| Android Studio | Hedgehog+ | https://developer.android.com/studio |
| Java (Android Gradle) | JDK 21 (not 22+) | `brew install --cask temurin@21` — pinned in `frontend/android/gradle.properties` via `org.gradle.java.home` |
| Java (general) | 17+ | `brew install openjdk@17` |
| Node | 20+ | `brew install node@20` |
| Apache Maven | 3.9.x | `brew install maven` — used as system `mvn` (there is no `./mvnw` wrapper at repo root) |
| Postgres | 16 | `brew install postgresql@16` (backend dev profile expects `localhost:5432`) |
| Android SDK 34 + AVD | — | Android Studio → SDK Manager + Device Manager (create a Pixel 6 / API 34 emulator) |

> **Capacitor 8 uses Swift Package Manager (SPM), not CocoaPods.** You do not need to install or run `pod install`. iOS dependencies are resolved by Xcode through SPM the first time you open the project.

## First-time setup after `git clone`

```bash
cd frontend
npm install
npx cap sync
# Open the iOS project in Xcode once, accept automatic signing under your personal team:
open ios/App/App.xcodeproj
```

> Note: the path is `App.xcodeproj`, **not** `App.xcworkspace` — under Capacitor 8 / SPM there is no `.xcworkspace`.

## Daily workflow (3 terminals)

1. **Backend** (requires Postgres running on `localhost:5432`):
   ```bash
   cd /Users/georgesand/IdeaProjects/remi
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

2. **Frontend dev server** (binds to all interfaces so the simulator can reach it):
   ```bash
   cd frontend
   npm start -- --host 0.0.0.0
   ```

3. **Capacitor live reload run** (target UUID is required — Ionic CLI does not pick one automatically):

   First, list targets:
   ```bash
   npm run cap:ios:targets        # or cap:android:targets
   ```
   Pick a simulator UUID from the rightmost column (e.g. `8BDA3EBA-…` for iPhone 17). Then:
   ```bash
   npm run cap:ios -- --target=<sim-uuid>
   # or
   npm run cap:android -- --target=<avd-uuid>
   ```

   The script auto-detects your Wi-Fi LAN IP via `ipconfig getifaddr en0` and passes it as `--public-host`, so the simulator/emulator loads the dev server from your Mac's LAN address (required for live reload when the host has multiple network interfaces).

If you only need to push the latest web bundle into the native shells without live reload (e.g. before opening Xcode for a release build), use:

```bash
npm run cap:sync
```

## Smoke checks

After each `cap:ios` / `cap:android` run, verify:

1. Cold launch → login page shows
2. Login with existing test user works
3. Matchmaking + game flow works (open second client in web browser for the opponent)
4. Edit a string in `src/app/features/lobby/.../*.html`, save → simulator reflects change without rebuild
5. Background simulator 30s, restore → WebSocket reconnects, game state intact

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `CORS error` in WebView console | Confirm backend started with `-Dspring-boot.run.profiles=dev`; check that backend log shows the request's `Origin:` header |
| `App Transport Security policy requires the use of a secure connection` (iOS) | Confirm `frontend/ios/App/App/Info.plist` contains the `NSExceptionDomains/localhost` block |
| `Failed to load resource: net::ERR_CONNECTION_REFUSED` (Android) | Use `10.0.2.2`, not `localhost` — runtime resolver handles this; if you see this anyway, rebuild with `npm run cap:sync` |
| Live reload doesn't update | `npx cap sync --force` then restart simulator |
| Xcode fails to resolve SPM packages (e.g. `@capacitor/ios` missing) | In Xcode: **File → Packages → Reset Package Caches**, then **File → Packages → Resolve Package Versions**. (Capacitor 8 uses SPM, not CocoaPods — there is no `pod install` step.) |
| Xcode can't find `App.xcworkspace` | There isn't one. Open `frontend/ios/App/App.xcodeproj` instead. |
| Android Gradle: `Unsupported class file major version XX` | Your `JAVA_HOME` points to JDK 22+. The repo pins JDK 21 via `org.gradle.java.home` in `frontend/android/gradle.properties` — if you deleted that line, restore it (`/usr/libexec/java_home -v 21` gives the path). Manual `./gradlew` invocations honor the pinned value automatically. |
| Android emulator can't reach host | Confirm the emulator is using the standard AVD (not "Phone" image without bridge); `adb shell ping 10.0.2.2` should succeed |
| `The --target option is required` | Ionic CLI cannot auto-pick a simulator. Run `npm run cap:ios:targets` (or `cap:android:targets`) and pass `-- --target=<uuid>` |
| `Multiple network interfaces detected` | Your Mac has more than one active IPv4 interface (Wi-Fi + VPN/Ethernet). The npm script handles this by passing `--public-host=$(ipconfig getifaddr en0)`. If en0 isn't your active interface, override: `npm run cap:ios -- --target=<uuid> --public-host=<your-lan-ip>` |
| Backend exits with `Connection refused` to `localhost:5432` | Postgres isn't running. Start it: `brew services start postgresql@16` (or run the project's Docker compose stack if you use one). The dev profile requires a live Postgres on `localhost:5432`. |
