package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ScoreExtractionSnapshotTest {
    @Test
    void restoresLeafTitleInsteadOfAlphanumericRowNumber() {
        var fact = new ScoreFact("revenue", "table", List.of(),
                "A1 | 商务 | 营业总收入：2024年营业总收入大于10亿元得5分 | 5",
                "A1", "商务", BigDecimal.valueOf(5), null, null, null,
                "MODEL", 0.8, ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        var scope = new OriginalScoreEvidencePolicy.Qualification("revenue", "scheme", "IN_SCHEME", true, "OK", "");
        var item = ScoreExtractionSnapshot.from("task", List.of(fact), List.of(scope), List.of()).items().getFirst();
        assertThat(item.name()).isEqualTo("营业总收入");
        assertThat(item.computable()).isTrue();
    }

    @Test void selectedUnknownBranchMakesTotalIncompleteEvenWhenEffectiveUnitsStillMatch() {
        var old=fact("old",BigDecimal.TEN);
        var parent=new ScoreFact("p","t",List.of(),"父项5分","p","父项",new BigDecimal("5"),null,null,null,"MODEL",.8,ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.UNKNOWN);
        var child=fact("c",new BigDecimal("5")).withParentFactId("p");
        var fs=List.of(old,parent,child);
        var qs=fs.stream().map(f -> new OriginalScoreEvidencePolicy.Qualification(f.factId(),"s","IN_SCHEME",false,"SOURCE_UNVERIFIED","")).toList();
        var snapshot=ScoreExtractionSnapshot.from("unknown-parent",fs,qs,List.of());
        assertThat(snapshot.schemes().getFirst().extractedKnownTotal()).isEqualByComparingTo("10");
        assertThat(snapshot.schemes().getFirst().extractedTotalComplete()).isFalse();
        assertThat(snapshot.items().stream().filter(i -> i.factId().equals("c")).findFirst().orElseThrow().rawMaximum()).isEqualByComparingTo("5");
    }
    @Test void precisionBeyondActiveStorageIsPreservedButNotProjected() {
        var f=fact("a",new BigDecimal("0.125"));
        var q=new OriginalScoreEvidencePolicy.Qualification("a","s","IN_SCHEME",false,"SOURCE_UNVERIFIED","");
        var item=ScoreExtractionSnapshot.from("task",List.of(f),List.of(q),List.of()).items().getFirst();
        assertThat(item.rawMaximum()).isEqualByComparingTo("0.125");
        assertThat(item.computable()).isFalse();
        assertThat(item.diagnostics()).contains("WEIGHT_PRECISION_UNSUPPORTED");
    }
    @Test void unverifiedNumericalClaimCanComputeButOutOfSchemeCannot() {
        var known = fact("a", new BigDecimal("6"));
        var outside = fact("b", new BigDecimal("40"));
        var unknown = fact("c", null);
        var q = new OriginalScoreEvidencePolicy.Qualification("b", "s", "OUT_OF_SCHEME", false, "OTHER", "");
        var in = new OriginalScoreEvidencePolicy.Qualification("a", "s", "IN_SCHEME", false, "UNVERIFIED", "", "评分标准", new BigDecimal("100"), "KNOWN", "总分100分", "t", "MULTI_TABLE", List.of(), ChildAggregation.SUM);
        var snapshot = ScoreExtractionSnapshot.from("task", List.of(known, outside, unknown), List.of(in,q), List.of("PARTIAL_SOURCE"));
        assertThat(snapshot.items()).hasSize(3);
        assertThat(snapshot.items().get(0).computable()).isTrue();
        assertThat(snapshot.items().get(0).validationStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(snapshot.items().get(1).computable()).isFalse();
        assertThat(snapshot.items().get(2).effectiveMaximum()).isNull();
        assertThat(snapshot.sourceDiagnostics()).contains("PARTIAL_SOURCE");
        assertThat(snapshot.schemes().get(0).declaredTotal()).isEqualByComparingTo("100");
    }
    @Test void percentagesCannotSilentlyBecomePointsAndNoDeclarationIsUnknown() {
        var percent = new ScoreFact("x", "t", List.of("c"), "占比6%", "1", "报价", new BigDecimal("6"), null, null, null, "MODEL", .5, ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        var snapshot = ScoreExtractionSnapshot.from("task", List.of(percent), List.of(), List.of());
        assertThat(snapshot.items().get(0).effectiveMaximum()).isNull();
        assertThat(snapshot.items().get(0).diagnostics()).contains("RATIO_UNRESOLVED");
        assertThat(snapshot.schemes()).allSatisfy(s -> assertThat(s.declaredTotal()).isNull());
    }
    @Test void unknownParentRelationshipAndOrphanMustNotDoubleCount() {
        var parent = fact("p", new BigDecimal("10"));
        parent = new ScoreFact("p", "", List.of(), "最高10分", "p", "p", new BigDecimal("10"),null,null,null,"MODEL",.5,ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.UNKNOWN);
        var snapshot = ScoreExtractionSnapshot.from("task", List.of(parent, fact("c", new BigDecimal("5")).withParentFactId("p"),fact("o",new BigDecimal("2")).withParentFactId("missing")), List.of(), List.of());
        assertThat(snapshot.items()).noneMatch(ExtractedScoreItem::computable);
    }
    @Test void priceDeductionPercentIsNotAWeightRatioAndSharedSourceHasDistinctUnits() {
        var span=new SourceSpan("doc","hash","PDF","s",1,"t","c",0,20,"报价满分28分，每偏离1%扣分","c");
        var q=new OriginalScoreEvidencePolicy.Qualification("a","s","IN_SCHEME",false,"UNVERIFIED","");
        var m=new ScoreSourceMetadata("价格满分28分，每偏离1%扣分","",List.of(span),List.of(),"PRICE","SUBJECTIVE",new ModelSourceClaim("评分标准",1,"t",List.of("c"),"s","IN_SCHEME"));
        var a=new ScoreFact("a","t",List.of("c"),span.rawText(),"1","价格1",new BigDecimal("28"),null,null,null,"MODEL",.5,ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.SUM,m);
        var b=new ScoreFact("b","t",List.of("c"),span.rawText(),"2","价格2",new BigDecimal("12"),null,null,null,"MODEL",.5,ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.SUM,m);
        var snapshot=ScoreExtractionSnapshot.from("task",List.of(a,b),List.of(q),List.of());
        assertThat(snapshot.items()).allMatch(ExtractedScoreItem::computable);
        assertThat(snapshot.items()).extracting(ExtractedScoreItem::itemId).doesNotHaveDuplicates();
    }
    @Test void sameNamedSchemesInDifferentDocumentsCannotBecomeOneAutomaticScheme() {
        List<ScoreFact> facts=new java.util.ArrayList<>();
        List<OriginalScoreEvidencePolicy.Qualification> qs=new java.util.ArrayList<>();
        for(String doc:List.of("docA","docB")) {
            var span=new SourceSpan(doc,doc,"PDF","s",1,"t","c",0,4,"最高5分","c");
            var metadata=new ScoreSourceMetadata("最高5分","最高5分",List.of(span),List.of(),"TECHNICAL","SUBJECTIVE");
            facts.add(new ScoreFact(doc,"t",List.of("c"),"最高5分","1","方案",new BigDecimal("5"),null,null,null,"MODEL",.5,ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.SUM,metadata));
            qs.add(new OriginalScoreEvidencePolicy.Qualification(doc,"s","IN_SCHEME",true,"OK","","评分标准",new BigDecimal(doc.equals("docA")?"100":"50"),"KNOWN","原文总分",doc+":total","SINGLE_TABLE",List.of("t"),ChildAggregation.SUM));
        }
        var snapshot=ScoreExtractionSnapshot.from("task",facts,qs,List.of());
        assertThat(snapshot.schemes()).hasSize(2);
        assertThat(snapshot.schemes().stream().filter(scheme -> scheme.schemeId().startsWith("docA::")).findFirst().orElseThrow().declaredTotal()).isEqualByComparingTo("100");
        assertThat(snapshot.schemes().stream().filter(scheme -> scheme.schemeId().startsWith("docB::")).findFirst().orElseThrow().declaredTotal()).isEqualByComparingTo("50");
        assertThat(snapshot.items()).extracting(ExtractedScoreItem::schemeId).doesNotHaveDuplicates();
    }
    @Test void numericConflictDoesNotBecomeComputableJustBecauseWeightIsPresent() {
        var f=fact("x",new BigDecimal("10"));
        var metadata=new ScoreSourceMetadata("最高10分","",List.of(),List.of("ITEM_WEIGHT_SOURCE_MISMATCH"),"TECHNICAL","SUBJECTIVE");
        var conflicting=new ScoreFact(f.factId(),f.sourceTableId(),f.sourceCellIds(),f.sourceText(),f.code(),f.dimension(),f.weight(),null,null,null,f.extractionMethod(),.5,f.validationStatus(),ChildAggregation.SUM,metadata);
        var q=new OriginalScoreEvidencePolicy.Qualification("x","s","IN_SCHEME",false,"ITEM_WEIGHT_SOURCE_MISMATCH","");
        assertThat(ScoreExtractionSnapshot.from("task",List.of(conflicting),List.of(q),List.of()).items()).noneMatch(ExtractedScoreItem::computable);
    }
    @Test void conflictingCandidatesForSamePhysicalUnitRemainDistinctAndCannotDoubleCount() {
        var span=new SourceSpan("doc","hash","PDF","s",1,"t","c",0,4,"规则原文","c");
        List<ScoreFact> facts=new java.util.ArrayList<>();List<OriginalScoreEvidencePolicy.Qualification> qs=new java.util.ArrayList<>();
        for(String id:List.of("a","b")) {
            facts.add(new ScoreFact(id,"t",List.of("c"),"规则原文","1","方案",id.equals("a")?BigDecimal.TEN:BigDecimal.ONE,null,null,null,"MODEL",.5,ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.SUM,new ScoreSourceMetadata("规则原文","",List.of(span),List.of())));
            qs.add(new OriginalScoreEvidencePolicy.Qualification(id,"s","IN_SCHEME",false,"SOURCE_UNVERIFIED",""));
        }
        var snapshot=ScoreExtractionSnapshot.from("task",facts,qs,List.of());
        assertThat(snapshot.items()).hasSize(2).noneMatch(ExtractedScoreItem::computable);
        assertThat(snapshot.items()).extracting(ExtractedScoreItem::itemId).doesNotHaveDuplicates();
    }
    @Test void pointsBeforeCategoryRatioStillRequireContributionConversion() {
        var f=new ScoreFact("a","t",List.of(),"技术原始满分100分，技术部分占比40%","1","技术",new BigDecimal("100"),null,null,null,"MODEL",.5,ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        var q=new OriginalScoreEvidencePolicy.Qualification("a","s","IN_SCHEME",false,"SOURCE_UNVERIFIED","");
        assertThat(ScoreExtractionSnapshot.from("task",List.of(f),List.of(q),List.of()).items()).noneMatch(ExtractedScoreItem::computable);
    }
    @Test void sourceVerifiedZeroPointDeductionCanBeManuallySelected() {
        var span = new SourceSpan("doc", "hash", "PDF", "s", 1, "t", "rule", 0, 12,
                "验收不通过按规定扣分。", "rule");
        var metadata = new ScoreSourceMetadata("", "", List.of(span), List.of(), "", "OBJECTIVE");
        var deduction = new ScoreFact("d", "t", List.of("rule"), "验收不通过按规定扣分。", "4", "质量扣分",
                BigDecimal.ZERO, null, null, "", "SOURCE", .9, ScoreFact.ValidationStatus.REVIEW_REQUIRED,
                ChildAggregation.SUM, metadata);
        var q = new OriginalScoreEvidencePolicy.Qualification("d", "s", "IN_SCHEME", true, null, "t:rule");
        var item = ScoreExtractionSnapshot.from("task", List.of(deduction), List.of(q), List.of()).items().getFirst();
        assertThat(item.computable()).isTrue();
        assertThat(item.effectiveMaximum()).isEqualByComparingTo("0");
    }
    @Test void unverifiedDeductionCannotBePromoted() {
        var span = new SourceSpan("doc", "hash", "PDF", "s", 1, "t", "rule", 0, 12,
                "验收不通过扣1分。", "rule");
        var metadata = new ScoreSourceMetadata("", "", List.of(span), List.of(), "", "OBJECTIVE");
        var deduction = new ScoreFact("d", "t", List.of("rule"), "验收不通过扣1分。", "4", "质量扣分",
                BigDecimal.ZERO, null, null, "", "SOURCE", .9, ScoreFact.ValidationStatus.REVIEW_REQUIRED,
                ChildAggregation.SUM, metadata);
        var q = new OriginalScoreEvidencePolicy.Qualification("d", "s", "SOURCE_UNVERIFIED", false, "SOURCE_UNVERIFIED", "t:rule");
        assertThat(ScoreExtractionSnapshot.from("task", List.of(deduction), List.of(q), List.of()).items().getFirst().computable()).isFalse();
        var verified = new OriginalScoreEvidencePolicy.Qualification("d", "s", "IN_SCHEME", true, null, "t:rule");
        assertThat(ScoreExtractionSnapshot.from("task", List.of(deduction), List.of(verified), List.of()).items().getFirst().computable()).isTrue();
    }

    @Test void partialDeductionRuleCannotBePromotedAsComplete() {
        var span = new SourceSpan("doc", "hash", "PDF", "s", 1, "t", "rule", 0, 25,
                "验收不通过扣分。每次另扣1分。", "rule");
        var metadata = new ScoreSourceMetadata("", "", List.of(span), List.of(), "", "OBJECTIVE");
        var deduction = new ScoreFact("d", "t", List.of("rule"), "验收不通过扣分。", "4", "质量扣分",
                BigDecimal.ZERO, null, null, "", "SOURCE", .9, ScoreFact.ValidationStatus.REVIEW_REQUIRED,
                ChildAggregation.SUM, metadata);
        var q = new OriginalScoreEvidencePolicy.Qualification("d", "s", "IN_SCHEME", true, null, "t:rule");
        assertThat(ScoreExtractionSnapshot.from("task", List.of(deduction), List.of(q), List.of()).items().getFirst().computable()).isFalse();
    }

    @Test void zeroPointPlaceholderRemainsUnselectable() {
        var item = ScoreExtractionSnapshot.from("task", List.of(fact("placeholder", BigDecimal.ZERO)),
                List.of(new OriginalScoreEvidencePolicy.Qualification("placeholder", "s", "IN_SCHEME", true, null, "")),
                List.of()).items().getFirst();
        assertThat(item.computable()).isFalse();
    }

    @Test void pureNumericOrBlankNameIsNotComputableEvenWithPositiveWeight() {
        var fact = new ScoreFact("num-1", "t", List.of("c"), "无冒号纯规则描述得5分", "1", "1",
                new BigDecimal("5"), null, null, "", "MODEL", .9, ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        var q = new OriginalScoreEvidencePolicy.Qualification("num-1", "s", "IN_SCHEME", true, null, "t:c");
        var item = ScoreExtractionSnapshot.from("task", List.of(fact), List.of(q), List.of()).items().getFirst();
        assertThat(item.name()).isEmpty();
        assertThat(item.computable()).isFalse();
        assertThat(item.diagnostics()).contains("INVALID_SCORE_ITEM_NAME");
    }

    static ScoreFact fact(String id, BigDecimal weight) {
        var m = new ScoreSourceMetadata("规则最高" + weight + "分", "原文", List.of(), List.of("SOURCE_UNVERIFIED"), "TECHNICAL", "OBJECTIVE", new ModelSourceClaim("评分标准",1,"t",List.of(),"s","IN_SCHEME"));
        return new ScoreFact(id,"t",List.of(),"原文","1",id,weight,null,null,null,"MODEL",.6,ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.SUM,m);
    }
}
