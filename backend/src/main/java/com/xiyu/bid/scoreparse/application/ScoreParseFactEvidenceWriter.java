package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Persists extracted facts and their source qualification evidence as one snapshot. */
@Slf4j
final class ScoreParseFactEvidenceWriter {
    private final ScoreParseFactRepository factRepository;
    private final ScoreParseEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;

    ScoreParseFactEvidenceWriter(ScoreParseFactRepository factRepository,
            ScoreParseEvidenceRepository evidenceRepository, ObjectMapper objectMapper) {
        this.factRepository = factRepository;
        this.evidenceRepository = evidenceRepository;
        this.objectMapper = objectMapper;
    }

    void persist(Long projectId, Long parseTaskId,
                                        List<com.xiyu.bid.scoreparse.domain.ScoreFact> facts,
                                        List<com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.Qualification> qualifications) {
        persist(projectId,parseTaskId,facts,qualifications,false);
    }
    void persist(Long projectId,Long parseTaskId,List<com.xiyu.bid.scoreparse.domain.ScoreFact> facts,
            List<com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.Qualification> qualifications,boolean preserveUnknown) {
        if (factRepository != null && factRepository.findByParseTaskId(parseTaskId).stream().anyMatch(f -> f.getPayloadVersion()!=null))
            throw new ScoreExtractionException(409,"EXTRACTION_FACTS_ALREADY_FROZEN");
        if (facts == null || facts.isEmpty()) {
            return;
        }
        List<com.xiyu.bid.scoreparse.entity.ScoreParseFact> factEntities = new ArrayList<>();
        List<com.xiyu.bid.scoreparse.entity.ScoreParseEvidence> evidenceEntities = new ArrayList<>();
        Map<String, com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.Qualification> qualificationByFact =
                (qualifications == null ? List.<com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.Qualification>of()
                        : qualifications).stream().collect(Collectors.toMap(
                        com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.Qualification::factId,
                        qualification -> qualification, (first, ignored) -> first));

        for (com.xiyu.bid.scoreparse.domain.ScoreFact f : facts) {
            if (f == null) continue;
            if (f.validationStatus() == com.xiyu.bid.scoreparse.domain.ScoreFact.ValidationStatus.AUTO_APPROVED
                    && (f.sourceCellIds() == null || f.sourceCellIds().isEmpty())
                    && f.weightEvidence() == null) {
                throw new IllegalArgumentException("持久化服务拒绝无证据自动通过项: factId=" + f.factId());
            }
            String cellIdsJson = serializeCellIds(f.sourceCellIds());
            String evidenceJson = serializeWeightEvidence(f.weightEvidence());
            if (f.weightEvidence() != null) {
                var ev = f.weightEvidence();
                evidenceEntities.add(com.xiyu.bid.scoreparse.entity.ScoreParseEvidence.builder()
                        .factId(f.factId())
                        .parseTaskId(parseTaskId)
                        .cellId(ev.cellId())
                        .kind(ev.kind().name())
                        .quotedText(ev.quotedText())
                        .startOffset(ev.startOffset())
                        .endOffset(ev.endOffset())
                        .explanation(ev.explanation())
                        .build());
            }
            if (f.sourceMetadata().diagnostics().contains("ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED")) {
                evidenceEntities.add(com.xiyu.bid.scoreparse.entity.ScoreParseEvidence.builder()
                        .factId(f.factId()).parseTaskId(parseTaskId)
                        .kind("ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED")
                        .explanation("备用提取结果默认不计入；人工选入时请核对是否重复计分。")
                        .build());
            }
            var qualification = qualificationByFact.get(f.factId());
            if (qualification != null) {
                evidenceEntities.add(com.xiyu.bid.scoreparse.entity.ScoreParseEvidence.builder()
                        .factId(f.factId())
                        .parseTaskId(parseTaskId)
                        .kind("SCHEME_QUALIFICATION")
                        .quotedText(qualification.locator())
                        .explanation(serializeQualification(qualification))
                        .build());
            }
            factEntities.add(com.xiyu.bid.scoreparse.entity.ScoreParseFact.builder()
                    .projectId(projectId)
                    .parseTaskId(parseTaskId)
                    .factId(f.factId())
                    .sourceTableId(f.sourceTableId())
                    .sourceCellIds(cellIdsJson)
                    .sourceText(f.sourceText() == null ? "" : f.sourceText())
                    .code(com.xiyu.bid.scoreparse.domain.ScoreCodeSanitizer.clean(f.code()))
                    .dimension(f.dimension() == null ? "" : f.dimension())
                    .weight(preserveUnknown?storableWeight(f.weight()):f.weight()==null?BigDecimal.ZERO:f.weight())
                    .weightEvidence(evidenceJson)
                    .parentFactId(f.parentFactId())
                    .childAggregation(f.childAggregation().name())
                    .extractionMethod(f.extractionMethod() == null ? "DETERMINISTIC" : f.extractionMethod())
                    .confidence(BigDecimal.valueOf(f.confidence()))
                    .validationStatus(f.validationStatus() == null ? "AUTO_APPROVED" : f.validationStatus().name())
                    .build());
        }
        if (factRepository != null) {
            // 删除范围按 parseTaskId 隔离（与 evidence 的删除口径一致）：
            // 复核读取走 findByParseTaskId(task)，历史任务的事实快照是旧正式评分项
            // 的证据链，不得在保存本次候选时按 projectId 一并洗掉。
            factRepository.deleteByParseTaskId(parseTaskId);
            factRepository.saveAll(factEntities);
        }
        if (evidenceRepository != null) {
            evidenceRepository.deleteByParseTaskId(parseTaskId);
            if (!evidenceEntities.isEmpty()) {
                evidenceRepository.saveAll(evidenceEntities);
            }
        }
    }

    /** The exact model value remains in versioned JSON; never round it to fit the legacy DECIMAL column. */
    static BigDecimal storableWeight(BigDecimal value) {
        return value!=null && value.stripTrailingZeros().scale()<=2
                && value.abs().compareTo(new BigDecimal("9999.99"))<=0?value:null;
    }

    private String serializeQualification(
            com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.Qualification qualification) {
        try {
            return objectMapper.writeValueAsString(qualification);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("评分方案资格证据序列化失败", exception);
        }
    }

    private String serializeCellIds(List<String> cellIds) {
        if (cellIds == null || cellIds.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(cellIds);
        } catch (JsonProcessingException e) {
            log.warn("序列化 sourceCellIds 失败: {}", e.getMessage());
            return "[]";
        }
    }

    private String serializeWeightEvidence(com.xiyu.bid.scoreparse.domain.ScoreEvidence ev) {
        if (ev == null) return null;
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "kind", ev.kind().name(),
                    "quotedText", ev.quotedText() == null ? "" : ev.quotedText(),
                    "cellId", ev.cellId() == null ? "" : ev.cellId()));
        } catch (JsonProcessingException e) {
            log.warn("序列化 weightEvidence 失败: {}", e.getMessage());
            return null;
        }
    }

}
