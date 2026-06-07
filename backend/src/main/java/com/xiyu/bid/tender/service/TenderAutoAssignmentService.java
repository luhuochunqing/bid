package com.xiyu.bid.tender.service;

import com.xiyu.bid.crm.domain.CrmProjectMapping;
import com.xiyu.bid.crm.domain.CrmProjectMappingRepository;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 标讯自动分配服务.
 *
 * <p>职责：在标讯创建后，根据业主单位名称自动匹配 CRM 项目负责人，
 * 实现自动分配。匹配失败时保持 PENDING_ASSIGNMENT 状态，等待手动分配。
 *
 * <p>集成点：
 * <ul>
 *   <li>查询 CrmProjectMapping 表进行本地匹配</li>
 *   <li>记录分配日志（INFO/DEBUG 级别，不影响业务流程）</li>
 * </ul>
 *
 * <p>副作用边界：
 * <ul>
 *   <li>Repository 查询（CrmProjectMappingRepository）</li>
 *   <li>日志记录</li>
 * </ul>
 */
@Service
public class TenderAutoAssignmentService {

    /** 日志. */
    private static final Logger LOG = LoggerFactory.getLogger(
            TenderAutoAssignmentService.class);

    /** 映射仓储. */
    private final CrmProjectMappingRepository mappingRepository;

    /**
     * 构造器.
     *
     * @param repo 映射仓储
     */
    public TenderAutoAssignmentService(
            final CrmProjectMappingRepository repo) {
        this.mappingRepository = repo;
    }

    /**
     * 根据标讯的业主单位名称尝试自动分配.
     *
     * @param tender 标讯实体
     * @return 分配结果
     */
    @Transactional(readOnly = true)
    public AssignmentResult tryAutoAssign(final Tender tender) {
        if (tender == null || !hasText(tender.getPurchaserName())) {
            LOG.debug("Skip: tender or purchaserName is null/blank");
            return AssignmentResult.noMatch();
        }

        String purchaserName = tender.getPurchaserName().trim();
        LOG.debug("Attempting auto-assignment for: {}", purchaserName);

        Optional<CrmProjectMapping> mapping =
                mappingRepository.findByPurchaserName(purchaserName);

        if (mapping.isPresent()) {
            CrmProjectMapping m = mapping.get();
            LOG.info("Auto-assignment matched: tender={}, purchaser={}, "
                    + "manager={}, dept={}",
                    tender.getId(), purchaserName,
                    m.getProjectManagerName(), m.getDepartmentName());
            return AssignmentResult.success(
                    m.getCrmProjectId(),
                    m.getProjectManagerId(),
                    m.getProjectManagerName(),
                    m.getDepartmentId(),
                    m.getDepartmentName());
        }

        LOG.debug("No mapping found for: {}", purchaserName);
        return AssignmentResult.noMatch();
    }

    /**
     * 根据标讯创建后自动尝试分配.
     *
     * <p>此方法应在标讯保存后调用。
     * 分配成功后更新标讯状态为 TRACKING。
     *
     * @param tender 标讯实体（已保存，带 ID）
     * @return true 成功匹配并分配，false 保持 PENDING_ASSIGNMENT
     */
    @Transactional
    public boolean autoAssignIfPossible(final Tender tender) {
        if (tender == null) {
            LOG.warn("Auto-assignment skipped: tender is null");
            return false;
        }

        AssignmentResult result = tryAutoAssign(tender);

        if (result.isMatched()) {
            // 状态转换检查由调用方处理，此处仅记录分配结果
            LOG.info("Tender {} assigned to manager {} ({})",
                    tender.getId(),
                    result.projectManagerName(),
                    result.projectManagerId());
            return true;
        }

        LOG.debug("Tender {} remains PENDING (no CRM mapping)",
                tender.getId());
        return false;
    }

    private boolean hasText(final String value) {
        return value != null && !value.trim().isEmpty();
    }
}
