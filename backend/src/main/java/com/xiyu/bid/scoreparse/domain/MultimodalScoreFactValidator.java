package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure validation boundary. It validates model facts but never recalls score items. */
public final class MultimodalScoreFactValidator {

    public ValidationReport validate(List<MultimodalScoreFact> input,
                                     DocumentArtifact artifact,
                                     BigDecimal declaredTotal) {
        return validate(input, artifact, (String) null, declaredTotal);
    }

    public ValidationReport validate(List<MultimodalScoreFact> input,
                                     DocumentArtifact artifact, String tenderText,
                                     BigDecimal declaredTotal) {
        return validate(input, artifact, OriginalScoringContext.resolve(artifact, tenderText), tenderText,
                declaredTotal);
    }

    public ValidationReport validate(List<MultimodalScoreFact> input,
                                     DocumentArtifact artifact, OriginalScoringContext context,
                                     BigDecimal declaredTotal) {
        return validate(input, artifact, context, null, declaredTotal);
    }

    public ValidationReport validate(List<MultimodalScoreFact> input,
                                     DocumentArtifact artifact, OriginalScoringContext context,
                                     String tenderText, BigDecimal declaredTotal) {
        List<MultimodalScoreFact> facts = input == null ? List.of() : List.copyOf(input);
        List<Violation> violations = new ArrayList<>();
        if (facts.isEmpty()) {
            violations.add(new Violation("", "facts", "EMPTY_FACTS", "模型必须返回至少一个评分项",
                    "non-empty", 0, ""));
        }
        Map<String, CellSource> cells = indexCells(artifact);
        var sourceVerification = OriginalScoreEvidencePolicy.verifyModel(context, artifact, tenderText, facts, declaredTotal);
        sourceVerification.problems().forEach(problem -> violations.add(new Violation(problem.factId(),
                problem.field(), problem.code(), "模型字段缺少对应原文依据", problem.expected(),
                problem.actual(), problem.locator())));
        Map<String, OriginalScoreEvidencePolicy.Qualification> qualificationsByFact = sourceVerification.qualifications().stream()
                .collect(java.util.stream.Collectors.toMap(OriginalScoreEvidencePolicy.Qualification::factId,
                        qualification -> qualification, (first, duplicate) -> first));
        Set<String> eligibleFactIds = sourceVerification.qualifications().stream()
                .filter(OriginalScoreEvidencePolicy.Qualification::candidateEligible)
                .map(OriginalScoreEvidencePolicy.Qualification::factId)
                .collect(java.util.stream.Collectors.toSet());
        List<MultimodalScoreFact> eligibleFacts = facts.stream()
                .filter(fact -> eligibleFactIds.contains(OriginalScoreEvidencePolicy.modelFactId(fact)))
                .map(fact -> applyQualification(fact, qualificationsByFact)).toList();
        Set<String> outOfSchemeIds = sourceVerification.qualifications().stream()
                .filter(qualification -> "OUT_OF_SCHEME".equals(qualification.scopeStatus()))
                .map(OriginalScoreEvidencePolicy.Qualification::factId)
                .collect(java.util.stream.Collectors.toSet());
        List<MultimodalScoreFact> validationFacts = facts.stream()
                .filter(fact -> !outOfSchemeIds.contains(OriginalScoreEvidencePolicy.modelFactId(fact)))
                .map(fact -> applyQualification(fact, qualificationsByFact)).toList();
        validateRequiredFields(validationFacts, violations);
        validateEvidence(validationFacts, cells, tenderText, violations);
        validateDuplicates(validationFacts, violations);
        validateSequences(validationFacts, violations);
        validateParents(validationFacts, cells, violations);
        List<ScoreUnitPolicy.Node> scoreNodes = eligibleFacts.stream().map(f -> new ScoreUnitPolicy.Node(
                factKey(f), parentKey(f, eligibleFacts), f.weight(), f.childAggregation().name())).toList();
        BigDecimal leafTotal = ScoreUnitPolicy.totalWeight(scoreNodes);
        if (ScoreUnitPolicy.hasUnknownRelation(scoreNodes)) {
            violations.add(new Violation("", "childAggregation", "CHILD_AGGREGATION_UNKNOWN",
                    "父子计分关系无法判明", "known relationship", "UNKNOWN", "model scoring facts"));
        }
        BigDecimal originalTotal = sourceVerification.total().value();
        if (originalTotal == null || leafTotal.compareTo(originalTotal) != 0) {
            violations.add(new Violation("", "weight", "WEIGHT_SUM_MISMATCH",
                    "叶子项总分必须与已核验原文总分闭合", originalTotal, leafTotal,
                    sourceVerification.total().locator()));
        }
        return new ValidationReport(facts, declaredTotal, originalTotal, leafTotal, violations.isEmpty(),
                violations, sourceVerification);
    }

