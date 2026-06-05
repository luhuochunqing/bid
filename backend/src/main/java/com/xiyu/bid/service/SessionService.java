package com.xiyu.bid.service;

import com.xiyu.bid.dto.SessionDTO;
import com.xiyu.bid.entity.RefreshSession;
import com.xiyu.bid.repository.RefreshSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing user sessions
 * Provides operations for viewing and revoking sessions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final RefreshSessionRepository sessionRepository;

    /**
     * Get all active sessions for a user
     *
     * @param userId the user ID
     * @return list of session DTOs
     */
    public List<SessionDTO> getUserSessions(Long userId) {
        return sessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Revoke a specific session
     *
     * @param sessionId the session ID to revoke
     * @param userId the user ID (for ownership verification)
     * @throws IllegalArgumentException if session not found
     * @throws IllegalStateException if session belongs to different user
     */
    @Transactional
    public void revokeSession(Long sessionId, Long userId) {
        RefreshSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You can only revoke your own sessions");
        }

        session.setRevokedAt(LocalDateTime.now());
        sessionRepository.save(session);

        log.info("Session {} revoked for user {}", sessionId, userId);
    }

    /**
     * Revoke all sessions except the current one
     *
     * @param userId the user ID
     * @param currentTokenHash the hash of the current session token (to exclude from revocation)
     */
    @Transactional
    public void revokeAllSessions(Long userId, String currentTokenHash) {
        List<RefreshSession> sessions = sessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(userId);

        int revokedCount = 0;
        for (RefreshSession session : sessions) {
            if (currentTokenHash == null || !session.getTokenHash().equals(currentTokenHash)) {
                session.setRevokedAt(LocalDateTime.now());
                sessionRepository.save(session);
                revokedCount++;
            }
        }

        log.info("Revoked {} sessions for user {} (excluding current)", revokedCount, userId);
    }

    /**
     * Update the last seen timestamp and optional metadata for a session
     *
     * @param tokenHash the session token hash
     * @param ipAddress optional IP address
     * @param userAgent optional user agent string
     */
    @Transactional
    public void updateLastSeen(String tokenHash, String ipAddress, String userAgent) {
        sessionRepository.findByTokenHash(tokenHash).ifPresent(session -> {
            session.setLastSeenAt(LocalDateTime.now());
            if (ipAddress != null) {
                session.setIpAddress(ipAddress);
            }
            if (userAgent != null && userAgent.length() <= 500) {
                session.setUserAgent(userAgent);
            }
            sessionRepository.save(session);
        });
    }

    /**
     * Convert entity to DTO
     */
    private SessionDTO toDTO(RefreshSession session) {
        return new SessionDTO(
            session.getId(),
            session.getDeviceInfo(),
            session.getIpAddress(),
            session.getCreatedAt(),
            session.getExpiresAt(),
            session.getLastSeenAt()
        );
    }
}
