// Input: PerformanceRepository mock 返回真实业绩记录 + 数量上限
// Output: 断言 KnowledgeMatch 结构（recordId/content 来自 DB 字段、limit 截断）
// Pos: performance/application/ - compliance 业绩库证据端口适配器测试（spec 049 US3 / T036）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.performance.application;

import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceEvidenceQueryAdapterTest {

    @Test
    @DisplayName("真实业绩记录转 KnowledgeMatch：content 来自 DB 字段且按 limit 截断")
    void testFindPerformanceMatches_RealRecords_StructuredAndLimited() {
        PerformanceRepository repository = mock(PerformanceRepository.class);
        List<PerformanceRecord> records = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            records.add(new PerformanceRecord((long) i, "合同项目" + i, "西域数智", null, null, null,
                    null, null, null, LocalDate.of(2025, 1, i), null, null, null,
                    null, null, null, null, null, false, null, List.of(), null, null));
        }
        when(repository.findAll(any(PerformanceSearchCriteria.class), any(PerformanceAlertConfig.class)))
                .thenReturn(records);

        List<KnowledgeMatch> matches =
                new PerformanceEvidenceQueryAdapter(repository).findPerformanceMatches(10);

        assertThat(matches).hasSize(10);
        assertThat(matches.get(0).knowledgeSource()).isEqualTo(KnowledgeMatch.SOURCE_PERFORMANCE);
        assertThat(matches.get(0).recordId()).isEqualTo("1");
        assertThat(matches.get(0).content()).contains("合同项目1").contains("西域数智").contains("2025-01-01");
        assertThat(matches).allMatch(KnowledgeMatch::matched);
    }

    @Test
    @DisplayName("合同名为空的记录被跳过")
    void testFindPerformanceMatches_BlankContractName_Skipped() {
        PerformanceRepository repository = mock(PerformanceRepository.class);
        when(repository.findAll(any(PerformanceSearchCriteria.class), any(PerformanceAlertConfig.class)))
                .thenReturn(List.of(
                        new PerformanceRecord(1L, "  ", null, null, null, null,
                                null, null, null, null, null, null, null,
                                null, null, null, null, null, false, null, List.of(), null, null)));

        List<KnowledgeMatch> matches =
                new PerformanceEvidenceQueryAdapter(repository).findPerformanceMatches(10);

        assertThat(matches).isEmpty();
    }
}
