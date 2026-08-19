package com.xiyu.bid.scoreparse.domain;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageAssignmentPlanTest {

    private static DocumentImage image(int index) {
        return new DocumentImage(index, "image/png", "第三章 资质证明", null, 800, 600, "aGk=");
    }

    @Test
    void noneIsDegradedWithEmptyImages() {
        ImageAssignmentPlan plan = ImageAssignmentPlan.none();
        assertThat(plan.images()).isEmpty();
        assertThat(plan.truncated()).isFalse();
        assertThat(plan.degraded()).isTrue();
        assertThat(plan.hasImages()).isFalse();
    }

    @Test
    void hasImagesReflectsImageList() {
        assertThat(new ImageAssignmentPlan(List.of(image(0)), false, false).hasImages()).isTrue();
        assertThat(new ImageAssignmentPlan(List.of(), false, false).hasImages()).isFalse();
    }

    @Test
    void nullImagesNormalizedToEmpty() {
        ImageAssignmentPlan plan = new ImageAssignmentPlan(null, false, false);
        assertThat(plan.images()).isEmpty();
    }

    @Test
    void degradedPlanMustNotCarryImages() {
        assertThatThrownBy(() -> new ImageAssignmentPlan(List.of(image(0)), false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("degraded");
    }

    @Test
    void truncatedPlanWithImagesIsAllowed() {
        ImageAssignmentPlan plan = new ImageAssignmentPlan(List.of(image(0), image(1)), true, false);
        assertThat(plan.truncated()).isTrue();
        assertThat(plan.hasImages()).isTrue();
    }
}
