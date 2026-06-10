package com.xiyu.bid.platform.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blueprint-aligned field contract for {@link BorrowAccountRequest}.
 *
 * <p>IJTHPB 修复后，{@code expectedReturnDate} 才是预计归还日期的权威字段；
 * {@code dueHours} 仅保留为向后兼容的 fallback。新业务代码应优先传
 * expectedReturnDate。
 */
@DisplayName("BorrowAccountRequest blueprint-aligned field contract")
class BorrowAccountRequestTest {

    @Test
    @DisplayName("expectedReturnDate is the canonical return date field")
    void expectedReturnDate_isCanonical() {
        BorrowAccountRequest req = BorrowAccountRequest.builder()
                .borrowedBy(1L)
                .purpose("投标使用")
                .projectId(42L)
                .expectedReturnDate("2026-12-31")
                .dueHours(7)  // legacy; should be ignored when expectedReturnDate is set
                .build();

        assertEquals(1L, req.getBorrowedBy());
        assertEquals("投标使用", req.getPurpose());
        assertEquals(42L, req.getProjectId());
        assertEquals("2026-12-31", req.getExpectedReturnDate());
        assertEquals(7, req.getDueHours());
    }

    @Test
    @DisplayName("expectedReturnDate accepts full ISO date-time as well as YYYY-MM-DD")
    void expectedReturnDate_acceptsIsoVariants() {
        BorrowAccountRequest dateOnly = BorrowAccountRequest.builder()
                .expectedReturnDate("2027-01-15")
                .build();
        BorrowAccountRequest dateTime = BorrowAccountRequest.builder()
                .expectedReturnDate("2027-01-15T18:30:00")
                .build();
        assertEquals("2027-01-15", dateOnly.getExpectedReturnDate());
        assertEquals("2027-01-15T18:30:00", dateTime.getExpectedReturnDate());
    }

    @Test
    @DisplayName("Builder + Lombok no-args constructor round-trip the new fields")
    void builderAndNoArgsConstructor_roundTrip() {
        BorrowAccountRequest a = BorrowAccountRequest.builder()
                .borrowedBy(2L)
                .projectId(7L)
                .expectedReturnDate(LocalDate.now().plusDays(3).toString())
                .build();
        BorrowAccountRequest b = new BorrowAccountRequest();
        b.setBorrowedBy(a.getBorrowedBy());
        b.setProjectId(a.getProjectId());
        b.setExpectedReturnDate(a.getExpectedReturnDate());

        assertEquals(a.getBorrowedBy(), b.getBorrowedBy());
        assertEquals(a.getProjectId(), b.getProjectId());
        assertEquals(a.getExpectedReturnDate(), b.getExpectedReturnDate());
    }
}
