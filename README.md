# Remi — Multiplayer Romanian Rummy

Platformă multiplayer pentru Remi (varianta cu piese — Romanian Rummy) cu backend Spring Boot, frontend Angular/Ionic și suport nativ iOS + Android prin Capacitor.

**Features:** auth complet (register/verify/login/JWT refresh), matchmaking + lobby private, partide multiplayer real-time prin WebSocket, prieteni + presence live, chat in-game + DM, push notifications (FCM), ELO rating + leaderboard, profile cu istoric meciuri, drag&drop UI pe mobil.

---

## Quick start (5 minute)

### 1. Prereqs

| Tool | Min version | Install |
|------|-------------|---------|
| Java | 21 (JDK 17 OK; JDK 25 OK pentru backend) | `brew install --cask temurin@21` |
| Maven | 3.9+ | `brew install maven` |
| Node.js | 20+ | `brew install node@20` |
| Docker | recent | Docker Desktop |
| PostgreSQL | 16 | Docker imagine (sau `brew install postgresql@16`) |

Pentru build-uri mobile, consultă `docs/MOBILE_DEV.md` (Xcode, Android Studio, CocoaPods).

### 2. Pornește Postgres

```bash
docker run -d --name remi-pg -p 5432:5432 \
  -e POSTGRES_USER=remi -e POSTGRES_PASSWORD=remi -e POSTGRES_DB=remi \
  postgres:16-alpine
```

(Sau `brew services start postgresql@16` dacă-l vrei nativ.)

### 3. Pornește backend-ul

```bash
cd /path/to/remi
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway aplică migrațiile V1-V7 la primul boot. App-ul ascultă pe `http://localhost:8080`.

### 4. Pornește frontend-ul

```bash
cd frontend
npm install
npm start
```

Aplicația web la `http://localhost:8100`. Dev proxy redirectează `/api` și `/ws` spre backend.

### 5. Joacă

1. Deschide `http://localhost:8100` într-un browser
2. **Register** un cont (alice@example.com / alice / passwordxx)
3. Verifică email-ul prin consola backend — caută în log linia `[MockMail] Verification token: ...` și deschide acel link sau folosește token-ul în UI
4. **Login**
5. Pentru un meci complet: deschide al doilea browser/incognito, register al doilea cont, login. Ambii dau **Quick Match** → matched automat → joc începe.

---

## Configurare

### Variabile de mediu

Pentru dev local nu trebuie nimic în plus. Pentru prod sau pentru a activa anumite features:

```bash
# JWT (default = un secret de dev, schimbă în prod!)
export JWT_SECRET="$(openssl rand -base64 48)"

# SMTP (înlocuiește MockMail care doar logă în consolă)
export SMTP_HOST=smtp.example.com
export SMTP_PORT=587
export SMTP_USER=...
export SMTP_PASS=...
export MAIL_FROM=noreply@remi.example
export MAIL_VERIFICATION_LINK_BASE=https://app.remi.example/verify
export MAIL_RESET_LINK_BASE=https://app.remi.example/reset

# Push notifications (FCM) — opțional, push silently disabled fără
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/firebase-service-account.json
```

Vezi `docs/MOBILE_DEV.md` pentru pașii de setup FCM (creare proiect Firebase + download `google-services.json` / `GoogleService-Info.plist`).

### Profile Spring

- `dev` — folosește MockMailService (loghează token-urile în consolă în loc să le trimită)
- `test` — Testcontainers Postgres (pentru `mvn test`)
- `prod` — nu există default explicit; activează cu `-Dspring-boot.run.profiles=prod` și setează toate variabilele de mediu

### Configurare DB diferită

