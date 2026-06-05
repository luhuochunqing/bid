// Input: 当前数据库连接能力与上传队列全局抢占请求
// Output: 数据库兼容的全局抢占锁申请与释放结果
// Pos: TenderUpload/Service
// 维护声明: 仅封装数据库 advisory lock 差异，任务调度规则仍由 TenderTaskWorkerService 维护.
package com.xiyu.bid.tenderupload.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
class TenderTaskClaimLock {

    private static final String GLOBAL_CLAIM_LOCK = "tender-task-global-claim";

    private final JdbcTemplate jdbcTemplate;
    private volatile Boolean mysqlAdvisoryLockSupported;

    boolean tryAcquire() {
        if (!supportsMysqlAdvisoryLock()) {
            return true;
        }
        try {
            Integer gotLock = jdbcTemplate.queryForObject("select GET_LOCK(?, 0)", Integer.class, GLOBAL_CLAIM_LOCK);
            return gotLock != null && gotLock == 1;
        } catch (RuntimeException ex) {
            log.warn("Global claim lock unavailable, fallback to non-lock mode: {}", ex.getMessage());
            return true;
        }
    }

    void release() {
        if (!supportsMysqlAdvisoryLock()) {
            return;
        }
        try {
            jdbcTemplate.queryForObject("select RELEASE_LOCK(?)", Integer.class, GLOBAL_CLAIM_LOCK);
        } catch (RuntimeException ex) {
            log.warn("Failed to release global claim lock: {}", ex.getMessage());
        }
    }

    private boolean supportsMysqlAdvisoryLock() {
        Boolean cached = mysqlAdvisoryLockSupported;
        if (cached != null) {
            return cached;
        }
        try {
            String productName = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());
            String normalized = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
            boolean supported = normalized.contains("mysql") || normalized.contains("mariadb");
            mysqlAdvisoryLockSupported = supported;
            return supported;
        } catch (RuntimeException ex) {
            log.warn("Unable to determine database advisory lock support, fallback to non-lock mode: {}", ex.getMessage());
            mysqlAdvisoryLockSupported = false;
            return false;
        }
    }
}
