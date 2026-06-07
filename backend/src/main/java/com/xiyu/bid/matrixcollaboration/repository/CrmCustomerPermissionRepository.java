package com.xiyu.bid.matrixcollaboration.repository;

import com.xiyu.bid.matrixcollaboration.entity.CrmCustomerPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrmCustomerPermissionRepository extends JpaRepository<CrmCustomerPermission, Long> {
    List<CrmCustomerPermission> findByCustomerId(String customerId);
    List<CrmCustomerPermission> findByUserId(Long userId);
    void deleteByCustomerId(String customerId);
}
