package com.xiyu.bid.casework.domain.policy;

import java.util.Locale;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：业务子主题（Sub-Topic）推断与匹配加权策略。
 *
 * <p>遵循 FP-Java 规范：无状态纯函数、无 Spring 依赖、入参不可变、100% 可单测。</p>
 */
public final class CaseSubTopicPolicy {

    public static final int SUB_TOPIC_EXACT_MATCH_SCORE = 25;
    public static final int SUB_TOPIC_TEXT_MATCH_SCORE = 15;

    public enum SubTopic {
        SLA_SERVICE("售后服务与SLA保障", new String[]{
                "售后服务", "售后保障", "服务承诺", "运维保障", "运维服务", "服务体系",
                "故障恢复", "响应时间", "7x24", "7*24", "sla", "日常巡检", "维保服务", "运行维护", "备件支持"
        }),
        EMERGENCY_PLAN("应急预案与突发事件响应", new String[]{
                "应急预案", "应急保障", "应急响应", "突发事件", "突发故障", "应急演练",
                "防汛", "防台", "备用方案", "容灾备份", "紧急情况", "应急处理"
        }),
        INTEGRATION("系统对接与数据集成", new String[]{
                "系统对接", "接口规范", "接口设计", "接口方案", "数据迁移", "数据接口",
                "api接口", "erp对接", "打通对接", "系统集成", "联调测试", "单点登录", "外部系统对接"
        }),
        SECURITY_COMPLIANCE("网络安全与等保合规", new String[]{
                "网络安全", "数据安全", "等保", "信息安全", "保密承诺", "保密措施",
                "数据加密", "防攻击", "漏洞防护", "权限管理", "身份认证", "安全合规", "访问控制"
        }),
        LOGISTICS_WAREHOUSE("仓储管理与物流配送", new String[]{
                "仓储管理", "物流配送", "仓储布局", "配送方案", "配送时效", "运输保障",
                "车辆调配", "库存管理", "供应链保障", "库房管理", "最后一公里"
        }),
        QUALITY_CONTROL("质量保证与检验验收", new String[]{
                "质量保证", "质量控制", "质量管理", "验收标准", "验收方案", "检验检测",
                "正品保障", "假一赔十", "质量检验", "质量溯源", "退换货", "不良品处理"
        }),
        DELIVERY_PROGRESS("项目进度与实施计划", new String[]{
                "项目进度", "实施计划", "实施方案", "交付计划", "项目计划", "里程碑",
                "工期保障", "时间节点", "交付节点", "进度控制", "阶段划分"
        }),
        SYS_ARCH("系统架构与技术方案", new String[]{
                "技术架构", "架构设计", "系统架构", "总体架构", "总体设计", "技术路线",
                "技术方案", "组网方案", "组网设计", "系统拓扑", "功能架构", "应用架构",
                "部署架构", "高可用设计", "高可用架构", "数据库设计", "建设方案"
        }),
        ORG_STAFF("组织架构与人员配置", new String[]{
                "项目组织", "团队配置", "人员配备", "项目经理", "项目团队", "岗位职责",
                "组织机构", "人员资质", "人员配置", "专家团队", "人员培训", "培训方案"
        }),
        COMMERCIAL_TERMS("商务响应与交付承诺", new String[]{
                "商务条款", "商务偏离", "付款方式", "结算方式", "交货期", "质保期承诺",
                "交货地点", "商务响应", "合同条款"
        }),
        OTHER("其他方案", new String[]{});

        private final String title;
        private final String[] keywords;

        SubTopic(String title, String[] keywords) {
            this.title = title;
            this.keywords = keywords;
        }

        public String getTitle() {
            return title;
        }

        public String[] getKeywords() {
            return keywords;
        }
    }

    public record SubTopicMatchResult(int score, String reason, SubTopic inferredTopic) {}

    private CaseSubTopicPolicy() {}

    /**
     * 根据评分项维度/标题和详情推断其所属的招投标业务子主题。
     */
    public static SubTopic inferSubTopic(String dim, String detail) {
        String dimText = dim != null ? dim.trim().toLowerCase(Locale.ROOT) : "";
        String detailText = detail != null ? detail.trim().toLowerCase(Locale.ROOT) : "";

        // 1. 优先通过标题/维度推断（最高信源优先级）
        if (hasText(dimText)) {
            for (SubTopic topic : SubTopic.values()) {
                if (topic == SubTopic.OTHER) continue;
                for (String kw : topic.getKeywords()) {
                    if (dimText.contains(kw)) {
                        return topic;
                    }
                }
            }
        }

        // 2. 标题未命中时，通过详情全文推断
        if (hasText(detailText)) {
            SubTopic bestTopic = SubTopic.OTHER;
            int maxHits = 0;
            for (SubTopic topic : SubTopic.values()) {
                if (topic == SubTopic.OTHER) continue;
                int hits = 0;
                for (String kw : topic.getKeywords()) {
                    if (detailText.contains(kw)) {
                        hits++;
                    }
                }
                if (hits > maxHits && hits >= 2) {
                    maxHits = hits;
                    bestTopic = topic;
                }
            }
            if (bestTopic != SubTopic.OTHER) {
                return bestTopic;
            }
        }

        return SubTopic.OTHER;
    }

    /**
     * 计算评分项推断子主题与候选案例之间的契合度得分。
     */
    public static SubTopicMatchResult calculateSubTopicScore(
            SubTopic inferredTopic, String candidateSubTopic, String candidateFullText) {
        if (inferredTopic == null || inferredTopic == SubTopic.OTHER) {
            return new SubTopicMatchResult(0, null, inferredTopic);
        }

        // 1. 候选案例有结构化 sub_topic 字段且精确一致
        if (hasText(candidateSubTopic) && candidateSubTopic.equalsIgnoreCase(inferredTopic.name())) {
            return new SubTopicMatchResult(
                    SUB_TOPIC_EXACT_MATCH_SCORE,
                    "同类业务子主题(" + inferredTopic.getTitle() + ")",
                    inferredTopic
            );
        }

        // 2. 存量案例无 sub_topic 字段时的降级补偿（基于文本关键词命中）
        if (!hasText(candidateSubTopic) && hasText(candidateFullText)) {
            String lowerCandText = candidateFullText.toLowerCase(Locale.ROOT);
            int hitCount = 0;
            for (String kw : inferredTopic.getKeywords()) {
                if (lowerCandText.contains(kw)) {
                    hitCount++;
                }
            }
            if (hitCount >= 1) {
                return new SubTopicMatchResult(
                        SUB_TOPIC_TEXT_MATCH_SCORE,
                        "业务主题部分契合(" + inferredTopic.getTitle() + ")",
                        inferredTopic
                );
            }
        }

        return new SubTopicMatchResult(0, null, inferredTopic);
    }
}
