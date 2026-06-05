// Input: CalendarEvent实体
// Output: CalendarEvent数据访问接口
// Pos: Repository/数据访问层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.repository;

import com.xiyu.bid.calendar.entity.CalendarEvent;
import com.xiyu.bid.calendar.entity.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 日历事件数据访问接口
 */
@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    /**
     * 查找日期范围内的事件
     * @param start 开始日期
     * @param end 结束日期
     * @return 事件列表
     */
    List<CalendarEvent> findByEventDateBetween(LocalDate start, LocalDate end);

    /**
     * 根据项目ID查找事件
     * @param projectId 项目ID
     * @return 事件列表
     */
    List<CalendarEvent> findByProjectId(Long projectId);

    /**
     * 根据事件类型查找
     * @param type 事件类型
     * @return 事件列表
     */
    List<CalendarEvent> findByEventType(EventType type);

    /**
     * 查找所有紧急事件
     * @return 紧急事件列表
     */
    List<CalendarEvent> findByIsUrgentTrue();

    /**
     * 查找即将到来的事件
     * @param startDate 开始日期
     * @return 事件列表
     */
    @Query("SELECT e FROM CalendarEvent e WHERE e.eventDate >= :startDate ORDER BY e.eventDate ASC")
    List<CalendarEvent> findUpcomingEvents(@Param("startDate") LocalDate startDate);
}
