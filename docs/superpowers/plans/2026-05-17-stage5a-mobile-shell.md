# Stage 5a — Mobile Native Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the existing Angular 20 / Ionic 8 web app natively on iOS Simulator and Android Emulator, connected to the local Spring Boot backend, with live reload.

**Architecture:** Capacitor 8 wraps `frontend/www/` into native iOS (Xcode) and Android (Gradle) shells. Live reload loads the WebView from `ng serve --host 0.0.0.0` over the dev's LAN IP. Backend URL resolves at runtime per platform via DI tokens (web → relative, iOS → `localhost:8080`, Android → `10.0.2.2:8080`). Backend gains a dev-profile CORS configuration for Capacitor WebView and live-reload LAN origins.

**Tech Stack:** Capacitor 8.3.4 (already present), Angular 20, Ionic 8, Spring Boot 3.3.5, Xcode 15+, Android Studio Hedgehog+, JDK 17+, CocoaPods.

**Repo layout notes:**
- Backend source root: `src/main/java/com/remi/` (NOT under `backend/`)
- Frontend source root: `frontend/src/`
- Spec reference: `docs/superpowers/specs/2026-05-17-stage5a-mobile-shell-design.md`

---

## File Structure

**Files created:**
- `frontend/src/app/core/config/api-url.tokens.ts` — `InjectionToken<string>` definitions
- `frontend/src/app/core/config/api-url.provider.ts` — runtime URL resolver
- `frontend/src/app/core/config/api-url.provider.spec.ts` — unit tests for resolver
- `frontend/resources/icon.png` — 1024×1024 placeholder
- `frontend/resources/splash.png` — 2732×2732 placeholder
- `frontend/ios/` — Capacitor iOS scaffold (one-shot, generated)
- `frontend/android/` — Capacitor Android scaffold (one-shot, generated)
- `frontend/android/app/src/main/res/xml/network_security_config.xml` — cleartext exceptions
- `src/main/java/com/remi/config/CorsConfig.java` — Spring `CorsConfigurationSource` bean
- `src/test/java/com/remi/config/CorsConfigTest.java` — Spring Boot CORS integration test
- `docs/MOBILE_DEV.md` — dev workflow guide

**Files modified:**
- `frontend/capacitor.config.ts` — appId, appName, server config
- `frontend/package.json` — add devDeps + npm scripts
- `frontend/src/app/app.config.ts` — wire `provideApiUrls()`
- `frontend/src/environments/environment.ts` — strip `apiUrl`/`wsUrl`
- `frontend/src/environments/environment.prod.ts` — strip `apiUrl`/`wsUrl`
- `frontend/src/app/core/api/auth.api.ts` — inject `API_URL`
- `frontend/src/app/core/api/auth.api.spec.ts` — provide `API_URL` in TestBed
- `frontend/src/app/core/api/lobby.api.ts` — inject `API_URL`
- `frontend/src/app/core/api/lobby.api.spec.ts` — provide `API_URL` in TestBed
- `frontend/src/app/core/api/matchmaking.api.ts` — inject `API_URL`
- `frontend/src/app/core/api/matchmaking.api.spec.ts` — provide `API_URL` in TestBed
- `frontend/src/app/core/ws/stomp.service.ts` — inject `WS_URL`
- `frontend/ios/App/App/Info.plist` — ATS exception for `localhost`
- `frontend/android/app/src/main/AndroidManifest.xml` — reference `network_security_config`
- `src/main/java/com/remi/config/SecurityConfig.java` — wire CORS bean into security chain
- `frontend/README.md` — link to `MOBILE_DEV.md`

---

## Phase A — Capacitor config + platform addition

### Task A1: Customize `capacitor.config.ts`

**Files:**
- Modify: `frontend/capacitor.config.ts`

- [ ] **Step 1: Replace the file content**

Write `frontend/capacitor.config.ts`:

```ts
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'ro.remi.app',
  appName: 'Remi',
  webDir: 'www',
  server: {
    androidScheme: 'http',
    cleartext: true,
  },
};

export default config;
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.app.json`
Expected: exit 0, no errors mentioning `capacitor.config.ts`.

