# Stage 5a — Mobile Native Shell + Local Dev Workflow

**Date:** 2026-05-17
**Status:** Design approved by user; pending plan
**Prior stage:** 4b (Frontend game UI complete, HEAD `a467fe4`)

---

## 1. Goal

Run the existing Angular 20 / Ionic 8 web app natively on **iOS Simulator** and **Android Emulator**, connected to the local Spring Boot backend, with **live reload** so daily iteration matches today's web dev experience.

This is the first of four mobile sub-stages:

| Sub-stage | Scope |
|-----------|-------|
| **5a** | Native shell, dev-local connectivity (THIS spec) |
| 5b | Native plugins for UX: push, secure JWT storage, deep links |
| 5c | Store-ready builds + CI signing + beta tracks |
| 5d | Store submission, listings, real branding |

---

## 2. Architecture

Capacitor 8 (already installed) wraps `frontend/www/` into two native shells:

- **iOS:** Xcode project at `frontend/ios/` (Swift + WebView host)
- **Android:** Gradle project at `frontend/android/` (Java/Kotlin + WebView host)

In **live reload mode**, the WebView loads the app from `ng serve --host 0.0.0.0` running on the developer's Mac, reachable from the simulator over the LAN IP. Code changes hot-reload exactly as on web today.

In **production mode** (still not used in 5a; verified only that the build succeeds), the WebView loads static assets bundled from `www/` via Capacitor's `capacitor://localhost` (iOS) or `http://localhost` (Android) custom scheme.

**Backend URL** is resolved at runtime by platform — no per-build environment proliferation:

| Platform | Resolved URL |
|----------|--------------|
| Web (dev or prod) | relative `/api`, `/ws` (proxy or reverse-proxy) |
| iOS simulator | `http://localhost:8080` |
| Android emulator | `http://10.0.2.2:8080` (emulator alias for host loopback) |

Same pattern for WebSocket (`/ws` STOMP endpoint).

---

## 3. Scope

### 3.1 In scope

- `capacitor.config.ts` customization (appId `ro.remi.app`, appName `Remi`, dev scheme)
- `npx cap add ios` + `npx cap add android`, both folders committed
- Runtime backend URL resolver (replaces `environment.apiUrl`/`wsUrl` usage)
- iOS Info.plist — ATS exception for `localhost`
- Android `network_security_config.xml` — cleartext for `localhost` + `10.0.2.2`
- Backend Spring Boot CORS extension for Capacitor WebView origins + dev live-reload LAN origin pattern
- Placeholder icon + splash via `@capacitor/assets`
- npm scripts: `cap:ios`, `cap:android`, `cap:sync`
- `docs/MOBILE_DEV.md` workflow guide

### 3.2 Explicitly out of scope

- Push notifications (FCM/APNs) → 5b
- Secure JWT storage upgrade (currently `localStorage`) → 5b
- Deep links / universal links → 5b
- Real branding (icon/splash design, app name localization) → 5d
- App Store / Play Store accounts, signing certs, provisioning profiles → 5c
- Physical device testing → optional later (5a targets simulators only)
- HTTPS / staging / production backend URL → 5c
- iPad / tablet layout adaptation → future (current UI is portrait-mobile only)

---

## 4. Components

### 4.1 `frontend/capacitor.config.ts`

```ts
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'ro.remi.app',
  appName: 'Remi',
  webDir: 'www',
  server: {
    androidScheme: 'http',  // allow cleartext to 10.0.2.2 during dev
    cleartext: true,         // dev only; fixed in 5c for production
  },
};

export default config;
```

### 4.2 Platform addition

Run **once** during plan execution:

```bash
cd frontend
npx cap add ios
npx cap add android
```

Generates `frontend/ios/` (Xcode workspace) and `frontend/android/` (Gradle project). Both folders are committed to git so the team doesn't re-generate on every clone.

### 4.3 Runtime URL resolver

**New files:**

- `frontend/src/app/core/config/api-url.tokens.ts`
  - `export const API_URL = new InjectionToken<string>('API_URL');`
  - `export const WS_URL = new InjectionToken<string>('WS_URL');`

- `frontend/src/app/core/config/api-url.provider.ts`
  - `provideApiUrls(): EnvironmentProviders` exports `makeEnvironmentProviders([{ provide: API_URL, useFactory: resolveApiUrl }, { provide: WS_URL, useFactory: resolveWsUrl }])`
  - `resolveApiUrl()` and `resolveWsUrl()` switch on `Capacitor.getPlatform()`:
    - `'ios'` → `http://localhost:8080/api` and `ws://localhost:8080/ws`
    - `'android'` → `http://10.0.2.2:8080/api` and `ws://10.0.2.2:8080/ws`
    - `'web'` → `/api` and `/ws` (relative; proxy handles dev, reverse proxy handles prod)

