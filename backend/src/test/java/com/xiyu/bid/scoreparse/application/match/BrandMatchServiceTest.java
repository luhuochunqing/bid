package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.ManufacturerAuthorizationEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.ManufacturerAuthorizationJpaRepository;
import com.xiyu.bid.scoreparse.dto.BrandMatchRequest;
import com.xiyu.bid.scoreparse.dto.BrandMatchedItem;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BrandMatchService - 品牌授权匹配")
class BrandMatchServiceTest {

    @Mock
    private ManufacturerAuthorizationJpaRepository repository;

    private BrandMatchService service;

    @BeforeEach
    void setUp() {
        service = new BrandMatchService(repository);
    }

    private ManufacturerAuthorizationEntity auth(Long id, String brandName, ProductLine line,
                                                LocalDate endDate) {
        return ManufacturerAuthorizationEntity.builder()
                .id(id).brandName(brandName).productLine(line)
                .manufacturerName("制造商" + id).importDomestic("国产")
                .authStartDate(LocalDate.now().minusYears(1)).authEndDate(endDate)
                .status(AuthStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("基本命中 → FULL/100")
    void basicMatch_full() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                auth(1L, "德力西", ProductLine.TOOLS, LocalDate.now().plusYears(1))));
        BrandMatchRequest request = new BrandMatchRequest(List.of("德力西"), null, "国产",
                LocalDate.now().plusMonths(6));

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("FULL");
        assertThat(result.matchRatio()).isEqualTo(100);
        assertThat(((BrandMatchedItem) result.matched().get(0)).expireSoon()).isFalse();
    }

    @Test
    @DisplayName("expireSoon 标记：授权止期 90 天内 → expireSoon=true 且 PARTIAL")
    void expireSoon_flagged() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                auth(1L, "德力西", ProductLine.TOOLS, LocalDate.now().plusDays(30))));
        BrandMatchRequest request = new BrandMatchRequest(List.of("德力西"), null, null, null);

        KnowledgeMatchResult result = service.match(request);

        assertThat(((BrandMatchedItem) result.matched().get(0)).expireSoon()).isTrue();
        assertThat(result.tier()).isEqualTo("PARTIAL");
        assertThat(result.matchDetail()).contains("即将到期");
    }

    @Test
    @DisplayName("授权范围降级：productLine/importDomestic 参与匹配 → matchDetail 注明近似表达")
    void scopeDegraded_noted() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                auth(1L, "德力西", ProductLine.TOOLS, LocalDate.now().plusYears(1))));
        BrandMatchRequest request = new BrandMatchRequest(List.of("德力西"), "TOOLS", "国产", null);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).hasSize(1);
        assertThat(result.matchDetail()).contains("近似表达");
    }

    @Test
    @DisplayName("空结果 → NONE/0 不抛错（FR-024）")
    void noMatch_empty() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        BrandMatchRequest request = new BrandMatchRequest(List.of("不存在品牌"), null, null, null);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(result.matchRatio()).isEqualTo(0);
        assertThat(result.matched()).isEmpty();
    }
}
