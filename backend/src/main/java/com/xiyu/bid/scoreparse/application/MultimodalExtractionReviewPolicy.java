package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreTableSplitPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreTableSummaryPolicy;
import com.xiyu.bid.scoreparse.domain.TableSplitResult;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/** Projection helpers for the multimodal score extraction result. */
final class MultimodalExtractionReviewPolicy {
    private MultimodalExtractionReviewPolicy() {}

    static DocumentArtifact artifact(LoadedBidFile loaded, String fileName,
                                             SidecarDocumentArtifactExtractor extractor)
            throws IOException, InterruptedException {
        return switch (loaded) {
            case LoadedBidFile.InMemory inMem -> extractor.extract(inMem.content(), fileName);
            case LoadedBidFile.OnDisk onDisk -> extractor.extract(onDisk.tempFile(), fileName);
        };
    }

    static MultimodalScoreFactExtractor.ExtractionResult normalized(
            MultimodalScoreFactExtractor.ExtractionResult extraction, OriginalScoringContext context,
            DocumentArtifact artifact, String tenderText) {
        BigDecimal declaredTotal = extraction.declaredTotal();
        if (declaredTotal == null && context != null && context.total() != null && context.total().value() != null) {
            declaredTotal = context.total().value();
        }
        List<OriginalScoringContext.SchemeEvidence> schemes = OriginalScoringContext.schemes(artifact, tenderText);
        List<MultimodalScoreFact> scopedFacts = extraction.facts().stream()
                .map(fact -> assignScope(fact, context, artifact, tenderText, schemes)).toList();
        return new MultimodalScoreFactExtractor.ExtractionResult(declaredTotal,
                MultimodalFactParentCodeNormalizer.normalize(scopedFacts), extraction.model(),
                extraction.imageCount());
    }

    static MultimodalScoreFact assignScope(MultimodalScoreFact fact, OriginalScoringContext context,
                                                   DocumentArtifact artifact, String tenderText,
                                                   List<OriginalScoringContext.SchemeEvidence> schemes) {
        if (context != null && !context.scheme().isBlank()) {
            String contextSchemeId = OriginalScoringContext.schemeId(context.scheme());
            if (fact.sectionTitle() != null && fact.sectionTitle().matches("(?s).*(?:合同履约考核|履约考核|合同绩效考核|服务考核).*")) {
                return fact.withScope(contextSchemeId, "OUT_OF_SCHEME");
            }
            if (fact.tableId().isBlank() && (
                    "performance".equalsIgnoreCase(fact.dimension())
                    || "service".equalsIgnoreCase(fact.dimension())
                    || (fact.code() != null && fact.code().matches("(?i).*(?:evaluation_metric|evaluation_indicator|_evaluation|evaluation_index|.*_KH$|.*_PJ$).*"))
                    || (fact.quotedText() != null && fact.quotedText().matches("(?s).*(?:考核指标|履约考核|绩效考核|动销率|合格率|时效考核|核销考核|故障处理).*")))) {
                return fact.withScope(contextSchemeId, "OUT_OF_SCHEME");
            }
            if (fact.tableId().isBlank() && (fact.weight() == null || fact.weight().compareTo(java.math.BigDecimal.ZERO) <= 0)) {
                return fact.withScope(contextSchemeId, "OUT_OF_SCHEME");
            }
            boolean hasScoringTables = artifact != null && artifact.tables().stream().anyMatch(candidate -> Boolean.TRUE.equals(candidate.isScoreTable()));
            if (hasScoringTables && fact.tableId().isBlank() && (fact.sourceCellIds() == null || fact.sourceCellIds().isEmpty())) {
                if (isScoringSection(fact.sectionTitle()) && hasScoringCode(fact.code())
                        && fact.weight() != null && fact.weight().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    return fact.withScope(contextSchemeId, "IN_SCHEME");
                }
                return fact.withScope(contextSchemeId, "OUT_OF_SCHEME");
            }
        }
        if (!fact.tableId().isBlank() && artifact != null) {
            var table = artifact.tables().stream().filter(candidate -> fact.tableId().equals(candidate.tableId()))
                    .findFirst().orElse(null);
            if (table != null) {
                String section = table.sourceSection() == null ? "" : table.sourceSection();
                String title = OriginalScoringContext.schemeTitleForTable(table);
                if (!title.isBlank() && Boolean.TRUE.equals(table.isScoreTable())) {
                    String logicalSchemeId = schemes.stream().filter(scheme -> scheme.tableIds().contains(table.tableId()))
                            .map(OriginalScoringContext.SchemeEvidence::schemeId).findFirst().orElse(null);
                    return fact.withScope(logicalSchemeId == null ? OriginalScoringContext.schemeId(title)
                            : logicalSchemeId, "IN_SCHEME");
                }
                if (section.matches("(?s).*(?:合同履约考核|履约考核|合同绩效考核|服务考核).*")
                        && !context.scheme().isBlank())
                    return fact.withScope(OriginalScoringContext.schemeId(context.scheme()), "OUT_OF_SCHEME");
                if (context.owns(table) && !context.scheme().isBlank())
                    return fact.withScope(OriginalScoringContext.schemeId(context.scheme()), "IN_SCHEME");
            }
        }
        if (fact.sourceCellIds().size() == 1 && fact.sourceCellIds().get(0).startsWith("text:line[")) {
            try {
                int line = Integer.parseInt(fact.sourceCellIds().get(0).replaceAll("\\D", ""));
                OriginalScoringContext.TextScope scope = OriginalScoringContext.textScopeAt(tenderText, line);
                if (scope != null && !scope.schemeId().isBlank())
                    return fact.withScope(scope.schemeId(), scope.scopeStatus());
            } catch (NumberFormatException ignored) {
                return fact;
            }
        }
        if (!fact.scopeStatus().isBlank() && !fact.schemeId().isBlank()) return fact;
        return fact;
    }

