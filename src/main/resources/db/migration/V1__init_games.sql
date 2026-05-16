CREATE TABLE games (
  id          UUID PRIMARY KEY,
  state       JSONB NOT NULL,
  version     BIGINT NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX games_updated_at_idx ON games(updated_at);
