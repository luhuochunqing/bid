package com.xiyu.bid.ai.service;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.ai.client.MockAiProvider;
import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.config.AsyncConfig;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {AiService.class, AsyncConfig.class})
@ActiveProfiles("test")
class AiServiceAsyncTest {

    @Autowired
    private AiService aiService;

    @MockBean
    private AiProvider aiProvider;

    @MockBean
    private TenderRepository tenderRepository;

    @MockBean
    private ProjectRepository projectRepository;

    @Test
    void analyzeTenderShouldUseSpringManagedAiExecutorAndSaveResults() throws Exception {
        Tender tender = Tender.builder()
                .id(42L)
                .title("智慧城市 IOC 项目")
                .source("政府采购网")
                .budget(new BigDecimal("680.00"))
                .deadline(LocalDateTime.now().plusDays(10))
                .status(Tender.Status.TRACKING)
                .build();

        AtomicReference<String> providerThread = new AtomicReference<>();

        when(tenderRepository.findById(42L)).thenReturn(Optional.of(tender));
        when(aiProvider.analyzeTender(anyString(), anyMap())).thenAnswer(invocation -> {
            providerThread.set(Thread.currentThread().getName());
            return AiAnalysisResponse.builder()
                    .score(88)
                    .riskLevel(Tender.RiskLevel.LOW)
                    .strengths(List.of("team"))
                    .weaknesses(List.of("timeline"))
                    .recommendations(List.of("proceed"))
                    .dimensionScores(List.of())
                    .build();
        });

        CompletableFuture<Void> future = aiService.analyzeTender(42L, Map.of("budget", tender.getBudget()));
        future.get(5, TimeUnit.SECONDS);

        assertThat(providerThread.get()).startsWith("ai-async-");
        verify(tenderRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getId().equals(42L)
                        && Integer.valueOf(88).equals(saved.getAiScore())
                        && Tender.RiskLevel.LOW.equals(saved.getRiskLevel())
        ));
    }

    @Test
    void analyzeProjectShouldUseSpringManagedAiExecutorAndSaveResults() throws Exception {
        Project project = Project.builder()
                .id(7L)
                .name("智慧城市 IOC 项目")
                .tenderId(42L)
                .managerId(1L)
                .status(Project.Status.BIDDING)
                .build();

        AtomicReference<String> providerThread = new AtomicReference<>();

        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(aiProvider.analyzeProject(eq(7L), anyMap())).thenAnswer(invocation -> {
            providerThread.set(Thread.currentThread().getName());
            return AiAnalysisResponse.builder()
                    .score(76)
                    .riskLevel(Tender.RiskLevel.MEDIUM)
                    .strengths(List.of("capacity"))
                    .weaknesses(List.of("schedule"))
                    .recommendations(List.of("refine plan"))
                    .dimensionScores(List.of())
                    .build();
        });

        CompletableFuture<Void> future = aiService.analyzeProject(7L, Map.of("teamSize", 4));
        future.get(5, TimeUnit.SECONDS);

        assertThat(providerThread.get()).startsWith("ai-async-");
        verify(projectRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getId().equals(7L)
        ));
    }

    @Test
    void analyzeTenderShouldCompleteExceptionallyWhenProviderFails() {
        Tender tender = Tender.builder()
                .id(99L)
                .title("异常标讯")
                .source("测试来源")
                .budget(new BigDecimal("100.00"))
                .deadline(LocalDateTime.now().plusDays(5))
                .status(Tender.Status.TRACKING)
                .build();

        when(tenderRepository.findById(99L)).thenReturn(Optional.of(tender));
        when(aiProvider.analyzeTender(anyString(), anyMap()))
                .thenThrow(new RuntimeException("provider unavailable"));

        CompletableFuture<Void> future = aiService.analyzeTender(99L, Map.of());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("provider unavailable");

        verify(tenderRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
