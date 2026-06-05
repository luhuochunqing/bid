package com.xiyu.bid.bidresult.repository;

import com.xiyu.bid.bidresult.entity.BidResultReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidResultReminderRepository extends JpaRepository<BidResultReminder, Long> {
    List<BidResultReminder> findAllByOrderByRemindTimeDesc();
    long countByStatus(BidResultReminder.ReminderStatus status);
    Optional<BidResultReminder> findFirstByProjectIdAndReminderTypeOrderByRemindTimeDesc(Long projectId, BidResultReminder.ReminderType reminderType);
}
