# Stage 5b — Mobile Plugins Implementation Plan

> **For agentic workers:** Subagent-driven execution. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Add secure JWT storage, custom-scheme deep links, and FCM push notification scaffolding to the mobile app.

**Architecture:** Three independent native integrations wrapped in Angular services. Backend gains a `DeviceToken` table + `PushService` that's no-op when Firebase credentials are unset (dev). Custom-scheme `remi://` deep links; Universal Links deferred to 5c.

**Spec:** `docs/superpowers/specs/2026-05-17-stage5b-mobile-plugins-design.md`

---

## Phase A — Secure JWT Storage

### Task A1: Install `@capacitor/preferences`

- [ ] **Step 1:** `cd /Users/georgesand/IdeaProjects/remi/frontend && npm install @capacitor/preferences`
- [ ] **Step 2:** `npx cap sync`
- [ ] **Step 3:** Commit: `git add frontend/package.json frontend/package-lock.json frontend/ios frontend/android && git commit -m "chore(capacitor): install @capacitor/preferences plugin"`

### Task A2: SecureStorageService + spec

**Files:**
- Create: `frontend/src/app/core/storage/secure-storage.service.ts`
- Create: `frontend/src/app/core/storage/secure-storage.service.spec.ts`

- [ ] **Step 1: Write the spec first**

```ts
import { TestBed } from '@angular/core/testing';
import { Preferences } from '@capacitor/preferences';
import { SecureStorageService } from './secure-storage.service';

describe('SecureStorageService', () => {
  let svc: SecureStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    svc = TestBed.inject(SecureStorageService);
  });

  it('set() delegates to Preferences.set', async () => {
    const spy = spyOn(Preferences, 'set').and.resolveTo();
    await svc.set('jwt', 'abc');
    expect(spy).toHaveBeenCalledWith({ key: 'jwt', value: 'abc' });
  });

  it('get() returns Preferences value', async () => {
    spyOn(Preferences, 'get').and.resolveTo({ value: 'abc' });
    expect(await svc.get('jwt')).toBe('abc');
  });

  it('get() returns null on missing key', async () => {
    spyOn(Preferences, 'get').and.resolveTo({ value: null });
    expect(await svc.get('jwt')).toBeNull();
  });

  it('remove() delegates to Preferences.remove', async () => {
    const spy = spyOn(Preferences, 'remove').and.resolveTo();
    await svc.remove('jwt');
    expect(spy).toHaveBeenCalledWith({ key: 'jwt' });
  });
});
```

- [ ] **Step 2:** Verify FAIL: `npx ng test --watch=false --browsers=ChromeHeadless --include='**/secure-storage.service.spec.ts'` → module-not-found
- [ ] **Step 3:** Implement service:

```ts
import { Injectable } from '@angular/core';
import { Preferences } from '@capacitor/preferences';

@Injectable({ providedIn: 'root' })
export class SecureStorageService {
  async set(key: string, value: string): Promise<void> {
    await Preferences.set({ key, value });
  }
  async get(key: string): Promise<string | null> {
    const { value } = await Preferences.get({ key });
    return value;
  }
  async remove(key: string): Promise<void> {
    await Preferences.remove({ key });
  }
}
```

- [ ] **Step 4:** Verify 4/4 PASS
- [ ] **Step 5:** Commit: `git add frontend/src/app/core/storage && git commit -m "feat(storage): SecureStorageService wrapping Capacitor Preferences"`

### Task A3: Migrate AuthService to SecureStorageService

**Files:**
- Modify: `frontend/src/app/core/auth/auth.service.ts` (or wherever JWT persistence currently lives — search `localStorage.setItem.*token\|localStorage.getItem.*token`)
- Modify: same service's spec file (if exists)

