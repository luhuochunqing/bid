// Input: TableGrid 表格结构
// Output: 经 LLM 语义识别与交叉验真后的 TableColumnMapping
// Pos: scoreparse/infrastructure/openai — 表格 Schema 识别服务
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.domain.grid.ColumnTypeCrossVerifier;
import com.xiyu.bid.scoreparse.domain.grid.HeuristicTableColumnMapper;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import com.xiyu.bid.scoreparse.domain.grid.TableGrid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 表格列语义结构识别映射器（spec 058）。
 * <p>核心流程：
 * <ol>
 *   <li>将表格前 3~5 行采样数据发送给 LLM 进行列属性分类；</li>
 *   <li>调用 {@link ColumnTypeCrossVerifier} 对 LLM 映射进行交叉验真；</li>
 *   <li>验真通过则采纳 LLM 映射；若 LLM 失败或验真未通过，则安全降级到 {@link HeuristicTableColumnMapper}。</li>
 * </ol>
 */
@Service
@Profile("!e2e")
@Slf4j
public class ScoreTableSchemaMapper {

    private static final String USE_CASE = "score table schema mapping";

    private final OpenAiStructuredOutputService structuredOutputService;
    private final OpenAiBidAgentConfigurationResolver configurationResolver;

    public ScoreTableSchemaMapper(
            OpenAiStructuredOutputService structuredOutputService,
            OpenAiBidAgentConfigurationResolver configurationResolver) {
        this.structuredOutputService = structuredOutputService;
        this.configurationResolver = configurationResolver;
    }

    /**
     * 推断表格的列语义映射。
     *
     * @param grid 待分析的表格网格
     * @return 经过验真的列映射配置
     */
    public TableColumnMapping mapSchema(TableGrid grid) {
        if (grid == null || grid.isEmpty()) {
            return TableColumnMapping.ofStandard(0, 1, 2, 3);
        }

        // 1. 尝试 LLM 列识别
        String sampleJson = formatGridSample(grid);
        String prompt = buildPrompt(sampleJson, grid.colCount());

        try {
            OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
            ScoreTableSchemaOutput output = structuredOutputService.request(
                    prompt, ScoreTableSchemaOutput.class, config,
                    "AI response did not include table schema mapping");

            if (output != null) {
                TableColumnMapping candidateMapping = new TableColumnMapping(
                        output.codeCol,
                        output.parentDimCol,
                        output.dimCol,
                        output.detailCol,
                        output.weightCol,
                        output.headerRows,
                        output.isPercentage
                );

                ColumnTypeCrossVerifier.ValidationResult vr =
                        ColumnTypeCrossVerifier.verify(grid, candidateMapping);
                if (vr.valid()) {
                    log.info("LLM 表格列映射识别成功且验真通过: code={}, dim={}, detail={}, weight={}, headerRows={}",
                            output.codeCol, output.dimCol, output.detailCol, output.weightCol, output.headerRows);
                    return candidateMapping;
                } else {
                    log.warn("LLM 表格列映射验真失败 ({})，回退至启发式映射", vr.reason());
                }
            }
        } catch (RuntimeException e) {
            log.warn("LLM 表格列识别调用异常: {}，回退至启发式映射", e.getMessage());
        }

        // 2. 降级回退到启发式规则推断
        TableColumnMapping fallback = HeuristicTableColumnMapper.infer(grid);
        log.info("采用启发式表格列映射: code={}, dim={}, detail={}, weight={}",
                fallback.codeCol(), fallback.dimCol(), fallback.detailCol(), fallback.weightCol());
        return fallback;
    }

    private static String formatGridSample(TableGrid grid) {
        StringBuilder sb = new StringBuilder("[\n");
        int sampleRows = Math.min(grid.rowCount(), 5);
        for (int r = 0; r < sampleRows; r++) {
            List<String> row = grid.row(r);
            sb.append("  [");
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) sb.append(", ");
                sb.append("\"").append(escapeJson(row.get(c))).append("\"");
            }
            sb.append("]");
            if (r < sampleRows - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String buildPrompt(String sampleJson, int totalCols) {
        return """
                你是一个资深招投标表格分析专家。请分析以下招标文件评分表格的前几行采样数据（共 %d 列），识别各列所代表的语义属性。
                
                表格采样数据（JSON二维数组，行优先）：
                %s
                
                请输出 JSON 格式（符合给定的 Schema）：
                1. code_col: 序号/条款编号所在列索引（0-based，无则填 -1）
                2. parent_dim_col: 评审大类/标包分组所在列索引（纵向合并单元格，如'商务部分'，无则填 -1）
                3. dim_col: 评分因素/子项名称所在列索引（无则填 -1）
                4. detail_col: 标书原文/打分细则所在列索引（通常包含大段评审文字要求）
                5. weight_col: 分值/权重所在列索引（包含数值、'分'或'%%'）
                6. header_rows: 表头所占行数（通常为 1）
                7. is_percentage: 分值列是否为百分比格式（如 6%%、10%%）
                8. explanation: 简要说明识别依据
                """.formatted(totalCols, sampleJson);
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