- [ ] **Step 3: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/capacitor.config.ts
git commit -m "chore(capacitor): set appId ro.remi.app, appName Remi, dev cleartext"
```

---

### Task A2: Install `@capacitor/assets` and `@ionic/cli` as devDeps

**Files:**
- Modify: `frontend/package.json`, `frontend/package-lock.json`

- [ ] **Step 1: Install both packages**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npm install --save-dev @capacitor/assets @ionic/cli
```

- [ ] **Step 2: Verify versions**

Run: `cd frontend && grep -E '"@capacitor/assets"|"@ionic/cli"' package.json`
Expected: both packages appear under `devDependencies` with version strings.

- [ ] **Step 3: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/package.json frontend/package-lock.json
git commit -m "chore(frontend): add @capacitor/assets and @ionic/cli devDeps"
```

---

### Task A3: Generate placeholder icon and splash

**Files:**
- Create: `frontend/resources/icon.png`, `frontend/resources/splash.png`

- [ ] **Step 1: Generate placeholder PNGs**

Use ImageMagick (already on macOS via Homebrew; if absent, run `brew install imagemagick`):

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
mkdir -p resources
magick -size 1024x1024 xc:'#1a4a7e' -gravity center -pointsize 600 -fill white -font Helvetica-Bold -draw "text 0,0 'R'" resources/icon.png
magick -size 2732x2732 xc:'#1a4a7e' -gravity center -pointsize 1200 -fill white -font Helvetica-Bold -draw "text 0,0 'R'" resources/splash.png
```

- [ ] **Step 2: Verify PNGs exist with correct dimensions**

Run: `cd frontend && file resources/icon.png resources/splash.png`
Expected output contains: `icon.png: PNG image data, 1024 x 1024` and `splash.png: PNG image data, 2732 x 2732`.

- [ ] **Step 3: Commit resources (asset generation runs after platforms exist)**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/resources/icon.png frontend/resources/splash.png
git commit -m "chore(branding): add placeholder icon + splash PNGs"
```

---

### Task A4: Add iOS platform

**Files:**
- Create: `frontend/ios/` (Capacitor scaffold)

- [ ] **Step 1: Build web bundle (required before `cap add`)**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npm run build
```

Expected: exits 0, `www/` populated.

- [ ] **Step 2: Add iOS platform**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx cap add ios
```

Expected: Capacitor prints "✔ Adding iOS platform ... in 12.34s" and runs `pod install` automatically. If `pod install` fails with Ruby errors, run `sudo gem install cocoapods` then retry `cd ios/App && pod install`.

- [ ] **Step 3: Verify Xcode workspace exists**

Run: `ls frontend/ios/App/App.xcworkspace`
Expected: directory exists.

- [ ] **Step 4: Commit the iOS scaffold**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/ios
git commit -m "chore(capacitor): add iOS native platform scaffold"
```

---

### Task A5: Add Android platform

**Files:**
- Create: `frontend/android/` (Capacitor scaffold)

- [ ] **Step 1: Add Android platform**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx cap add android
```

Expected: Capacitor prints "✔ Adding Android platform ... in X.XXs". If it fails with "Android SDK not found", set `ANDROID_HOME` env var (typically `~/Library/Android/sdk` after installing Android Studio).

- [ ] **Step 2: Verify Gradle wrapper exists**

Run: `ls frontend/android/gradlew frontend/android/app/build.gradle`
Expected: both files exist.

- [ ] **Step 3: Commit the Android scaffold**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/android
git commit -m "chore(capacitor): add Android native platform scaffold"
```

---

### Task A6: Generate icons + splash into native projects

**Files:**
- Modify: `frontend/ios/App/App/Assets.xcassets/AppIcon.appiconset/` (generated)
- Modify: `frontend/android/app/src/main/res/mipmap-*/` (generated)

- [ ] **Step 1: Run asset generator**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx @capacitor/assets generate
```

Expected output ends with: `✔ Generating Android assets ... done.` and `✔ Generating iOS assets ... done.`.

- [ ] **Step 2: Verify generated icons exist on both platforms**

Run:
```bash
ls frontend/ios/App/App/Assets.xcassets/AppIcon.appiconset | head -5
ls frontend/android/app/src/main/res/mipmap-hdpi | head -5
```
Expected: PNG files present in both directories.

- [ ] **Step 3: Commit generated assets**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/ios/App/App/Assets.xcassets frontend/android/app/src/main/res
git commit -m "chore(branding): generate native icons + splash from placeholder PNGs"
```