- [ ] **Step 1:** Grep to find token-storage sites: `grep -rn "localStorage" frontend/src/app/core/auth/`. Expect 1-2 files using `localStorage.setItem('jwt', ...)` and `localStorage.getItem('jwt')`.
- [ ] **Step 2:** Add `private readonly storage = inject(SecureStorageService);` to the service.
- [ ] **Step 3:** Replace `localStorage.setItem('jwt', token)` with `await this.storage.set('jwt', token);` (make caller `async` if not already).
- [ ] **Step 4:** Replace `localStorage.getItem('jwt')` with `await this.storage.get('jwt')` — note this becomes async; you may need to adapt the auth state initialization (likely already async since interceptors fetch JWT lazily).
- [ ] **Step 5:** Replace `localStorage.removeItem('jwt')` with `await this.storage.remove('jwt')`.
- [ ] **Step 6:** Add a one-shot migration in service constructor or init method:
```ts
async migrateLegacyToken(): Promise<void> {
  const legacy = localStorage.getItem('jwt');
  if (legacy) {
    await this.storage.set('jwt', legacy);
    localStorage.removeItem('jwt');
  }
}
```
Call this from app bootstrap (in app.component.ts `ngOnInit` or main.ts after bootstrap).
- [ ] **Step 7:** Update the spec file: provide `SecureStorageService` stub in TestBed, update any `localStorage` assertions to call into the stub.
- [ ] **Step 8:** Run full suite: `npx ng test --watch=false --browsers=ChromeHeadless`. Expected: 0 regressions.
- [ ] **Step 9:** Commit: `git add frontend/src/app/core/auth/ frontend/src/app/app.component.ts frontend/src/main.ts 2>/dev/null && git commit -m "refactor(auth): persist JWT in SecureStorageService with one-shot localStorage migration"`

---

## Phase B — Deep Links (custom scheme `remi://`)

### Task B1: DeepLinkService + spec

**Files:**
- Create: `frontend/src/app/core/deeplink/deep-link.service.ts`
- Create: `frontend/src/app/core/deeplink/deep-link.service.spec.ts`

- [ ] **Step 1: Spec**

```ts
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { App } from '@capacitor/app';
import { DeepLinkService } from './deep-link.service';

describe('DeepLinkService', () => {
  let svc: DeepLinkService;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: router }],
    });
    svc = TestBed.inject(DeepLinkService);
  });

  it('routes remi://match/abc123 to /game/abc123', () => {
    svc.handleUrl('remi://match/abc123');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/game/abc123');
  });

  it('routes remi://invite/JOIN42 to /lobby/join-by-code?code=JOIN42', () => {
    svc.handleUrl('remi://invite/JOIN42');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/lobby/join-by-code?code=JOIN42');
  });

  it('ignores garbage URLs', () => {
    svc.handleUrl('https://example.com/foo');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2:** Verify FAIL.
- [ ] **Step 3: Implement**

```ts
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { App, URLOpenListenerEvent } from '@capacitor/app';

@Injectable({ providedIn: 'root' })
export class DeepLinkService {
  private readonly router = inject(Router);

  async init(): Promise<void> {
    await App.addListener('appUrlOpen', (event: URLOpenListenerEvent) => {
      this.handleUrl(event.url);
    });
  }

  handleUrl(url: string): void {
    if (!url.startsWith('remi://')) return;
    const path = url.substring('remi://'.length);
    const [host, ...rest] = path.split('/');
    if (host === 'match' && rest[0]) {
      this.router.navigateByUrl(`/game/${rest[0]}`);
    } else if (host === 'invite' && rest[0]) {
      this.router.navigateByUrl(`/lobby/join-by-code?code=${rest[0]}`);
    }
  }
}
```

- [ ] **Step 4:** Verify 3/3 PASS.
- [ ] **Step 5:** Commit: `git add frontend/src/app/core/deeplink && git commit -m "feat(deeplink): DeepLinkService routing remi:// URLs via Angular Router"`

### Task B2: iOS URL scheme + Android intent-filter

**Files:**
- Modify: `frontend/ios/App/App/Info.plist`
- Modify: `frontend/android/app/src/main/AndroidManifest.xml`

- [ ] **Step 1:** In `frontend/ios/App/App/Info.plist`, before the closing `</dict>` of the root dict, add:

```xml
	<key>CFBundleURLTypes</key>
	<array>
		<dict>
			<key>CFBundleURLName</key>
			<string>ro.remi.app</string>
			<key>CFBundleURLSchemes</key>
			<array>
				<string>remi</string>
			</array>
		</dict>
	</array>
