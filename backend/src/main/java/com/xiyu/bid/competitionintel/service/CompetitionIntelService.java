// Input: competitionintel repositories, DTOs, and support services
// Output: Competition Intel business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.competitionintel.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.competitionintel.dto.AnalysisCreateRequest;
import com.xiyu.bid.competitionintel.dto.CompetitorCreateRequest;
import com.xiyu.bid.competitionintel.dto.CompetitionAnalysisDTO;
import com.xiyu.bid.competitionintel.dto.CompetitorDTO;
import com.xiyu.bid.competitionintel.entity.CompetitionAnalysis;
import com.xiyu.bid.competitionintel.entity.Competitor;
import com.xiyu.bid.competitionintel.repository.CompetitionAnalysisRepository;
import com.xiyu.bid.competitionintel.repository.CompetitorRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 竞争情报服务
 * 处理竞争对手管理和竞争分析相关的业务逻辑
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompetitionIntelService {

    private final CompetitorRepository competitorRepository;
    private final CompetitionAnalysisRepository analysisRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    /**
     * 创建竞争对手
     */
    @Auditable(action = "CREATE", entityType = "Competitor", description = "Create new competitor")
    @Transactional
    public CompetitorDTO createCompetitor(CompetitorCreateRequest request) {
        log.info("Creating competitor: {}", request.getName());

        // Validate input
        validateCompetitorRequest(request);

        Competitor competitor = Competitor.builder()
                .name(request.getName())
                .industry(request.getIndustry())
                .strengths(request.getStrengths())
                .weaknesses(request.getWeaknesses())
                .marketShare(request.getMarketShare())
                .typicalBidRangeMin(request.getTypicalBidRangeMin())
                .typicalBidRangeMax(request.getTypicalBidRangeMax())
                .build();

        Competitor savedCompetitor = competitorRepository.save(competitor);
        log.info("Created competitor with id: {}", savedCompetitor.getId());

        return convertToDTO(savedCompetitor);
    }

    /**
     * 获取所有竞争对手
     */
    @Transactional(readOnly = true)
    public List<CompetitorDTO> getAllCompetitors() {
        log.debug("Fetching all competitors");
        return competitorRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 创建竞争分析
     */
    @Auditable(action = "CREATE", entityType = "CompetitionAnalysis", description = "Create competition analysis")
    @Transactional
    public CompetitionAnalysisDTO createAnalysis(AnalysisCreateRequest request) {
        log.info("Creating analysis for project: {}", request.getProjectId());

        // Validate input
        validateAnalysisRequest(request);
        projectAccessScopeService.assertCurrentUserCanAccessProject(request.getProjectId());

        CompetitionAnalysis analysis = CompetitionAnalysis.builder()
                .projectId(request.getProjectId())
                .competitorId(request.getCompetitorId())
                .winProbability(request.getWinProbability())
                .competitiveAdvantage(request.getCompetitiveAdvantage())
                .recommendedStrategy(request.getRecommendedStrategy())
                .riskFactors(request.getRiskFactors())
                .analysisDate(LocalDateTime.now())
                .build();

        CompetitionAnalysis savedAnalysis = analysisRepository.save(analysis);
        log.info("Created analysis with id: {}", savedAnalysis.getId());

        return convertToDTO(savedAnalysis);
    }

    /**
     * 根据项目ID获取竞争分析
     */
    @Transactional(readOnly = true)
    public List<CompetitionAnalysisDTO> getAnalysisByProject(Long projectId) {
        log.debug("Fetching analysis for project: {}", projectId);

        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        return analysisRepository.findByProjectId(projectId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取竞争对手历史表现
     */
    @Transactional(readOnly = true)
    public List<CompetitionAnalysisDTO> getHistoricalPerformance(Long competitorId) {
        log.debug("Fetching historical performance for competitor: {}", competitorId);

        if (competitorId == null) {
            throw new IllegalArgumentException("Competitor ID is required");
        }

        return analysisRepository.findByCompetitorIdOrderByAnalysisDateDesc(competitorId).stream()
                .filter(this::canAccessAnalysisProject)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 分析项目竞争情况
     */
    @Auditable(action = "ANALYZE", entityType = "CompetitionAnalysis", description = "Analyze competition for project")
    @Transactional
    public CompetitionAnalysisDTO analyzeCompetition(Long projectId) {
        log.info("Analyzing competition for project: {}", projectId);

        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        // In a real implementation, this would use AI to analyze the competition
        // For now, we create a basic analysis with default values
        CompetitionAnalysis analysis = CompetitionAnalysis.builder()
                .projectId(projectId)
                .analysisDate(LocalDateTime.now())
                .winProbability(new BigDecimal("60.0"))
                .competitiveAdvantage("自动生成的优势分析")
                .recommendedStrategy("自动生成的策略建议")
                .riskFactors("自动生成的风险因素")
                .build();

        CompetitionAnalysis savedAnalysis = analysisRepository.save(analysis);
        log.info("Created competition analysis with id: {}", savedAnalysis.getId());

        return convertToDTO(savedAnalysis);
    }

    /**
     * 验证竞争对手创建请求
     */
    private void validateCompetitorRequest(CompetitorCreateRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Competitor name is required");
        }

        if (request.getMarketShare() != null) {
            if (request.getMarketShare().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Market share cannot be negative");
            }
            if (request.getMarketShare().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Market share cannot exceed 100");
            }
        }

        if (request.getTypicalBidRangeMin() != null && request.getTypicalBidRangeMax() != null) {
            if (request.getTypicalBidRangeMin().compareTo(request.getTypicalBidRangeMax()) > 0) {
                throw new IllegalArgumentException("Bid range minimum cannot be greater than maximum");
            }
        }

        if (request.getTypicalBidRangeMin() != null && request.getTypicalBidRangeMin().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Bid range values cannot be negative");
        }

        if (request.getTypicalBidRangeMax() != null && request.getTypicalBidRangeMax().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Bid range values cannot be negative");
        }
    }

    /**
     * 验证分析创建请求
     */
    private void validateAnalysisRequest(AnalysisCreateRequest request) {
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required");
        }

        if (request.getWinProbability() != null) {
            if (request.getWinProbability().compareTo(BigDecimal.ZERO) < 0 ||
                request.getWinProbability().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Win probability must be between 0 and 100");
            }
        }
    }

    /**
     * 转换竞争对手实体为DTO
     */
    private CompetitorDTO convertToDTO(Competitor competitor) {
        return CompetitorDTO.builder()
                .id(competitor.getId())
                .name(competitor.getName())
                .industry(competitor.getIndustry())
                .strengths(competitor.getStrengths())
                .weaknesses(competitor.getWeaknesses())
                .marketShare(competitor.getMarketShare())
                .typicalBidRangeMin(competitor.getTypicalBidRangeMin())
                .typicalBidRangeMax(competitor.getTypicalBidRangeMax())
                .createdAt(competitor.getCreatedAt())
                .build();
    }

    /**
     * 转换竞争分析实体为DTO
     */
    private CompetitionAnalysisDTO convertToDTO(CompetitionAnalysis analysis) {
        return CompetitionAnalysisDTO.builder()
                .id(analysis.getId())
                .projectId(analysis.getProjectId())
                .competitorId(analysis.getCompetitorId())
                .analysisDate(analysis.getAnalysisDate())
                .winProbability(analysis.getWinProbability())
                .competitiveAdvantage(analysis.getCompetitiveAdvantage())
                .recommendedStrategy(analysis.getRecommendedStrategy())
                .riskFactors(analysis.getRiskFactors())
                .build();
    }

    private boolean canAccessAnalysisProject(CompetitionAnalysis analysis) {
        if (analysis == null || analysis.getProjectId() == null) {
            return false;
        }
        try {
            projectAccessScopeService.assertCurrentUserCanAccessProject(analysis.getProjectId());
            return true;
        } catch (org.springframework.security.access.AccessDeniedException exception) {
            return false;
        }
    }
}