---

## Phase B — Runtime URL resolver (TDD)

### Task B1: Create DI tokens

**Files:**
- Create: `frontend/src/app/core/config/api-url.tokens.ts`

- [ ] **Step 1: Write the tokens file**

```ts
import { InjectionToken } from '@angular/core';

export const API_URL = new InjectionToken<string>('API_URL');
export const WS_URL = new InjectionToken<string>('WS_URL');
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.app.json`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/src/app/core/config/api-url.tokens.ts
git commit -m "feat(core): API_URL and WS_URL injection tokens"
```

---

### Task B2: Write failing tests for `api-url.provider`

**Files:**
- Create: `frontend/src/app/core/config/api-url.provider.spec.ts`

- [ ] **Step 1: Write the spec**

```ts
import { Capacitor } from '@capacitor/core';
import { resolveApiUrl, resolveWsUrl } from './api-url.provider';

describe('api-url.provider', () => {
  let getPlatformSpy: jasmine.Spy;

  beforeEach(() => {
    getPlatformSpy = spyOn(Capacitor, 'getPlatform');
  });

  describe('resolveApiUrl()', () => {
    it('returns "/api" for web platform', () => {
      getPlatformSpy.and.returnValue('web');
      expect(resolveApiUrl()).toBe('/api');
    });

    it('returns "http://localhost:8080/api" for iOS platform', () => {
      getPlatformSpy.and.returnValue('ios');
      expect(resolveApiUrl()).toBe('http://localhost:8080/api');
    });

    it('returns "http://10.0.2.2:8080/api" for Android platform', () => {
      getPlatformSpy.and.returnValue('android');
      expect(resolveApiUrl()).toBe('http://10.0.2.2:8080/api');
    });
  });

  describe('resolveWsUrl()', () => {
    it('returns "/ws" for web platform', () => {
      getPlatformSpy.and.returnValue('web');
      expect(resolveWsUrl()).toBe('/ws');
    });

    it('returns "http://localhost:8080/ws" for iOS platform', () => {
      getPlatformSpy.and.returnValue('ios');
      expect(resolveWsUrl()).toBe('http://localhost:8080/ws');
    });

    it('returns "http://10.0.2.2:8080/ws" for Android platform', () => {
      getPlatformSpy.and.returnValue('android');
      expect(resolveWsUrl()).toBe('http://10.0.2.2:8080/ws');
    });
  });
});
```

**Note on WS URL scheme:** the resolver returns `http://...` URLs for mobile because `StompService` builds the SockJS endpoint from the base URL by appending `/ws`; SockJS handles ws/wss upgrade internally. This matches today's behavior (`environment.wsUrl = '/ws'`).

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng test --watch=false --browsers=ChromeHeadless --include='**/api-url.provider.spec.ts'
```

Expected: FAIL with module-not-found error for `./api-url.provider`.

---

### Task B3: Implement resolver to make tests pass

**Files:**
- Create: `frontend/src/app/core/config/api-url.provider.ts`

- [ ] **Step 1: Write the resolver**

```ts
import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { API_URL, WS_URL } from './api-url.tokens';

export function resolveApiUrl(): string {
  switch (Capacitor.getPlatform()) {
    case 'ios': return 'http://localhost:8080/api';
    case 'android': return 'http://10.0.2.2:8080/api';
    default: return '/api';
  }
}

export function resolveWsUrl(): string {
  switch (Capacitor.getPlatform()) {
    case 'ios': return 'http://localhost:8080/ws';
    case 'android': return 'http://10.0.2.2:8080/ws';
    default: return '/ws';
  }
}

export function provideApiUrls(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: API_URL, useFactory: resolveApiUrl },
    { provide: WS_URL, useFactory: resolveWsUrl },
  ]);
}
```

- [ ] **Step 2: Run tests to verify they pass**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng test --watch=false --browsers=ChromeHeadless --include='**/api-url.provider.spec.ts'
```

