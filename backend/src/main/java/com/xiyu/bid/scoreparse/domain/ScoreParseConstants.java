// Input: 无（模块级常量单一出处）
// Output: scoreparse 全模块共享的数值常量（权重阈值/裁决语料上限/遥测告警阈）
// Pos: scoreparse/domain — 纯常量类，无框架依赖（xcode-review B-3：消 MAX_SINGLE_WEIGHT 双处定义）
// 维护声明: 维护者按项目SOP；修改任一常量时确认 ScoreMergeResultGuardTest / RunSummaryAssemblerTest /
//   GoldenCasesReplayTest 的边界断言语义仍成立（三处测试锚定同一组值）
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;

/**
 * scoreparse 模块共享常量（spec 046/047/055/057 数值口径唯一出处）。
 */
public final class ScoreParseConstants {

    /** 单项权重有效上界：>100 视为误提金额/年份等噪声（PostProcessor 权重过滤 + Guard 高置信判定共用） */
    public static final BigDecimal MAX_SINGLE_WEIGHT = new BigDecimal("100");

    /** 权重超载判定上限（spec 046 FR-002；WeightSumCheck 容差 +0.5） */
    public static final BigDecimal OVERLOAD_LIMIT = new BigDecimal("100.5");

    /** 裁决上下文（评分章节拼接）字符上界（spec 057 FR-03，扩容支持复杂多标包章节完整性） */
    public static final int SECTION_TEXT_LIMIT = 24000;

    /** 对齐率地板：低于此值说明模型改写/概括严重（对齐器未命中）（spec 057 FR-06） */
    public static final double ALIGNED_RATIO_FLOOR = 0.5;

    /** 幻觉丢弃率天花板：高于此值说明归并裁决质量劣化（spec 057 FR-06） */
    public static final double HALLUCINATION_RATIO_CEIL = 0.3;

    /** 须知前附表程序性与合同履约期非评分条款过滤正则（行级/单元格级/幽灵候选共用唯一出处） */
    public static final java.util.regex.Pattern NON_SCORE_PROCEDURE_PATTERN = java.util.regex.Pattern.compile(
            "推荐成交候选|成交候选|推荐候选|定标|预备会|开启程序|开标程序|递交响应文件|响应文件的递交|响应文件的编制|成交结果公示|履约保证金|投标保证金|失信行为|“码”上监督|监督中心|异议|质疑|答疑|澄清|踏勘|电子响应文件|分包|服务费|采购代理服务费|"
                    + "考核项目|考核内容|履约考核|供应商考核|季度考核|年度考核|违约责任|违约金|违规行为判定|违约责任承担|判定依据|扣款细则|履约评价|日常考核|供应商评价|考核细则|扣款标准"
    );

    /** 合同履约考核表表头特征（表格块定位/网格列映射/章节截断共用唯一出处） */
    public static final java.util.regex.Pattern CONTRACT_ASSESSMENT_PATTERN = java.util.regex.Pattern.compile(
            "考核项目|考核内容|履约考核|供应商考核|季度考核|年度考核|违约责任|违约金|违规行为判定|违约责任承担|判定依据|扣款细则|供应商评价"
    );

    /** 外部招投标系统/专用软件格式评分细则声明（如 ZBJ/电子交易平台页签） */
    public static final java.util.regex.Pattern EXTERNAL_SOFTWARE_SCORE_PATTERN = java.util.regex.Pattern.compile(
            "(?:详见|参见|见)[^。\n;；]{0,30}?(?:ZBJ|zbj|电子交易平台|电子招投标系统|评审项目|系统软件|平台页签|采购系统)[^。\n;；]{0,10}?(?:页签|系统|平台|文件|中)"
    );

    private ScoreParseConstants() {
    }
}
