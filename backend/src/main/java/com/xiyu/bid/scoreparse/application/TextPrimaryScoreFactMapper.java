package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreCategoryClassifier;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreItemTitleExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.HeuristicTableColumnMapper;
import com.xiyu.bid.scoreparse.domain.grid.TableGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.stream.Collectors;

final class TextPrimaryScoreFactMapper {
    private TextPrimaryScoreFactMapper() {
    }

    static ScoreFact map(MultimodalScoreFact fact, DocumentArtifact artifact,
                         MultimodalScoreFactValidator.ValidationReport validation) {
        String cellId = fact.sourceCellIds().isEmpty() ? "" : fact.sourceCellIds().get(0);
        String raw = artifact == null ? "" : artifact.tables().stream().flatMap(t -> t.cells().stream())
                .filter(cell -> cell.cellId().equals(cellId)).map(cell -> cell.rawText() == null ? "" : cell.rawText())
                .findFirst().orElse("");
        int start = raw.indexOf(fact.quotedText());
        String factId = com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(fact);
        var parent = com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer
                .resolveParent(fact, validation.facts());
        String parentId = parent == null ? ""
                : com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(parent);
        // 逐条问题的 factId 是 modelFactId（来源侧）或空串（文档级）；按 modelFactId 匹配，否则
        // 「来源侧问题挂不到该事实」——本类缺陷（引文不逐字）的诊断信息会整体丢失。
        // 本类自有校验的 of() 问题用 sectionTitle+"/"+code，比事实身份粗（空 code 兄弟共用同一
        // 键），故意不匹配：沿用粗键会把问题挂到错误的那条兄弟行上，正是本次修复要消灭的歧义。
        String violations = validation.violations().stream()
                .filter(v -> v.factId().isBlank() || v.factId().equals(factId))
                .map(v -> v.code() + "[field=" + v.field() + ", expected=" + v.expected() + ", actual=" + v.actual()
                        + ", evidence=" + v.evidence() + "]").collect(Collectors.joining("; "));
        boolean candidateEligible = validation.sourceVerification().qualifications().stream()
                .anyMatch(qualification -> factId.equals(qualification.factId())
                        && qualification.candidateEligible());
        var restored = com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration.restore(fact, artifact, validation.facts());
        boolean verified = restored.closed();
        var status = candidateEligible && !restored.text().isBlank() && verified && violations.isBlank() && validation.autoApprovable()
                ? ScoreFact.ValidationStatus.AUTO_APPROVED : ScoreFact.ValidationStatus.REVIEW_REQUIRED;
        String ruleTextForDimension = restored.text();
        if (!verified && !ruleTextForDimension.equals(restored.spans().stream()
                .map(SourceSpan::rawText).collect(Collectors.joining("\n")).strip())) {
            ruleTextForDimension = "";
        }
        String dimension = OriginalScoreEvidencePolicy.publishedDimension(
                fact.dimension(), ruleTextForDimension, fact.weight());
        if (ScoreCategoryClassifier.needsSpecificTitle(dimension)) {
            dimension = resolveRowTitleFromArtifact(artifact, fact, cellId, dimension);
        }
        return new ScoreFact(factId, fact.tableId(), fact.sourceCellIds(), restored.text(), fact.code(), dimension,
                fact.weight(), new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, fact.quotedText(), cellId,
                start, start < 0 ? -1 : start + fact.quotedText().length(), "page=" + fact.pageNumber() + "; scoreType="
                + fact.scoreType() + (violations.isBlank() ? "" : "; validationDiff=" + violations)), ScoreRule.MAX_SCORE,
                parentId, "MULTIMODAL", fact.confidence(), status, fact.childAggregation(), sourceMetadata(fact, artifact, validation, restored));
    }
    private static com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata sourceMetadata(
            MultimodalScoreFact fact, DocumentArtifact artifact, MultimodalScoreFactValidator.ValidationReport validation, com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration.Result restored) {
        var existing = fact.sourceMetadata();
        var diagnostics = new java.util.ArrayList<>(existing.diagnostics());
        if (restored.text().isBlank() || !restored.closed()) {
            if (!diagnostics.contains("FULL_RULE_UNIT_UNRESOLVED")) diagnostics.add("FULL_RULE_UNIT_UNRESOLVED");
        }
        if (!restored.closed() && !diagnostics.contains("UNVERIFIED_CLOSURE")) {
            diagnostics.add("UNVERIFIED_CLOSURE");
        }
        var sourceSpans = new java.util.LinkedHashMap<String, com.xiyu.bid.scoreparse.domain.SourceSpan>();
        existing.sourceSpans().forEach(span -> sourceSpans.put(span.sourceId(), span));
        com.xiyu.bid.scoreparse.domain.SourceSpanResolver.resolve(artifact, fact.sourceCellIds())
                .forEach(span -> sourceSpans.putIfAbsent(span.sourceId(), span));
        restored.spans().forEach(span -> sourceSpans.putIfAbsent(span.sourceId(), span));
        String identity=com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(fact);
        String locator=fact.tableId()+":"+String.join(",",fact.sourceCellIds());
        String coarse=fact.sectionTitle()+"/"+fact.code();
        boolean unique=validation.facts().stream().filter(f -> (f.sectionTitle()+"/"+f.code()).equals(coarse)
                && (f.tableId()+":"+String.join(",",f.sourceCellIds())).equals(locator)).count()==1;
        validation.violations().stream().filter(v -> v.factId().isBlank() || v.factId().equals(identity)
                || unique && v.factId().equals(coarse) && v.evidence().equals(locator))
                .map(MultimodalScoreFactValidator.Violation::code).forEach(diagnostics::add);
        return new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(
                existing.modelRuleText().isBlank() ? fact.ruleText() : existing.modelRuleText(),
                existing.modelQuotedText().isBlank() ? fact.quotedText() : existing.modelQuotedText(),
                java.util.List.copyOf(sourceSpans.values()), diagnostics.stream().distinct().toList(),
                existing.modelCategory().isBlank() ? fact.dimension() : existing.modelCategory(),
                existing.modelScoreType().isBlank() ? fact.scoreType() : existing.modelScoreType(),
                existing.modelSourceClaim().sectionTitle().isBlank()
                        ? com.xiyu.bid.scoreparse.domain.ModelSourceClaim.from(fact) : existing.modelSourceClaim());
    }

    private static String resolveRowTitleFromArtifact(
            DocumentArtifact artifact, MultimodalScoreFact fact, String cellId, String fallbackDimension) {
        if (artifact == null || cellId.isBlank()) {
            return fallbackDimension;
        }
        for (var table : artifact.tables()) {
            if (!fact.tableId().isBlank() && !fact.tableId().equals(table.tableId())) {
                continue;
            }
            var target = table.cells().stream().filter(c -> cellId.equals(c.cellId())).findFirst().orElse(null);
            if (target == null || target.row() == null) {
                continue;
            }
            var grid = originalGrid(table);
            var mapping = HeuristicTableColumnMapper.infer(grid);
            if (target.row() < mapping.headerRows()) {
                return fallbackDimension;
            }
            int titleCol = mapping.dimCol();
            if (titleCol == mapping.weightCol() && mapping.headerRows() > 0
                    && ScoreCategoryClassifier.isInvalidOrPlaceholderName(grid.cell(target.row(), titleCol))) {
                // A bracketed score can make column inference mistake a separate points column for an inline title.
                var header = new TableGrid(List.of(grid.row(mapping.headerRows() - 1)));
                titleCol = HeuristicTableColumnMapper.infer(header).dimCol();
            } else if (titleCol == mapping.weightCol() && mapping.headerRows() == 0
                    && ScoreParseConstants.BRACKET_WEIGHT_PATTERN.matcher(grid.cell(target.row(), titleCol)).matches()) {
                titleCol = inferTitleWithStandaloneScores(grid, titleCol);
            }
            if (titleCol == mapping.detailCol()) {
                return fallbackDimension;
            }
            String candidate = ScoreItemTitleExtractor.resolveDimension(grid.cell(target.row(), titleCol), "");
            return ScoreCategoryClassifier.needsSpecificTitle(candidate) ? fallbackDimension : candidate;
        }
        return fallbackDimension;
    }

    private static int inferTitleWithStandaloneScores(TableGrid grid, int weightColumn) {
        // Give column inference the equivalent bare score while retaining the original evidence.
        List<List<String>> rows = new ArrayList<>();
        for (var row : grid.allRows()) {
            var score = ScoreParseConstants.BRACKET_WEIGHT_PATTERN.matcher(row.get(weightColumn).strip());
            var cells = new ArrayList<>(row);
            if (score.matches()) {
                cells.set(weightColumn, score.group(1));
            }
            rows.add(cells);
        }
        return HeuristicTableColumnMapper.infer(new TableGrid(rows)).dimCol();
    }

    private static TableGrid originalGrid(DocumentArtifact.CanonicalTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < table.rows(); row++) {
            rows.add(new ArrayList<>(Collections.nCopies(table.columns(), "")));
        }
        for (var cell : table.cells()) {
            Integer row = cell.row();
            Integer column = cell.column();
            if (row != null && row >= 0 && row < table.rows() && column != null && column >= 0
                    && column < table.columns() && !Boolean.TRUE.equals(cell.isMergedContinue()) && cell.rawText() != null) {
                rows.get(row).set(column, cell.rawText());
            }
        }
        return new TableGrid(rows);
    }
}
