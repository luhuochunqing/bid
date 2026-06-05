package com.xiyu.bid.repository;

import com.xiyu.bid.entity.PasswordResetToken;
import com.xiyu.bid.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 密码重置令牌Repository
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

        /**
         * 根据令牌哈希查找重置令牌
         */
        Optional<PasswordResetToken> findByToken(String token);

        /**
         * 查找用户的未使用且未过期的重置令牌
         */
        Optional<PasswordResetToken> findByUserAndUsedAtIsNullAndExpiresAtAfter(
                        User user, LocalDateTime now
        );

        /**
         * 删除过期或已使用的令牌
         * 返回删除的记录数
         */
        @Modifying
        @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now OR t.usedAt IS NOT NULL")
        int deleteExpiredOrUsedTokens(@Param("now") LocalDateTime now);

        /**
         * 标记令牌为已使用
         */
        @Modifying
        @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.token = :token")
        void markAsUsed(@Param("token") String token, @Param("now") LocalDateTime now);
}