```

- [ ] **Step 2:** Lint: `plutil -lint frontend/ios/App/App/Info.plist` → `OK`
- [ ] **Step 3:** In `frontend/android/app/src/main/AndroidManifest.xml`, find the `<activity android:name=".MainActivity" ...>` element and add inside it (alongside existing `<intent-filter>`):

```xml
            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="remi" />
            </intent-filter>
```

- [ ] **Step 4:** Verify Android still builds: `cd frontend/android && ./gradlew assembleDebug --offline 2>&1 | tail -5`. Expected: `BUILD SUCCESSFUL`. (If offline fails, drop `--offline`.)
- [ ] **Step 5:** Commit: `git add frontend/ios/App/App/Info.plist frontend/android/app/src/main/AndroidManifest.xml && git commit -m "feat(deeplink): register remi:// scheme on iOS + Android"`

### Task B3: Bootstrap DeepLinkService

**Files:**
- Modify: `frontend/src/app/app.component.ts`

- [ ] **Step 1:** Add `private readonly deepLink = inject(DeepLinkService);` (and import) to AppComponent.
- [ ] **Step 2:** In `ngOnInit()` (create if missing), call `void this.deepLink.init();` — fire-and-forget.
- [ ] **Step 3:** Build: `npx ng build --configuration=development` → exit 0.
- [ ] **Step 4:** Commit: `git add frontend/src/app/app.component.ts && git commit -m "feat(deeplink): bootstrap DeepLinkService in AppComponent"`

---

## Phase C — Push Notifications (frontend)

### Task C1: Install plugin

- [ ] **Step 1:** `cd /Users/georgesand/IdeaProjects/remi/frontend && npm install @capacitor/push-notifications`
- [ ] **Step 2:** `npx cap sync`
- [ ] **Step 3:** Commit: `git add frontend/package.json frontend/package-lock.json frontend/ios frontend/android && git commit -m "chore(capacitor): install @capacitor/push-notifications plugin"`

### Task C2: PushNotificationsService + spec

**Files:**
- Create: `frontend/src/app/core/push/push-notifications.service.ts`
- Create: `frontend/src/app/core/push/push-notifications.service.spec.ts`

- [ ] **Step 1: Spec**

```ts
import { TestBed } from '@angular/core/testing';
import { PushNotifications } from '@capacitor/push-notifications';
import { Capacitor } from '@capacitor/core';
import { PushNotificationsService } from './push-notifications.service';

