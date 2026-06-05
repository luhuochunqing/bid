package com.xiyu.bid.changetracking.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeDiffPolicyTest {

    @Test
    void diff_ShouldReturnEmpty_WhenBeforeIsNull() {
        List<FieldChange> result = ChangeDiffPolicy.diff(null, Map.of("a", 1));

        assertThat(result).isEmpty();
    }

    @Test
    void diff_ShouldReturnEmpty_WhenAfterIsNull() {
        List<FieldChange> result = ChangeDiffPolicy.diff(Map.of("a", 1), null);

        assertThat(result).isEmpty();
    }

    @Test
    void diff_ShouldReturnEmpty_WhenMapsAreIdentical() {
        Map<String, Object> m = Map.of("a", 1, "b", "two");

        List<FieldChange> result = ChangeDiffPolicy.diff(m, m);

        assertThat(result).isEmpty();
    }

    @Test
    void diff_ShouldDetectChangedValue() {
        Map<String, Object> before = Map.of("a", 1, "b", 2);
        Map<String, Object> after = Map.of("a", 1, "b", 3);

        List<FieldChange> result = ChangeDiffPolicy.diff(before, after);

        assertThat(result).containsExactly(new FieldChange("b", 2, 3));
    }

    @Test
    void diff_ShouldDetectNewField_WithNullBefore() {
        Map<String, Object> before = Map.of("a", 1);
        Map<String, Object> after = Map.of("a", 1, "b", 2);

        List<FieldChange> result = ChangeDiffPolicy.diff(before, after);

        assertThat(result).containsExactly(new FieldChange("b", null, 2));
    }

    @Test
    void diff_ShouldDetectRemovedField_WithNullAfter() {
        Map<String, Object> before = Map.of("a", 1, "b", 2);
        Map<String, Object> after = Map.of("a", 1);

        List<FieldChange> result = ChangeDiffPolicy.diff(before, after);

        assertThat(result).containsExactly(new FieldChange("b", 2, null));
    }

    @Test
    void diff_ShouldCapAtMaxChanges_WhenExceeded() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        for (int i = 0; i < 60; i++) {
            before.put("k" + i, i);
            after.put("k" + i, i + 1);
        }

        List<FieldChange> result = ChangeDiffPolicy.diff(before, after);

        assertThat(result).hasSize(50);
    }

    @Test
    void diff_ShouldReturnEmpty_WhenBothMapsEmpty() {
        List<FieldChange> result = ChangeDiffPolicy.diff(Map.of(), Map.of());

        assertThat(result).isEmpty();
    }
}
