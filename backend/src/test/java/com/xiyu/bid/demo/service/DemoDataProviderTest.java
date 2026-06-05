package com.xiyu.bid.demo.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataProviderTest {

    private final DemoDataProvider provider = new DemoDataProvider();

    @Test
    void projectAndTenderDemoIds_shouldBeNegative() {
        assertThat(provider.getDemoProjects()).isNotEmpty();
        assertThat(provider.getDemoProjects()).allMatch(item -> item.getId() < 0);
        assertThat(provider.getDemoTenders()).isNotEmpty();
        assertThat(provider.getDemoTenders()).allMatch(item -> item.getId() < 0);
    }

    @Test
    void scheduleEvents_shouldBeFilteredByDateRange() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(6);

        assertThat(provider.getDemoScheduleEvents(start, end))
                .isNotEmpty()
                .allMatch(event -> !event.getEventDate().isBefore(start) && !event.getEventDate().isAfter(end));
    }
}
