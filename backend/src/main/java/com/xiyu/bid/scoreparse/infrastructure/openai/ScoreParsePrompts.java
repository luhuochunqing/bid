// Input: 文档 chunk 文本（已 sanitize）+ 上下文参数
// Output: String prompt（召回三/四候选提取、完整性回补、阶段2打分）
// Pos: scoreparse/infrastructure/openai
package com.xiyu.bid.scoreparse.infrastructure.openai;

import java.util.regex.Pattern;

/** 评分标准解析 prompt 模板（纯静态，无状态）。 */
public final class ScoreParsePrompts {

    /** markdown 内联图片（含 sidecar /convert 对图片 base64 的 `...` 占位符），喂模型前剥离，避免 Gemini 解码报 500。 */
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^\\)]*\\)");
    /** 兜底剥离未被 markdown 包裹的裸 data:image URI 片段（`data:...;base64,...`）。 */
    private static final Pattern BARE_DATA_IMAGE =
            Pattern.compile("data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\\.]*");

    private ScoreParsePrompts() {
    }

    /** 过剥后作为基于文本的 LLM 输入，避免残留 base64 占位符触发多模态解码失败（spec 048 测试环境回归）。 */
    static String promptSafe(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = MD_IMAGE.matcher(raw).replaceAll(" ");
        stripped = BARE_DATA_IMAGE.matcher(stripped).replaceAll(" ");
        return stripped.trim();
    }

    /**
     * 召回三/四：评分项候选提取（每 chunk 一次调用）。
     *
     * <p>召回三 = 评分规则语义（CONDITION_TO_SCORE 等模式）；
     * 召回四 = LLM 全文语义兜底。两者共用本 prompt，由模型输出 semanticPattern 区分来源。
     */
    public static String buildCandidateExtractionPrompt(String chunkText, int index, int total) {
        return """
                你是招标文件评分标准解析专家。以下是招标文件的第 %d/%d 个片段（可能包含评分表、
                评分办法章节、正文中的散落评分条款）。

                任务：提取本片段中所有"评分项"，输出 JSON。

                评分项判定特征（满足其一即提取）：
                - 明确的分值（如"10分"、"占30%%"、"得X分"）
                - 条件式给分（如"具备X证书的得Y分"）
                - 档位给分（如"优:5-4分 良:3-2分 一般:1分"）
                - 数量/指标给分（如"每提供1个案例得2分，最多6分"）
                - 价格分公式类：含价格分计算、评标基准价、报价折扣率等即使无单点分值也属评分项

                输出字段要求：
                - code: 评分项编号（如 A1、1.2、2.2.1）；原文无编号则输出空字符串 ""（严禁输出 "null"、"undefined" 等字符串占位符）
                - dim: 所属评分维度或具体评分因素名称（如 财务状况、注册资本金、营业收入、现金流、信用评价、价格折扣、系统对接能力等末级评价因素）；无法判断输出 null
                - detail: 评分条件的完整原文表述（标书原文），必须完整保留原始句子、符号、比例、分号与段落，绝对禁止任何形式的 AI 摘要、概括、缩写或改写；报价类需把价格分计算方法、
                  评标基准价公式、价格分计算公式、进入价格评分报价人数、下浮率、BP%%/HP%%/LP%%/L1/L2/RH/RL
                  等参数完整保留在同一条"报价评分"项的 detail 中，不得拆分或遗漏
                - weight: 该项满分分值（绝对数字，如 10 或 2.5 或 50）；必须准确提取末级分值列数字，禁止填 0 除非原文明确规定为 0 分
                - scoreTypeGuess: OBJECTIVE（可验证的量化条件）或 SUBJECTIVE（需人工评审的描述性要求）
                - contextNote: 前后文中的约束注记（如"注：得分不超过X分"）；无则 null
                - sourceText: 该项对应的原文片段（保持原文）
                - location: 定位描述（如"P47 评分办法表 第3行"）；无法定位则 null
                - semanticPattern: 从以下选一
                  CONDITION_TO_SCORE（条件式给分）、QUANTITY_TO_SCORE（数量/指标给分）、
                  GRADE_TO_SCORE（档位给分）、METRIC_TO_SCORE（公式/指标计算）、NONE（无法归类）

                硬性规则：
                - 本片段没有评分项时，输出 {"candidates":[]}
                - 禁止编造原文中不存在的内容，detail 必须逐字来自招标文件原文
                - 权重与评分项严格对齐：在非标准表格或多行混排文本中，每个权重数值必须严格绑定其所属的评分因素名称与细则，严禁相邻项权重错位、串行或相互颠倒借用；逐项核对原文真实分值，发现错位必须纠正
                - 并列能力与方案评分项完整提取（防穿插遗漏）：在非标准表格或多行混排文本中，当评分大项下包含多个并列的具体方案/能力评价时，即使列文本在排版中发生了穿插（例如细则描述语句中间穿插了列标题与分值），必须完整识别并提取出每一个并列评分项，严禁遗漏并列项中的任何一项
                - 多层级叶子细则独立提取：当大项下包含多个具体评分因素/细则时，必须将每个末级小项独立输出为一条评分项，权重取该小项分值，严禁合并为一条
                - 多标包处理：若片段包含综合评分法标包（如标包一）和最低价法标包（如标包二三），仅提取综合评分法标包的明细评分项，严禁提取最低价法标包的程序性说明
                - 报价参数（下浮率/BP%%/HP%%/LP%%/L1/L2/RH/RL）不得单独拆为一条评分项，应作为报价主项的组成部分
                - 须知前附表中的程序性条款（如开启程序、成交结果公示、履约保证金、采购预备会、分包、供应商须知等）均不是评分项，禁止提取
                - 形式评审标准、资格评审标准、响应性评审标准中的条款无分值规则，禁止提取
                - 合同履约考核表、供应商季度/年度考核细则、违约扣款责任表（如周期保证、质量保证、安全环保考核等履约期管理指标）非评标打分项，绝对禁止提取
                - 凡是在正文中声明“详见ZBJ/电子交易平台页签”且正文无具体分值标准的，严禁从文末合同附件考核表中拼凑替代
                - 仅描述总分构成的汇总行（如"商务部分：X%% 技术部分：Y%% 报价部分：Z%%"或"某大类：N分"）不是末级评分项，禁止提取

                片段内容：
                %s
                """.formatted(index, total, chunkText);
    }

    /**
     * 完整性回补扫描（按需触发：WeightSumCheck 合计≠100 或结构召回存在未覆盖区域）。
     */
    public static String buildGapRecheckPrompt(
            String fullTextExcerpt, String knownItemsSummary, String zonesToCheck) {
        return """
                你是招标文件评分标准完整性校验专家。

                已提取的评分项清单（编号+名称+分值）：
                %s

                请重点复查以下易漏区域：%s
                （footnotes=脚注、table-remarks=表格备注、cross-page-refs=跨页引用/见某节、parallel-sub-items=并列子评分项与方案能力项）

                任务：
                1. 找出上表中遗漏的评分项（重点是脚注中的给分条件、表格备注栏、"详见第X节"引用的散落条款、报价计算公式）。
                2. 大项分值与并列子项完整性复查（防总分缺额）：请逐一核对商务部分、技术部分、报价部分等大项的子项分值和。例如技术部分标明占 30%%，若清单中只有 5 个 5%% 的子项（合计 25%%），必定存在 1 个 5%% 的并列能力子项被遗漏，必须在节选原文中逐段复查并完整回补为 missedItems！

                没有遗漏时输出 {"missedItems":[],"checkedZones":[复查过的区域]}。
                发现遗漏时按候选提取的字段要求输出 missedItems（与首次提取相同 schema）。

                文本节选：
                %s
                """.formatted(knownItemsSummary, zonesToCheck, fullTextExcerpt);
    }

    /**
     * 阶段 2：投标文件对标打分（客观项，每评分项一次调用）。
     * 主观项调用见 {@link #buildSubjectiveSuggestionPrompt}。
     */
    public static String buildObjectiveAssessmentPrompt(
            String scoreItemDetail, double weight, String bidDocExcerpt) {
        return buildObjectiveAssessmentPrompt(scoreItemDetail, weight, bidDocExcerpt, false);
    }

    /**
     * 阶段 2：客观项打分（多模态过载）。
     * withImages=true 时请求携带投标文件图片 content parts，prompt 追加中性图片判定指引
     * （配给与评分项无关，声明"相关"会失真，见 spec 048 review M3）。
     */
    public static String buildObjectiveAssessmentPrompt(
            String scoreItemDetail, double weight, String bidDocExcerpt, boolean withImages) {
        String imageGuidance = withImages ? """

                附图为投标文件中的部分图片，请先判断每张图是否与评分项相关，
                再结合相关图片判定满足度，引用依据时说明来自图片。
                """ : "";
        return """
                你是投标文件评审专家。对照评分标准，评估投标文件对本评分项的满足程度。

                评分标准（满分 %s 分）：
                %s

                投标文件相关内容节选：
                %s
                %s
                输出 JSON 字段要求：
                - actualScore: 投标文件实际可得分（0 到 %s 之间的数字）
                - matchRatio: 满足度百分比（0-100 整数）
                - evidence: 满足/部分满足的依据说明
                - quote: 投标文件中的原文引用（含章节/页码）；无引用输出 null
                - quoteMissing: 投标文件中找不到相关内容时为 true
                - missedReason: 未满足的具体原因；满足时输出 null
                - suggestion: 针对缺口的改进建议（一句话）

                硬性规则：
                - 只依据投标文件节选判断，禁止假设文件中不存在的内容
                - quoteMissing=true 时 quote 必须为 null
                """.formatted(weight, promptSafe(scoreItemDetail), promptSafe(bidDocExcerpt), imageGuidance, weight);
    }

    /**
     * 阶段 2：主观项建议（不生成数字得分，SC-003 零泄漏）。
     */
    public static String buildSubjectiveSuggestionPrompt(String scoreItemDetail, String bidDocExcerpt) {
        return """
                你是投标文件评审专家。本评分项为主观评审项（由评标专家人工打分），
                你的任务仅是给出完善建议，禁止输出任何数字得分。

                评分标准：
                %s

                投标文件相关内容节选：
                %s

                输出 JSON 字段要求：
                - actualScore: 必须为 null
                - matchRatio: 必须为 null
                - evidence: null
                - quote: null
                - quoteMissing: false
                - missedReason: null
                - suggestion: 针对该主观项的投标文件完善建议（一到两句话，指出可补强的方向）
                """.formatted(promptSafe(scoreItemDetail), promptSafe(bidDocExcerpt));
    }

    /**
     * 全局归并裁决（spec 055）：候选池身份裁决 + 结构重建，单次调用。
     * <p>契约见 specs/055-score-parse-global-merge/contracts/llm-global-merge-schema.md §5。
     */
    public static String buildGlobalMergePrompt(String candidatesJson, String sectionText) {
        return buildGlobalMergePrompt(candidatesJson, sectionText, null);
    }

    /**
     * 全局归并裁决（含词汇注册表 escape-hatch）。
     * <p>{@code vocabularyExamples} 来自 {@link com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry#toPromptExamples}，
     * 为 null 或空白时不注入词汇参考段。
     */
    public static String buildGlobalMergePrompt(String candidatesJson, String sectionText, String vocabularyExamples) {
        String vocabularyBlock = (vocabularyExamples == null || vocabularyExamples.isBlank())
                ? ""
                : "\n                词汇参考（项目特定评分维度，归并时优先匹配）：\n                " + vocabularyExamples + "\n";
        return """
                你是招标文件评分标准结构重建专家。多路召回产生了含重复与残缺的评分项候选池，
                请结合评分章节原文，一次性重建最终评分项清单，输出 JSON。

                候选池（JSON 数组，idx 为池内索引）：
                %s

                评分章节原文（裁决唯一依据）：
                %s

                输出字段要求：
                - items[]: 最终评分项清单
                  - code: 编号（沿用候选或原文编号）；无编号则输出空字符串 ""（严禁输出 "null" 等占位字符串）
                  - dim: 评分维度或评分因素名称（如 财务状况、注册资本金、营业收入、现金流、信用评价、价格折扣、系统对接能力等末级评价因素；优先体现末级）
                  - detail: 该项评审细则逐字原文，必须能在"评分章节原文"中逐字找到；禁止摘要、改写、补写
                  - weight: 该项满分分值（数字）；无法确定输出 null
                  - scoreTypeGuess: OBJECTIVE（可验证量化条件）或 SUBJECTIVE（需人工评审）
                  - sourceText: 对应原文片段
                - summary: {mergedGroupCount: 合并掉的重复候选组数, discardedCandidateCount: 丢弃的非评分项候选数, notes: ≤100字决策说明}

                硬性规则：
                1. 只允许从候选池与章节原文中选取与重组，禁止发明候选池外的新评分项
                2. 同一条款的多次召回（含截断残缺版）必须归并为一项，detail 取最完整逐字版本
                3. 各自包含完整分值裁决（如"得X分""不得分"）的独立条款必须保持独立，禁止合并
                4. detail 一律逐字摘自章节原文，必须包含该项下的全部完整细则（含 1、2、3... 全量编号及细则要求），绝对禁止中途截断、省略或改写；若候选池 detail 不完整，必须在章节原文中找到对应完整条款
                5. 输出 items 必须覆盖候选池中所有真实评分项，不得遗漏；没有评分项时输出 {"items":[],"summary":{...}}
                6. 须知前附表程序条款（开启程序、成交结果公示、履约保证金、采购预备会、异议质疑等）若混入候选池，归并时必须丢弃，不得出现在 items 中
                7. 多标包隔离：若文档包含多个标包（如标包一为综合评分法、标包二三为最低价法），仅归并综合评分法标包的评分项，严禁将最低价法等非评分标包的程序性文字作为评分项提取
                8. 叶子打分项优先：当遇到"一级汇总大项"与"二级/三级具体打分细则"时，必须保留末级具体打分细则，严禁将具体细则全部折叠合并为一个粗颗粒度的大项
                9. 排除一级分类总览项：如"某大类：N分"等纯大类总览项，若其子细则已在 items 中，严禁将该大类总览项独立作为一条评分项输出，防止总分翻倍
                10. 权重与细则严密核对防错位：必须逐项比对评分项与招标文件原文中的真实分值，严禁发生相邻项权重颠倒错位；若候选池中存在权重错位，必须在归并时纠正为标书原文真实数值
                11. 并列评价细则与分值闭环完整性：对于包含多个并列评价因素的大项，归并时必须确保所有并列评分因素全部保留且分值正确，绝对禁止遗漏任何并列子项，确保总分精准闭环为 100%%，防止下游因缺项触发权重比例重新分配与拼凑变形
                %s""".formatted(candidatesJson, promptSafe(sectionText), vocabularyBlock);
    }

    /**
     * 事实裁决（spec 059 Phase 3）：仅对已存在的 factId 输出受限操作（KEEP / CLASSIFY / LINK_PARENT / MARK_DUPLICATE / REVIEW_REQUIRED）。
     */
    public static String buildFactAdjudicationPrompt(String factsJson, String sectionText) {
        return """
                你是招标文件评分事实语义适配专家。已有底层确定性解析产出的评分事实池（ScoreFact），
                你只能针对已有的 factId 执行受限操作，绝对禁止生成新事实，禁止修改权重分值（weight）与细则文本（detail/sourceText）。

                既有事实池（JSON 数组）：
                %s

                评分章节原文（参考背景）：
                %s

                允许的操作类型（action）：
                - KEEP: 确认保留该事实为最终打分项（需提供 confidence 与 reasonCellIds）
                - CLASSIFY: 补充语义维度分类（填写 category，confidence 须 ≥ 0.80）
                - LINK_PARENT: 建立同表层级关联（必须填写 parentFactId，两者必须同表）
                - MARK_DUPLICATE: 将重复事实折叠到权威事实（填写 duplicateFactIds，被折叠项将被弃用）
                - REVIEW_REQUIRED: 标记该事实存疑需要人工复核（填写 reason）

                硬性禁令（违规将被安全拦截并判定为违规）：
                1. 严禁输出任何不存在于事实池的 factId
                2. 严禁创建新事实（禁止 CREATE_SCORE_FACT）
                3. 严禁修改权重（禁止 UPDATE_WEIGHT，禁止输出 weight 字段）
                4. 严禁删除事实（禁止 DELETE_FACT）
                5. 严禁跨表链接父子关系（LINK_PARENT 双方必须来自同一表格）
                6. 严禁输出 detail、sourceText、weight、newFact 字段
                """.formatted(factsJson, promptSafe(sectionText));
    }
}
