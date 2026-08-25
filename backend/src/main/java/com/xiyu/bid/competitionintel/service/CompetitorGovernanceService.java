// Input: CompetitorRepository, CompetitionAnalysisRepository, CompetitorDictionaryPolicy
// Output: 竞品字典治理业务操作（upsert/改名/合并/停用 + 查询）
// Pos: Service/业务层（命令式 shell）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.competitionintel.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.competitionintel.domain.CompetitorDictionaryPolicy;
import com.xiyu.bid.competitionintel.dto.CompetitorDTO;
import com.xiyu.bid.competitionintel.entity.Competitor;
import com.xiyu.bid.competitionintel.entity.CompetitorStatus;
import com.xiyu.bid.competitionintel.repository.CompetitionAnalysisRepository;
import com.xiyu.bid.competitionintel.repository.CompetitorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 竞品字典治理服务（specs/055）。
 * <p>承载竞品字典的读（启用/全量）与写（upsert/改名/合并/停用），与竞争分析
 * （{@link CompetitionIntelService}）职责分离。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompetitorGovernanceService {

    private final CompetitorRepository competitorRepository;
    private final CompetitionAnalysisRepository analysisRepository;

    /**
     * 查询启用中的竞品（录入下拉数据源）。
     */
    @Transactional(readOnly = true)
    public List<CompetitorDTO> getActiveCompetitors() {
        return competitorRepository.findAllByStatusOrderByNameAsc(CompetitorStatus.ACTIVE).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 查询全部竞品（治理页）。
     */
    @Transactional(readOnly = true)
    public List<CompetitorDTO> getAllCompetitors() {
        return competitorRepository.findAllByOrderByNameAsc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 幂等 upsert：名称已存在则返回已有竞品，不存在则创建。
     * <p>并发安全：数据库唯一约束 + 原生 INSERT ... ON DUPLICATE KEY UPDATE。</p>
     */
    @Auditable(action = "UPSERT", entityType = "Competitor", description = "Upsert competitor by name")
    @Transactional
    public CompetitorDTO upsertCompetitor(String rawName) {
        String name = requireNormalizedName(rawName);
        competitorRepository.insertCompetitorIfAbsent(name);
        return competitorRepository.findByName(name)
                .map(this::convertToDTO)
                .orElseThrow(() -> new IllegalStateException("竞品创建失败: " + name));
    }

    /**
     * 改名竞品（历史快照不受影响，新录入使用新名称）。
     */
    @Auditable(action = "RENAME", entityType = "Competitor", description = "Rename competitor")
    @Transactional
    public CompetitorDTO renameCompetitor(Long id, String rawName) {
        String name = requireNormalizedName(rawName);
        Competitor competitor = findCompetitor(id);
        competitor.setName(name);
        return convertToDTO(competitorRepository.save(competitor));
    }

    /**
     * 合并竞品：将 source 停用，其竞争分析引用迁移到 target。
     * <p>历史项目结果快照（project_result_competitor.name）不改（审计真实性）。</p>
     */
    @Auditable(action = "MERGE", entityType = "Competitor", description = "Merge competitor into target")
    @Transactional
    public CompetitorDTO mergeCompetitor(Long sourceId, Long targetId) {
        CompetitorDictionaryPolicy.Decision decision = CompetitorDictionaryPolicy.decideMerge(sourceId, targetId);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.reason());
        }
        Competitor target = findCompetitor(targetId);
        if (target.getStatus() == CompetitorStatus.DISABLED) {
            throw new IllegalArgumentException("目标竞品已停用，不能作为合并目标");
        }
        Competitor source = findCompetitor(sourceId);
        int migrated = analysisRepository.updateCompetitorIdForMerge(sourceId, targetId);
        source.setStatus(CompetitorStatus.DISABLED);
        competitorRepository.save(source);
        log.info("Merged competitor {} into {}, migrated {} analysis records", sourceId, targetId, migrated);
        return convertToDTO(target);
    }

    /**
     * 停用竞品（软删除）。
     */
    @Auditable(action = "DISABLE", entityType = "Competitor", description = "Disable competitor")
    @Transactional
    public CompetitorDTO disableCompetitor(Long id) {
        Competitor competitor = findCompetitor(id);
        competitor.setStatus(CompetitorStatus.DISABLED);
        return convertToDTO(competitorRepository.save(competitor));
    }

    private String requireNormalizedName(String rawName) {
        String name = CompetitorDictionaryPolicy.normalizeName(rawName);
        if (name == null) {
            throw new IllegalArgumentException("竞品名称不能为空");
        }
        return name;
    }

    private Competitor findCompetitor(Long id) {
        return competitorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("竞品不存在: " + id));
    }

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
                .status(competitor.getStatus())
                .createdAt(competitor.getCreatedAt())
                .build();
    }
}
