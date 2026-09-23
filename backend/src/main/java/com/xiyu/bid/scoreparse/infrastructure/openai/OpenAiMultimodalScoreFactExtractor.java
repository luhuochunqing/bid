package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.infrastructure.openai.MultimodalImage;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.application.MultimodalScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

/** One request owns recall and structure; deterministic code validates the response afterwards. */
@Service
@Profile("!e2e")
public class OpenAiMultimodalScoreFactExtractor implements MultimodalScoreFactExtractor {
    private static final String USE_CASE = "multimodal score fact extraction";
    private final OpenAiStructuredOutputService outputService;
    private final OpenAiBidAgentConfigurationResolver configurationResolver;
    private final ObjectMapper objectMapper;

    public OpenAiMultimodalScoreFactExtractor(OpenAiStructuredOutputService outputService,
                                              OpenAiBidAgentConfigurationResolver configurationResolver,
                                              ObjectMapper objectMapper) {
        this.outputService = outputService;
        this.configurationResolver = configurationResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExtractionResult extract(DocumentArtifact artifact, List<DocumentImage> pageImages) {
        var config = configurationResolver.resolve(USE_CASE);
        List<DocumentImage> selected = selectScorePageImages(artifact, pageImages);
        List<MultimodalImage> images = selected.stream()
                .map(image -> new MultimodalImage(image.mimeType(), image.base64())).toList();
        Output response = outputService.request(buildPrompt(artifact), images, Output.class, config,
                "AI response did not include multimodal score facts");
        List<MultimodalScoreFact> facts = response == null || response.facts == null ? List.of()
                : response.facts.stream().map(Item::toDomain).toList();
        return new ExtractionResult(response == null ? null : response.declaredTotal,
                facts, config.model(), images.size());
    }

    @Override
    public ExtractionResult recheck(DocumentArtifact artifact) {
        return recheck(artifact, List.of());
    }

    @Override
    public ExtractionResult recheck(DocumentArtifact artifact, List<DocumentImage> pageImages) {
        var config = configurationResolver.resolve(USE_CASE);
        List<DocumentImage> selected = selectScorePageImages(artifact, pageImages);
        List<MultimodalImage> images = selected.stream()
                .map(image -> new MultimodalImage(image.mimeType(), image.base64())).toList();
        Output response = outputService.request(buildRecheckPrompt(artifact), images, Output.class, config,
                "AI response did not include rechecked score facts");
        List<MultimodalScoreFact> facts = response == null || response.facts == null ? List.of()
                : response.facts.stream().map(Item::toDomain).toList();
        return new ExtractionResult(response == null ? null : response.declaredTotal,
                facts, config.model(), images.size());
    }

    private String buildRecheckPrompt(DocumentArtifact artifact) {
        // isScoreTable is heuristic: an unmarked table can contain the actual score details.
        var evidence = artifact.tables().stream().map(table -> new CompactTable(table.tableId(), table.sourcePage(),
                table.sourceSection(), table.cells().stream()
                        .filter(cell -> cell.rawText() != null && !cell.rawText().isBlank())
                        .map(cell -> new CompactCell(cell.cellId(), cell.row(), cell.column(),
                                cell.sourcePage() == null ? table.sourcePage() : cell.sourcePage(), cell.rawText()))
                        .toList())).toList();
        try {
            return """
                    你正在独立复核招标文件的正式评分办法。首次识别失败或评分总分未闭合。
                    请根据下面按页和物理顺序排列的原始单元格，重新输出全部正式评分项，不要只补差额。
                    必须区分正式评分表、报价品类权重表、资格审查表和响应模板；不得臆造评分项或分值。
                    每个正式评分项提供 sectionTitle、code、parentCode、dimension、weight、ruleText、scoreType、
                    pageNumber、tableId、sourceCellIds、quotedText、confidence、childAggregation。
                    childAggregation 描述该项与直接子项的关系，只能为 SUM、SHARED_CAP、BEST_OF、MUTUALLY_EXCLUSIVE、UNKNOWN。
                    只有原文明示子项分别计分并累计才用 SUM；共享上限、多条件满足择一只用 SHARED_CAP；明确择优用 BEST_OF；
                    互斥方案用 MUTUALLY_EXCLUSIVE；不确定一律 UNKNOWN。叶子项关系填 SUM。
                    父项和子项均输出，sourceCellIds 可引用多个同表单元格；quotedText 必须逐字来自所引单元格文本。
                    pageNumber 使用 quotedText 来源格的真实物理页，且 ruleText 保留完整规则。
                    declaredTotal 只能取原文明确声明的总分；无法确认则为 null。逐项核对大类小计和叶子总分。
                    不完整时仍输出可证实的事实，禁止为了闭合总分编造缺项。
                    评分证据 JSON：
                    """ + objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化紧凑评分证据", exception);
        }
    }

    private record CompactTable(String tableId, Integer pageNumber, String sectionTitle,
                                List<CompactCell> cells) {}
    private record CompactCell(String cellId, Integer row, Integer column, Integer sourcePage, String rawText) {}

    private String buildPrompt(DocumentArtifact artifact) {
        try {
            return """
                    你是招标文件评分办法事实抽取器。页面图像、表格网格和原始单元格是同一份文档的证据。
                    一次性输出全部正式评分项，不得把报价品类权重表、资格审查表、响应模板当评分项；不得漏掉报价评分。
                    每项必须保留原始章节和物理顺序、父子编号、最高分、完整规则、页码、tableId、sourceCellIds，quotedText 必须逐字复制原始单元格。
                    每项还必须给出 childAggregation（SUM/SHARED_CAP/BEST_OF/MUTUALLY_EXCLUSIVE/UNKNOWN），只按原文明示的关系分类，不确定用 UNKNOWN。
                    scoreType：明确阶梯、证书、数量、金额、日期、公式、可验证满足条件为 OBJECTIVE；只有横向比较、酌情、优良中差为 SUBJECTIVE。
                    父项和子项均输出。declaredTotal 取评分办法明确声明的总分，不得猜测。
                    DocumentArtifact JSON：
                    """ + objectMapper.writeValueAsString(artifact);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化评分表格证据", exception);
        }
    }

    private static List<DocumentImage> selectScorePageImages(DocumentArtifact artifact, List<DocumentImage> images) {
        if (images == null || images.isEmpty()) return List.of();
        var scorePages = artifact == null ? java.util.Set.<Integer>of() : artifact.tables().stream()
                .flatMap(table -> table.cells().stream().map(cell -> cell.sourcePage() == null
                        ? table.sourcePage() : cell.sourcePage()))
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var matched = images.stream().filter(image -> scorePages.isEmpty() || scorePages.contains(image.pageHint()))
                .limit(MAX_SCORE_PAGE_IMAGES).toList();
        return matched.isEmpty() ? images.stream().limit(MAX_SCORE_PAGE_IMAGES).toList() : matched;
    }

    public static final class Output {
        public BigDecimal declaredTotal;
        public List<Item> facts;
    }
    public static final class Item {
        public String sectionTitle;
        public String code;
        public String parentCode;
        public String dimension;
        public BigDecimal weight;
        public String ruleText;
        public String scoreType;
        public Integer pageNumber;
        public String tableId;
        public List<String> sourceCellIds;
        public String quotedText;
        public double confidence;
        public String childAggregation;
        MultimodalScoreFact toDomain() {
            return new MultimodalScoreFact(sectionTitle, code, parentCode, dimension, weight, ruleText,
                    scoreType, pageNumber, tableId, sourceCellIds, quotedText, confidence,
                    ChildAggregation.modelValue(childAggregation));
        }
    }
}