Expected: 6/6 PASS.

- [ ] **Step 3: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/src/app/core/config/api-url.provider.ts frontend/src/app/core/config/api-url.provider.spec.ts
git commit -m "feat(core): runtime API/WS URL resolver per Capacitor platform"
```

---

### Task B4: Wire `provideApiUrls()` into `app.config.ts`

**Files:**
- Modify: `frontend/src/app/app.config.ts`

- [ ] **Step 1: Read the file to locate the providers array**

Run: `cat frontend/src/app/app.config.ts`

- [ ] **Step 2: Add the import**

At the top of `frontend/src/app/app.config.ts`, alongside other imports from `./core/...`, add:

```ts
import { provideApiUrls } from './core/config/api-url.provider';
```

- [ ] **Step 3: Add to providers array**

Inside the `providers: [...]` array (after the existing entries, before the closing `]`), add:

```ts
    provideApiUrls(),
```

- [ ] **Step 4: Build to verify**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng build --configuration=development
```

Expected: exit 0, no new errors (warnings about unused imports OK).

- [ ] **Step 5: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/src/app/app.config.ts
git commit -m "feat(core): wire provideApiUrls into app bootstrap"
```

---

## Phase C — Migrate consumers from `environment.*` to DI tokens

### Task C1: Migrate `auth.api.ts`

**Files:**
- Modify: `frontend/src/app/core/api/auth.api.ts`
- Modify: `frontend/src/app/core/api/auth.api.spec.ts`

- [ ] **Step 1: Read current file**

```bash
cat frontend/src/app/core/api/auth.api.ts frontend/src/app/core/api/auth.api.spec.ts
```

- [ ] **Step 2: Modify `auth.api.ts`**

Replace the import `import { environment } from '../../../environments/environment';` with:

```ts
import { inject } from '@angular/core';
import { API_URL } from '../config/api-url.tokens';
```

Inside the service class, replace `private readonly baseUrl = environment.apiUrl;` (or however it's stored — likely line 10 area) with:

```ts
  private readonly baseUrl = inject(API_URL);
```

Leave every use of `this.baseUrl` (or equivalent) untouched.

- [ ] **Step 3: Modify `auth.api.spec.ts`**

Add the import:

```ts
import { API_URL } from '../config/api-url.tokens';
```

In the `TestBed.configureTestingModule({ providers: [...] })` call, add the provider entry:

```ts
        { provide: API_URL, useValue: '/api' },
```

Remove the now-unused `import { environment } from '../../../environments/environment';` line.

- [ ] **Step 4: Run tests**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng test --watch=false --browsers=ChromeHeadless --include='**/auth.api.spec.ts'
```

Expected: all tests in that file still PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/src/app/core/api/auth.api.ts frontend/src/app/core/api/auth.api.spec.ts
git commit -m "refactor(auth): inject API_URL token instead of environment.apiUrl"
```

---

### Task C2: Migrate `lobby.api.ts`

**Files:**
- Modify: `frontend/src/app/core/api/lobby.api.ts`
- Modify: `frontend/src/app/core/api/lobby.api.spec.ts`

- [ ] **Step 1: Read current file**

```bash
cat frontend/src/app/core/api/lobby.api.ts frontend/src/app/core/api/lobby.api.spec.ts
```

- [ ] **Step 2: Modify `lobby.api.ts`**

Replace the line `import { environment } from '../../../environments/environment';` with these two imports (preserve all other imports):

```ts
import { inject } from '@angular/core';
import { API_URL } from '../config/api-url.tokens';
```

Inside the service class, replace the line that reads `private readonly baseUrl = environment.apiUrl;` (line ~17 per current state) with:

```ts
  private readonly baseUrl = inject(API_URL);
```

Leave every `this.baseUrl` reference untouched — only the assignment changes.

- [ ] **Step 3: Modify `lobby.api.spec.ts`**

Add the import near the top (alongside existing imports):

```ts
import { API_URL } from '../config/api-url.tokens';
```

Remove the existing line `import { environment } from '../../../environments/environment';`.

In `TestBed.configureTestingModule({ providers: [...] })`, add the provider entry:

```ts
        { provide: API_URL, useValue: '/api' },
