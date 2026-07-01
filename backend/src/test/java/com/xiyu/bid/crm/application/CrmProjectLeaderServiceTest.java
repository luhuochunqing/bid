package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmProjectLeaderService} 单元测试。
 * <p>覆盖：正常查询、空 code、CRM 返回空列表、商机无负责人等场景。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrmProjectLeaderServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private CrmChanceService crmChanceService;

    @Mock
    private CrmChanceDetailService crmChanceDetailService;

    private CrmProjectLeaderService service;

    @BeforeEach
    void setUp() {
        service = new CrmProjectLeaderService(crmChanceService, crmChanceDetailService);
    }

    private CustomerChanceVO buildVO(String code, String name, String leaderName, String leaderNo) {
        try {
            String json = """
                    {"id":1,"code":"%s","name":"%s","projectLeaderName":"%s","projectLeaderNo":"%s"}
                    """.formatted(
                    code == null ? "" : code,
                    name == null ? "" : name,
                    leaderName == null ? "" : leaderName,
                    leaderNo == null ? "" : leaderNo);
            return MAPPER.readValue(json, CustomerChanceVO.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void findProjectLeaderByChanceCode_blankCode_returnsNullAndSkipsQuery() {
        assertThat(service.findProjectLeaderByChanceCode("")).isNull();
        assertThat(service.findProjectLeaderByChanceCode(null)).isNull();
        assertThat(service.findProjectLeaderByChanceCode("   ")).isNull();
        verify(crmChanceService, never()).pageList(any(), any());
    }

    @Test
    void findProjectLeaderByChanceCode_emptyResult_returnsNull() {
        when(crmChanceService.pageList(any(CustomerChancePageRequest.class), any()))
                .thenReturn(new CrmChancePageResult(List.of(), 0, 1, 10));

        assertThat(service.findProjectLeaderByChanceCode("CC001")).isNull();
    }

    @Test
    void findProjectLeaderByChanceCode_chanceWithoutLeader_returnsNull() {
        when(crmChanceService.pageList(any(CustomerChancePageRequest.class), any()))
                .thenReturn(new CrmChancePageResult(
                        List.of(buildVO("CC001", "商机A", "", "")), 1, 1, 10));

        assertThat(service.findProjectLeaderByChanceCode("CC001")).isNull();
    }

    @Test
    void findProjectLeaderByChanceCode_chanceWithLeader_returnsResult() {
        when(crmChanceService.pageList(any(CustomerChancePageRequest.class), any()))
                .thenReturn(new CrmChancePageResult(
                        List.of(buildVO("CC001", "商机A", "张三", "EMP001")), 1, 1, 10));

        CrmProjectLeaderService.ProjectLeaderResult result = service.findProjectLeaderByChanceCode("CC001");

        assertThat(result).isNotNull();
        assertThat(result.projectLeaderName()).isEqualTo("张三");
        assertThat(result.projectLeaderNo()).isEqualTo("EMP001");
        assertThat(result.opportunityName()).isEqualTo("商机A");
        assertThat(result.opportunityCode()).isEqualTo("CC001");
    }

    // ===== CO-275：findProjectLeaderByChanceId（按主键 id 反查） =====

    @Test
    void findProjectLeaderByChanceId_nullId_returnsNullAndSkipsQuery() {
        assertThat(service.findProjectLeaderByChanceId(null)).isNull();
        verify(crmChanceDetailService, never()).getDetailById(any());
    }

    @Test
    void findProjectLeaderByChanceId_detailReturnsNull_returnsNull() {
        when(crmChanceDetailService.getDetailById(999L)).thenReturn(null);

        assertThat(service.findProjectLeaderByChanceId(999L)).isNull();
    }

    @Test
    void findProjectLeaderByChanceId_chanceWithoutLeader_returnsResultWithNullLeader() {
        // 即使无负责人，也返回结果，因为调用方需要 opportunityCode 来关联商机
        when(crmChanceDetailService.getDetailById(243L))
                .thenReturn(buildVO("CC20260619283", "商机A", "", ""));

        CrmProjectLeaderService.ProjectLeaderResult result = service.findProjectLeaderByChanceId(243L);

        assertThat(result).isNotNull();
        assertThat(result.opportunityCode()).isEqualTo("CC20260619283");
        assertThat(result.opportunityName()).isEqualTo("商机A");
        assertThat(result.projectLeaderName()).isNull();
        assertThat(result.projectLeaderNo()).isNull();
    }

    @Test
    void findProjectLeaderByChanceId_chanceWithLeader_returnsResult() {
        when(crmChanceDetailService.getDetailById(243L))
                .thenReturn(buildVO("CC20260619283", "商机A", "张三", "EMP001"));

        CrmProjectLeaderService.ProjectLeaderResult result = service.findProjectLeaderByChanceId(243L);

        assertThat(result).isNotNull();
        assertThat(result.projectLeaderName()).isEqualTo("张三");
        assertThat(result.projectLeaderNo()).isEqualTo("EMP001");
        assertThat(result.opportunityCode()).isEqualTo("CC20260619283");
    }
}
