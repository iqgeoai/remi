export type GameVisibility = 'PRIVATE' | 'PUBLIC';
export type Mode = 'ETALAT' | 'TABLA';
export type Difficulty = 'EASY' | 'MED' | 'HARD';

export interface LobbyGame {
  id: string;
  ownerId: string;
  visibility: GameVisibility;
  joinCode: string | null;
  numPlayers: number;
  mode: Mode;
  difficulty: Difficulty;
  seatsTaken: number;
  started: boolean;
  createdAt: string;
}
