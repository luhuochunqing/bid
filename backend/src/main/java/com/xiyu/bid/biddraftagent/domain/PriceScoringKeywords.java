// Input: 无（常量定义）
// Output: 报价评分领域关键词常量（单一真相源）
// Pos: biddraftagent/domain — 纯常量，无框架依赖
package com.xiyu.bid.biddraftagent.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 报价评分关键词常量（单一真相源）。
 *
 * <p>抽取自 2026-08-25 报价截断专项：原分散在 5 处的字面量
 * （下浮率/BP%/HP%/基准价等）收敛至此，避免后续新增关键词时漏改。
 *
 * <p>Python 侧同步点：document-converter-sidecar/converter.py 的
 * {@code PRICE_PARAM_KEYWORDS} 与 {@link #PRICE_PARAMS} 必须同步维护；
 * CI 校验脚本 scripts/check-price-keywords-sync.sh 会自动检测偏差。
 */
public final class PriceScoringKeywords {

    private PriceScoringKeywords() {}

    /** 长词参数（无歧义） */
    public static final String LONG_PRICE_PARAMS = "下浮率|基准价|平均价|最高价|最低价|折扣率|单边法|BP%|HP%|LP%|报价人|进入价格|参数描述|价格分计算|评标基准价";
    /** 短词参数（歧义：与评分项编号 L1/RH 等冲突，需后接数字才视为参数行） */
    public static final String SHORT_PRICE_PARAMS = "L1|L2|RH|RL";
    /** 全部管道参数关键词 */
    public static final String PRICE_PARAMS = LONG_PRICE_PARAMS + "|" + SHORT_PRICE_PARAMS;

    /** 扩展到评分块的报价特征（用于 TABLE_SCORE / chunk 过滤的增量） */
    public static final String SCORE_EXTENSION = "基准价|平均价|下浮率|报价人|最高价|最低价|折扣率|单边法|评标基准|报价评分|价格评分|报价满分|进入价格|参数描述";

    /** 报价维度名称词元（归一化后 contains 判定）：合并策略判同报价维度用，避免各处硬编码漂移 */
    public static final List<String> PRICE_DIM_TOKENS = List.of("报价", "价格");

    /**
     * 管道行级别：判定为报价参数行（跳过不生成候选）。
     * <p>短词 L1/L2/RH/RL 需后接数字（参数值如 L1 3）才视为参数行，避免误杀评分项
     * 如 “L1 技术方案 5分” 这类以 L1 为编号的真实评分项（歧义边界）。
     * <p>长词含 “%” 的以无 \\b 前缀匹配（% 非 \\w，\\b 在 “BP% ” 间不成立），改用前缀匹配。
     * <p>带序号的参数行（如 "2 BP% 80%"/"1 下浮率 3%"）：允许行首可选序号前缀，但要求
     * 行尾以 % 值结束（参数值特征）。已知边界："1 价格分计算方法 30%" 类以 % 书写
     * 权重的报价主项行也会被命中，由提取器侧维度词元守卫（PRICE_DIM_TOKENS）兼容保留。
     */
    public static final Pattern PRICE_PARAM_ROW = Pattern.compile(
            "^(" + LONG_PRICE_PARAMS + ")"
                    + "|^(?:[0-9]+[.、]?\\s+)?(?:" + LONG_PRICE_PARAMS + ")[^%]*%\\s*$"
                    + "|^(?:" + SHORT_PRICE_PARAMS + ")\\s+[0-9]");

    /** 表格块级别：报价参数块特征 */
    public static final Pattern PRICE_PARAM_BLOCK = Pattern.compile(PRICE_PARAMS);

    /** Chunk 级别：价格分计算扩展关键词 */
    public static final Pattern SCORE_CHUNK_EXTENSION = Pattern.compile(SCORE_EXTENSION);

    private static final List<String> LONG_PRICE_PARAM_LIST = List.of(LONG_PRICE_PARAMS.split("\\|"));

    /**
     * 统计文本命中多少个不同的长词参数（孤立报价参数块兜底判定用：≥2 才收）。
     * <p>嵌套关键词只计一次（如 基准价 ⊂ 评标基准价，同现时只算 1 个信号），
     * 避免 "评标基准价" 一词重复计数虚增置信度。
     */
    public static long countDistinctLongParams(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        List<String> hits = LONG_PRICE_PARAM_LIST.stream()
                .filter(k -> !k.isEmpty() && text.contains(k))
                .toList();
        return hits.stream()
                .filter(k -> hits.stream().noneMatch(other -> !other.equals(k) && other.contains(k)))
                .count();
    }
}
