package com.xiyu.bid.bidresult.core;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BidResultReminderLogicTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 4, 17, 10, 0);

    @Test
    @DisplayName("Should determine correct reminder type based on fetch result")
    void testDetermineType() {
        assertEquals(BidResultReminder.ReminderType.NOTICE, 
            BidResultReminderLogic.determineType(BidResultFetchResult.Result.WON));
        assertEquals(BidResultReminder.ReminderType.REPORT, 
            BidResultReminderLogic.determineType(BidResultFetchResult.Result.LOST));
    }

    @Test
    @DisplayName("Should create new reminder with PENDING status when none exists")
    void testCalculateNewReminder() {
        BidResultFetchResult result = BidResultFetchResult.builder()
                .id(100L)
                .projectId(1L)
                .projectName("Test Project")
                .result(BidResultFetchResult.Result.WON)
                .build();

        BidResultReminder reminder = BidResultReminderLogic.calculateReminderState(
                result, null, 2L, "Owner Name", "Custom Comment",
                3L, "Operator Name", now, false
        );

        assertNotNull(reminder);
        assertEquals(1L, reminder.getProjectId());
        assertEquals(2L, reminder.getOwnerId());
        assertEquals(BidResultReminder.ReminderStatus.PENDING, reminder.getStatus());
        assertEquals(now, reminder.getRemindTime());
        assertEquals("Custom Comment", reminder.getLastReminderComment());
        assertEquals(100L, reminder.getLastResultId());
    }

    @Test
    @DisplayName("Should update existing reminder status and comment")
    void testUpdateExistingReminder() {
        BidResultFetchResult result = BidResultFetchResult.builder()
                .id(101L)
                .projectId(1L)
                .result(BidResultFetchResult.Result.LOST)
                .build();

        BidResultReminder existing = BidResultReminder.builder()
                .id(50L)
                .status(BidResultReminder.ReminderStatus.UPLOADED) // Previously uploaded
                .build();

        BidResultReminder reminder = BidResultReminderLogic.calculateReminderState(
                result, existing, 2L, "Owner Name", "",
                3L, "Operator Name", now, false
        );

        // Even if status was UPLOADED, if we ensure pending, it stays UPLOADED unless we specifically reset it?
        // Wait, the logic said: if (reminder.getStatus() == null || reminder.getStatus() != UPLOADED) { status = PENDING }
        // So if it IS UPLOADED, it stays UPLOADED. 
        assertEquals(BidResultReminder.ReminderStatus.UPLOADED, reminder.getStatus());
        assertEquals("待上传中标资料", reminder.getLastReminderComment());
    }

    @Test
    @DisplayName("Should transition to REMINDED when isSendAction is true")
    void testTransitionToReminded() {
        BidResultFetchResult result = BidResultFetchResult.builder()
                .id(102L)
                .projectId(1L)
                .result(BidResultFetchResult.Result.WON)
                .build();

        BidResultReminder existing = BidResultReminder.builder()
                .id(51L)
                .status(BidResultReminder.ReminderStatus.PENDING)
                .build();

        BidResultReminder reminder = BidResultReminderLogic.calculateReminderState(
                result, existing, 2L, "Owner Name", "Send Now!",
                3L, "Operator Name", now, true
        );

        assertEquals(BidResultReminder.ReminderStatus.REMINDED, reminder.getStatus());
        assertEquals(now, reminder.getRemindTime());
        assertEquals("Send Now!", reminder.getLastReminderComment());
    }
}
