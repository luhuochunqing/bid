package com.xiyu.bid.projectworkflow.repository;

import com.xiyu.bid.projectworkflow.entity.ProjectReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectReminderRepository extends JpaRepository<ProjectReminder, Long> {

    List<ProjectReminder> findByProjectIdOrderByRemindAtDesc(Long projectId);
}
