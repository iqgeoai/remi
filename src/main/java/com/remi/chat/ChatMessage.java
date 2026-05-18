package com.remi.chat;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 8)
    private ChannelType channelType;

    @Column(name = "channel_key", nullable = false, length = 80)
    private String channelKey;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ChatMessage() {}

    public ChatMessage(ChannelType channelType, String channelKey, UUID senderId, String body) {
        this.channelType = channelType;
        this.channelKey = channelKey;
        this.senderId = senderId;
        this.body = body;
    }

    public Long getId() { return id; }
    public ChannelType getChannelType() { return channelType; }
    public String getChannelKey() { return channelKey; }
    public UUID getSenderId() { return senderId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
