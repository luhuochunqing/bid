package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreItemCaseSourceMetaLoaderTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TenderRepository tenderRepository;

    @InjectMocks
    private ScoreItemCaseSourceMetaLoader loader;

    @Test
    @DisplayName("按来源项目批量装配招标主体与项目状态")
    void loadAssemblesBidEntityAndStatus() {
        Project sourceProject = new Project();
        sourceProject.setId(501L);
        sourceProject.setTenderId(88L);
        sourceProject.setStatus(Project.Status.WON);

        Tender tender = new Tender();
        tender.setId(88L);
        tender.setPurchaserName("某省电力公司");

        when(projectRepository.findAllById(anyCollection())).thenReturn(List.of(sourceProject));
        when(tenderRepository.findAllById(anyCollection())).thenReturn(List.of(tender));

        Map<Long, ScoreItemCaseSourceMetaLoader.SourceMeta> meta = loader.load(List.of(501L));

        assertThat(meta.get(501L).bidEntity()).isEqualTo("某省电力公司");
        assertThat(meta.get(501L).projectStatus()).isEqualTo("WON");
    }

    @Test
    @DisplayName("采购人名称为空时招标主体回退招标代理")
    void loadFallsBackToTenderAgencyWhenPurchaserBlank() {
        Project sourceProject = new Project();
        sourceProject.setId(501L);
        sourceProject.setTenderId(88L);
        sourceProject.setStatus(Project.Status.WON);

        Tender tender = new Tender();
        tender.setId(88L);
        tender.setPurchaserName("  ");
        tender.setTenderAgency("某招标代理公司");

        when(projectRepository.findAllById(anyCollection())).thenReturn(List.of(sourceProject));
        when(tenderRepository.findAllById(anyCollection())).thenReturn(List.of(tender));

        Map<Long, ScoreItemCaseSourceMetaLoader.SourceMeta> meta = loader.load(List.of(501L));

        assertThat(meta.get(501L).bidEntity()).isEqualTo("某招标代理公司");
    }

    @Test
    @DisplayName("空来源项目 ID 返回空 Map，不访问仓库")
    void loadEmptyIdsReturnsEmpty() {
        assertThat(loader.load(List.of())).isEmpty();
        assertThat(loader.load(null)).isEmpty();
    }
}
