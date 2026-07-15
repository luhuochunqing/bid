package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.domain.valueobject.CustomerLevel;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.DockingMethod;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import lombok.extern.slf4j.Slf4j;

/**
 * 业绩枚举反向解析（Excel 导入用：中文 → 枚举）
 *
 * <p>正向映射（枚举 → 中文）已统一使用各枚举的 {@code displayName()} 方法，
 * 不再需要外部映射函数。
 */
@Slf4j
public final class PerformanceEnumLabels {

    private PerformanceEnumLabels() {}

    // ── 反向解析（中文 → 枚举）──

    public static CustomerType parseCustomerType(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s) {
            case "政府机关/事业单位/高校", "政府机关/事业单位" -> CustomerType.GOVERNMENT_INSTITUTION;
            case "央企" -> CustomerType.CENTRAL_SOE;
            case "地方国企" -> CustomerType.LOCAL_SOE;
            case "民企" -> CustomerType.PRIVATE_ENTERPRISE;
            case "港澳台及外企", "港澳台/外企" -> CustomerType.FOREIGN_HK_MACAO_TW;
            default -> {
                try { yield CustomerType.valueOf(s); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("无效值 \"" + s + "\"，可选值：政府机关/事业单位、央企、地方国企、民企、港澳台及外企"); }
            }
        };
    }

    // 反向解析仍保留 "政府机关/事业单位/高校" 兼容旧 Excel 模板，但枚举 displayName 统一为 "政府机关/事业单位"

    public static ProjectType parseProjectType(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s) {
            case "办公" -> ProjectType.OFFICE;
            case "综合" -> ProjectType.COMPREHENSIVE;
            case "集采" -> ProjectType.COLLECTIVE;
            case "工业品" -> ProjectType.INDUSTRIAL;
            case "其他" -> ProjectType.OTHER;
            default -> {
                try { yield ProjectType.valueOf(s); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("无效值 \"" + s + "\"，可选值：办公、综合、集采、工业品、其他"); }
            }
        };
    }

    public static DockingMethod parseDockingMethod(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s) {
            case "Emall" -> DockingMethod.EMALL;
            case "Punch-out" -> DockingMethod.PUNCH_OUT;
            case "API" -> DockingMethod.API;
            default -> {
                try { yield DockingMethod.valueOf(s); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("无效值 \"" + s + "\"，可选值：Emall、Punch-out、API"); }
            }
        };
    }

    public static CustomerLevel parseCustomerLevel(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s) {
            case "集团" -> CustomerLevel.GROUP;
            case "二级单位" -> CustomerLevel.SUBSIDIARY;
            default -> {
                try { yield CustomerLevel.valueOf(s); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("无效值 \"" + s + "\"，可选值：集团、二级单位"); }
            }
        };
    }
}
