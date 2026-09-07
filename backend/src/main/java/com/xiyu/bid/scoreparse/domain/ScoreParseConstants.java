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

    /**
     * 合同履约考核特征词表（CONTRACT_ASSESSMENT_PATTERN / NON_SCORE_PROCEDURE_PATTERN 共享子表，
     * 单一出处防双写漂移——两个 pattern 必须同步演化，改动时同步检查三处消费方：
     * ScoreTableBlockLocator / MarkdownScoreSectionLocator（表头判定，含 SCORE_HEADER_ESCAPE_PATTERN
     * 逃生门；PROCEDURE 词分支另有宽版 SCORE_COLUMN_MARKER）与 HeuristicTableColumnMapper（表头判定，
     * 含 EXPLICIT_BID_SCORE_HEADER 逃生门），行级消费方（ScoreTableRowParser / CellAnchoredScoreExtractor
     * / GhostCandidateFilter）依赖"得X分"/括号分值救生通道兜底误杀）。
     */
    private static final String ASSESSMENT_KEYWORDS =
            "考核项目|考核内容|履约考核|供应商考核|季度考核|年度考核|违约责任|违约金|违规行为判定|违约责任承担|判定依据|扣款细则|供应商评价|"
                    + "投诉扣分|扣分项|考核指标|时效考核|服务水平评价|配送服务评价|发票开具及时|收款核销|采购需求回复率|妥投信息";

    /** 合同履约考核表表头与正文特征（表格块定位/网格列映射/章节截断共用唯一出处） */
    public static final java.util.regex.Pattern CONTRACT_ASSESSMENT_PATTERN = java.util.regex.Pattern.compile(
            ASSESSMENT_KEYWORDS
    );

    /** 须知前附表程序性非评分条款过滤正则（行级/单元格级/幽灵候选共用唯一出处；词表 = 程序性词 + 考核词表） */
    public static final java.util.regex.Pattern NON_SCORE_PROCEDURE_PATTERN = java.util.regex.Pattern.compile(
            "推荐成交候选|成交候选|推荐候选|定标|预备会|开启程序|开标程序|递交响应文件|响应文件的递交|响应文件的编制|成交结果公示|履约保证金|投标保证金|失信行为|“码”上监督|监督中心|异议|质疑|答疑|澄清|踏勘|电子响应文件|分包|服务费|采购代理服务费|"
                    + "履约评价|日常考核|考核细则|扣款标准|" + ASSESSMENT_KEYWORDS
    );

    /**
     * 显式给分/扣分规则正则（"得X分/扣X分/不扣/扣减/扣除"，行级程序性过滤救生通道唯一出处；
     * GhostCandidateFilter / CellAnchoredScoreExtractor / ScoreTableRowParser 共用，禁止本地再编译同义正则）。
     * <p>注：扣分分支含数字后 `\s*`（"扣5 分"亦命中），系有意的空格容忍，非纯收敛搬运。
     */
    public static final java.util.regex.Pattern EXPLICIT_SCORE_RULE_PATTERN = java.util.regex.Pattern.compile(
            "得\\s*\\d+(?:\\.\\d+)?\\s*分|扣\\s*\\d+(?:\\.\\d+)?\\s*(?:分)?|不扣(?:分)?|扣减|扣除"
    );

    /**
     * 括号分值正则（"（7分）"/"(7.5分)"，捕获组 1 = 分值数字）。
     * 表格行解析/网格提取/列验真/列映射四处共用唯一出处，禁止本地再编译同义正则。
     */
    public static final java.util.regex.Pattern BRACKET_WEIGHT_PATTERN =
            java.util.regex.Pattern.compile("[（(]\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分\\s*[）)]");

    /**
     * 评分表头逃生词（强评分列名版）：考核/程序表判定命中后的白名单出口。
     * 刻意收窄为"评分因素/评审因素"两类强评分列名——"评分标准/分值/得分"是考核表、计费表
     * 也会出现的泛词（"考核项目|考核内容|评分标准|分值"是履约考核表真实表头布局），放行会
     * 泄漏整张考核表（复审 jshell 实证）。已知接受面：表头含考核词且无强评分列名的评分表
     * 会被跳过，由行级 NON_SCORE 词表兜底（见 ScoreTableBlockLocatorTest 逃生门锚定用例）。
     */
    public static final java.util.regex.Pattern SCORE_HEADER_ESCAPE_PATTERN =
            java.util.regex.Pattern.compile("评分因素|评审因素");

    /** 商品目录/分类清单/偏离表/服务费率表等非评分表格特征（网格列映射过滤用） */
    public static final java.util.regex.Pattern NON_SCORE_CATALOG_PATTERN = java.util.regex.Pattern.compile(
            "一级分类|二级分类|规格型号|品类详情|专区类别|商品名称|物料编码|采购清单|货物清单|偏差描述|响应文件中条款描述|计费基数|差额定率|服务费率"
    );

    /** 外部招投标系统/专用软件格式评分细则声明（如 ZBJ/电子交易平台页签） */
    public static final java.util.regex.Pattern EXTERNAL_SOFTWARE_SCORE_PATTERN = java.util.regex.Pattern.compile(
            "(?:详见|参见|见)[^。\n;；]{0,30}?(?:ZBJ|zbj|电子交易平台|电子招投标系统|评审项目|系统软件|平台页签|采购系统)[^。\n;；]{0,10}?(?:页签|系统|平台|文件|中)"
    );

    /** 中文大写数字章节标题（"一、"、"二、"等，表格行过滤与章节大类仲裁共用） */
    public static final java.util.regex.Pattern CHINESE_SECTION_HEADING = java.util.regex.Pattern.compile(
            "^[一二三四五六七八九十]+、.*"
    );

    /** 括号包裹的内容（中文圆括号/半角圆括号/方括号/六角括号等，用于清洗维度提取核心词） */
    public static final java.util.regex.Pattern BRACKET_CONTENT_PATTERN = java.util.regex.Pattern.compile(
            "[（(][^）)]*[）)]|[\\[【][^\\]】]*[\\]】]"
    );

    private ScoreParseConstants() {
    }
}
