package com.xiyu.bid.casework.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderQualificationQueryAdapterTest {

    @Mock
    private BidTenderDocumentSnapshotRepository snapshotRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TenderQualificationQueryAdapter adapter;

    @Test
    @DisplayName("根据 projectId 查询：快照包含 profileJson 和 extractedText 时成功提取")
    void testFindByProjectIdSuccess() {
        Long projectId = 100L;
        String profileJson = "{\"qualificationRequirements\":[\"具备独立法人资格\",\"近三年大型集团电商化供应业绩不低于2000万元\"]}";
        String rawText = "第二章 投标人须知\n第三章 评标办法...";

        BidTenderDocumentSnapshot snapshot = BidTenderDocumentSnapshot.builder()
                .id(1L)
                .projectId(projectId)
                .profileJson(profileJson)
                .extractedText(rawText)
                .build();

        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(eq(projectId)))
                .thenReturn(Optional.of(snapshot));

        var resultOpt = adapter.findByProjectId(projectId);

        assertThat(resultOpt).isPresent();
        var result = resultOpt.get();
        assertThat(result.rawTenderText()).isEqualTo(rawText);
        assertThat(result.qualificationRequirements())
                .containsExactly("具备独立法人资格", "近三年大型集团电商化供应业绩不低于2000万元");
    }

    @Test
    @DisplayName("快照不存在或 projectId 为空时返回 empty")
    void testFindByProjectIdEmpty() {
        assertThat(adapter.findByProjectId(null)).isEmpty();

        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(eq(999L)))
                .thenReturn(Optional.empty());
        assertThat(adapter.findByProjectId(999L)).isEmpty();
    }
}
