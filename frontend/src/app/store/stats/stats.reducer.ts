import { createFeature, createReducer, on } from '@ngrx/store';
import { Profile, LeaderboardEntry } from './stats.models';
import { StatsActions } from './stats.actions';

export interface StatsState {
  profiles: Record<string, Profile>;
  leaderboard: LeaderboardEntry[];
}

const initial: StatsState = { profiles: {}, leaderboard: [] };

export const statsFeature = createFeature({
  name: 'stats',
  reducer: createReducer<StatsState>(
    initial,
    on(StatsActions.profileLoaded, (s, { profile }) => ({
      ...s,
      profiles: { ...s.profiles, [profile.id]: profile },
    })),
    on(StatsActions.leaderboardLoaded, (s, { entries }) => ({
      ...s,
      leaderboard: entries,
    })),
  ),
});
