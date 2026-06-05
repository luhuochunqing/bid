package com.xiyu.bid.service;

import com.xiyu.bid.dto.SessionDTO;
import com.xiyu.bid.entity.RefreshSession;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RefreshSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionService
 * Tests cover: session retrieval, session revocation, last seen updates
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private RefreshSessionRepository sessionRepository;

    @InjectMocks
    private SessionService sessionService;

    private User testUser;
    private RefreshSession testSession1;
    private RefreshSession testSession2;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .build();

        testSession1 = RefreshSession.builder()
                .id(1L)
                .user(testUser)
                .tokenHash("hash1")
                .deviceInfo("Chrome on Windows")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0...")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now().minusDays(1))
                .lastSeenAt(LocalDateTime.now().minusHours(1))
                .build();

        testSession2 = RefreshSession.builder()
                .id(2L)
                .user(testUser)
                .tokenHash("hash2")
                .deviceInfo("Safari on iPhone")
                .ipAddress("192.168.1.2")
                .userAgent("Mozilla/5.0 iPhone...")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now().minusHours(2))
                .lastSeenAt(LocalDateTime.now().minusMinutes(30))
                .build();
    }

    @Test
    void testGetUserSessions_Success() {
        List<RefreshSession> sessions = Arrays.asList(testSession1, testSession2);
        when(sessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(sessions);

        List<SessionDTO> result = sessionService.getUserSessions(1L);

        assertEquals(2, result.size());
        verify(sessionRepository).findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(1L);
    }

    @Test
    void testGetUserSessions_EmptyList() {
        when(sessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        List<SessionDTO> result = sessionService.getUserSessions(1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void testRevokeSession_Success() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession1));
        when(sessionRepository.save(any(RefreshSession.class))).thenReturn(testSession1);

        sessionService.revokeSession(1L, 1L);

        assertNotNull(testSession1.getRevokedAt());
        verify(sessionRepository).findById(1L);
        verify(sessionRepository).save(testSession1);
    }

    @Test
    void testRevokeSession_SessionNotFound() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            sessionService.revokeSession(999L, 1L);
        });

        verify(sessionRepository).findById(999L);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void testRevokeSession_WrongUser() {
        User otherUser = User.builder().id(2L).username("otheruser").build();
        testSession1.setUser(otherUser);

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession1));

        assertThrows(IllegalStateException.class, () -> {
            sessionService.revokeSession(1L, 1L);
        });

        verify(sessionRepository).findById(1L);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void testRevokeAllSessions_Success() {
        List<RefreshSession> sessions = Arrays.asList(testSession1, testSession2);
        when(sessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(sessions);
        when(sessionRepository.save(any(RefreshSession.class))).thenReturn(testSession1);

        sessionService.revokeAllSessions(1L, "hash1");

        verify(sessionRepository).findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(1L);
        // Session1 should not be revoked (current session)
        verify(sessionRepository, times(1)).save(any(RefreshSession.class));
    }

    @Test
    void testRevokeAllSessions_NoCurrentSessionProvided() {
        List<RefreshSession> sessions = Arrays.asList(testSession1, testSession2);
        when(sessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(sessions);
        when(sessionRepository.save(any(RefreshSession.class))).thenReturn(testSession1);

        sessionService.revokeAllSessions(1L, "different-hash");

        verify(sessionRepository, times(2)).save(any(RefreshSession.class));
    }

    @Test
    void testUpdateLastSeen_Success() {
        when(sessionRepository.findByTokenHash("hash1")).thenReturn(Optional.of(testSession1));
        when(sessionRepository.save(any(RefreshSession.class))).thenReturn(testSession1);

        sessionService.updateLastSeen("hash1", "192.168.1.100", "Mozilla/5.0 New");

        assertNotNull(testSession1.getLastSeenAt());
        assertEquals("192.168.1.100", testSession1.getIpAddress());
        assertEquals("Mozilla/5.0 New", testSession1.getUserAgent());
        verify(sessionRepository).findByTokenHash("hash1");
        verify(sessionRepository).save(testSession1);
    }

    @Test
    void testUpdateLastSeen_SessionNotFound() {
        when(sessionRepository.findByTokenHash("nonexistent")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> sessionService.updateLastSeen("nonexistent", "192.168.1.1", "UA"));

        verify(sessionRepository).findByTokenHash("nonexistent");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void testUpdateLastSeen_NullOptionalFields() {
        when(sessionRepository.findByTokenHash("hash1")).thenReturn(Optional.of(testSession1));
        when(sessionRepository.save(any(RefreshSession.class))).thenReturn(testSession1);

        sessionService.updateLastSeen("hash1", null, null);

        assertNotNull(testSession1.getLastSeenAt());
        verify(sessionRepository).save(testSession1);
    }

    @Test
    void testUpdateLastSeen_LongUserAgentTruncated() {
        String longUserAgent = "a".repeat(600);
        when(sessionRepository.findByTokenHash("hash1")).thenReturn(Optional.of(testSession1));
        when(sessionRepository.save(any(RefreshSession.class))).thenReturn(testSession1);

        sessionService.updateLastSeen("hash1", "192.168.1.1", longUserAgent);

        // UserAgent should be truncated to 500 chars
        assertTrue(testSession1.getUserAgent().length() <= 500);
    }

    @Test
    void testToDTO_Mapping() {
        List<RefreshSession> sessions = Arrays.asList(testSession1);
        when(sessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(sessions);

        List<SessionDTO> result = sessionService.getUserSessions(1L);

        assertEquals(1, result.size());
        SessionDTO dto = result.get(0);
        assertEquals(testSession1.getId(), dto.id());
        assertEquals(testSession1.getDeviceInfo(), dto.deviceInfo());
        assertEquals(testSession1.getIpAddress(), dto.ipAddress());
        assertEquals(testSession1.getCreatedAt(), dto.createdAt());
        assertEquals(testSession1.getExpiresAt(), dto.expiresAt());
        assertEquals(testSession1.getLastSeenAt(), dto.lastSeenAt());
    }

    @Test
    void testRevokeSession_OnlyOwnSessions() {
        User otherUser = User.builder().id(2L).build();
        RefreshSession otherSession = RefreshSession.builder()
                .id(99L)
                .user(otherUser)
                .tokenHash("other-hash")
                .build();

        when(sessionRepository.findById(99L)).thenReturn(Optional.of(otherSession));

        assertThrows(IllegalStateException.class, () -> {
            sessionService.revokeSession(99L, 1L);
        });

        assertNull(otherSession.getRevokedAt());
        verify(sessionRepository, never()).save(any(RefreshSession.class));
    }

    @Test
    void testGetUserSessions_ExcludesRevokedSessions() {
        RefreshSession revokedSession = RefreshSession.builder()
                .id(3L)
                .user(testUser)
                .tokenHash("hash3")
                .revokedAt(LocalDateTime.now())
                .build();

        List<RefreshSession> sessions = Arrays.asList(testSession1, revokedSession);
        when(sessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(sessions);

        List<SessionDTO> result = sessionService.getUserSessions(1L);

        // Revoked sessions should be excluded by the repository query
        assertEquals(2, result.size());
    }
}