Modifică `src/main/resources/application.yml` sau setează:
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db
export SPRING_DATASOURCE_USERNAME=user
export SPRING_DATASOURCE_PASSWORD=pass
```

---

## Cum se joacă (regulile pe scurt)

**Remi** se joacă cu 106 piese (4 culori × 13 valori × 2 seturi + 2 jokeri). 2-4 jucători. Câștigi prin scor minim când unul închide.

### Flow de bază al unei ture

1. **Draw** — alegi una din:
   - **Stoc** (piesă necunoscută)
   - **Discard pile** (piesa de sus pe care a aruncat-o oponentul anterior)
2. **Action** (opțional):
   - **Etalat** — pui jos combinații (group = 3-4 piese aceeași valoare, culori diferite; suite = 3+ piese consecutive, aceeași culoare)
     - **Prima ta etalare** trebuie să totalizeze ≥45 puncte ȘI să conțină o **suite** sau o **terță de 1-uri**
     - Joker valorează ce înlocuiește
   - **Layoff** — adaugi piese la combinațiile etalate (ale tale sau ale oponenților)
3. **Discard** — arunci o piesă pe discard pile → tura se termină

### Mecanici speciale

- **Atu** — la începutul jocului se trage o piesă "atu" expusă; cine se etalează cu meld incluzând piesa atu primește **JOC DUBLU** (scor ×2 la final)
- **Închis** — dacă-ți consumi toată mâna (etalat + layoff + discard), runda se închide; ceilalți primesc penalty pe piesele rămase
- **Timer** — ai 120s sugerat per tură, server forțează auto-discard la 180s

### În UI

- **Drag pieces** între mâna ta și discard pile pentru a arunca
- **Tap multiple** pentru a marca piese pentru etalat → buton "Etalează"
- **Tap meld + drop piesă** pentru layoff
- **Match chat** — buton 💬 jos-dreapta (drawer)
- **Timer ring** sus indică timpul rămas

### Surse complete pentru reguli

`assets/remi.html` — implementarea originală single-player. Logica Java este port fidel.

---

## Mobil (iOS + Android)

App-ul rulează nativ pe simulatoarele iOS și emulatoarele Android prin Capacitor. Setup în detaliu:

📱 **`docs/MOBILE_DEV.md`** — prereqs (Xcode, Android Studio, JDK 21 pentru Gradle), first-time setup, daily workflow cu live reload, troubleshooting.

Quick start mobile (după ce ai backend + frontend pornite):

```bash
cd frontend

# Listează simulatoarele disponibile
npm run cap:ios:targets

# Rulează pe iPhone 17 (UUID dintre cele de mai sus)
npm run cap:ios -- --target=<sim-uuid>

# Sau Android
npm run cap:android:targets
npm run cap:android -- --target=<avd-uuid>
```

---

## Arhitectură

```
remi/
├── src/main/java/com/remi/
│   ├── engine/       — pure-functional game engine (NU depinde de Spring)
│   ├── auth/         — JWT, register/verify/login/refresh, MockMail dev
│   ├── user/         — UserEntity + repo, rating column
│   ├── lobby/        — create/join games, matchmaking FIFO queue
│   ├── ws/           — STOMP+SockJS broadcast (state, presence, chat)
│   ├── friends/      — friendships, blocks, presence registry, invites
│   ├── chat/         — match + DM messages, rate limiter, history
│   ├── push/         — FCM device tokens, Firebase Admin SDK (env-gated)
│   ├── stats/        — ELO calculator, match history, profile/leaderboard
│   ├── service/      — GameService orchestrator (load → engine.apply → save → broadcast)
│   └── config/       — Security, CORS, Jackson polymorphism, WebSocket
├── src/main/resources/db/migration/  — Flyway V1-V7
├── frontend/src/app/
│   ├── core/         — api/, ws/, auth/, storage/, deeplink/, push/, config/
│   ├── store/        — NgRx 20 features (auth, lobby, match, game, friends, chat, stats)
│   ├── features/
│   │   ├── auth/     — Login, Register, VerifyEmail, ResetPassword
│   │   ├── lobby/    — Home, CreateGame, JoinByCode, PublicList, QuickMatch
│   │   ├── game/     — GamePage + 13 sub-componente (drag&drop, atu, hand, timer)
│   │   ├── friends/  — Home, Search, Requests, Blocked
│   │   ├── chat/     — MatchChatPanel (drawer), DM list + thread
│   │   └── stats/    — Profile, Leaderboard
│   └── shared/       — ErrorBanner, WsIndicator, GlobalErrorHandler
├── frontend/ios/     — Capacitor 8 iOS scaffold (Xcode project)
├── frontend/android/ — Capacitor 8 Android scaffold (Gradle project)
└── docs/superpowers/
    ├── specs/        — design docs per stage
    └── plans/        — implementation plans per stage
