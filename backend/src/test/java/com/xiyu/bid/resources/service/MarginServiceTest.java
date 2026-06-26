package com.xiyu.bid.resources.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MarginService} JPA parameter binding.
 *
 * <p>Root cause of margin 500 (CO-358 comments): {@link MarginQueryRole}
 * SQL fragments reference {@code :muid} for non-admin/manager roles, but
 * {@link MarginService} never called {@code setParameter("muid", uid)},
 * causing "Parameter 'muid' not set" at query execution → 500.
 *
 * <p>These tests verify the fix: {@code muid} is bound whenever the role
 * policy's SQL fragment references it.
 */
@ExtendWith(MockitoExtension.class)
class MarginServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private Query query;

    @InjectMocks
    private MarginService marginService;

    // ── getSummary ──────────────────────────────────────────────────

    @Test
    void getSummary_staffRole_bindsMuidParam() {
        // Staff fragment contains :muid — must be bound or query throws
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{0, 0, 0L, 0, 0L});

        marginService.getSummary(42L, "staff");

        verify(query).setParameter("muid", 42L);
    }

    @Test
    void getSummary_bidProjectLeaderRole_bindsMuidParam() {
        // ownerFragment also contains :muid
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{0, 0, 0L, 0, 0L});

        marginService.getSummary(42L, "bid-projectLeader");

        verify(query).setParameter("muid", 42L);
    }

    @Test
    void getSummary_adminRole_doesNotBindMuid() {
        // Admin fragment is empty — SQL has no :muid, binding would throw
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{0, 0, 0L, 0, 0L});

        marginService.getSummary(42L, "admin");

        verify(query, never()).setParameter(eq("muid"), any());
    }

    // ── getList ─────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getList_staffRole_bindsMuidParam() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        marginService.getList(42L, "staff", Map.of(), 1, 20);

        verify(query).setParameter("muid", 42L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getList_adminRole_doesNotBindMuid() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        marginService.getList(42L, "admin", Map.of(), 1, 20);

        verify(query, never()).setParameter(eq("muid"), any());
    }

    // ── getCount ────────────────────────────────────────────────────

    @Test
    void getCount_staffRole_bindsMuidParam() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);

        marginService.getCount(42L, "staff", Map.of());

        verify(query).setParameter("muid", 42L);
    }

    @Test
    void getCount_adminRole_doesNotBindMuid() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);

        marginService.getCount(42L, "admin", Map.of());

        verify(query, never()).setParameter(eq("muid"), any());
    }
}
