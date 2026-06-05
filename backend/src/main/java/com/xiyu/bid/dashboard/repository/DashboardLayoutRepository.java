package com.xiyu.bid.dashboard.repository;

import com.xiyu.bid.dashboard.entity.DashboardLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DashboardLayoutRepository extends JpaRepository<DashboardLayout, Long> {
    Optional<DashboardLayout> findByCode(String code);
    Optional<DashboardLayout> findByRoleCode(String roleCode);
}