    static boolean hasMultipleTables(List<MultimodalScoreFact> facts) {
        if (facts == null) return false;
        java.util.Set<String> parentCodes = facts.stream().map(MultimodalScoreFact::parentCode)
                .filter(p -> p != null && !p.isBlank()).collect(java.util.stream.Collectors.toSet());
        long leafTables = facts.stream().filter(f -> !parentCodes.contains(f.code()))
                .map(MultimodalScoreFact::tableId).filter(t -> t != null && !t.isBlank())
                .distinct().count();
        return leafTables > 1;
    }

    static boolean hasMultipleFactTables(List<ScoreFact> facts) {
        if (facts == null) return false;
        java.util.Set<String> parentIds = facts.stream().map(ScoreFact::parentFactId)
                .filter(p -> p != null && !p.isBlank()).collect(java.util.stream.Collectors.toSet());
        long leafTables = facts.stream().filter(f -> !parentIds.contains(f.factId()))
                .map(ScoreFact::sourceTableId).filter(t -> t != null && !t.isBlank())
                .distinct().count();
        return leafTables > 1;
    }

    static boolean hasIndependentTableTotals(OriginalScoringContext context,
                                                      List<MultimodalScoreFact> facts) {
        if (context == null || context.subtotals().isEmpty()) {
            return false;
        }
        return facts.stream().map(MultimodalScoreFact::tableId).filter(tableId -> tableId != null && !tableId.isBlank())
                .distinct().allMatch(context.subtotals()::containsKey);
    }

    static boolean documentTotalCloses(OriginalScoringContext context, MultimodalScoreFactValidator.ValidationReport validation) {
        return context != null && context.total().value() != null && validation != null && validation.leafTotal() != null
                && context.total().value().compareTo(validation.leafTotal()) == 0;
    }

    static TableSplitResult splitModelFacts(List<ScoreFact> facts, OriginalScoringContext context,
                                                     String tenderSectionText) {
        List<ScoreTableSplitPolicy.ScoreTableInput> inputs = facts.stream()
                .collect(java.util.stream.Collectors.groupingBy(ScoreFact::sourceTableId,
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new ScoreTableSplitPolicy.ScoreTableInput(entry.getKey(), true, entry.getValue()))
                .toList();
        List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> totals = context == null ? List.of()
                : context.subtotals().entrySet().stream()
                .map(entry -> new ScoreTableSummaryPolicy.DeclaredTotalEvidence(entry.getValue().value(),
                        entry.getKey(), -1, entry.getValue().locator(), entry.getValue().locator()))
                .toList();
        return ScoreTableSplitPolicy.split(inputs, totals, tenderSectionText);
    }

    private static boolean isScoringSection(String sectionTitle) {
        if (sectionTitle == null || sectionTitle.isBlank()) return true;
        return sectionTitle.matches("(?s).*(?:评审办法|评标办法|评分办法|评分标准|评审标准|综合评估法|分值构成|综合评分).*")
                || (sectionTitle.matches("(?s).*第[一二三四五六七八九十0-9]+章.*") && !sectionTitle.matches("(?s).*(?:合同|履约|考核).*"));
    }

    private static boolean hasScoringCode(String code) {
        if (code == null || code.isBlank()) return false;
        return code.matches("^(?:3\\.2\\.\\d|\\d+[._()（）\\-0-9]|\\d+（\\d+）|\\d+\\(\\d+\\)|(?i)(?:BIZ|TECH|PRICE)[_\\d]).*");
    }
}
