// Input: projectId + TenderEvaluation（basic + customerInfos）
// Output: 副作用——建 ProjectInitiationDetails（评估数据 + evalPrefilled=true）
// Pos: tender/service/ - CO-323 标讯评估表 → 项目立项预填（命令式外壳）
package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CO-323: 标讯评估表 → 项目立项预填服务。
 * <p>{@code TenderEvaluationService.proceedToBid} 创建项目后调用，把评估数据（basic +
 * 客户信息 EAV）带入 {@link ProjectInitiationDetails}，并标记 {@code evalPrefilled=true}
 * （前端据此将带入字段设为只读，保证金/招标文件除外）。幂等（已存在不重复建）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InitiationPrefillService {

    private final ProjectInitiationDetailsRepository initiationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 把评估数据带入项目立项。评估表不存在/为空则跳过（FR-005），不阻塞 proceedToBid。
     *
     * @param projectId  新创建的项目 id
     * @param evaluation 标讯评估表（可为 null）
     */
    @Transactional
    public void prefillFromEvaluation(Long projectId, TenderEvaluation evaluation) {
        if (initiationRepository.findByProjectId(projectId).isPresent()) {
            log.info("CO-323: ProjectInitiationDetails already exists for project {}, skip prefill", projectId);
            return;
        }
        if (evaluation == null || evaluation.getBasic() == null) {
            log.info("CO-323: no evaluation data for project {}, skip prefill", projectId);
            return;
        }
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(projectId)
                .evalPrefilled(Boolean.TRUE)
                .reviewStatus("DRAFT")
                .build();
        EvaluationToInitiationMapper.applyEvaluationBasic(details, evaluation.getBasic());
        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(evaluation.getCustomerInfos());
        try {
            details.setCustomerInfoJson(objectMapper.writeValueAsString(rows));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("CO-323: serialize customer info rows failed", ex);
        }
        initiationRepository.save(details);
        log.info("CO-323: prefilled ProjectInitiationDetails for project {} from evaluation", projectId);
    }
}
