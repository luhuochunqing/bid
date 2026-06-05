package com.xiyu.bid.tenderupload.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderTaskClaimLockTest {

    private static final String LOCK_NAME = "tender-task-global-claim";

    @Mock
    private JdbcTemplate jdbcTemplate;

    private TenderTaskClaimLock claimLock;

    @BeforeEach
    void setUp() {
        claimLock = new TenderTaskClaimLock(jdbcTemplate);
    }

    @Test
    void tryAcquire_shouldSkipMysqlAdvisoryLockForNonMysql() {
        when(jdbcTemplate.execute(ArgumentMatchers.<ConnectionCallback<String>>any()))
                .thenReturn("H2");

        assertTrue(claimLock.tryAcquire());
        claimLock.release();

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), any());
    }

    @Test
    void tryAcquire_shouldUseMysqlAdvisoryLockForMysql() {
        when(jdbcTemplate.execute(ArgumentMatchers.<ConnectionCallback<String>>any()))
                .thenReturn("MySQL");
        when(jdbcTemplate.queryForObject("select GET_LOCK(?, 0)", Integer.class, LOCK_NAME))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject("select RELEASE_LOCK(?)", Integer.class, LOCK_NAME))
                .thenReturn(1);

        assertTrue(claimLock.tryAcquire());
        claimLock.release();

        verify(jdbcTemplate).queryForObject("select GET_LOCK(?, 0)", Integer.class, LOCK_NAME);
        verify(jdbcTemplate).queryForObject("select RELEASE_LOCK(?)", Integer.class, LOCK_NAME);
    }
}