describe('PushNotificationsService', () => {
  let svc: PushNotificationsService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    svc = TestBed.inject(PushNotificationsService);
  });

  it('ensurePermission() no-ops on web platform', async () => {
    spyOn(Capacitor, 'getPlatform').and.returnValue('web');
    const spy = spyOn(PushNotifications, 'requestPermissions');
    await svc.ensurePermission();
    expect(spy).not.toHaveBeenCalled();
  });

  it('ensurePermission() requests + registers on native', async () => {
    spyOn(Capacitor, 'getPlatform').and.returnValue('ios');
    spyOn(PushNotifications, 'requestPermissions').and.resolveTo({ receive: 'granted' });
    const reg = spyOn(PushNotifications, 'register').and.resolveTo();
    await svc.ensurePermission();
    expect(reg).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2:** Verify FAIL.
- [ ] **Step 3: Implement**

```ts
import { Injectable, inject } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { PushNotifications, Token } from '@capacitor/push-notifications';
import { DeviceTokenApi } from './device-token.api';

@Injectable({ providedIn: 'root' })
export class PushNotificationsService {
  private readonly api = inject(DeviceTokenApi);

  async ensurePermission(): Promise<void> {
    const platform = Capacitor.getPlatform();
    if (platform === 'web') return;
    const { receive } = await PushNotifications.requestPermissions();
    if (receive !== 'granted') return;
    await PushNotifications.register();
    await PushNotifications.addListener('registration', async (t: Token) => {
      await this.api.sendDeviceToken(t.value, platform as 'ios' | 'android');
    });
  }
}
```

- [ ] **Step 4:** Create `device-token.api.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_URL } from '../config/api-url.tokens';

@Injectable({ providedIn: 'root' })
export class DeviceTokenApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  sendDeviceToken(token: string, platform: 'ios' | 'android'): Promise<void> {
    return this.http
      .post<void>(`${this.base}/push/device-token`, { token, platform })
      .toPromise()
      .then(() => undefined);
  }
}
```

- [ ] **Step 5:** Run spec: 2/2 PASS.
- [ ] **Step 6:** Commit: `git add frontend/src/app/core/push && git commit -m "feat(push): PushNotificationsService + DeviceTokenApi"`

### Task C3: Wire push permission into LobbyHomePage

**Files:**
- Modify: `frontend/src/app/features/lobby/lobby-home.page.ts`

- [ ] **Step 1:** Inject `PushNotificationsService` into LobbyHomePage.
- [ ] **Step 2:** In `ngOnInit()`, fire-and-forget: `void this.push.ensurePermission();`
- [ ] **Step 3:** Build verifies: `npx ng build --configuration=development` → exit 0.
- [ ] **Step 4:** Commit: `git add frontend/src/app/features/lobby/lobby-home.page.ts && git commit -m "feat(push): request permission on first lobby visit"`

---

## Phase D — Push Notifications (backend)

### Task D1: Flyway migration V4 device_tokens

**Files:**
- Create: `src/main/resources/db/migration/V4__device_tokens.sql`

- [ ] **Step 1: Write migration**

```sql
CREATE TABLE device_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(512) NOT NULL,
    platform   VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, token)
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);
```

- [ ] **Step 2:** Run backend test suite (verifies migration applies): `mvn test -Dtest='*FlywayTest*' 2>&1 | tail -10` — if no FlywayTest exists, just confirm full suite runs: `mvn test 2>&1 | tail -5`. Expected: BUILD SUCCESS.
- [ ] **Step 3:** Commit: `git add src/main/resources/db/migration/V4__device_tokens.sql && git commit -m "feat(push): V4 migration device_tokens table"`

### Task D2: DeviceToken entity + repo

**Files:**
- Create: `src/main/java/com/remi/push/DeviceToken.java`
- Create: `src/main/java/com/remi/push/DeviceTokenRepository.java`

- [ ] **Step 1: Entity**

```java
package com.remi.push;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device_tokens", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "token"}))
public class DeviceToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(nullable = false, length = 16)
    private String platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DeviceToken() {}

    public DeviceToken(Long userId, String token, String platform) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getToken() { return token; }
    public String getPlatform() { return platform; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 2: Repo**

```java
package com.remi.push;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    List<DeviceToken> findByUserId(Long userId);
    Optional<DeviceToken> findByUserIdAndToken(Long userId, String token);
}
```

- [ ] **Step 3:** Compile check: `mvn compile 2>&1 | tail -5` → BUILD SUCCESS.
- [ ] **Step 4:** Commit: `git add src/main/java/com/remi/push && git commit -m "feat(push): DeviceToken entity + repository"`

### Task D3: FirebaseConfig + PushService

**Files:**
- Modify: `pom.xml` (add Firebase Admin SDK dependency)
- Create: `src/main/java/com/remi/push/FirebaseConfig.java`
- Create: `src/main/java/com/remi/push/PushService.java`

- [ ] **Step 1: Add Maven dependency**

In `pom.xml`, inside `<dependencies>`, add:

```xml
        <dependency>
            <groupId>com.google.firebase</groupId>
            <artifactId>firebase-admin</artifactId>
            <version>9.4.3</version>
        </dependency>
```

Run `mvn dependency:resolve 2>&1 | tail -3` to download.

- [ ] **Step 2: FirebaseConfig (env-gated, no-op when credentials missing)**

```java
package com.remi.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;

@Configuration
public class FirebaseConfig {
    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @PostConstruct
    void init() {
        String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credPath == null || credPath.isBlank()) {
            log.warn("GOOGLE_APPLICATION_CREDENTIALS not set; push notifications disabled.");
            return;
        }
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new FileInputStream(credPath)))
                .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            log.info("Firebase Admin SDK initialized.");
        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 3: PushService**

