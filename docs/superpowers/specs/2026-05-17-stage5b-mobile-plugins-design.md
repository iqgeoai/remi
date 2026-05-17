# Stage 5b — Mobile Native Plugins (push, secure storage, deep links)

**Date:** 2026-05-17
**Status:** Autonomous design (user waived approval gates)
**Prior stage:** 5a (mobile shell, HEAD `826c4b0`)

## Goal

Add three native UX capabilities to the mobile app:
1. **Secure JWT storage** — replace `localStorage` with platform keychain (iOS Keychain via Capacitor Preferences encrypted; Android EncryptedSharedPreferences). Survive app uninstall? No (keychain cleared with app). Survive backgrounding? Yes.
2. **Deep links** — custom scheme `remi://` for invite-by-URL flow. App opens to specific match or join code. Universal Links / App Links deferred to 5c (need verified domain).
3. **Push notifications** — Firebase Cloud Messaging integration for "turn ready" + "match found" alerts. Code-complete but end-to-end testing requires FCM project + APNs auth key (user provides later).

## Decisions (defaulted)

| Topic | Choice | Why |
|-------|--------|-----|
| Secure storage plugin | `@capacitor/preferences` (8.x) | Built-in, iOS Keychain backed, Android encrypted-prefs backed. No abandoned community plugins. |
| JWT migration | One-shot read-from-localStorage on first launch, write to secure store, clear localStorage | No data loss for existing users; idempotent. |
| Deep link scheme | `remi://` (custom URL scheme) | Universal Links need a verified domain + apple-app-site-association file hosted on HTTPS — Stage 5c work. Custom scheme is enough for 5b's invite flow. |
| Deep link routes | `remi://match/<matchId>`, `remi://invite/<code>` | Maps to existing Angular routes. |
| Push library | `@capacitor/push-notifications` (8.x) | Official Capacitor plugin, FCM under the hood on Android, APNs on iOS. |
| Push routing | Server sends FCM message with `data.type=turn_ready` or `data.type=match_found` and `data.matchId=<id>` — client navigates via deep link logic | Single code path for deep-link & push. |
| Permission request | On first lobby visit, not on cold launch | Less aggressive UX. |
| Backend push fan-out | Spring Boot service that talks to FCM HTTP v1 API | One server-side path covers both iOS+Android (FCM relays APNs). |
| FCM credentials | Service account JSON, env var `GOOGLE_APPLICATION_CREDENTIALS` | Standard; never committed to repo. |

## Components

### Frontend (Angular)

- **New** `frontend/src/app/core/storage/secure-storage.service.ts` — wraps Capacitor `Preferences` API; tokens `set/get/remove`. Falls back to `localStorage` on web platform.
- **Modify** `frontend/src/app/core/auth/auth.service.ts` — migrate from `localStorage` calls to `SecureStorageService`. On first call to `getToken()`, if old `localStorage` value exists, copy to secure store and clear it.
- **New** `frontend/src/app/core/deeplink/deep-link.service.ts` — listens to `App.addListener('appUrlOpen', ...)`, parses `remi://<host>/<path>` URLs, navigates via Angular Router.
- **Modify** `frontend/src/main.ts` (or `app.component.ts`) — bootstrap `DeepLinkService` early.
- **New** `frontend/src/app/core/push/push-notifications.service.ts` — wraps `@capacitor/push-notifications`: register, request permission, listen for `pushNotificationReceived` and `pushNotificationActionPerformed`.
- **Modify** `frontend/src/app/features/lobby/lobby-home.page.ts` — call `PushNotificationsService.ensurePermission()` on init (only on native platforms).
- **New** `frontend/src/app/core/push/device-token.api.ts` — POSTs FCM device token to backend after registration.

### Backend (Spring Boot)

- **New** `src/main/java/com/remi/push/PushService.java` — sends FCM messages via Firebase Admin SDK.
- **New** `src/main/java/com/remi/push/DeviceTokenController.java` — `POST /api/push/device-token` accepts `{ "token": "...", "platform": "ios|android" }`, persists to DB.
- **New** `src/main/java/com/remi/push/DeviceToken.java` — JPA entity `(userId, token, platform, createdAt)`.
- **New** `src/main/java/com/remi/push/DeviceTokenRepository.java` — Spring Data repo.
- **New** Flyway migration `src/main/resources/db/migration/V<n>__device_tokens.sql`.
- **Modify** `src/main/java/com/remi/game/match/MatchService.java` (or equivalent) — after `match-found` and `turn-changed` events, call `PushService.notify(userId, ...)`.
- **New** `src/main/java/com/remi/push/FirebaseConfig.java` — initializes Firebase Admin SDK if `GOOGLE_APPLICATION_CREDENTIALS` env var is set; otherwise no-op (push silently disabled in dev).

### Native config

- **iOS** `Info.plist`: register URL scheme `remi`. Add `UIBackgroundModes` with `remote-notification`.
- **iOS** `App/App.entitlements`: add `aps-environment` capability (development for dev builds). NOTE: requires Apple Developer Program team selected in Xcode; pure simulator dev can skip this and push won't work in sim (Apple limitation: simulators do not receive APNs).
- **Android** `AndroidManifest.xml`: add `<intent-filter>` for `remi://` scheme. Add Firebase messaging service.
- **Android** `google-services.json`: required for FCM. User downloads from Firebase console, places at `frontend/android/app/google-services.json` (gitignored).

## Out of scope

- Universal Links (iOS) / App Links (Android) — needs HTTPS-hosted `apple-app-site-association` + `assetlinks.json` — 5c
- In-app rich notifications (images, action buttons) — vanilla text+route is enough for 5b
- Notification preferences UI — flag for 5b stretch if time permits
- Push to web (web push API) — out of scope; web users in browser already see UI updates
- iOS push testing on simulator — Apple disallows; must use physical device (out of 5a/5b scope)

## Testing

- **SecureStorageService:** unit tests with stubbed `Preferences` plugin, 4 cases (set/get/remove/missing-key).
- **DeepLinkService:** unit tests with stubbed `App.addListener`, navigation assertions, 3 cases (match URL, invite URL, garbage URL ignored).
- **PushNotificationsService:** unit tests with stubbed plugin, permission flow, 2 cases.
- **Backend PushService:** unit tests with mocked FCM client, 2 cases (success, no token registered).
- **DeviceTokenController:** integration test with MockMvc, 2 cases (auth required, persists token).
- Full Karma suite: 135 → 145+ tests.

## DoD

- [ ] JWT lives in iOS Keychain (verifiable via `Keychain Access.app`) and Android encrypted prefs
- [ ] `remi://match/<id>` opens app to that match's GamePage
- [ ] Backend has `POST /api/push/device-token` endpoint, persists to DB
- [ ] Match-found and turn-changed events trigger FCM send (logged even if no token registered)
- [ ] App requests notification permission on first lobby visit
- [ ] If `GOOGLE_APPLICATION_CREDENTIALS` unset, backend logs warning and continues (push disabled)
- [ ] Tests pass: 145+ frontend + 200+ backend
- [ ] `docs/MOBILE_DEV.md` updated with FCM setup instructions for when user creates project
