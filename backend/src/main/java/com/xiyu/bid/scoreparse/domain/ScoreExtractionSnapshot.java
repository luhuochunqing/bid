package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/** Pure full-candidate projection; no evidence approval gate is used as a numerical gate. */
public record ScoreExtractionSnapshot(int payloadVersion, List<ExtractedScoreItem> items,
        List<Scheme> schemes, List<String> sourceDiagnostics, Map<String,Object> runDiagnostics) {
    public ScoreExtractionSnapshot(int version,List<ExtractedScoreItem> items,List<Scheme> schemes,List<String> diagnostics) {
        this(version,items,schemes,diagnostics,Map.of());
    }
    public record Scheme(String schemeId, String title, int extractedCount, BigDecimal extractedKnownTotal,
            boolean extractedTotalComplete, int computableCount, BigDecimal computableTotal,
            BigDecimal declaredTotal, String declaredTotalStatus) {}

    public static ScoreExtractionSnapshot from(String taskId, List<ScoreFact> facts,
            List<OriginalScoreEvidencePolicy.Qualification> qualifications, List<String> sourceDiagnostics) {
        List<ScoreFact> safe = facts == null ? List.of() : facts;
        var qs = qualifications == null ? List.<OriginalScoreEvidencePolicy.Qualification>of() : qualifications;
        Map<String, OriginalScoreEvidencePolicy.Qualification> byFact = new LinkedHashMap<>();
        Map<String, OriginalScoreEvidencePolicy.Qualification> schemes = new LinkedHashMap<>();
        qs.forEach(q -> {
            byFact.putIfAbsent(q.factId(), q);
            if (q.schemeId() != null && !q.schemeId().isBlank() && !"OUT_OF_SCHEME".equals(q.scopeStatus()))
                schemes.putIfAbsent(q.schemeId(),q);
        });
        Set<String> documents=safe.stream().map(ScoreExtractionSnapshot::fileId).filter(d -> !d.isBlank()).collect(Collectors.toSet());
        Map<String,String> scopedSchemes=new LinkedHashMap<>();
        for(var f:safe) {
            String scope=resolveScheme(f,byFact.get(f.factId()),schemes);
            if(documents.size()>1 && !scope.isBlank()) scope=fileId(f).isBlank()?"":fileId(f)+"::"+scope;
            scopedSchemes.put(f.factId(),scope);
        }
        Map<String, String> ids = new LinkedHashMap<>();
        safe.forEach(f -> ids.putIfAbsent(f.factId(), stableId(taskId,f)));
        var sourceIdentityCounts=safe.stream().filter(ScoreExtractionSnapshot::selected).map(f -> ids.get(f.factId())).collect(Collectors.groupingBy(java.util.function.Function.identity(),Collectors.counting()));
        Map<String, ScoreFact> factMap = new LinkedHashMap<>();
        safe.forEach(f -> factMap.putIfAbsent(f.factId(), f));
        Map<String, Long> occurrences = safe.stream().collect(Collectors.groupingBy(ScoreFact::factId,Collectors.counting()));
        List<ExtractedScoreItem> result = new ArrayList<>();
        for (ScoreFact f : safe) {
            var q = byFact.get(f.factId());
            List<String> problems = new ArrayList<>(f.sourceMetadata().diagnostics());
            String scheme = scopedSchemes.get(f.factId());
            if (q != null && !q.candidateEligible()) problems.add(q.reason());
            if (q != null && "OUT_OF_SCHEME".equals(q.scopeStatus())) problems.add("OUT_OF_SCHEME");
            if (scheme.isBlank()) problems.add("SCHEME_UNRESOLVED");
            String numericalText=f.sourceText()+" "+f.sourceMetadata().modelRuleText()+" "+(f.weightEvidence()==null?"":f.weightEvidence().quotedText());
            boolean ratio = numericalText.matches("(?s).*(?:(?:技术|商务|价格|报价|综合)(?:部分|评分|得分)?\\s*(?:占比|权重|占总分|占综合分)|(?:本项|原始满分|原始分值)[^。；;\\n]{0,30}(?:占比|权重))[^。；;\\n]{0,8}\\d+(?:\\.\\d+)?\\s*[％%].*")
                    || numericalText.matches("(?s).*[％%].*") && !numericalText.matches("(?s).*\\d+(?:\\.\\d+)?\\s*分.*");
            boolean numericalConflict = problems.stream().anyMatch(p -> Set.of("ITEM_WEIGHT_SOURCE_MISMATCH", "CHILD_AGGREGATION_UNSUPPORTED", "DUPLICATE_ITEM", "DUPLICATE_WEIGHT_CONFLICT").contains(p));
            boolean precision = f.weight()==null || f.weight().stripTrailingZeros().scale()<=2;
            if (!precision) problems.add("WEIGHT_PRECISION_UNSUPPORTED");
            if (ratio) problems.add("RATIO_UNRESOLVED");
            boolean zeroDeduction = SourceDeductionSelectionPolicy.verified(f, q);
            if (f.weight() == null) problems.add("WEIGHT_UNKNOWN");
            else if ((f.weight().signum() <= 0 && !zeroDeduction) || f.weight().compareTo(new BigDecimal("9999.99")) > 0) problems.add("WEIGHT_INVALID");
            var localGraph=factMap.values().stream().filter(ScoreExtractionSnapshot::selected).filter(x -> scheme.equals(scopedSchemes.get(x.factId())))
                    .filter(x -> occurrences.get(x.factId())==1).collect(Collectors.toMap(ScoreFact::factId,java.util.function.Function.identity(), (first, duplicate) -> first));
            boolean structure = validPath(f, localGraph, new HashSet<>());
            if (!structure) problems.add("PARENT_STRUCTURE_UNRESOLVED");
            if (selected(f) && sourceIdentityCounts.getOrDefault(ids.get(f.factId()),0L)>1) problems.add("DUPLICATE_SOURCE_UNIT");
            if (occurrences.get(f.factId()) > 1) problems.add("DUPLICATE_FACT_ID");
            var nodes = safe.stream().filter(ScoreExtractionSnapshot::selected).filter(x -> scheme.equals(scopedSchemes.get(x.factId())))
                    .map(x -> new ScoreUnitPolicy.Node(x.factId(),x.parentFactId(),x.weight(),x.childAggregation().name())).toList();
            boolean unit = ScoreUnitPolicy.effectiveUnitIds(nodes).contains(f.factId());
            if (!unit) problems.add("NOT_EFFECTIVE_SCORING_UNIT");
            boolean ruleSourceVerified = !f.sourceMetadata().diagnostics().contains("FULL_RULE_UNIT_UNRESOLVED")
                    && !f.sourceMetadata().diagnostics().contains("UNVERIFIED_CLOSURE")
                    && !f.sourceText().isBlank();
            String itemRule = ruleSourceVerified ? f.sourceText()
                    : (!f.sourceMetadata().modelRuleText().isBlank() ? f.sourceMetadata().modelRuleText() : f.sourceText());
            String resolvedDim = ScoreItemTitleExtractor.resolveDimension(f.dimension(), itemRule);
            boolean validName = !ScoreCategoryClassifier.isInvalidOrPlaceholderName(resolvedDim);
            if (!validName) problems.add("INVALID_SCORE_ITEM_NAME");
            boolean computable = selected(f) && validName && precision && !scheme.isBlank() && !ratio && f.weight()!=null && (f.weight().signum()>0 || zeroDeduction)
                    && f.weight().compareTo(new BigDecimal("9999.99"))<=0 && !numericalConflict && structure && unit && occurrences.get(f.factId())==1 && sourceIdentityCounts.getOrDefault(ids.get(f.factId()),0L)==1
                    && f.validationStatus()!=ScoreFact.ValidationStatus.DUPLICATE && f.validationStatus()!=ScoreFact.ValidationStatus.STRUCTURE_INCOMPLETE;
            String scoreType = f.sourceMetadata().modelScoreType();
            if (!Set.of("OBJECTIVE","SUBJECTIVE").contains(scoreType)) scoreType = new ScoreTypeClassificationPolicy().classify(f.sourceText());
            String id = ids.get(f.factId());
            if (occurrences.get(f.factId()) > 1 || sourceIdentityCounts.getOrDefault(id,0L)>1) id = "local:"+ScoreVersionFingerprint.hash(taskId+":"+f.factId()+":"+result.size()).substring(0,40);
            result.add(new ExtractedScoreItem(1,id,f.factId(),scheme,fileId(f),resolvedDim,
                    f.sourceMetadata().modelCategory(), itemRule,
                    List.of(),f.weight(),null,computable?f.weight():null,ratio?"PERCENTAGE":"POINTS",scoreType,
                    ids.get(f.parentFactId()),f.childAggregation().name(),Map.of("rawMaximum",f.isAutoApprovable() && f.weightEvidence()!=null?"SOURCE_EVIDENCE":"MODEL_CLAIM",
                    "ruleText",ruleSourceVerified?"SOURCE":"MODEL_CLAIM",
                    "schemeId",q!=null && "IN_SCHEME".equals(q.scopeStatus())?"SOURCE_EVIDENCE":"MODEL_CLAIM"),
                    problems.stream().filter(Objects::nonNull).distinct().toList(),f.validationStatus().name(),
                    BigDecimal.valueOf(f.confidence()),computable,f,q));
        }
        List<Scheme> summaries = new ArrayList<>();
        Set<String> schemeIds = documents.size()>1?new LinkedHashSet<>():new LinkedHashSet<>(schemes.keySet());
        result.stream().map(ExtractedScoreItem::schemeId).filter(s -> !s.isBlank()).forEach(schemeIds::add);
        for(String id : schemeIds) {
            String declarationId=id.contains("::")?id.substring(id.indexOf("::")+2):id;
            var members = result.stream().filter(i -> id.equals(i.schemeId())).toList();
            var units = members.stream().filter(i -> !i.diagnostics().contains("NOT_EFFECTIVE_SCORING_UNIT") && !i.diagnostics().contains("DUPLICATE_SOURCE_UNIT") && !i.diagnostics().contains("DUPLICATE_FACT_ID")).toList();
            var schemeQualifications=qs.stream().filter(q -> declarationId.equals(q.schemeId()) && declarationBelongs(q,id,documents)).toList();
            var declared = schemeQualifications.stream().map(OriginalScoreEvidencePolicy.Qualification::declaredTotal)
                    .filter(Objects::nonNull).map(BigDecimal::stripTrailingZeros).distinct().toList();
            boolean conflict = declared.size()>1 || schemeQualifications.stream().anyMatch(q -> "CONFLICT".equals(q.totalEvidenceStatus()));
            BigDecimal total = declared.size()==1 && !conflict ? declared.get(0) : null;
            var computable = members.stream().filter(ExtractedScoreItem::computable).toList();
            summaries.add(new Scheme(id,schemes.containsKey(declarationId)?schemes.get(declarationId).schemeTitle():id,units.size(),
                    units.stream().map(ExtractedScoreItem::rawMaximum).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add),
                    !units.isEmpty() && members.stream().filter(i -> selected(i.fact())).noneMatch(i -> i.rawMaximum()==null || i.diagnostics().contains("FULL_RULE_UNIT_UNRESOLVED") || i.diagnostics().contains("WEIGHT_PRECISION_UNSUPPORTED") || i.diagnostics().contains("RATIO_UNRESOLVED") || i.diagnostics().contains("PARENT_STRUCTURE_UNRESOLVED") || i.diagnostics().contains("DUPLICATE_SOURCE_UNIT") || i.diagnostics().contains("DUPLICATE_FACT_ID") || i.diagnostics().contains("ITEM_WEIGHT_SOURCE_MISMATCH")),
                    computable.size(),computable.stream().map(ExtractedScoreItem::effectiveMaximum).reduce(BigDecimal.ZERO,BigDecimal::add),total,conflict?"CONFLICT":total==null?"UNKNOWN":"KNOWN"));
        }
        return new ScoreExtractionSnapshot(1,List.copyOf(result),List.copyOf(summaries),sourceDiagnostics==null?List.of():List.copyOf(sourceDiagnostics));
    }
    private static boolean selected(ScoreFact fact) {
        return !fact.sourceMetadata().diagnostics().contains("ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED");
    }
    private static boolean declarationBelongs(OriginalScoreEvidencePolicy.Qualification q,String scope,Set<String> documents) {
        if(documents.size()<=1) return true;
        String locator=q.totalLocator();
        var matches=documents.stream().filter(d -> locator.startsWith(d+":") || locator.startsWith(d+"-") || locator.startsWith(d+"/")).toList();
        return matches.size()==1 && scope.startsWith(matches.get(0)+"::");
    }
    private static boolean validPath(ScoreFact f, Map<String,ScoreFact> all, Set<String> seen) {
        if(!seen.add(f.factId()) || f.sourceMetadata().diagnostics().contains("CHILD_AGGREGATION_UNSUPPORTED")) return false;
        var children=all.values().stream().filter(c -> f.factId().equals(c.parentFactId())).toList();
        if(f.childAggregation()==ChildAggregation.SUM && f.weight()!=null && !children.isEmpty() && children.stream().allMatch(c -> c.weight()!=null)
                && children.stream().map(ScoreFact::weight).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(f.weight())!=0) return false;
        if(f.parentFactId()==null || f.parentFactId().isBlank()) return true;
        var parent = all.get(f.parentFactId());
        return parent!=null && parent.childAggregation()!=ChildAggregation.UNKNOWN && validPath(parent,all,seen);
    }
    private static String resolveScheme(ScoreFact f, OriginalScoreEvidencePolicy.Qualification q,
            Map<String,OriginalScoreEvidencePolicy.Qualification> schemes) {
        if(q!=null && "OUT_OF_SCHEME".equals(q.scopeStatus())) return "";
        if(q!=null && "IN_SCHEME".equals(q.scopeStatus()) && q.schemeId()!=null && !q.schemeId().isBlank()) return q.schemeId();
        var claim = f.sourceMetadata().modelSourceClaim();
        if("OUT_OF_SCHEME".equals(claim.scopeStatus())) return "";
        var matches = schemes.values().stream().filter(s -> s.schemeId().equals(claim.schemeId())
                || !claim.sectionTitle().isBlank() && OriginalScoringContext.schemeId(s.schemeTitle()).equals(OriginalScoringContext.schemeId(claim.sectionTitle())))
                .map(OriginalScoreEvidencePolicy.Qualification::schemeId).distinct().toList();
        return matches.size()==1 ? matches.get(0) : "";
    }
    private static String stableId(String task, ScoreFact f) {
        if(!selected(f)) return "alternative:"+ScoreVersionFingerprint.hash(task+":"+f.factId()).substring(0,40);
        var spans=f.sourceMetadata().sourceSpans();
        if(spans.isEmpty()) return "local:"+ScoreVersionFingerprint.hash(task+":"+f.factId()).substring(0,40);
        String anchors=spans.stream().map(s -> s.fileSha256()+":"+s.documentId()+":"+localSourceId(s)+":"+s.startOffset()+":"+s.endOffset()).sorted().collect(Collectors.joining("|"));
        return "source:"+ScoreVersionFingerprint.hash(anchors+":"+f.code()+":"+f.dimension()).substring(0,40);
    }
    private static String localSourceId(SourceSpan span) {
        // Bundle docN prefixes are routing aliases; the file hash owns identity.
        return span.fileSha256().isBlank() || span.documentId().isBlank()
                ? span.sourceId() : span.sourceId().replaceFirst("^doc[0-9]+-", "");
    }
    private static String fileId(ScoreFact f) {
        return f.sourceMetadata().sourceSpans().isEmpty()?"":f.sourceMetadata().sourceSpans().get(0).documentId();
    }
}
