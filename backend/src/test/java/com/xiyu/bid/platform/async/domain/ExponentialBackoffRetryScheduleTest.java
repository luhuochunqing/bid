package com.xiyu.bid.platform.async.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExponentialBackoffRetryScheduleTest {
    @Test
    void shouldCalculateExponentialBackoffWithCap() {
        ExponentialBackoffRetrySchedule schedule = new ExponentialBackoffRetrySchedule(300, 3600, 4);

        assertEquals(300, schedule.nextDelaySeconds(0));
        assertEquals(600, schedule.nextDelaySeconds(1));
        assertEquals(1200, schedule.nextDelaySeconds(2));
        assertEquals(2400, schedule.nextDelaySeconds(3));
        assertEquals(3600, schedule.nextDelaySeconds(4));
        assertEquals(3600, schedule.nextDelaySeconds(10));
    }
}
