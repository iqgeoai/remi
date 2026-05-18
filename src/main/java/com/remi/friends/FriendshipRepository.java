package com.remi.friends;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    @Query("SELECT f FROM Friendship f WHERE (f.requesterId = :a AND f.addresseeId = :b) OR (f.requesterId = :b AND f.addresseeId = :a)")
    Optional<Friendship> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("SELECT f FROM Friendship f WHERE f.status = com.remi.friends.FriendshipStatus.ACCEPTED AND (f.requesterId = :userId OR f.addresseeId = :userId)")
    List<Friendship> findAccepted(@Param("userId") UUID userId);

    List<Friendship> findByAddresseeIdAndStatus(UUID addresseeId, FriendshipStatus status);
    List<Friendship> findByRequesterIdAndStatus(UUID requesterId, FriendshipStatus status);
}