```

- [ ] **Step 4: Run tests**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng test --watch=false --browsers=ChromeHeadless --include='**/lobby.api.spec.ts'
```

Expected: all tests still PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/src/app/core/api/lobby.api.ts frontend/src/app/core/api/lobby.api.spec.ts
git commit -m "refactor(lobby): inject API_URL token instead of environment.apiUrl"
```

---

### Task C3: Migrate `matchmaking.api.ts`

**Files:**
- Modify: `frontend/src/app/core/api/matchmaking.api.ts`
- Modify: `frontend/src/app/core/api/matchmaking.api.spec.ts`

- [ ] **Step 1: Read current file**

```bash
cat frontend/src/app/core/api/matchmaking.api.ts frontend/src/app/core/api/matchmaking.api.spec.ts
```

- [ ] **Step 2: Modify `matchmaking.api.ts`**

Replace the line `import { environment } from '../../../environments/environment';` with:

```ts
import { inject } from '@angular/core';
import { API_URL } from '../config/api-url.tokens';
```

Inside the service class, replace the line that reads `private readonly baseUrl = environment.apiUrl;` (line ~21 per current state) with:

```ts
  private readonly baseUrl = inject(API_URL);
```

Leave every `this.baseUrl` reference untouched.

- [ ] **Step 3: Modify `matchmaking.api.spec.ts`**

Add the import near the top:

```ts
import { API_URL } from '../config/api-url.tokens';
```

Remove the existing line `import { environment } from '../../../environments/environment';`.

In `TestBed.configureTestingModule({ providers: [...] })`, add the provider entry:

```ts
        { provide: API_URL, useValue: '/api' },
```

- [ ] **Step 4: Run tests**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng test --watch=false --browsers=ChromeHeadless --include='**/matchmaking.api.spec.ts'
```

Expected: all tests still PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/src/app/core/api/matchmaking.api.ts frontend/src/app/core/api/matchmaking.api.spec.ts
git commit -m "refactor(matchmaking): inject API_URL token instead of environment.apiUrl"
```

---

### Task C4: Migrate `stomp.service.ts` and clean environment files

**Files:**
- Modify: `frontend/src/app/core/ws/stomp.service.ts`
- Modify: `frontend/src/environments/environment.ts`
- Modify: `frontend/src/environments/environment.prod.ts`

- [ ] **Step 1: Read current StompService**

```bash
cat frontend/src/app/core/ws/stomp.service.ts | head -50
```

- [ ] **Step 2: Modify `stomp.service.ts`**

Replace `import { environment } from '../../../environments/environment';` with:

```ts
import { inject } from '@angular/core';
import { WS_URL } from '../config/api-url.tokens';
```

Replace `private readonly url = environment.wsUrl;` (likely around line 32) with:

```ts
  private readonly url = inject(WS_URL);
```

If a `stomp.service.spec.ts` file exists alongside the service, add the same import (`import { WS_URL } from '../config/api-url.tokens';`) and add the provider `{ provide: WS_URL, useValue: '/ws' },` to its TestBed `providers` array. If no spec file exists, skip this — Step 4 below will surface any missing provider via the full test run.

- [ ] **Step 3: Strip URL fields from environment files**

Replace `frontend/src/environments/environment.ts` content with:

```ts
export const environment = {
  production: false,
};
```

Replace `frontend/src/environments/environment.prod.ts` content with:

```ts
export const environment = {
  production: true,
};
```

- [ ] **Step 4: Run full test suite to catch any lingering uses**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng test --watch=false --browsers=ChromeHeadless
```

Expected: 129+ tests PASS, 0 FAIL. If anything fails with "Cannot read properties of undefined (reading 'apiUrl')" or similar, you missed a consumer — grep `environment.apiUrl\|environment.wsUrl` under `src/` and fix.

- [ ] **Step 5: Verify build**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng build --configuration=development
```

Expected: exit 0.

- [ ] **Step 6: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/src/app/core/ws/stomp.service.ts frontend/src/environments/environment.ts frontend/src/environments/environment.prod.ts
git commit -m "refactor(ws): inject WS_URL token; strip url fields from environment.*"
```