**Modified files:**
- Every service currently reading `environment.apiUrl` / `environment.wsUrl` — inject `API_URL` / `WS_URL` via `inject(API_URL)` instead. Likely: `AuthService`, `StompService`, any HTTP service in `core/` or `features/lobby/` or `features/game/`.
- `frontend/src/app/app.config.ts` (or `main.ts` if providers live there) — add `provideApiUrls()` to providers array.
- `frontend/src/environments/environment.ts` and `environment.prod.ts` — strip `apiUrl` and `wsUrl`; keep only `production: boolean`.

### 4.4 iOS Info.plist — ATS

**Modified:** `frontend/ios/App/App/Info.plist` — add:

```xml
<key>NSAppTransportSecurity</key>
<dict>
  <key>NSAllowsArbitraryLoads</key>
  <false/>
  <key>NSExceptionDomains</key>
  <dict>
    <key>localhost</key>
    <dict>
      <key>NSExceptionAllowsInsecureHTTPLoads</key>
      <true/>
    </dict>
  </dict>
</dict>
```

Capacitor's `ionic capacitor run ios --livereload --external` injects the LAN IP into `capacitor.config.ts`'s runtime `server.url`. The iOS WebView host already permits this load path; only the API/WS XHR calls go through ATS, and those target `localhost` covered by the exception above. If a future Capacitor version changes this behavior, the troubleshooting section documents adding a `NSAllowsLocalNetworking` key or per-LAN-IP exception.

### 4.5 Android network security

**New file:** `frontend/android/app/src/main/res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
  <domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">localhost</domain>
    <domain includeSubdomains="true">10.0.2.2</domain>
  </domain-config>
</network-security-config>
```

**Modified:** `frontend/android/app/src/main/AndroidManifest.xml` — on `<application>` element, add attribute:
`android:networkSecurityConfig="@xml/network_security_config"`

### 4.6 Backend CORS update

**Modified:** the existing Spring Boot CORS configuration class (currently likely in `backend/src/main/java/.../config/WebSecurityConfig.java` or a dedicated `CorsConfig`). Extend allowed origins for **dev profile**:

- `capacitor://localhost` (iOS Capacitor WebView in production mode)
- `http://localhost` (Android Capacitor WebView in production mode)
- Pattern `http://192.168.*:8100` and `http://10.*:8100` for live reload from any dev's LAN IP — use `setAllowedOriginPatterns(...)`, NOT `setAllowedOrigins(...)`

**Modified:** WebSocket STOMP endpoint registration — `registry.addEndpoint("/ws").setAllowedOriginPatterns(<same list>).withSockJS()`.

**Profile separation:** Apply the wildcard LAN patterns only when Spring profile `dev` is active. The `capacitor://localhost` + `http://localhost` entries are safe enough to live in `prod` too (they only match the Capacitor WebView, not arbitrary browsers).

### 4.7 Icons & splash placeholder

- **New:** `frontend/resources/icon.png` (1024×1024 PNG, single letter "R" centered on solid color background — generated, not designed)
- **New:** `frontend/resources/splash.png` (2732×2732 PNG, same minimal brand)

Run once during plan execution:

```bash
cd frontend
npx @capacitor/assets generate
```

This populates `ios/App/App/Assets.xcassets/AppIcon.appiconset/`, `android/app/src/main/res/mipmap-*/`, and the launch image assets. Commit the generated output.

### 4.8 npm scripts

**Modified:** `frontend/package.json` — add to `scripts`:

```json
"cap:ios": "ionic capacitor run ios --livereload --external",
"cap:android": "ionic capacitor run android --livereload --external",
"cap:sync": "ng build && npx cap sync"
```

(`ionic` CLI is already a transitive dep via `@ionic/cli`; if absent, the plan task adds it as devDep.)

### 4.9 Documentation

**New:** `docs/MOBILE_DEV.md`

Sections:

- **Prereqs:** macOS Sonoma+, Xcode ≥ 15, Android Studio ≥ Hedgehog, Java 17+, Node 20+, CocoaPods (`sudo gem install cocoapods`), iOS Simulator (bundled with Xcode), Android SDK 34 + an emulator AVD (e.g., Pixel 6 API 34)
- **First-time setup:** After `git clone`, run `npm install`, then `npx cap sync`, then in Xcode open `frontend/ios/App.xcworkspace` and accept automatic signing under your personal team
- **Daily workflow:** 3 terminals:
  1. Backend: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
  2. Frontend dev server: `cd frontend && npm start -- --host 0.0.0.0`
  3. Capacitor: `cd frontend && npm run cap:ios` (or `cap:android`)
- **Troubleshooting:**
  - "CORS error" → check backend log for `Origin: ...` and confirm dev profile is active
  - "ATS policy" iOS error → confirm Info.plist contains the `NSExceptionDomains/localhost` block
  - "ECONNREFUSED" on Android → use `10.0.2.2`, not `localhost`
  - "Live reload not updating" → `npx cap sync --force` and restart the simulator
  - "Pod install failed" → `sudo gem install cocoapods` then `cd ios/App && pod install`

**Modified:** `frontend/README.md` — add "Running on mobile" section linking to `docs/MOBILE_DEV.md`.

---

## 5. Data Flow