```java
package com.remi.push;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private final DeviceTokenRepository repo;

    public PushService(DeviceTokenRepository repo) {
        this.repo = repo;
    }

    public void notify(Long userId, String title, String body, Map<String, String> data) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized; would notify user {}: {}", userId, title);
            return;
        }
        List<DeviceToken> tokens = repo.findByUserId(userId);
        for (DeviceToken dt : tokens) {
            Message msg = Message.builder()
                .setToken(dt.getToken())
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build();
            try {
                FirebaseMessaging.getInstance().send(msg);
            } catch (Exception e) {
                log.warn("Failed to send push to user {} token {}: {}", userId, dt.getToken(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 4:** `mvn compile 2>&1 | tail -5` → BUILD SUCCESS.
- [ ] **Step 5:** Commit: `git add pom.xml src/main/java/com/remi/push/FirebaseConfig.java src/main/java/com/remi/push/PushService.java && git commit -m "feat(push): FirebaseConfig + PushService (env-gated)"`

### Task D4: DeviceTokenController + test

**Files:**
- Create: `src/main/java/com/remi/push/DeviceTokenController.java`
- Create: `src/test/java/com/remi/push/DeviceTokenControllerTest.java`

- [ ] **Step 1: Controller**

```java
package com.remi.push;

import com.remi.auth.user.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push")
public class DeviceTokenController {
    private final DeviceTokenRepository repo;

    public DeviceTokenController(DeviceTokenRepository repo) {
        this.repo = repo;
    }

    public record RegisterReq(String token, String platform) {}

