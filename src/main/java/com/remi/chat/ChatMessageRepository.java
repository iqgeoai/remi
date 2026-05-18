package com.remi.chat;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("SELECT m FROM ChatMessage m WHERE m.channelType = :type AND m.channelKey = :key ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecent(@Param("type") ChannelType type, @Param("key") String key, PageRequest page);

    @Query(value = """
        SELECT DISTINCT ON (m.channel_key) m.* FROM chat_messages m
        WHERE m.channel_type = 'DM' AND m.channel_key LIKE :userPattern
        ORDER BY m.channel_key, m.created_at DESC
        """, nativeQuery = true)
    List<ChatMessage> findDmConversations(@Param("userPattern") String userIdPattern);
}