---

## Phase D — Native platform configs

### Task D1: iOS Info.plist ATS exception

**Files:**
- Modify: `frontend/ios/App/App/Info.plist`

- [ ] **Step 1: Read the current Info.plist**

```bash
cat frontend/ios/App/App/Info.plist
```

- [ ] **Step 2: Add the ATS block**

Edit `frontend/ios/App/App/Info.plist`. Inside the outermost `<dict>` element (the only one at root level), before the closing `</dict>`, insert:

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

(Indent with tabs to match the existing file style.)

- [ ] **Step 3: Verify the file is still valid plist XML**

Run: `plutil -lint frontend/ios/App/App/Info.plist`
Expected: `frontend/ios/App/App/Info.plist: OK`.

- [ ] **Step 4: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/ios/App/App/Info.plist
git commit -m "feat(ios): ATS exception allowing HTTP to localhost in dev"
```

---

### Task D2: Android network security config

**Files:**
- Create: `frontend/android/app/src/main/res/xml/network_security_config.xml`
- Modify: `frontend/android/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create the network security config**

Create `frontend/android/app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

- [ ] **Step 2: Read current AndroidManifest**

```bash
cat frontend/android/app/src/main/AndroidManifest.xml
```

- [ ] **Step 3: Reference the config in the manifest**

In `frontend/android/app/src/main/AndroidManifest.xml`, on the `<application>` element (typically near the top), add the attribute `android:networkSecurityConfig="@xml/network_security_config"`. Example: if the element currently reads

```xml
<application
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    ...
    android:theme="@style/AppTheme">
```

change it to:

```xml
<application
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:networkSecurityConfig="@xml/network_security_config"
    ...
    android:theme="@style/AppTheme">
```

- [ ] **Step 4: Verify Android build still works**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx cap sync android
cd android && ./gradlew assembleDebug --offline 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. (If offline mode fails because of unresolved deps, drop `--offline` and accept the longer run.)

- [ ] **Step 5: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/android/app/src/main/res/xml/network_security_config.xml frontend/android/app/src/main/AndroidManifest.xml
git commit -m "feat(android): permit cleartext to localhost + 10.0.2.2 for dev"
```

---

## Phase E — Backend CORS

### Task E1: Add CORS configuration with integration test

**Files:**
- Create: `src/main/java/com/remi/config/CorsConfig.java`
- Create: `src/test/java/com/remi/config/CorsConfigTest.java`
- Modify: `src/main/java/com/remi/config/SecurityConfig.java`

- [ ] **Step 1: Write the failing CORS test first**

Create `src/test/java/com/remi/config/CorsConfigTest.java`:

```java
package com.remi.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.annotation.PostConstruct;
import org.springframework.web.filter.CorsFilter;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
class CorsConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @PostConstruct
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void capacitorIosOriginAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "capacitor://localhost")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "capacitor://localhost"));
    }

    @Test
    void capacitorAndroidOriginAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost"));
    }

    @Test
    void devLanLiveReloadOriginAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://192.168.1.42:8100")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.1.42:8100"));
    }

    @Test
    void unknownOriginRejected() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/georgesand/IdeaProjects/remi
./mvnw test -Dtest=CorsConfigTest 2>&1 | tail -20
```

Expected: all 4 tests FAIL (no CORS bean yet — likely "Access-Control-Allow-Origin" header missing).

- [ ] **Step 3: Create the CORS config**

Create `src/main/java/com/remi/config/CorsConfig.java`:

```java
package com.remi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    @Profile("!prod")
    UrlBasedCorsConfigurationSource devCorsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "capacitor://localhost",
            "http://localhost",
            "http://localhost:*",
            "http://192.168.*:*",
            "http://10.*:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    @Profile("prod")
    UrlBasedCorsConfigurationSource prodCorsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "capacitor://localhost",
            "http://localhost"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

- [ ] **Step 4: Wire CORS into SecurityConfig**

Read current SecurityConfig:

```bash
cat src/main/java/com/remi/config/SecurityConfig.java
```

In the `SecurityFilterChain` bean, locate the `HttpSecurity` builder chain. Add `.cors(Customizer.withDefaults())` near the start of the chain (right after `http.csrf(...)` or equivalent). Also add the import `import org.springframework.security.config.Customizer;` if not already present. The CORS source bean is auto-picked by Spring Security when `.cors(Customizer.withDefaults())` is set.

Example: if the chain currently reads:

```java
http
    .csrf(csrf -> csrf.disable())
    .authorizeHttpRequests(...)
    ...
