package com.xiyu.bid.tenderreminder.repository;

import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 标讯提醒发送日志数据访问层
 */
@Repository
public interface TenderReminderLogRepository extends JpaRepository<TenderReminderLog, Long> {

    /**
     * 根据提醒设置ID查询日志
     */
    List<TenderReminderLog> findByReminderSettingId(Long reminderSettingId);

    /**
     * 根据标讯ID查询日志
     */
    List<TenderReminderLog> findByTenderIdOrderBySentAtDesc(Long tenderId);

    /**
     * 检查是否已发送给指定用户
     */
    boolean existsByReminderSettingIdAndRecipientUserId(Long reminderSettingId, Long recipientUserId);

    /**
     * 根据提醒设置ID和通知对象删除
     */
    void deleteByReminderSettingIdAndRecipientUserId(Long reminderSettingId, Long recipientUserId);
}
