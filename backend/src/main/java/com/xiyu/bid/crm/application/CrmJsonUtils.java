package com.xiyu.bid.crm.application;

/**
 * CRM JSON 工具方法。
 */
final class CrmJsonUtils {

    private CrmJsonUtils() {
    }

    static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