    private static MultimodalScoreFact applyQualification(MultimodalScoreFact fact,
            Map<String, OriginalScoreEvidencePolicy.Qualification> qualificationsByFact) {
        var qualification = qualificationsByFact.get(OriginalScoreEvidencePolicy.modelFactId(fact));
        return qualification == null ? fact : fact.withScope(qualification.schemeId(), qualification.scopeStatus());
    }

    private static void validateRequiredFields(List<MultimodalScoreFact> facts, List<Violation> violations) {
        for (MultimodalScoreFact fact : facts) {
            if (fact.dimension().isBlank()) {
                violations.add(of(fact, "dimension", "DIMENSION_MISSING", "评分项名称不能为空", "non-empty", ""));
            }
            if (fact.ruleText().isBlank()) {
                violations.add(of(fact, "ruleText", "RULE_MISSING", "评分规则不能为空", "non-empty", ""));
            }
            if (!"OBJECTIVE".equals(fact.scoreType()) && !"SUBJECTIVE".equals(fact.scoreType())) {
                violations.add(of(fact, "scoreType", "SCORE_TYPE_INVALID", "主客观类型必须明确合法",
                        "OBJECTIVE|SUBJECTIVE", fact.scoreType()));
            }
        }
    }

    private static void validateEvidence(List<MultimodalScoreFact> facts, Map<String, CellSource> cells,
                                         String tenderText,
                                         List<Violation> violations) {
        for (MultimodalScoreFact fact : facts) {
            List<CellSource> sources = fact.sourceCellIds().stream().map(cells::get).filter(java.util.Objects::nonNull).toList();
            String normalizedQuote = normalizeEvidence(fact.quotedText());
            boolean textLocator = fact.tableId().isBlank() && fact.sourceCellIds().size() == 1
                    && fact.sourceCellIds().get(0).startsWith("text:line[")
                    && tenderText != null && !fact.quotedText().isBlank()
                    && !normalizedQuote.isBlank() && normalizeEvidence(tenderText).contains(normalizedQuote);
            boolean locatorValid = textLocator || (!fact.tableId().isBlank() && !fact.sourceCellIds().isEmpty() && !sources.isEmpty()
                    && sources.size() == fact.sourceCellIds().size()
                    && sources.stream().allMatch(cell -> cell.tableId().equals(fact.tableId()))
                    && sources.stream().anyMatch(cell -> java.util.Objects.equals(cell.page(), fact.pageNumber())));
            if (!locatorValid) {
                violations.add(of(fact, "sourceCellIds", "SOURCE_LOCATOR_INVALID", "引用必须绑定输入资产中的页码、表和来源单元格", fact.sourceCellIds(), sources));
            }
            String exactSource = textLocator ? tenderText
                    : sources.stream().map(CellSource::rawText).reduce((a, b) -> a + b).orElse("");
            if (fact.quotedText().isBlank() || normalizedQuote.isBlank()
                    || !normalizeEvidence(exactSource).contains(normalizedQuote)) {
                violations.add(of(fact, "quotedText", "QUOTE_NOT_VERBATIM", "引用必须逐字存在于指定原始单元格", exactSource, fact.quotedText()));
            }
        }
    }