```

change to:

```java
http
    .cors(Customizer.withDefaults())
    .csrf(csrf -> csrf.disable())
    .authorizeHttpRequests(...)
    ...
```

- [ ] **Step 5: Run the CORS test to verify it passes**

```bash
cd /Users/georgesand/IdeaProjects/remi
./mvnw test -Dtest=CorsConfigTest 2>&1 | tail -20
```

Expected: all 4 tests PASS.

- [ ] **Step 6: Run the full backend test suite to verify no regressions**

```bash
cd /Users/georgesand/IdeaProjects/remi
./mvnw test 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 7: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add src/main/java/com/remi/config/CorsConfig.java src/main/java/com/remi/config/SecurityConfig.java src/test/java/com/remi/config/CorsConfigTest.java
git commit -m "feat(backend): CORS config for Capacitor WebView + dev LAN origins"
```

---

## Phase F — Scripts + docs

### Task F1: Add npm scripts for mobile workflow

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: Read current scripts block**

```bash
grep -A 10 '"scripts"' frontend/package.json
```

- [ ] **Step 2: Add scripts**

In `frontend/package.json`, inside the `"scripts": { ... }` object, add three new entries (preserving existing entries):

```json
    "cap:ios": "ionic capacitor run ios --livereload --external",
    "cap:android": "ionic capacitor run android --livereload --external",
    "cap:sync": "ng build && npx cap sync"
```

- [ ] **Step 3: Verify npm can list them**

```bash
cd frontend && npm run 2>&1 | grep -E 'cap:(ios|android|sync)'
```

Expected: all three scripts listed.

- [ ] **Step 4: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/package.json
git commit -m "chore(scripts): npm cap:ios, cap:android, cap:sync"
```

---

### Task F2: Write `docs/MOBILE_DEV.md` and link from frontend README

**Files:**
- Create: `docs/MOBILE_DEV.md`
- Modify: `frontend/README.md`

- [ ] **Step 1: Create `docs/MOBILE_DEV.md`**

```markdown
# Mobile Development (Stage 5a)

Runs the Remi app on iOS Simulator and Android Emulator with live reload against the local Spring Boot backend.

## Prereqs

| Tool | Version | How |
|------|---------|-----|
| macOS | Sonoma+ | (host OS) |
| Xcode | 15+ | Mac App Store |
| Android Studio | Hedgehog+ | https://developer.android.com/studio |
| Java | 17+ | `brew install openjdk@17` |
| Node | 20+ | `brew install node@20` |
| CocoaPods | 1.15+ | `sudo gem install cocoapods` (fallback: `brew install cocoapods`) |
| Android SDK 34 + AVD | — | Android Studio → SDK Manager + Device Manager (create a Pixel 6 / API 34 emulator) |

## First-time setup after `git clone`

```bash
cd frontend
npm install
npx cap sync
# Open iOS workspace in Xcode once, accept automatic signing under your personal team:
open ios/App/App.xcworkspace
```

## Daily workflow (3 terminals)

1. **Backend:**
   ```bash
   cd /Users/georgesand/IdeaProjects/remi
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

2. **Frontend dev server** (binds to all interfaces so the simulator can reach it):
   ```bash
   cd frontend
   npm start -- --host 0.0.0.0
   ```

