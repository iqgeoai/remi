CREATE TYPE friendship_status AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED');

CREATE TABLE friendships (
    id           BIGSERIAL PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       friendship_status NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at  TIMESTAMPTZ,
    CONSTRAINT friendships_distinct CHECK (requester_id <> addressee_id),
    CONSTRAINT friendships_unique_pair UNIQUE (requester_id, addressee_id)
);
CREATE INDEX friendships_requester_idx ON friendships(requester_id);
CREATE INDEX friendships_addressee_idx ON friendships(addressee_id);
CREATE INDEX friendships_status_accepted_idx ON friendships(status) WHERE status = 'ACCEPTED';

CREATE TABLE user_blocks (
    id         BIGSERIAL PRIMARY KEY,
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_blocks_distinct CHECK (blocker_id <> blocked_id),
    CONSTRAINT user_blocks_unique UNIQUE (blocker_id, blocked_id)
);
CREATE INDEX user_blocks_blocker_idx ON user_blocks(blocker_id);
