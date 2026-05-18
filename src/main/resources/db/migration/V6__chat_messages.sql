CREATE TABLE chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    channel_type VARCHAR(8) NOT NULL,
    channel_key  VARCHAR(80) NOT NULL,
    sender_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body         VARCHAR(500) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chat_messages_channel_type_valid CHECK (channel_type IN ('MATCH', 'DM')),
    CONSTRAINT chat_messages_body_nonempty CHECK (length(trim(body)) > 0)
);

CREATE INDEX chat_messages_channel_idx ON chat_messages(channel_type, channel_key, created_at DESC);
CREATE INDEX chat_messages_sender_idx ON chat_messages(sender_id);