```

**Engine este pur funcțional** (zero Spring/JPA imports) — refolosibil pentru replay, bot training, mod offline mobil.

---

## Tests

```bash
# Backend (218 teste, Testcontainers pentru Postgres)
mvn test

# Frontend (147 teste Karma + Jasmine, ChromeHeadless)
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless

# Backend cu coverage gate
mvn verify
```

---

## API Endpoints (rezumat)

| Categorie | Base path | Auth |
|-----------|-----------|------|
| Auth | `/api/auth/*` | unele public, restul JWT |
| Users | `/api/users/{id}/profile`, `/api/users/search`, `/api/users/{id}/block`, `/api/users/blocked` | JWT |
| Games | `/api/games`, `/api/games/{id}/*`, `/api/games/join-by-code` | JWT |
| Matchmaking | `/api/matchmaking/quick` | JWT |
| Friends | `/api/friends/*`, `/api/friends/{id}/invite` | JWT |
| Chat | `/api/chat/match/{id}`, `/api/chat/dm/{otherId}`, `/api/chat/dm/conversations` | JWT |
| Push | `/api/push/device-token` | JWT |
| Stats | `/api/users/me/stats`, `/api/users/{id}/profile`, `/api/leaderboard` | JWT |
| Dev (Stage 1) | `/api/dev/games/*` | public (whitelist) |

**WebSocket** la `ws://localhost:8080/ws` (STOMP + SockJS), `CONNECT` header `Authorization: Bearer <token>`.

Topics:
- `/user/queue/games/{gameId}` — state per partidă
- `/user/queue/match` — match-found din matchmaking
- `/user/queue/presence` — friend online/offline
- `/user/queue/friend-requests`, `/user/queue/invites` — friends evenimente
- `/topic/chat/match/{matchId}` — chat partidă
- `/user/queue/dm/{otherUserId}` — DM

---

## Troubleshooting

| Simptom | Fix |
|---------|-----|
| Backend nu pornește, `Connection refused localhost:5432` | Postgres nu rulează. Pornește container-ul Docker sau `brew services start postgresql@16` |
| `Unsupported class file major version 69` la Gradle | Java 25 incompatibil cu Gradle 8.14. `frontend/android/gradle.properties` pinning JDK 21 — verifică să nu fi șters linia |
| `CORS error` în WebView mobil | Backend trebuie pornit cu `-Dspring-boot.run.profiles=dev` (CORS allow-list mai larg pe dev) |
| `App Transport Security` iOS | `frontend/ios/App/App/Info.plist` lipsește `NSAppTransportSecurity` block — vezi 5a |
| Login email nu primește token | Folosești MockMail. Verifică log backend pentru `[MockMail] Verification token: ...` |
| Frontend nu se conectează la backend pe mobil | Vezi `docs/MOBILE_DEV.md` — runtime URL resolver folosește `localhost:8080` (iOS) / `10.0.2.2:8080` (Android) |
| `429` la mesaje chat | Rate limit: 10 msgs/10s per channel. Așteaptă 10s |
| Push nu sosește | Lipsește `GOOGLE_APPLICATION_CREDENTIALS`. Backend logă "Firebase not initialized; would notify..." — fără erori, doar nu trimite |

---

## Documentație detaliată

- `frontend/README.md` — frontend setup specific + features per stage
- `frontend/SMOKE_TEST.md` — flow manual E2E pentru game UI
- `docs/MOBILE_DEV.md` — setup mobile complet (iOS + Android)
- `docs/superpowers/specs/` — design docs per stadiu
- `docs/superpowers/plans/` — planuri de implementare cu fiecare task

## Status proiect

| Stadiu | Descriere | Status |
|--------|-----------|--------|
| 1 | Game engine pure-functional | ✅ |
| 2 | Auth + users | ✅ |
| 3 | Multiplayer + lobby | ✅ |
| 4a | Frontend Angular/Ionic | ✅ |
| 4b | Game UI complet | ✅ |
| 5a | Mobile native shell | ✅ |
| 5b | Push + secure storage + deep links | ✅ |
| 5c | Store-ready builds + CI | ⏸ manual |
| 5d | Submisie store | ⏸ manual |
| 6 | Friends + presence | ✅ |
| 7 | Chat (match + DM) | ✅ |
| 8 | Stats + ELO + leaderboard | ✅ |

**Total: 218 backend tests + 147 frontend tests, toate verzi.**
