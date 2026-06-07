package com.xiyu.bid.marketinsight.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure core policy for classifying tender titles into industries.
 * No state, no dependencies, no side effects.
 */
public final class IndustryClassificationPolicy {

    private static final String DEFAULT_INDUSTRY = "综合类";

    private static final Map<String, List<String>> INDUSTRY_KEYWORDS;

    private static final Map<String, String> INDUSTRY_COLORS;

    static {
        var map = new LinkedHashMap<String, List<String>>();
        map.put("工具", List.of("扳手", "钳子", "螺丝刀", "电钻", "角磨机", "工具"));
        map.put("工具耗材", List.of("钻头", "锯片", "磨片", "砂纸", "焊条", "耗材"));
        map.put("焊接", List.of("电焊机", "氩弧焊", "焊接", "焊机"));
        map.put("劳保安全", List.of("安全帽", "防护", "劳保", "安全鞋", "安全带", "防护服"));
        map.put("消防", List.of("灭火器", "消火栓", "消防"));
        map.put("工控低压", List.of("PLC", "变频器", "伺服", "断路器", "接触器", "继电器"));
        map.put("电工照明", List.of("电缆", "开关", "LED灯", "防爆灯", "照明", "配电"));
        map.put("办公", List.of("打印机", "办公", "文件柜", "碎纸机", "复印机", "投影仪"));
        map.put("制冷暖通", List.of("空调", "冷风机", "新风机组", "冷却塔", "暖通", "制冷"));
        map.put("建工材料", List.of("水泥", "钢材", "管材", "防水", "建材", "混凝土"));
        map.put("能源电力", List.of("电力", "电网", "发电", "输变电", "变电", "充电桩"));
        map.put("数据中心", List.of("数据中心", "服务器", "机房", "云计算", "存储"));
        map.put("自动化", List.of("自动化改造", "生产线", "智能制造", "机器人"));
        map.put("运维服务", List.of("运维", "外包服务", "IT服务", "维保"));
        map.put("智慧园区", List.of("智慧园区", "信息化建设", "智慧城市", "数字化"));
        map.put("高速公路", List.of("高速公路", "监控", "收费系统", "ETC"));
        map.put("交通运输", List.of("地铁", "铁路", "航空", "港口"));
        INDUSTRY_KEYWORDS = Collections.unmodifiableMap(map);

        var colors = new LinkedHashMap<String, String>();
        colors.put("工具", "blue");
        colors.put("工具耗材", "blue");
        colors.put("焊接", "blue");
        colors.put("劳保安全", "red");
        colors.put("消防", "red");
        colors.put("工控低压", "yellow");
        colors.put("电工照明", "yellow");
        colors.put("办公", "cyan");
        colors.put("制冷暖通", "cyan");
        colors.put("建工材料", "lime");
        colors.put("能源电力", "indigo");
        colors.put("数据中心", "teal");
        colors.put("自动化", "pink");
        colors.put("运维服务", "orange");
        colors.put("智慧园区", "purple");
        colors.put("高速公路", "green");
        colors.put("交通运输", "green");
        colors.put(DEFAULT_INDUSTRY, "gray");
        INDUSTRY_COLORS = Collections.unmodifiableMap(colors);
    }

    private IndustryClassificationPolicy() {
    }

    /**
     * Classify a tender title into an industry category.
     * First matching keyword wins. Defaults to "综合类".
     *
     * @param title tender title to classify
     * @return industry name
     */
    public static String classifyIndustry(final String title) {
        if (title == null || title.isEmpty()) {
            return DEFAULT_INDUSTRY;
        }
        for (var entry : INDUSTRY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (title.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return DEFAULT_INDUSTRY;
    }

    /**
     * Get the display color for an industry.
     *
     * @param industry industry name
     * @return color name, defaults to "gray"
     */
    public static String getColorForIndustry(final String industry) {
        return INDUSTRY_COLORS.getOrDefault(industry, "gray");
    }

    /**
     * Get all defined industry names including the default.
     *
     * @return unmodifiable set of industry names
     */
    public static Set<String> getAllIndustries() {
        var all = new LinkedHashSet<String>();
        all.addAll(INDUSTRY_KEYWORDS.keySet());
        all.add(DEFAULT_INDUSTRY);
        return Set.copyOf(all);
    }
}
