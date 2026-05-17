import { ApiError } from '../models';

export const ERROR_MESSAGES: Record<string, string> = {
  // Auth (Stage 2)
  EMAIL_TAKEN: 'Acest email este deja folosit.',
  USERNAME_TAKEN: 'Acest username este deja folosit.',
  INVALID_CREDENTIALS: 'Email sau parolă incorecte.',
  INVALID_TOKEN: 'Link invalid sau expirat.',
  TOKEN_REUSED: 'Sesiunea a fost compromisă. Te-am delogat din toate device-urile.',
  TOKEN_EXPIRED: 'Sesiunea a expirat. Te rugăm să te reloghezi.',
  PASSWORD_POLICY: 'Parola trebuie să aibă minim 10 caractere.',
  USERNAME_POLICY: 'Username 3-20 caractere, litere/cifre/_/-.',
  USER_NOT_FOUND: 'Utilizator inexistent.',
  // Lobby (Stage 3)
  LOBBY_FULL: 'Lobby plin.',
  LOBBY_NOT_FOUND: 'Lobby inexistent.',
  ALREADY_SEATED: 'Ești deja la această masă.',
  NOT_SEATED: 'Nu ești la această masă.',
  NOT_YOUR_SEAT: 'Nu e locul tău.',
  GAME_ALREADY_STARTED: 'Jocul a început deja.',
  JOIN_CODE_NOT_FOUND: 'Cod invalid.',
  ALREADY_QUEUED: 'Ești deja în coada de matchmaking.',
  // Engine (Stage 1)
  NOT_YOUR_TURN: 'Nu e rândul tău.',
  WRONG_PHASE: 'Nu poți face asta acum.',
  GAME_CLOSED: 'Runda este închisă.',
  STOCK_EMPTY: 'Grămada este goală.',
  DISCARD_EMPTY: 'Nu este nicio piesă aruncată.',
  CANNOT_TAKE_OPENING_PIECE: 'Nu poți lua piesa de start.',
  CANNOT_BREAK_LINE: 'Nu poți rupe șirul acum.',
  BREAK_REQUIRES_ETALAT: 'Trebuie să fii etalat pentru a rupe șirul.',
  PIECE_NOT_IN_HAND: 'Piesă inexistentă în mână.',
  INVALID_MELD: 'Combinație invalidă.',
  FIRST_MELD_TOO_FEW_POINTS: 'Prima etalare e sub 45 puncte.',
  FIRST_MELD_NEEDS_SUITE_OR_1S: 'Prima etalare necesită o suită sau o terță de 1.',
  MUST_USE_TAKEN_PIECE: 'Trebuie să folosești piesa luată din șir.',
  NOT_ETALAT: 'Trebuie să fii etalat mai întâi.',
  INVALID_LAYOFF: 'Piesa nu se potrivește.',
  HAND_TOO_FULL_TO_DISCARD: 'Prea multe piese în mână.',
  // System
  GAME_VERSION_CONFLICT: 'Joc actualizat între timp. Refresh.',
  ENGINE_ERROR: 'Eroare internă engine.',
  INTERNAL_ERROR: 'Eroare neașteptată.',
  INVALID_REQUEST: 'Cerere invalidă.',
  // Frontend-only
  NETWORK: 'Eroare de rețea. Verifică conexiunea.',
  UNAUTHORIZED: 'Autentificare necesară.',
  WS_DISCONNECTED: 'Conexiunea s-a întrerupt. Se reconectează...',
  UNKNOWN: 'A apărut o eroare neașteptată.',
};

export function localizeError(error: ApiError | null | undefined): string {
  if (error === null || error === undefined) return '';
  const known = ERROR_MESSAGES[error.code];
  if (known) return known;
  if (error.message) return error.message;
  return ERROR_MESSAGES['UNKNOWN'];
}