    private static String normalizeEvidence(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ')
                .replace('\u2007', ' ').replace('\u202f', ' ')
                .replaceAll("[\\t\\r\\n]+", " ").replaceAll(" +", " ").trim();
    }

    private static void validateDuplicates(List<MultimodalScoreFact> facts, List<Violation> violations) {
        Set<String> codes = new HashSet<>();
        Set<String> locators = new HashSet<>();
        for (MultimodalScoreFact fact : facts) {
            if (!codes.add(fact.schemeId() + "\u0000" + fact.scopeStatus() + "\u0000"
                    + fact.sectionTitle() + "\u0000" + fact.code())) {
                violations.add(of(fact, "code", "DUPLICATE_CODE", "同章节序号重复", "unique", fact.code()));
            }
            String locator = fact.tableId() + "\u0000" + String.join(",", fact.sourceCellIds());
            if (fact.tableId().isBlank() && fact.sourceCellIds().size() == 1
                    && fact.sourceCellIds().get(0).startsWith("text:line[")) {
                locator += "\u0000quote:" + normalizeEvidence(fact.quotedText());
            }
            if (!locators.add(locator)) {
                violations.add(of(fact, "sourceCellIds", "DUPLICATE_SOURCE", "多个评分项重复引用同一来源", "unique", locator));
            }
        }
    }

    private static void validateSequences(List<MultimodalScoreFact> facts, List<Violation> violations) {
        Map<String, List<MultimodalScoreFact>> siblings = new LinkedHashMap<>();
        facts.forEach(f -> siblings.computeIfAbsent(f.schemeId() + "\u0000" + f.scopeStatus() + "\u0000" + f.sectionTitle()
                + "\u0000" + f.parentCode(), key -> new ArrayList<>()).add(f));
        siblings.forEach((group, rows) -> {
            boolean numbered = rows.stream().anyMatch(row -> fLocalNumber(row) != null);
            if (!numbered) return;
            for (int i = 0; i < rows.size(); i++) {
                Integer actual = fLocalNumber(rows.get(i));
                int expected = i + 1;
                if (actual == null || actual != expected) {
                    violations.add(of(rows.get(i), "code", "SEQUENCE_GAP", "同章节同父项下序号须从1按物理顺序连续", expected, rows.get(i).code()));
                }
            }
        });
    }

    private static void validateParents(List<MultimodalScoreFact> facts, Map<String, CellSource> cells,
                                        List<Violation> violations) {
        Map<String, MultimodalScoreFact> byKey = new HashMap<>();
        facts.forEach(f -> byKey.put(logicalItemKey(f), f));
        Map<String, BigDecimal> childTotals = new HashMap<>();
        Map<String, List<MultimodalScoreFact>> childrenByParent = new HashMap<>();
        for (MultimodalScoreFact child : facts) {
            if (child.parentCode().isBlank()) continue;
            MultimodalScoreFact parent = MultimodalFactParentCodeNormalizer.resolveParent(child, facts);
            if (parent == null) {
                violations.add(of(child, "parentCode", "PARENT_NOT_FOUND", "父项不存在", child.parentCode(), "missing"));
            } else if (child.weight() != null) {
                String key = logicalItemKey(parent);
                childTotals.merge(key, child.weight(), BigDecimal::add);
                childrenByParent.computeIfAbsent(key, ignored -> new ArrayList<>()).add(child);
            }
        }
        childTotals.keySet().forEach(key -> {
            MultimodalScoreFact parent = byKey.get(key);
            BigDecimal total = childTotals.getOrDefault(key, BigDecimal.ZERO);
            if (parent == null) return;
            if (parent.childAggregation() == ChildAggregation.SUM
                    && (parent.weight() == null || parent.weight().compareTo(total) != 0)) {
                violations.add(of(parent, "weight", "PARENT_WEIGHT_MISMATCH", "父项分值必须等于直接子项合计", total, parent.weight()));
            } else if (parent.childAggregation() != ChildAggregation.UNKNOWN
                    && !relationSupported(parent, childrenByParent.getOrDefault(key, List.of()), cells)) {
                violations.add(of(parent, "childAggregation", "CHILD_AGGREGATION_UNSUPPORTED_BY_SOURCE",
                        "原文未能证明模型选择的父子计分关系", parent.childAggregation(), parent.ruleText()));
            }
        });
    }

