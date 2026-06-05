package com.xiyu.bid.task.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class DeliverableAssociationPolicyTest {

    @Test
    void validateAssociation_ShouldAccept_InProgressWithValidType() {
        var result = DeliverableAssociationPolicy.validateAssociation(
                "IN_PROGRESS", DeliverableAssociationPolicy.DeliverableType.DOCUMENT, 5);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateAssociation_ShouldReject_CompletedTask() {
        var result = DeliverableAssociationPolicy.validateAssociation(
                "COMPLETED", DeliverableAssociationPolicy.DeliverableType.DOCUMENT, 0);
        assertThat(result.valid()).isFalse();
        assertThat(result.rejectionReason()).contains("已完成");
    }

    @Test
    void validateAssociation_ShouldReject_CancelledTask() {
        var result = DeliverableAssociationPolicy.validateAssociation(
                "CANCELLED", DeliverableAssociationPolicy.DeliverableType.TECHNICAL, 0);
        assertThat(result.valid()).isFalse();
        assertThat(result.rejectionReason()).contains("已取消");
    }

    @Test
    void validateAssociation_ShouldReject_NullType() {
        var result = DeliverableAssociationPolicy.validateAssociation(
                "IN_PROGRESS", null, 3);
        assertThat(result.valid()).isFalse();
        assertThat(result.rejectionReason()).contains("类型不能为空");
    }

    @Test
    void validateAssociation_ShouldReject_ExceedsMaxPerTask() {
        var result = DeliverableAssociationPolicy.validateAssociation(
                "IN_PROGRESS", DeliverableAssociationPolicy.DeliverableType.OTHER, 20);
        assertThat(result.valid()).isFalse();
        assertThat(result.rejectionReason()).contains("上限");
    }

    @Test
    void validateAssociation_ShouldReject_NullStatus() {
        var result = DeliverableAssociationPolicy.validateAssociation(
                null, DeliverableAssociationPolicy.DeliverableType.DOCUMENT, 0);
        assertThat(result.valid()).isFalse();
    }

    @Test
    void computeCompletionCoverage_EmptyRequired_ShouldBe100Percent() {
        var coverage = DeliverableAssociationPolicy.computeCompletionCoverage(List.of(), List.of());
        assertThat(coverage.percentage()).isEqualTo(100.0);
        assertThat(coverage.required()).isEqualTo(0);
        assertThat(coverage.covered()).isEqualTo(0);
    }

    @Test
    void computeCompletionCoverage_FullMatch_ShouldBe100Percent() {
        var coverage = DeliverableAssociationPolicy.computeCompletionCoverage(
                List.of("DOCUMENT", "TECHNICAL"),
                List.of(DeliverableAssociationPolicy.DeliverableType.DOCUMENT,
                        DeliverableAssociationPolicy.DeliverableType.TECHNICAL));
        assertThat(coverage.percentage()).isEqualTo(100.0);
        assertThat(coverage.required()).isEqualTo(2);
        assertThat(coverage.covered()).isEqualTo(2);
    }

    @Test
    void computeCompletionCoverage_PartialMatch_ShouldBe50Percent() {
        var coverage = DeliverableAssociationPolicy.computeCompletionCoverage(
                List.of("DOCUMENT", "QUALIFICATION", "TECHNICAL"),
                List.of(DeliverableAssociationPolicy.DeliverableType.DOCUMENT));
        assertThat(coverage.percentage()).isBetween(33.0, 34.0);
        assertThat(coverage.covered()).isEqualTo(1);
        assertThat(coverage.typeCoverages()).hasSize(3);
    }

    @Test
    void computeCompletionCoverage_OtherTypeShouldNotMatchSpecificRequirement() {
        // OTHER type should not auto-match specific type requirements
        var coverage = DeliverableAssociationPolicy.computeCompletionCoverage(
                List.of("DOCUMENT"),
                List.of(DeliverableAssociationPolicy.DeliverableType.OTHER));
        assertThat(coverage.percentage()).isEqualTo(0.0);
        assertThat(coverage.covered()).isEqualTo(0);
        assertThat(coverage.covered()).isEqualTo(0);
    }

    @Test
    void computeCompletionCoverage_TypeCoverageLabels_ShouldBeChinese() {
        var coverage = DeliverableAssociationPolicy.computeCompletionCoverage(
                List.of("DOCUMENT", "QUOTATION"),
                List.of(DeliverableAssociationPolicy.DeliverableType.DOCUMENT));
        assertThat(coverage.typeCoverages().get(0).label()).isEqualTo("文档");
        assertThat(coverage.typeCoverages().get(1).label()).isEqualTo("报价单");
    }

    @Test
    void completionCoverage_ShouldDefensivelyCopyTypeCoverages() {
        ArrayList<DeliverableAssociationPolicy.TypeCoverage> typeCoverages = new ArrayList<>(List.of(
                new DeliverableAssociationPolicy.TypeCoverage("DOCUMENT", "文档", true, 1)
        ));

        DeliverableAssociationPolicy.CompletionCoverage coverage =
                new DeliverableAssociationPolicy.CompletionCoverage(1, 1, 100.0, typeCoverages);

        typeCoverages.add(new DeliverableAssociationPolicy.TypeCoverage("TECHNICAL", "技术方案", false, 0));

        assertThat(coverage.typeCoverages()).hasSize(1);
        assertThatThrownBy(() -> coverage.typeCoverages().add(
                new DeliverableAssociationPolicy.TypeCoverage("QUOTATION", "报价单", false, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
