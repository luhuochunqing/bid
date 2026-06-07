package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.domain.valueobject.CustomerLevel;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.DockingMethod;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;

/**
 * 业绩枚举中文映射（导入导出共用）
 */
public final class PerformanceEnumLabels {

    private PerformanceEnumLabels() {}

    public static String customerType(String code) {
        if (code == null) return "";
        return switch (code) {
            case "GOVERNMENT_INSTITUTION" -> "政府机关/事业单位";
            case "CENTRAL_SOE" -> "央企";
            case "LOCAL_SOE" -> "地方国企";
            case "PRIVATE_ENTERPRISE" -> "民企";
            case "FOREIGN_HK_MACAO_TW" -> "港澳台及外企";
            default -> code;
        };
    }

    public static String projectType(String code) {
        if (code == null) return "";
        return switch (code) {
            case "OFFICE" -> "办公";
            case "COMPREHENSIVE" -> "综合";
            case "CENTRALIZED" -> "集采";
            case "INDUSTRIAL" -> "工业品";
            case "OTHER" -> "其他";
            default -> code;
        };
    }

    public static String dockingMethod(String code) {
        if (code == null) return "";
        return switch (code) {
            case "EMALL" -> "Emall";
            case "PUNCH_OUT" -> "Punch-out";
            default -> code;
        };
    }

    public static String customerLevel(String code) {
        if (code == null) return "";
        return switch (code) {
            case "GROUP" -> "集团";
            case "SUBSIDIARY" -> "二级单位";
            default -> code;
        };
    }

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
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("无效的客户类型: " + s); }
            }
        };
    }

    public static ProjectType parseProjectType(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s) {
            case "办公" -> ProjectType.OFFICE;
            case "综合" -> ProjectType.COMPREHENSIVE;
            case "集采" -> ProjectType.CENTRALIZED;
            case "工业品" -> ProjectType.INDUSTRIAL;
            case "其他" -> ProjectType.OTHER;
            default -> {
                try { yield ProjectType.valueOf(s); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("无效的项目类型: " + s); }
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
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("无效的对接方式: " + s); }
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
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("无效的客户级别: " + s); }
            }
        };
    }
}