    private static boolean relationSupported(MultimodalScoreFact parent, List<MultimodalScoreFact> children,
                                             Map<String, CellSource> cells) {
        var citedCellIds = new java.util.ArrayList<>(parent.sourceCellIds());
        children.stream().flatMap(child -> child.sourceCellIds().stream()).forEach(citedCellIds::add);
        String evidence = citedCellIds.stream().map(cells::get).filter(java.util.Objects::nonNull)
                .map(CellSource::rawText).collect(java.util.stream.Collectors.joining(" "));
        return parent.childAggregation().supportedByOriginalText(evidence);
    }

    private static String factKey(MultimodalScoreFact fact) {
        return logicalKey(fact, fact.code());
    }

    private static String parentKey(MultimodalScoreFact fact, List<MultimodalScoreFact> facts) {
        MultimodalScoreFact parent = MultimodalFactParentCodeNormalizer.resolveParent(fact, facts);
        return parent == null ? "" : factKey(parent);
    }

    private static String logicalItemKey(MultimodalScoreFact fact) {
        return logicalKey(fact, fact.code());
    }

    private static String logicalKey(MultimodalScoreFact fact, String code) {
        return fact.schemeId() + "\u0000" + fact.scopeStatus() + "\u0000" + fact.sectionTitle() + "\u0000" + code;
    }

    private static Map<String, CellSource> indexCells(DocumentArtifact artifact) {
        Map<String, CellSource> indexed = new HashMap<>();
        if (artifact == null) return indexed;
        artifact.tables().forEach(table -> table.cells().forEach(cell -> indexed.put(cell.cellId(),
                new CellSource(table.tableId(), cell.sourcePage() == null ? table.sourcePage() : cell.sourcePage(),
                        cell.rawText() == null ? "" : cell.rawText()))));
        return indexed;
    }

    private static Integer leadingNumber(String code) {
        if (code == null) return null;
        var matcher = java.util.regex.Pattern.compile("^(\\d+)").matcher(code.trim());
        if (matcher.find()) return Integer.valueOf(matcher.group(1));
        return switch (code.trim().replaceFirst("^[（(]", "").replaceFirst("[）)].*$", "")) {
            case "一" -> 1; case "二" -> 2; case "三" -> 3; case "四" -> 4; case "五" -> 5;
            case "六" -> 6; case "七" -> 7; case "八" -> 8; case "九" -> 9; case "十" -> 10;
            default -> null;
        };
    }

    private static Integer fLocalNumber(MultimodalScoreFact fact) {
        if (fact.parentCode().isBlank()) return leadingNumber(fact.code());
        String prefix = fact.parentCode() + ".";
        return fact.code().startsWith(prefix) ? leadingNumber(fact.code().substring(prefix.length())) : null;
    }

    private static Violation of(MultimodalScoreFact fact, String field, String code, String message,
                                Object expected, Object actual) {
        return new Violation(fact.sectionTitle() + "/" + fact.code(), field, code, message, expected, actual,
                fact.tableId() + ":" + String.join(",", fact.sourceCellIds()));
    }

    private record CellSource(String tableId, Integer page, String rawText) {}
    public record Violation(String factId, String field, String code, String message,
                            Object expected, Object actual, String evidence) {}
    public record ValidationReport(List<MultimodalScoreFact> facts, BigDecimal declaredTotal,
                                   BigDecimal originalTotal, BigDecimal leafTotal, boolean autoApprovable,
                                   List<Violation> violations, OriginalScoreEvidencePolicy.Verification sourceVerification) {
        public ValidationReport {
            facts = List.copyOf(facts);
            violations = List.copyOf(violations);
        }
    }
}
