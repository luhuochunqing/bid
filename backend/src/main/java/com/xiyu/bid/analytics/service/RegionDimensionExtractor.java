// Input: Raw location string (from ProjectInitiationDetails.headquartersLocation or Tender.region)
// Output: Standard Province/Municipality name (e.g. "江苏省", "北京市", "内蒙古自治区")
// Pos: Domain/纯函数工具类
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 区域维度省份提取器（纯函数，无状态，完全避免在 SQL 中进行跨表 collation 拼接）。
 * <p>统一取值口径：总部所在地（headquartersLocation）非空白优先，空白回退标讯区域（t.region），
 * 两者均空白视为无区域（见 {@link #resolveRawLocation}）。
 */
public final class RegionDimensionExtractor {

    /**
     * 无后缀短省名 → 标准全称映射（31 个大陆省级单位 + 港澳台），
     * 兼容历史存量数据中的短省名存储格式（如 "江苏"、"内蒙古"、"北京"）。
     * 全称口径与 {@code TenderRegionCatalog} 省份表一致。
     */
    private static final Map<String, String> SHORT_NAME_ALIASES = buildShortNameAliases();

    /**
     * 存在前缀歧义的短省名 key："海南" 是青海省"海南藏族自治州"/"海南州"的前缀。
     * 此类 key 仅在与短省名精确相等时归一化，前缀值继续走兜底保持独立桶，
     * 避免被静默并入错误省份（吉林/吉林市同名但同属吉林省，不在此列）。
     */
    private static final Set<String> EXACT_MATCH_ONLY_KEYS = Set.of("海南");

    private RegionDimensionExtractor() {
    }

    /**
     * 区域双源统一取值：总部所在地非空白优先，空白回退标讯区域，均为空白返回 null。
     * 空串与空白一律视为空（消除 SQL 层 {@code is null} 与 {@code <> ''} 混用的语义差异）。
     *
     * @param headquartersLocation 立项详情总部所在地（可为 null/空白）
     * @param tenderRegion         标讯区域（可为 null/空白）
     * @return 有效原始区域字符串（已 trim），无有效值返回 null
     */
    public static String resolveRawLocation(String headquartersLocation, String tenderRegion) {
        if (headquartersLocation != null && !headquartersLocation.isBlank()) {
            return headquartersLocation.trim();
        }
        if (tenderRegion != null && !tenderRegion.isBlank()) {
            return tenderRegion.trim();
        }
        return null;
    }

    /**
     * 将原始地址或区域字符串解析为一级省级行政区单位。
     *
     * @param location 原始字符串，例如 "江苏省淮安市"、"北京市北京市"、"内蒙古自治区呼和浩特市"
     * @return 一级行政区名称，例如 "江苏省"、"北京市"、"内蒙古自治区"；
     *         null/空白返回 "其他"；其余无法归一化的原串原样返回，保持独立桶
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

        // 无后缀短省名（历史存量格式，如 "江苏"、"江苏淮安"、"北京"）；
        // 歧义 key（见 EXACT_MATCH_ONLY_KEYS）仅精确匹配才归一化
        for (Map.Entry<String, String> alias : SHORT_NAME_ALIASES.entrySet()) {
            String key = alias.getKey();
            boolean matched = EXACT_MATCH_ONLY_KEYS.contains(key)
                    ? loc.equals(key)
                    : loc.startsWith(key);
            if (matched) {
                return alias.getValue();
            }
        }

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

    private static Map<String, String> buildShortNameAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        // 4 个直辖市
        aliases.put("北京", "北京市");
        aliases.put("天津", "天津市");
        aliases.put("上海", "上海市");
        aliases.put("重庆", "重庆市");
        // 22 个普通省（台湾省在下方港澳台段）
        aliases.put("河北", "河北省");
        aliases.put("山西", "山西省");
        aliases.put("辽宁", "辽宁省");
        aliases.put("吉林", "吉林省");
        aliases.put("黑龙江", "黑龙江省");
        aliases.put("江苏", "江苏省");
        aliases.put("浙江", "浙江省");
        aliases.put("安徽", "安徽省");
        aliases.put("福建", "福建省");
        aliases.put("江西", "江西省");
        aliases.put("山东", "山东省");
        aliases.put("河南", "河南省");
        aliases.put("湖北", "湖北省");
        aliases.put("湖南", "湖南省");
        aliases.put("广东", "广东省");
        aliases.put("海南", "海南省");
        aliases.put("四川", "四川省");
        aliases.put("贵州", "贵州省");
        aliases.put("云南", "云南省");
        aliases.put("陕西", "陕西省");
        aliases.put("甘肃", "甘肃省");
        aliases.put("青海", "青海省");
        // 5 个自治区（为全集完整性保留：已被上方前置分支遮蔽，实际不会命中）
        aliases.put("内蒙古", "内蒙古自治区");
        aliases.put("广西", "广西壮族自治区");
        aliases.put("西藏", "西藏自治区");
        aliases.put("宁夏", "宁夏回族自治区");
        aliases.put("新疆", "新疆维吾尔自治区");
        // 港澳台（为全集完整性保留：已被上方前置分支遮蔽，实际不会命中）
        aliases.put("台湾", "台湾省");
        aliases.put("香港", "香港特别行政区");
        aliases.put("澳门", "澳门特别行政区");
        return Map.copyOf(aliases);
    }
}
