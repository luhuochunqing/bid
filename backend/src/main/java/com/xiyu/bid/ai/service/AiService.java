// Input: AiProvider, Repository
// Output: AI Analysis Service
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.ai.service;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI Service
 * Provides asynchronous AI analysis capabilities for tenders and projects
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final AiProvider aiProvider;
    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;

    /**
     * Analyze tender asynchronously
     *
     * @param tenderId The tender ID to analyze
     * @param context Additional context for analysis
     * @return CompletableFuture that completes when analysis is done
     */
    @Async("aiTaskExecutor")
    @Transactional
    public CompletableFuture<Void> analyzeTender(Long tenderId, Map<String, Object> context) {
        try {
            analyzeTenderSync(tenderId, context);
            return CompletableFuture.completedFuture(null);
        } catch (ResourceNotFoundException e) {
            log.error("Tender not found for AI analysis: {}", tenderId);
            throw e;
        } catch (Exception e) {
            log.error("Error during AI analysis for tender id: {}", tenderId, e);
            throw new RuntimeException("Failed to analyze tender", e);
        }
    }

    /**
     * Analyze project asynchronously
     *
     * @param projectId The project ID to analyze
     * @param context Additional context for analysis
     * @return CompletableFuture that completes when analysis is done
     */
    @Async("aiTaskExecutor")
    @Transactional
    public CompletableFuture<Void> analyzeProject(Long projectId, Map<String, Object> context) {
        try {
            analyzeProjectSync(projectId, context);
            return CompletableFuture.completedFuture(null);
        } catch (ResourceNotFoundException e) {
            log.error("Project not found for AI analysis: {}", projectId);
            throw e;
        } catch (Exception e) {
            log.error("Error during AI analysis for project id: {}", projectId, e);
            throw new RuntimeException("Failed to analyze project", e);
        }
    }

    @Transactional
    public AiAnalysisResponse analyzeTenderSync(Long tenderId, Map<String, Object> context) {
        log.debug("Starting synchronous AI analysis for tender id: {}", tenderId);

        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));

        String content = prepareTenderContent(tender);
        Map<String, Object> normalizedContext = context != null ? context : Map.of();

        AiAnalysisResponse response = aiProvider.analyzeTender(content, normalizedContext);
        updateTenderWithAnalysis(tender, response);

        log.info("Completed synchronous AI analysis for tender id: {}, score: {}", tenderId, tender.getAiScore());
        return response;
    }

    @Transactional
    public AiAnalysisResponse analyzeProjectSync(Long projectId, Map<String, Object> context) {
        log.debug("Starting synchronous AI analysis for project id: {}", projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));

        Map<String, Object> normalizedContext = context != null ? context : Map.of();

        AiAnalysisResponse response = aiProvider.analyzeProject(projectId, normalizedContext);
        updateProjectWithAnalysis(project, response);

        log.info("Completed synchronous AI analysis for project id: {}", projectId);
        return response;
    }

    /**
     * Prepare tender content for AI analysis
     */
    private String prepareTenderContent(Tender tender) {
        StringBuilder content = new StringBuilder();

        if (tender.getTitle() != null) {
            content.append("Title: ").append(tender.getTitle()).append("\n");
        }

        if (tender.getSource() != null) {
            content.append("Source: ").append(tender.getSource()).append("\n");
        }

        if (tender.getBudget() != null) {
            content.append("Budget: ").append(tender.getBudget()).append("\n");
        }

        if (tender.getDeadline() != null) {
            content.append("Deadline: ").append(tender.getDeadline()).append("\n");
        }

        if (tender.getStatus() != null) {
            content.append("Status: ").append(tender.getStatus()).append("\n");
        }

        return content.toString();
    }

    /**
     * Update tender entity with AI analysis results
     */
    private void updateTenderWithAnalysis(Tender tender, AiAnalysisResponse response) {
        if (response == null) {
            log.warn("Received null AI analysis response for tender id: {}", tender.getId());
            return;
        }

        // Validate and set score
        Integer score = response.getScore();
        if (score != null && score >= 0 && score <= 100) {
            tender.setAiScore(score);
        } else {
            log.warn("Invalid AI score received for tender id: {}, score: {}", tender.getId(), score);
        }

        // Set risk level
        if (response.getRiskLevel() != null) {
            tender.setRiskLevel(response.getRiskLevel());
        }

        // Save updated tender
        tenderRepository.save(tender);

        log.debug("Updated tender id: {} with AI analysis results", tender.getId());
    }

    /**
     * Update project entity with AI analysis results
     * Note: Project entity doesn't currently have AI fields, so we just log the analysis
     * In the future, you may want to add aiScore and riskLevel to the Project entity
     */
    private void updateProjectWithAnalysis(Project project, AiAnalysisResponse response) {
        if (response == null) {
            log.warn("Received null AI analysis response for project id: {}", project.getId());
            return;
        }

        // Log the analysis results
        log.info("Project AI Analysis - ID: {}, Score: {}, Risk Level: {}",
                project.getId(), response.getScore(), response.getRiskLevel());

        // Note: Project entity doesn't have AI fields yet
        // You can extend the Project entity to include:
        // - @Column(name = "ai_score") private Integer aiScore;
        // - @Column(name = "risk_level") private RiskLevel riskLevel;
        // And then update them here like we do for Tender

        // For now, we just save the project to update the timestamp
        projectRepository.save(project);
    }
}
