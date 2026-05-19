export interface RecentMatch {
  gameId: string;
  finishedAt: string;
  durationSec: number;
  rank: number;
  score: number;
  ratingDelta: number;
  winnerUsername: string;
}

export interface Profile {
  id: string;
  username: string;
  rating: number;
  totalMatches: number;
  wins: number;
  losses: number;
  winRate: number;
  totalPoints: number;
  recentMatches: RecentMatch[];
}

export interface LeaderboardEntry {
  id: string;
  username: string;
  rating: number;
  totalMatches: number;
  wins: number;
}
