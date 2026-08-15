package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchRequest;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WarehouseMatchService - 仓库匹配")
class WarehouseMatchServiceTest {

    @Mock
    private WarehouseRepository repository;

    private WarehouseMatchService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseMatchService(repository);
    }

    private WarehouseEntity warehouse(Long id, String name, String region, String area, String remarks) {
        return WarehouseEntity.builder()
                .id(id).name(name).region(region).area(new BigDecimal(area))
                .status(WarehouseStatus.IN_USE).remarks(remarks).build();
    }

    @Test
    @DisplayName("硬条件命中（无降级字段）→ FULL/100")
    void hardConditionOnly_full() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                warehouse(1L, "华东一号仓", "华东", "6000", "标准仓")));
        WarehouseMatchRequest request = new WarehouseMatchRequest(List.of("华东"), "华东",
                new BigDecimal("5000"), null);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("FULL");
        assertThat(result.matchRatio()).isEqualTo(100);
        assertThat(result.matched()).hasSize(1);
    }

    @Test
    @DisplayName("设施降级匹配：facilityKeywords 命中备注 → PARTIAL + matchDetail 注明降级")
    void facilityDegraded_partialWithNote() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                warehouse(1L, "华东冷链仓", "华东", "6000", "含冷链设施、温区监控")));
        WarehouseMatchRequest request = new WarehouseMatchRequest(List.of("华东"), "华东",
                new BigDecimal("5000"), List.of("冷链"));

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).hasSize(1);
        assertThat(result.tier()).isEqualTo("PARTIAL");
        assertThat(result.matchDetail()).contains("备注文本");
    }

    @Test
    @DisplayName("设施关键词未命中备注 → 仓库剔除 → NONE")
    void facilityNotHit_excluded() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                warehouse(1L, "华东普通仓", "华东", "6000", "标准干货仓")));
        WarehouseMatchRequest request = new WarehouseMatchRequest(List.of("华东"), "华东",
                new BigDecimal("5000"), List.of("冷链"));

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(result.matchRatio()).isEqualTo(0);
        assertThat(result.matched()).isEmpty();
    }

    @Test
    @DisplayName("remarks 为空 + 设施关键词 → 剔除")
    void nullRemarks_excluded() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                warehouse(1L, "华东仓", "华东", "6000", null)));
        WarehouseMatchRequest request = new WarehouseMatchRequest(null, "华东",
                null, List.of("冷链"));

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).isEmpty();
        assertThat(result.tier()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("空结果 → NONE/0 不抛错（FR-024）")
    void noMatch_empty() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        WarehouseMatchRequest request = new WarehouseMatchRequest(List.of("不存在"), null, null, null);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(((List<WarehouseMatchedItem>) result.matched())).isEmpty();
    }
}
