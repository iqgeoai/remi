ALTER TABLE users ADD COLUMN rating INT NOT NULL DEFAULT 1000;
CREATE INDEX users_rating_idx ON users(rating DESC);

CREATE TABLE match_history (
    id            BIGSERIAL PRIMARY KEY,
    game_id       UUID NOT NULL UNIQUE REFERENCES games(id) ON DELETE CASCADE,
    finished_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_sec  INT NOT NULL,
    winner_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX match_history_winner_idx ON match_history(winner_id);
CREATE INDEX match_history_finished_at_idx ON match_history(finished_at DESC);

CREATE TABLE match_history_score (
    id             BIGSERIAL PRIMARY KEY,
    history_id     BIGINT NOT NULL REFERENCES match_history(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score          INT NOT NULL,
    rank           INT NOT NULL,
    rating_before  INT NOT NULL,
    rating_after   INT NOT NULL,
    rating_delta   INT NOT NULL,
    UNIQUE (history_id, user_id)
);

CREATE INDEX match_history_score_user_idx ON match_history_score(user_id);
