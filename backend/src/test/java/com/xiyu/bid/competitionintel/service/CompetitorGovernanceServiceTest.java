package com.xiyu.bid.competitionintel.service;

import com.xiyu.bid.competitionintel.dto.CompetitorDTO;
import com.xiyu.bid.competitionintel.entity.Competitor;
import com.xiyu.bid.competitionintel.entity.CompetitorStatus;
import com.xiyu.bid.competitionintel.repository.CompetitionAnalysisRepository;
import com.xiyu.bid.competitionintel.repository.CompetitorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitorGovernanceServiceTest {

    @Mock
    private CompetitorRepository competitorRepository;

    @Mock
    private CompetitionAnalysisRepository analysisRepository;

    private CompetitorGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new CompetitorGovernanceService(competitorRepository, analysisRepository);
    }

    private Competitor competitor(Long id, String name, CompetitorStatus status) {
        return Competitor.builder().id(id).name(name).status(status).build();
    }

    @Test
    void upsertCompetitor_WithExistingName_ReturnsExisting() {
        Competitor existing = competitor(1L, "震坤行", CompetitorStatus.ACTIVE);
        when(competitorRepository.findByName("震坤行")).thenReturn(Optional.of(existing));

        CompetitorDTO result = service.upsertCompetitor(" 震坤行 ");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("震坤行");
        // 幂等：始终走原生 upsert（ON DUPLICATE KEY UPDATE no-op），撞唯一约束不报错
        verify(competitorRepository).insertCompetitorIfAbsent("震坤行");
    }

    @Test
    void upsertCompetitor_WithNewName_CreatesAndReturns() {
        Competitor saved = competitor(2L, "新竞品", CompetitorStatus.ACTIVE);
        when(competitorRepository.findByName("新竞品")).thenReturn(Optional.of(saved));

        CompetitorDTO result = service.upsertCompetitor(" 新竞品 ");

        assertThat(result.getId()).isEqualTo(2L);
        verify(competitorRepository).insertCompetitorIfAbsent("新竞品");
    }

    @Test
    void upsertCompetitor_WithBlankName_Throws() {
        assertThatThrownBy(() -> service.upsertCompetitor("   "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(competitorRepository, never()).insertCompetitorIfAbsent(any());
    }

    @Test
    void renameCompetitor_UpdatesName() {
        Competitor target = competitor(1L, "旧名", CompetitorStatus.ACTIVE);
        when(competitorRepository.findById(1L)).thenReturn(Optional.of(target));
        when(competitorRepository.save(any(Competitor.class))).thenAnswer(inv -> inv.getArgument(0));

        CompetitorDTO result = service.renameCompetitor(1L, " 新名 ");

        assertThat(result.getName()).isEqualTo("新名");
        assertThat(target.getName()).isEqualTo("新名");
    }

    @Test
    void mergeCompetitor_MigratesAndDisablesSource() {
        Competitor source = competitor(1L, "上海震坤行", CompetitorStatus.ACTIVE);
        Competitor target = competitor(2L, "震坤行", CompetitorStatus.ACTIVE);
        when(competitorRepository.findById(2L)).thenReturn(Optional.of(target));
        when(competitorRepository.findById(1L)).thenReturn(Optional.of(source));
        when(analysisRepository.updateCompetitorIdForMerge(1L, 2L)).thenReturn(3);
        when(competitorRepository.save(any(Competitor.class))).thenAnswer(inv -> inv.getArgument(0));

        CompetitorDTO result = service.mergeCompetitor(1L, 2L);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(source.getStatus()).isEqualTo(CompetitorStatus.DISABLED);
        verify(analysisRepository).updateCompetitorIdForMerge(1L, 2L);
    }

    @Test
    void mergeCompetitor_WithSameId_Throws() {
        assertThatThrownBy(() -> service.mergeCompetitor(5L, 5L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(analysisRepository, never()).updateCompetitorIdForMerge(any(), any());
    }

    @Test
    void mergeCompetitor_WithDisabledTarget_Throws() {
        Competitor target = competitor(2L, "震坤行", CompetitorStatus.DISABLED);
        when(competitorRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.mergeCompetitor(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("停用");
    }

    @Test
    void disableCompetitor_SetsDisabled() {
        Competitor target = competitor(1L, "震坤行", CompetitorStatus.ACTIVE);
        when(competitorRepository.findById(1L)).thenReturn(Optional.of(target));
        when(competitorRepository.save(any(Competitor.class))).thenAnswer(inv -> inv.getArgument(0));

        CompetitorDTO result = service.disableCompetitor(1L);

        assertThat(result.getStatus()).isEqualTo(CompetitorStatus.DISABLED);
        assertThat(target.getStatus()).isEqualTo(CompetitorStatus.DISABLED);
    }
}
