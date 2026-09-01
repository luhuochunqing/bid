// Input: 单个 Markdown 表格文本
// Output: String prompt（表格分类判定与整表结构化抽取）
// Pos: scoreparse/infrastructure/openai
package com.xiyu.bid.scoreparse.infrastructure.openai;

import java.util.regex.Pattern;

/**
 * 评分表格整表结构化抽取 Prompt 构建器。
 * <p>输入单个 Markdown 表格片段，一次性完成表格分类判定与多层级评分项结构化抽取。
 */
public final class TableScoreExtractionPrompt {

    private static final Pattern MD_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^\\)]*\\)");
    private static final Pattern BARE_DATA_IMAGE =
            Pattern.compile("data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\\.]*");

    private TableScoreExtractionPrompt() {
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = MD_IMAGE.matcher(raw).replaceAll(" ");
        stripped = BARE_DATA_IMAGE.matcher(stripped).replaceAll(" ");
        return stripped.trim();
    }

    public static String build(String tableMarkdown) {
        String safeTable = sanitize(tableMarkdown);
        return """
                你是一个资深招标文件评分标准分析专家。请分析以下单个 Markdown 表格，完成【表格分类判定】与【评分项结构化抽取】。

                表格内容：
                %s

                【判定与提取规则】：
                1. 表格分类（table_type & is_score_table）：
                   - SCORE_TABLE（评标打分/评审细则表）：包含评分标准、评审因素、分值权重，属于评标时对投标文件打分的表格。此时 is_score_table=true。
                   - CONTRACT_ASSESSMENT（合同履约考核表/违约扣款表/供应商年度考核表）：非评标打分，属于中标后合同执行期的考核。此时 is_score_table=false，items 输出空数组 []。
                   - PROCEDURE_TABLE（前附表程序性须知/废标条款/形式审查/资格审查）：无分值或仅为合格制审查。此时 is_score_table=false，items 输出空数组 []。
                   - UNKNOWN：其他非评分表格。is_score_table=false，items 输出空数组 []。

                2. 评分项提取要求（当 is_score_table=true 时）：
                   - detail 必须是表格单元格原文逐字复制（Verbatim），绝对禁止总结、概括、改写或缩略！
                   - 跨行合并单元格（Rowspan）：如果某列（如第2列"商务评分标准 56分"）纵向跨越多行，必须将该大类名称正确填充到每个子行的 parent_dim 或 dim 中，确保每个子项独立完整。
                   - 括号分值提取：若维度名包含括号分值（如"财务状况及抗风险能力（10分）"或"供应商业绩（7分）"），weight 应准确提取括号内的数值（10 或 7）。
                   - 末级细则拆解：若大项包含多个末级打分小项（如财务状况下含净资产 2.5分、现金流 2.5分等），将每个小项作为独立条目提取，禁止合并为一条。
                   - 汇总行/大类标题行：如"分值构成（总分100分）"、"商务部分 56分"等大类总计行，标记 is_summary_row=true。
                   - 报价参数（如下浮率、基准价公式）不得单独拆分为评分项，应作为报价评分主项的 detail 组成部分。

                请严格按 JSON Schema 输出结构化结果。
                """.formatted(safeTable);
    }
}
