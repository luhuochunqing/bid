package com.xiyu.bid.repository;

import com.xiyu.bid.entity.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    /**
     * Find all active (non-revoked) sessions for a user, ordered by creation time descending
     */
    List<RefreshSession> findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);
}
