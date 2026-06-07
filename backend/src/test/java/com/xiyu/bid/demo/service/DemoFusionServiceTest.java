package com.xiyu.bid.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoFusionServiceTest {

    private final DemoFusionService service = new DemoFusionService();

    @Test
    void mergeByKey_shouldPreferRealAndAppendMissingDemo() {
        List<TestItem> real = List.of(new TestItem(1L, "real-1"), new TestItem(2L, "real-2"));
        List<TestItem> demo = List.of(new TestItem(2L, "demo-2"), new TestItem(3L, "demo-3"));

        List<TestItem> merged = service.mergeByKey(real, demo, TestItem::id);

        assertThat(merged).extracting(TestItem::id).containsExactly(1L, 2L, 3L);
        assertThat(merged).extracting(TestItem::value).containsExactly("real-1", "real-2", "demo-3");
    }

    @Test
    void mergePage_shouldMergeAndRespectPageWindow() {
        Page<TestItem> realPage = new PageImpl<>(
                List.of(new TestItem(1L, "real-1")),
                PageRequest.of(0, 2),
                1
        );
        List<TestItem> demo = List.of(new TestItem(2L, "demo-2"), new TestItem(3L, "demo-3"));

        Page<TestItem> merged = service.mergePage(realPage, demo, TestItem::id);

        assertThat(merged.getTotalElements()).isEqualTo(3);
        assertThat(merged.getContent()).extracting(TestItem::id).containsExactly(1L, 2L);
    }

    private record TestItem(Long id, String value) {}
}
