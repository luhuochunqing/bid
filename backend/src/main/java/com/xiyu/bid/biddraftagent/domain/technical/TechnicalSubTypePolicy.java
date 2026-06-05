// Input: 技术要求文本列表
// Output: 按四种标签分类的技术要点列表（硬指标/功能/兼容性/加分项）
// Pos: biddraftagent/domain/technical — 技术要点子类型分类纯核心策略

package com.xiyu.bid.biddraftagent.domain.technical;

import com.xiyu.bid.biddraftagent.domain.RegexKeywordMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * 技术要点子类型分类策略。
 * 基于关键词匹配，将技术要求文本归类为四种标签：
 * 硬指标、功能、兼容性、加分项。
 * 如果无法匹配任何标签，归类为 FUNCTIONAL（功能）。
 */
public class TechnicalSubTypePolicy {

    // 硬指标关键词：参数规格、强制性数值要求
    private static final List<String> HARD_INDEX_KEYWORDS = List.of(
            "≥", "≤", ">", "<", "不低于", "不小于", "不大于",
            "GHz", "MHz", "TB", "GB", "MB", "KB",
            "内存", "硬盘", "CPU", "主频", "核", "线程",
            "并发", "吞吐量", "响应时间", "延迟", "带宽",
            "存储容量", "传输速率", "分辨率", "帧率",
            "接口数量", "端口数量", "功率", "电压",
            "达到", "满足",
            "容量", "规格", "配置要求", "最低配置"
    );

    // 兼容性关键词
    private static final List<String> COMPATIBILITY_KEYWORDS = List.of(
            "兼容", "适配", "支持.*系统", "支持.*平台",
            "跨平台", "国产化", "信创", "自主可控",
            "Linux", "Windows", "国产操作系统",
            "中间件", "数据库.*兼容", "浏览器.*兼容",
            "接口协议", "对接", "集成.*系统", "标准化接口"
    );

    // 加分项关键词
    private static final List<String> BONUS_KEYWORDS = List.of(
            "优先", "加分", "加分项", "优选",
            "经验优先", "案例优先",
            "同类项目", "项目经验",
            "认证优先", "资质优先",
            "建议", "推荐使用",
            "额外加分", "附加分"
    );

    // 功能关键词（默认值，当不匹配其他时使用）
    private static final List<String> FUNCTIONAL_KEYWORDS = List.of(
            "功能", "支持", "提供", "实现",
            "展示", "查询", "统计", "导出",
            "导入", "管理", "配置", "监控",
            "告警", "通知", "权限", "用户管理",
            "日志", "备份", "恢复", "部署"
    );

    /**
     * 分类单个技术要求文本，返回对应的子类型。
     * 按优先级：硬指标 > 兼容性 > 加分项 > 功能
     */
    public TechnicalSubType classify(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            return TechnicalSubType.FUNCTIONAL;
        }

        if (RegexKeywordMatcher.matchesAny(requirement, HARD_INDEX_KEYWORDS)) return TechnicalSubType.HARD_INDEX;
        if (RegexKeywordMatcher.matchesAny(requirement, COMPATIBILITY_KEYWORDS)) return TechnicalSubType.COMPATIBILITY;
        if (RegexKeywordMatcher.matchesAny(requirement, BONUS_KEYWORDS)) return TechnicalSubType.BONUS;
        return TechnicalSubType.FUNCTIONAL;
    }

    /**
     * 分类一组技术要求，返回带标签的结果列表。
     */
    public List<TechnicalRequirementItem> classifyAll(List<String> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        List<TechnicalRequirementItem> items = new ArrayList<>();
        for (String req : requirements) {
            items.add(new TechnicalRequirementItem(req, classify(req)));
        }
        return items;
    }

}
