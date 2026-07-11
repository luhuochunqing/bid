package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-297: TenderCrmOccupancyChecker 的占位校验单元测试。
 * <p>
 * 负责验证 Checker 自身逻辑：
 * - 商机未被占 → 静默通过
 * - 商机被自己占 → 静默通过（不冲突）
 * - 商机被他标占 → 抛 409 BusinessException
 * - crmOpportunityId 为 null/blank → 静默跳过查询
 */
@ExtendWith(MockitoExtension.class)
class TenderCrmOccupancyCheckerTest {

    @Mock private TenderRepository tenderRepository;

    @InjectMocks private TenderCrmOccupancyChecker checker;

    private Tender tenderA;  // id=100
    private Tender tenderB;  // id=200
    private static final String CRM_OPP_X = "CRM-OPP-X";

    @BeforeEach
    void setUp() {
        tenderA = Tender.builder().id(100L).title("A").build();
        tenderB = Tender.builder().id(200L).title("B").build();
    }

    @Test
    @DisplayName("CO-297 正常：商机未被占 → 静默通过")
    void assertCrmOpportunityNotOccupied_WhenNotOccupied_ShouldPass() {
        when(tenderRepository.findByCrmOpportunityId(CRM_OPP_X)).thenReturn(Optional.empty());

        assertThatCode(() -> checker.assertCrmOpportunityNotOccupied(100L, CRM_OPP_X))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CO-297 自身占用：当前标讯自身已占 → 静默通过")
    void assertCrmOpportunityNotOccupied_WhenOccupiedBySelf_ShouldPass() {
        when(tenderRepository.findByCrmOpportunityId(CRM_OPP_X)).thenReturn(Optional.of(tenderA));

        assertThatCode(() -> checker.assertCrmOpportunityNotOccupied(100L, CRM_OPP_X))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CO-297 他标占用：商机已被标讯 B 占用 → 抛 409 业务异常")
    void assertCrmOpportunityNotOccupied_WhenOccupiedByAnotherTender_ShouldThrow409() {
        when(tenderRepository.findByCrmOpportunityId(CRM_OPP_X)).thenReturn(Optional.of(tenderB));

        assertThatCode(() -> checker.assertCrmOpportunityNotOccupied(100L, CRM_OPP_X))
                .isInstanceOf(com.xiyu.bid.exception.BusinessException.class)
                .hasMessageContaining("已被标讯")
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("CO-297 null 商机 ID → 跳过查询，静默通过")
    void assertCrmOpportunityNotOccupied_WhenCrmOpportunityIdIsNull_ShouldSkipQuery() {
        assertThatCode(() -> checker.assertCrmOpportunityNotOccupied(100L, null))
                .doesNotThrowAnyException();
        verify(tenderRepository, never()).findByCrmOpportunityId(anyString());
    }

    @Test
    @DisplayName("CO-297 blank 商机 ID → 跳过查询，静默通过")
    void assertCrmOpportunityNotOccupied_WhenCrmOpportunityIdIsBlank_ShouldSkipQuery() {
        assertThatCode(() -> checker.assertCrmOpportunityNotOccupied(100L, "   "))
                .doesNotThrowAnyException();
        verify(tenderRepository, never()).findByCrmOpportunityId(anyString());
    }

    // ===== CO-277 防复发：纯数字 crmOpportunityId 警告 =====

    @Test
    @DisplayName("CO-277 防复发：纯数字 crmOpportunityId 仍执行查询（兼容历史数据），便于监控发现回归")
    void assertCrmOpportunityNotOccupied_WhenNumericId_ShouldStillQueryForMonitoring() {
        // 纯数字是 CRM 推送误传的主键 id（PR !2011 回归根因），
        // 校验本身不拦截（因为可能有历史数据），但应记录警告日志便于监控发现
        when(tenderRepository.findByCrmOpportunityId("21364")).thenReturn(Optional.empty());

        assertThatCode(() -> checker.assertCrmOpportunityNotOccupied(100L, "21364"))
                .doesNotThrowAnyException();
        verify(tenderRepository).findByCrmOpportunityId("21364");
    }
}
