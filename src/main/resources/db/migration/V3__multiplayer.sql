ALTER TABLE games
  ADD COLUMN owner_id   UUID REFERENCES users(id),
  ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
  ADD COLUMN join_code  VARCHAR(8);

CREATE UNIQUE INDEX games_join_code_uniq ON games(join_code) WHERE join_code IS NOT NULL;
CREATE INDEX games_visibility_idx ON games(visibility) WHERE visibility = 'PUBLIC';

CREATE TABLE game_players (
  game_id    UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  player_idx INT  NOT NULL,
  user_id    UUID NOT NULL REFERENCES users(id),
  joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (game_id, player_idx)
);
CREATE INDEX game_players_user_idx ON game_players(user_id);