For a single HTTP login request:

1. User taps Login in iOS Simulator
2. `AuthService.login()` calls `this.http.post(\`${this.apiUrl}/auth/login\`, ...)` where `this.apiUrl = inject(API_URL)` resolves to `http://localhost:8080/api` (iOS branch)
3. WebView issues `POST http://localhost:8080/api/auth/login` over the simulator's host network
4. Spring Boot receives the request; CORS filter validates `Origin: capacitor://localhost` (production WebView origin) or `http://<lan>:8100` (live reload origin) against the dev profile's allow-list; passes
5. Response flows back; JWT stored in `localStorage` (5b will upgrade this to secure storage)

For WebSocket:

1. After login, `StompService.connect()` uses `wsUrl = inject(WS_URL)` → `ws://localhost:8080/ws`
2. SockJS handshake → STOMP CONNECT → backend `addEndpoint("/ws").setAllowedOriginPatterns(...)` validates and accepts
3. Bidirectional STOMP messages flow normally; identical to web behavior

---

## 6. Testing

### 6.1 Automated

**New unit tests** in `api-url.provider.spec.ts`:
- Stub `Capacitor.getPlatform()` returning each of `'web'`, `'ios'`, `'android'`
- Toggle `environment.production` true/false
- Assert returned URL is the expected one in all 6 combinations

**Regression gate:** Full suite (`npx ng test --watch=false --browsers=ChromeHeadless`) — must remain 129+ tests green; no behavior change for web mode.

### 6.2 Manual smoke (documented in `MOBILE_DEV.md`)

Per platform (iOS Simulator + Android Emulator), exercise:

1. **Cold launch:** app opens, shows login page
2. **Login:** existing test user authenticates against local backend
3. **Matchmaking:** queue, get matched with second client (run a second simulator or use web in browser as the opponent)
4. **Game flow:** draw, discard, etalat — confirm UI updates, opponent sees changes
5. **Live reload:** edit a string in a template, save, see hot-reload in simulator without rebuild
6. **Background/foreground:** push simulator app to background for 30s, restore, confirm WebSocket reconnects and game state syncs

### 6.3 Explicitly not tested in 5a

- Physical device flows
- Offline / airplane mode
- App backgrounded for >5 min (OS may aggressively suspend; 5b push notifications will handle this)
- Notification taps, deep link entry
- Production HTTPS endpoint

---

## 7. Risks

- **R1 — Capacitor 8 + Angular 20 build interaction.** Angular 20 ships Vite-based esbuild as default; Capacitor's `cap sync` reads from `webDir`, which still maps to the same `www/` output. Risk is low but unverified until first end-to-end run. *Mitigation:* gate the rest of the plan on a successful first `cap:ios` boot — if it fails, stop and fix before continuing.

- **R2 — WebSocket reconnection after OS suspend.** iOS/Android pause JavaScript timers in background; STOMP heartbeats may fail and trigger reconnect on resume. *Mitigation:* `StompService` already has exponential backoff reconnection (Stage 4a); manual smoke step 6 verifies this explicitly.

- **R3 — CORS for live reload LAN origin.** Dev's IP changes when they switch networks; hardcoded allow-list breaks. *Mitigation:* use `setAllowedOriginPatterns("http://192.168.*:8100", "http://10.*:8100")` instead of fixed origins. Dev profile only.

- **R4 — CocoaPods install fails on fresh machines.** Common Ruby version conflicts. *Mitigation:* `MOBILE_DEV.md` prereqs explicitly list the install command and the fallback (`brew install cocoapods` if the gem install fails).

- **R5 — Generated icons committed to git inflate the repo.** Capacitor generates ~30 PNGs across resolutions. *Mitigation:* accepted — they're small (<2 MB total), and regenerating on every clone is fragile.

---

## 8. Definition of Done

- [ ] `frontend/capacitor.config.ts` uses `ro.remi.app` / `Remi`
- [ ] `frontend/ios/` and `frontend/android/` exist in git, build cleanly
- [ ] Runtime URL resolver replaces `environment.apiUrl`/`wsUrl` everywhere; web mode behavior unchanged
- [ ] Backend CORS allows Capacitor WebView origins + dev LAN patterns
- [ ] iOS Info.plist contains ATS exception block for `localhost`
- [ ] Android network security config permits cleartext to `localhost` + `10.0.2.2`
- [ ] `npm run cap:ios` boots iOS Simulator and the app reaches login → game flow against local backend
- [ ] `npm run cap:android` does the same on Android Emulator
- [ ] All Karma tests (existing + new for URL resolver) pass
- [ ] `docs/MOBILE_DEV.md` exists; a dev unfamiliar with the project can boot mobile in <30 min following it
- [ ] `frontend/README.md` links to `MOBILE_DEV.md`

---

## 9. Open Questions

None. All decisions made during brainstorming. Future stages (5b/5c/5d) will revisit:
- HTTPS strategy (5c)
- Real branding (5d)
- Notification permission UX (5b)
- Secure storage migration path for existing JWTs (5b)
