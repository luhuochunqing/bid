// Input: CompetitorAnalysisRequest（项目模式/默认模式）
// Output: 项目模式响应断言（空竞品放行 + 动态竞品 + 是否中标读 notes 枚举）
// Pos: Test/M4 竞品分析装配服务单测
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.CompetitorAnalysisRequest;
import com.xiyu.bid.analytics.dto.CompetitorAnalysisResponse;
import com.xiyu.bid.analytics.dto.CompetitorTableRowDTO;
import com.xiyu.bid.analytics.model.CompetitorAnalysisRow;
import com.xiyu.bid.entity.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("M4 竞品分析装配服务 — 项目名称模式回归")
class CompetitorAnalysisAssemblerServiceTest {

    @Mock
    private CompetitorAnalysisQueryService queryService;

    @Mock
    private CompetitorAnalysisComputationService computationService;

    @InjectMocks
    private CompetitorAnalysisAssemblerService service;

    @Test
    @DisplayName("项目模式：competitorNames 为空也放行，动态返回项目全部竞品")
    void projectModeProceedsWithEmptyCompetitors() {
        // 前端项目模式仅传 projectName 不传竞品列表，此前被入口空检查拦截导致"暂无数据"
        CompetitorAnalysisRequest request = CompetitorAnalysisRequest.builder()
                .projectName("测试竞品公司")
                .competitorNames(null)
                .build();

        when(queryService.fetchProjectCompetitorRows(eq("测试竞品公司"), isNull()))
                .thenReturn(List.of(
                        new CompetitorAnalysisRow(1L, "测试竞品公司", "震坤行", "92", "某主体", "30",
                                "已中标", "WON", Project.Status.WON, LocalDateTime.of(2025, 6, 1, 10, 0)),
                        new CompetitorAnalysisRow(1L, "测试竞品公司", "京东", "88", "某主体", "45",
                                "未中标", "WON", Project.Status.WON, LocalDateTime.of(2025, 6, 1, 10, 0))
                ));
        when(computationService.parseDiscount("92")).thenReturn(92.0);
        when(computationService.parseDiscount("88")).thenReturn(88.0);

        CompetitorAnalysisResponse response = service.analyze(request);

        assertThat(response.getMode()).isEqualTo("project");
        assertThat(response.getCategories()).containsExactly("震坤行", "京东");
        assertThat(response.getDiscounts()).containsExactly(92.0, 88.0);
        assertThat(response.getTableRows()).hasSize(2);
    }

    @Test
    @DisplayName("项目模式：是否中标读竞品行 notes 枚举，项目级 WON 状态不传染")
    void projectModeWonFlagReadsCompetitorNotesNotProjectStatus() {
        // 回归：此前 isWon 由项目级状态推导，项目 WON 时整列误显「已中标」
        CompetitorAnalysisRequest request = CompetitorAnalysisRequest.builder()
                .projectName("测试竞品公司")
                .competitorNames(null)
                .build();

        when(queryService.fetchProjectCompetitorRows(eq("测试竞品公司"), isNull()))
                .thenReturn(List.of(
                        // 项目状态 WON（我司中标）不代表竞品中标
                        new CompetitorAnalysisRow(1L, "测试竞品公司", "震坤行", "92", "某主体", "30",
                                "未中标", "WON", Project.Status.WON, LocalDateTime.of(2025, 6, 1, 10, 0)),
                        new CompetitorAnalysisRow(1L, "测试竞品公司", "京东", "88", "某主体", "45",
                                "已中标", "WON", Project.Status.WON, LocalDateTime.of(2025, 6, 1, 10, 0)),
                        // 历史「其他说明」自由文本非枚举，按未中标处理
                        new CompetitorAnalysisRow(1L, "测试竞品公司", "得力", "90", "某主体", "60",
                                "价格偏高", "WON", Project.Status.WON, LocalDateTime.of(2025, 6, 1, 10, 0))
                ));
        when(computationService.parseDiscount("92")).thenReturn(92.0);
        when(computationService.parseDiscount("88")).thenReturn(88.0);
        when(computationService.parseDiscount("90")).thenReturn(90.0);
        when(computationService.parseCompetitorWon("未中标")).thenReturn(false);
        when(computationService.parseCompetitorWon("已中标")).thenReturn(true);
        when(computationService.parseCompetitorWon("价格偏高")).thenReturn(false);

        CompetitorAnalysisResponse response = service.analyze(request);

        assertThat(response.getTableRows())
                .extracting(CompetitorTableRowDTO::getCompetitor, CompetitorTableRowDTO::getIsWon)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("震坤行", false),
                        org.assertj.core.groups.Tuple.tuple("京东", true),
                        org.assertj.core.groups.Tuple.tuple("得力", false));
    }

    @Test
    @DisplayName("默认模式：competitorNames 为空仍返回空响应")
    void defaultModeStillRejectsEmptyCompetitors() {
        CompetitorAnalysisRequest request = CompetitorAnalysisRequest.builder().build();

        CompetitorAnalysisResponse response = service.analyze(request);

        assertThat(response.getMode()).isEqualTo("default");
        assertThat(response.getCategories()).isEmpty();
    }

    @Test
    @DisplayName("项目模式查询不再按时间过滤（往年后创建的项目可命中）")
    void projectModeQueryDropsTimeFilters() {
        CompetitorAnalysisRequest request = CompetitorAnalysisRequest.builder()
                .projectName("老项目")
                .startDate(java.time.LocalDate.of(2026, 1, 1))
                .endDate(java.time.LocalDate.of(2026, 12, 31))
                .competitorNames(List.of())
                .build();

        when(queryService.fetchProjectCompetitorRows(eq("老项目"), anyList())).thenReturn(List.of());

        CompetitorAnalysisResponse response = service.analyze(request);

        assertThat(response.getMode()).isEqualTo("project");
        assertThat(response.getCategories()).isEmpty();
    }
}