    @PostMapping("/device-token")
    public ResponseEntity<Void> register(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody RegisterReq req
    ) {
        repo.findByUserIdAndToken(principal.id(), req.token())
            .orElseGet(() -> repo.save(new DeviceToken(principal.id(), req.token(), req.platform())));
        return ResponseEntity.noContent().build();
    }
}
```

**Note:** if the existing project uses a different `UserPrincipal` shape, adapt the import + accessor. Search for current pattern: `grep -rn "AuthenticationPrincipal" src/main/java/ | head -3`.

- [ ] **Step 2: Integration test**

```java
package com.remi.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceTokenControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DeviceTokenRepository repo;

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(post("/api/push/device-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc\",\"platform\":\"ios\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "test")
    void persistsTokenForAuthenticatedUser() throws Exception {
        // NOTE: this test may need adaptation if @WithMockUser doesn't map to a UserPrincipal with id().
        // If the project's auth pattern requires a real JWT, adapt to use TestRestTemplate with a generated JWT.
        mvc.perform(post("/api/push/device-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc-token\",\"platform\":\"ios\"}"))
            .andExpect(status().isNoContent());
    }
}
```

- [ ] **Step 3:** Run: `mvn test -Dtest=DeviceTokenControllerTest 2>&1 | tail -10`. **If the `@WithMockUser` test fails because `UserPrincipal` requires a real id, adapt the test to mint a JWT** using the project's existing JWT util (search `grep -rn "Jwts.builder\|jwtUtil\|generateToken" src/main/java/com/remi/auth/`). The "rejectsUnauthenticated" test should pass regardless.
- [ ] **Step 4:** Commit: `git add src/main/java/com/remi/push/DeviceTokenController.java src/test/java/com/remi/push/DeviceTokenControllerTest.java && git commit -m "feat(push): POST /api/push/device-token + integration test"`

### Task D5: Wire PushService into match/turn events

**Files:**
- Modify: `src/main/java/com/remi/lobby/service/MatchmakingServiceImpl.java`
- Modify: `src/main/java/com/remi/ws/broadcast/StompGameBroadcaster.java`

- [ ] **Step 1:** Inject `PushService push` into `MatchmakingServiceImpl` via constructor.
- [ ] **Step 2:** Find the site where match-found is broadcast (search `convertAndSendToUser.*match` in the file). After the WS broadcast, for each player in the match, call:
```java
push.notify(playerId, "Match găsit", "Adversarul tău așteaptă!", Map.of("type", "match_found", "matchId", matchId.toString()));
```

- [ ] **Step 3:** Inject `PushService push` into `StompGameBroadcaster`. Find the turn-changed broadcast (search `convertAndSendToUser.*turn` or similar). After broadcast, for the active player only:
```java
push.notify(activePlayerId, "E rândul tău", "Joacă acum în Remi", Map.of("type", "turn_ready", "matchId", matchId.toString()));
```

- [ ] **Step 4:** `mvn compile 2>&1 | tail -5` → BUILD SUCCESS. Then `mvn test 2>&1 | tail -10` → no regressions (FirebaseConfig stays no-op without credentials, so PushService.notify just logs).
- [ ] **Step 5:** Commit: `git add src/main/java/com/remi/lobby/service/MatchmakingServiceImpl.java src/main/java/com/remi/ws/broadcast/StompGameBroadcaster.java && git commit -m "feat(push): notify on match-found and turn-changed events"`

---

## Phase E — Docs

### Task E1: Document FCM setup

**Files:**
- Modify: `docs/MOBILE_DEV.md`

- [ ] **Step 1:** Append a new section:

```markdown

## Push Notifications (FCM) — setup needed before testing

Push code is wired but disabled without Firebase credentials. To enable:

1. Create a Firebase project at https://console.firebase.google.com
2. Add an Android app with package name `ro.remi.app`. Download `google-services.json` → place at `frontend/android/app/google-services.json` (gitignored).
3. Add an iOS app with bundle ID `ro.remi.app`. Download `GoogleService-Info.plist` → place at `frontend/ios/App/App/GoogleService-Info.plist` (gitignored).
4. In Firebase console → Project Settings → Service accounts → "Generate new private key". Download the JSON file (keep secret).
5. Export the env var before starting the backend:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/firebase-service-account.json
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```
6. For iOS push to actually deliver: enable APNs in Firebase console → Cloud Messaging → APNs Authentication Key (requires Apple Developer Program — Stage 5c). On simulators APNs is unavailable; physical device required.

Until step 5 is done, backend logs "Firebase not initialized; would notify..." instead of sending.

## Deep links

Custom scheme `remi://` is registered on both platforms. Test from terminal:

```bash
# iOS Simulator
xcrun simctl openurl booted "remi://match/abc123"

# Android Emulator
adb shell am start -a android.intent.action.VIEW -d "remi://match/abc123"
```

The app opens to `/game/abc123`.
```

- [ ] **Step 2:** Add to gitignore (separate file mod):
```bash
echo "frontend/android/app/google-services.json" >> .gitignore
echo "frontend/ios/App/App/GoogleService-Info.plist" >> .gitignore
```
- [ ] **Step 3:** Commit: `git add docs/MOBILE_DEV.md .gitignore && git commit -m "docs(stage5b): FCM setup instructions + deep link test commands"`
