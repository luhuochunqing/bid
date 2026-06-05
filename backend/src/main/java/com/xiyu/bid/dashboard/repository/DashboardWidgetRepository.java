package com.xiyu.bid.dashboard.repository;

import com.xiyu.bid.dashboard.entity.DashboardWidget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, Long> {
    Optional<DashboardWidget> findByCode(String code);
}
