package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * CO-297: 标讯关联 CRM 商机的占位校验器。
 * <p>
 * 命名含义：Checker 而非 Guard — 本文件仅做业务去重校验，
 * 不涉及 projectId/权限访问控制（项目中 *Guard 保留给权限语义）。
 * <p>
 * 双层防御：
 * 1. 写入前 SELECT 检查（应用层）
 * 2. 写入时数据库 UNIQUE 索引（数据库最终防线），
 *    捕获 {@link DataIntegrityViolationException} 后转成 409 提示
 * <p>
 * CO-277 防复发：crm_opportunity_id 列应存 CC 格式编号（如 CC20260711739），
 * 不应存纯数字主键 id（如 21364）。当检测到纯数字值时记录警告日志，
 * 便于及时发现 CRM 推送字段语义回归问题（PR !2011 回归根因）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderCrmOccupancyChecker {

    private final TenderRepository tenderRepository;

    public void assertCrmOpportunityNotOccupied(Long currentTenderId, String crmOpportunityId) {
        if (crmOpportunityId == null || crmOpportunityId.isBlank()) {
            return;
        }
        // 防复发：纯数字的 crmOpportunityId 是 CRM 推送误传的主键 id，记录警告便于排查
        if (crmOpportunityId.trim().matches("\\d+")) {
            log.warn("Suspicious crmOpportunityId '{}' (numeric id, expected CC-format code) "
                    + "for tender id={}, occupancy check may be ineffective", crmOpportunityId, currentTenderId);
        }
        tenderRepository.findByCrmOpportunityId(crmOpportunityId)
                .filter(occupied -> !occupied.getId().equals(currentTenderId))
                .ifPresent(occupied -> {
                    throw new BusinessException(409,
                            "该 CRM 商机已被标讯 ID=" + occupied.getId() + " 关联，请先解除原关联");
                });
    }

    public void translateUniqueConstraintViolation(Exception ex) {
        if (ex instanceof DataIntegrityViolationException
                || ex.getCause() instanceof java.sql.SQLIntegrityConstraintViolationException
                || (ex.getMessage() != null && ex.getMessage().contains("idx_tender_crm_opportunity_id"))) {
            throw new BusinessException(409, "CRM 商机已被其他标讯关联（并发冲突），请刷新后重试");
        }
    }
}
