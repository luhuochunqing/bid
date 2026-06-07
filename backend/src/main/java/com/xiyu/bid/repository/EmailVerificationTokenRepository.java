package com.xiyu.bid.repository;

import com.xiyu.bid.entity.EmailVerificationToken;
import com.xiyu.bid.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for EmailVerificationToken entity
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /**
     * Find a verification token by its hash
     */
    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Find an active (not verified, not expired) token for a user
     */
    @Query("SELECT t FROM EmailVerificationToken t WHERE t.user = :user AND t.verifiedAt IS NULL AND t.expiresAt > :now")
    Optional<EmailVerificationToken> findActiveByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    /**
     * Delete all expired tokens
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);
}