3. **Capacitor live reload run:**
   - iOS: `npm run cap:ios`
   - Android: `npm run cap:android`

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
| `CORS error` in WebView console | Confirm backend started with `--spring-boot.run.profiles=dev`; check that backend log shows the request's `Origin:` header |
| `App Transport Security policy requires the use of a secure connection` (iOS) | Confirm `frontend/ios/App/App/Info.plist` contains the `NSExceptionDomains/localhost` block |
| `Failed to load resource: net::ERR_CONNECTION_REFUSED` (Android) | Use `10.0.2.2`, not `localhost` — runtime resolver handles this; if you see this anyway, rebuild with `npm run cap:sync` |
| Live reload doesn't update | `npx cap sync --force` then restart simulator |
| `pod install failed` | `sudo gem install cocoapods`, then `cd ios/App && pod install` |
| Android emulator can't reach host | Confirm the emulator is using the standard AVD (not "Phone" image without bridge); `adb shell ping 10.0.2.2` should succeed |
```

- [ ] **Step 2: Update `frontend/README.md`**

Append the following section to `frontend/README.md`:

```markdown

## Running on mobile

See [`docs/MOBILE_DEV.md`](../docs/MOBILE_DEV.md) for prereqs, first-time setup, daily workflow, and troubleshooting for iOS Simulator + Android Emulator targets.
```

- [ ] **Step 3: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add docs/MOBILE_DEV.md frontend/README.md
git commit -m "docs(stage5a): mobile dev workflow guide + README pointer"
```

---

## Phase G — Smoke verification (manual)

These tasks confirm the end-to-end flow. They cannot be automated in CI; they require the dev's machine and human eyes.

### Task G1: iOS Simulator smoke

**No files modified. Manual verification.**

- [ ] **Step 1: Start backend**

```bash
cd /Users/georgesand/IdeaProjects/remi
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Leave running.

- [ ] **Step 2: Start frontend dev server**

In a second terminal:

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npm start -- --host 0.0.0.0
```

Leave running.

- [ ] **Step 3: Run on iOS Simulator**

In a third terminal:

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npm run cap:ios
```

- [ ] **Step 4: Verify smoke checks pass**

In the iOS Simulator, verify ALL of:
- [ ] App launches to login page
- [ ] Existing test user logs in successfully
- [ ] Lobby loads
- [ ] Matchmaking queue accepts the request
- [ ] (Open second client in web browser at http://localhost:8100 as opponent; match starts)
- [ ] First turn: draw a piece, see hand update
- [ ] Discard a piece, see opponent's WebSocket-pushed update
- [ ] Edit any visible string in a template file, save → simulator reflects change without rebuild
- [ ] Background the app for 30s, restore → game state intact

If ANY step fails, debug before declaring G1 done. Common fixes are in `docs/MOBILE_DEV.md`.

- [ ] **Step 5: Mark Phase G1 complete in a no-op commit (optional)**

If everything passes, no code commit is needed. Phase G is verification only.

---

### Task G2: Android Emulator smoke

**No files modified. Manual verification.**

- [ ] **Step 1: Confirm Android emulator is running**

In Android Studio: Device Manager → start the Pixel 6 / API 34 AVD. Or from CLI: `emulator -avd Pixel_6_API_34 &`.

- [ ] **Step 2: With backend + frontend dev server still running from G1, run Android target**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npm run cap:android
```

- [ ] **Step 3: Verify same smoke checks as G1**

Repeat the checklist from G1 step 4 inside the Android Emulator.

- [ ] **Step 4: Mark Phase G2 complete**

If everything passes, Stage 5a is DONE. No code commit needed.

---

## Definition of Done verification

After all tasks complete, verify each DoD item from the spec:

- [ ] `frontend/capacitor.config.ts` uses `ro.remi.app` / `Remi` (Task A1)
- [ ] `frontend/ios/` and `frontend/android/` in git, build cleanly (A4, A5, D2)
- [ ] Runtime URL resolver replaces `environment.apiUrl`/`wsUrl` everywhere (Phase B+C)
- [ ] Backend CORS allows Capacitor WebView origins + dev LAN patterns (Task E1)
- [ ] iOS Info.plist has ATS exception (Task D1)
- [ ] Android network security config permits cleartext (Task D2)
- [ ] `npm run cap:ios` reaches login → game flow (Task G1)
- [ ] `npm run cap:android` does the same (Task G2)
- [ ] All Karma tests pass (verified at end of each Phase B/C task)
- [ ] `docs/MOBILE_DEV.md` exists; <30min onboarding (Task F2)
- [ ] `frontend/README.md` links to `MOBILE_DEV.md` (Task F2)
