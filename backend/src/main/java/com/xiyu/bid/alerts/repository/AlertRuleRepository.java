package com.xiyu.bid.alerts.repository;

import com.xiyu.bid.alerts.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByEnabledTrue();

    List<AlertRule> findByType(AlertRule.AlertType type);

    List<AlertRule> findByCreatedBy(String createdBy);
}
