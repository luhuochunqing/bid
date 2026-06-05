package com.xiyu.bid.performance.domain.service;

import com.xiyu.bid.performance.domain.valueobject.ContractStatus;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 纯核心策略：合同状态自动计算（蓝图 4.5）
 *
 * 规则：
 *   - 央企：截止日期前 180 天进入"即将到期"
 *   - 其他客户类型：截止日期前 90 天进入"即将到期"
 *   - 截止日期 < 今天 → 已到期
 *   - 其余 → 履约中
 *
 * 无副作用：不读写数据库/API/文件/时间/日志
 */
public final class ContractStatusPolicy {

    private ContractStatusPolicy() { }

    /** 央企的到期提醒阈值（天） */
    public static final int CENTRAL_SOE_THRESHOLD_DAYS = 180;

    /** 非央企的到期提醒阈值（天） */
    public static final int DEFAULT_THRESHOLD_DAYS = 90;

    /**
     * 根据客户类型获取到期提醒阈值
     * @param customerType 客户类型
     * @return 到期提醒天数阈值
     */
    public static int getExpiryThreshold(CustomerType customerType) {
        if (customerType == null) {
            return DEFAULT_THRESHOLD_DAYS;
        }
        return customerType == CustomerType.CENTRAL_SOE
                ? CENTRAL_SOE_THRESHOLD_DAYS
                : DEFAULT_THRESHOLD_DAYS;
    }

    /**
     * 计算到期天数（截止日期 - 今天）
     * @param expiryDate 截止日期
     * @param today 当天日期
     * @return 到期天数（负数表示已过期）
     */
    public static long calculateDaysRemaining(LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null || today == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(today, expiryDate);
    }

    /**
     * 自动计算合同状态
     * @param customerType 客户类型
     * @param expiryDate 截止日期
     * @param today 当天日期
     * @return 合同状态
     */
    public static ContractStatus calculateStatus(
            CustomerType customerType, LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) {
            return ContractStatus.IN_PERFORMANCE;
        }
        long daysRemaining = calculateDaysRemaining(expiryDate, today);
        if (daysRemaining < 0) {
            return ContractStatus.EXPIRED;
        }
        int threshold = getExpiryThreshold(customerType);
        if (daysRemaining <= threshold) {
            return ContractStatus.EXPIRING;
        }
        return ContractStatus.IN_PERFORMANCE;
    }

    /**
     * 生成到期提醒文本
     * @param customerType 客户类型
     * @param expiryDate 截止日期
     * @param today 当天日期
     * @return 提醒文本，无提醒时返回 null
     */
    public static String calculateExpiryReminder(
            CustomerType customerType, LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) {
            return null;
        }
        long daysRemaining = calculateDaysRemaining(expiryDate, today);
        if (daysRemaining < 0) {
            return "合同已到期";
        }
        int threshold = getExpiryThreshold(customerType);
        if (daysRemaining <= threshold) {
            return "合同即将到期";
        }
        return null;
    }
}
