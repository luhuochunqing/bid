// Input: Raw location string (from ProjectInitiationDetails.headquartersLocation or Tender.region)
// Output: Standard Province/Municipality name (e.g. "江苏省", "北京市", "内蒙古自治区")
// Pos: Domain/纯函数工具类
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

/**
 * 区域维度省份提取器（纯函数，无状态，完全避免在 SQL 中进行跨表 collation 拼接）。
 */
public final class RegionDimensionExtractor {

    private RegionDimensionExtractor() {
    }

    /**
     * 将原始地址或区域字符串解析为一级省级行政区单位。
     *
     * @param location 原始字符串，例如 "江苏省淮安市"、"北京市北京市"、"内蒙古自治区呼和浩特市"
     * @return 一级行政区名称，例如 "江苏省"、"北京市"、"内蒙古自治区"；无法解析时返回 "其他"
     */
    public static String extractProvince(String location) {
        if (location == null || location.isBlank()) {
            return "其他";
        }
        String loc = location.trim();

        // 4 个直辖市
        if (loc.startsWith("北京市") || loc.equals("北京")) return "北京市";
        if (loc.startsWith("天津市") || loc.equals("天津")) return "天津市";
        if (loc.startsWith("上海市") || loc.equals("上海")) return "上海市";
        if (loc.startsWith("重庆市") || loc.equals("重庆")) return "重庆市";

        // 特别行政区 & 台湾省
        if (loc.startsWith("香港")) return "香港特别行政区";
        if (loc.startsWith("澳门")) return "澳门特别行政区";
        if (loc.startsWith("台湾")) return "台湾省";

        // 5 个自治区
        if (loc.startsWith("内蒙古")) return "内蒙古自治区";
        if (loc.startsWith("新疆")) return "新疆维吾尔自治区";
        if (loc.startsWith("西藏")) return "西藏自治区";
        if (loc.startsWith("宁夏")) return "宁夏回族自治区";
        if (loc.startsWith("广西")) return "广西壮族自治区";

        int ziIdx = loc.indexOf("自治区");
        if (ziIdx > 0) {
            return loc.substring(0, ziIdx + 3);
        }

        // 23 个省
        int shengIdx = loc.indexOf("省");
        if (shengIdx > 0) {
            return loc.substring(0, shengIdx + 1);
        }

        return loc;
    }
}
