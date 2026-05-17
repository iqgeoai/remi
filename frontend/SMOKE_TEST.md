# Smoke Test — Stage 4a (Manual E2E)

Prerequisites:
- Backend running (`cd .. && mvn spring-boot:run`) with Postgres in Docker
- Frontend dev server (`cd frontend && npm start` → http://localhost:4200)

## Happy path

1. Open http://localhost:4200 → redirect to /login.
2. Click "Cont nou" → /register.
3. Fill: email `a@a.com`, username `alice`, password `passwordxx` → "Înregistrează-te".
4. Success banner shown. Look in backend console for the verification email log; copy the token UUID.
5. Click "Am primit linkul" → /verify.
6. Paste token → "Verifică" → success banner.
7. Click "Login" link → fill `alice` / `passwordxx` → "Login".
8. Land in /lobby. Header shows username + green WS indicator dot.
9. Click "Creează masă privată" → form → keep defaults (PRIVATE, 2 players, ETALAT, MED) → "Creează".
10. Navigated to /game/&lt;id&gt;. JSON state visible. `current: 0`, `phase: DISCARD`.
11. In another browser/tab/incognito: repeat steps 1-7 with `b@b.com` / `bob`.
12. As Bob: click "Intră cu cod" → paste the join code (shown in Alice's state JSON under `joinCode`) → "Intră". Lands in /game/&lt;id&gt;.
13. Both Alice and Bob see updated state (started=true, both seated).
14. As Alice: scroll to "Trimite Action" form. type=DISCARD, playerIdx=0, pieceId=&lt;first piece in her hand from JSON&gt; → "Send".
15. Both browsers update: `current` becomes 1, Alice's hand shrinks to 14, Bob's hand still hidden (Bob sees Alice's hand as `[]` with `handCount: 14`).
16. As Bob: type=DRAW_FROM_STOCK, playerIdx=1 → "Send". Both update.
17. Click logout button in header → /login. Bob still in /game until his session also logs out.

## Error paths

- Wrong password on login → banner "Email sau parolă incorecte."
- Try to log in before email verified → same banner (no enumeration).
- Join with invalid code → banner "Cod invalid."
- As Alice (not her turn): type=DRAW_FROM_STOCK, playerIdx=0 → toast appears with NOT_YOUR_TURN message.
- Stop the backend mid-game → WS indicator turns yellow (RECONNECTING); restart backend → goes green again.

## Matchmaking

- Alice goes to /lobby/quick → form (2, ETALAT, MED) → "Caută meci" → "În așteptare..." spinner.
- Bob (different tab) goes to /lobby/quick → same form → "Caută meci".
- Both auto-navigate to the same /game/&lt;id&gt; with their seats assigned.
