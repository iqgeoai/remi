import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Profile, LeaderboardEntry } from './stats.models';

export const StatsActions = createActionGroup({
  source: 'Stats',
  events: {
    'Load Profile': props<{ userId: string }>(),
    'Profile Loaded': props<{ profile: Profile }>(),
    'Load My Stats': emptyProps(),
    'Load Leaderboard': emptyProps(),
    'Leaderboard Loaded': props<{ entries: LeaderboardEntry[] }>(),
  },
});
