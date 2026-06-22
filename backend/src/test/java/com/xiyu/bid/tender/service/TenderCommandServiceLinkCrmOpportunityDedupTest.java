package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-297: TenderCommandService.linkCrmOpportunity 与 TenderCrmOccupancyChecker 的协作测试。
 * <p>
 * 职责边界：
 * - TenderCrmOccupancyChecker 自身的占位判定（happy / 自身幂等 / 冲突 / null / blank）由
 *   {@link TenderCrmOccupancyCheckerTest} 覆盖。
 * - 本测试只验证 service 端是否正确调用 checker、透传异常、不破坏 happy path。
 */
@ExtendWith(MockitoExtension.class)
class TenderCommandServiceLinkCrmOpportunityDedupTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderCommandAccessGuard commandAccessGuard;
    @Mock private TenderMapper tenderMapper;
    @Mock private TenderCrmOccupancyChecker crmOccupancyChecker;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TenderCommandService tenderCommandService;

    private Tender tenderA;
    private static final String CRM_OPP_X = "CRM-OPP-X";

    @BeforeEach
    void setUp() {
        tenderA = Tender.builder()
                .id(100L)
                .title("标讯 A")
                .status(Tender.Status.TRACKING)
                .build();
        tenderCommandService = new TenderCommandService(
                null,                  // TenderDeduplicationService
                tenderRepository,
                null,                  // ProjectRepository
                tenderMapper,
                null,                  // TenderProjectAccessGuard
                null,                  // TaskService
                commandAccessGuard,
                null,                  // TenderAutoAssignmentService
                eventPublisher,        // CO-305: ApplicationEventPublisher
                null,                  // UserRepository
                null,                  // NotificationApplicationService
                null,                  // TenderAssignmentNotifier
                null,                  // TenderAttachmentRepository
                crmOccupancyChecker    // CO-297: CRM 商机占用校验器（应用层 + UNIQUE 双层防御）
        );
    }

    @Test
    @DisplayName("CO-297 happy：checker 不抛错时，service 正常完成关联并切换状态")
    void linkCrmOpportunity_WhenCheckerPasses_ShouldSucceed() {
        when(tenderRepository.findById(100L)).thenReturn(Optional.of(tenderA));
        when(tenderRepository.save(tenderA)).thenReturn(tenderA);
        when(tenderMapper.toDTO(tenderA)).thenReturn(TenderDTO.builder().id(100L).build());

        TenderDTO result = tenderCommandService.linkCrmOpportunity(100L, CRM_OPP_X, "商机 X", 1L);

        assertThat(result).isNotNull();
        assertThat(tenderA.getCrmOpportunityId()).isEqualTo(CRM_OPP_X);
        assertThat(tenderA.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        // 验证 service 确实调用了 checker
        verify(crmOccupancyChecker).assertCrmOpportunityNotOccupied(100L, CRM_OPP_X);
    }

    @Test
    @DisplayName("CO-297 冲突：checker 抛 409 → service 透传，crmOpportunityId 不被覆盖")
    void linkCrmOpportunity_WhenCheckerThrows409_ShouldPropagateAndNotMutateTender() {
        when(tenderRepository.findById(100L)).thenReturn(Optional.of(tenderA));
        doThrow(new BusinessException(409, "该 CRM 商机已被标讯 ID=200 关联，请先解除原关联"))
                .when(crmOccupancyChecker).assertCrmOpportunityNotOccupied(anyLong(), anyString());

        assertThatThrownBy(() -> tenderCommandService.linkCrmOpportunity(100L, CRM_OPP_X, "商机 X", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被标讯");

        // 关键：crmOpportunityId 未被覆盖
        assertThat(tenderA.getCrmOpportunityId()).isNull();
    }
}
