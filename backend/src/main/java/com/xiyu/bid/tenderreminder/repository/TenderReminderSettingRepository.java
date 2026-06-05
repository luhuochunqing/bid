package com.xiyu.bid.tenderreminder.repository;

import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 标讯提醒设置数据访问层
 */
@Repository
public interface TenderReminderSettingRepository extends JpaRepository<TenderReminderSetting, Long> {

    /**
     * 根据标讯ID查询所有提醒设置
     */
    List<TenderReminderSetting> findByTenderId(Long tenderId);

    /**
     * 根据标讯ID和提醒类型查询
     */
    Optional<TenderReminderSetting> findByTenderIdAndReminderType(Long tenderId, ReminderType reminderType);

    /**
     * 查询标讯的某种提醒类型的唯一设置
     */
    default Optional<TenderReminderSetting> findUniqueByTenderIdAndReminderType(Long tenderId, ReminderType reminderType) {
        return findByTenderIdAndReminderType(tenderId, reminderType);
    }

    /**
     * 查询所有启用的提醒设置
     */
    List<TenderReminderSetting> findByEnabledTrue();

    /**
     * 查询标讯的启用的提醒设置
     */
    List<TenderReminderSetting> findByTenderIdAndEnabledTrue(Long tenderId);

    /**
     * 查询需要在指定时间范围内提醒的设置
     * 例如：查询报名截止时间在 now 到 now+hours 之间的设置
     */
    @Query("""
        SELECT r FROM TenderReminderSetting r
        JOIN Tender t ON r.tenderId = t.id
        WHERE r.enabled = true
        AND r.reminderType = :reminderType
        AND r.lastNotifiedAt IS NULL
        AND (
            (:reminderType = 'REGISTRATION_DEADLINE' AND t.registrationDeadline IS NOT NULL
                AND t.registrationDeadline BETWEEN :startTime AND :endTime)
            OR
            (:reminderType = 'BID_OPENING' AND t.bidOpeningTime IS NOT NULL
                AND t.bidOpeningTime BETWEEN :startTime AND :endTime)
        )
        """)
    List<TenderReminderSetting> findSettingsDueForReminder(
            @Param("reminderType") ReminderType reminderType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 删除标讯的所有提醒设置
     */
    void deleteByTenderId(Long tenderId);

    /**
     * 检查标讯是否有某种类型的提醒设置
     */
    boolean existsByTenderIdAndReminderType(Long tenderId, ReminderType reminderType);
}
