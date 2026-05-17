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

## Stage 4b — Game UI scenarios

### Tap-to-select etalat

1. As Alice, after Bob joins and game starts, locate 3 consecutive RED pieces in your hand (e.g. 5/6/7).
2. Tap each — they lift up with a yellow glow.
3. Action bar shows "+ Adaugă meld" (green) — tap it.
4. Selection clears; "Etalează (1)" button (warning color) appears.
5. Repeat to add another meld if you have 45+ points worth. Otherwise tap "Etalează (1)".
6. Backend may reject if total <45p (first meld) → toast "Prima etalare are X<45p" + proposed meld restored. Adjust and retry.
7. On success: meld appears in table area; hand shrinks; current advances to next player.

### Etalat invalid

1. Tap 2 RED pieces that are NOT consecutive (e.g. 5 and 8).
2. "+ Adaugă meld" button does NOT appear (detectMeld returns null client-side).
3. No backend roundtrip needed.

### Layoff drag&drop

1. As Bob with hasEtalat=true, drag a piece from your hand onto an existing meld card (e.g. Alice's group of 7s).
2. Drop target turns green during drag (cdkDropList highlight).
3. Drop: backend processes; piece appears in meld; hand reduces.
4. If invalid (e.g. piece doesn't fit): toast "Piesa nu se potrivește."

### Take discard with break-line

1. As Alice with hasEtalat=true and hand≥4, the discard pile shows count + top piece.
2. Tap top piece (canTake=true → click handler fires).
3. Backend returns multiple pieces (the broken row): top + everything above the chosen index.
4. Hand grows; chosen piece has red border (mustUsePieceId).
5. You must use that piece in a meld/layoff before discarding else server rejects with MUST_USE_TAKEN_PIECE.

### Round-end modal

1. Play until you close the round (etalat your second-to-last piece + discard the last, or play your last via etalat that empties the hand).
2. View updates with `closed: true`.
3. Modal pops up with results table (each player + round score + total) + winner highlighted.
4. Tap "Înapoi la lobby" → /lobby.

### Hybrid timer

1. Start your turn, don't act for 120 seconds. Timer in header counts down.
2. At 0, client auto-dispatches FORCE_AUTO. Server processes (auto-draw or auto-discard depending on phase).
3. If client stalls (e.g. tab backgrounded), server fallback fires at 180s.
