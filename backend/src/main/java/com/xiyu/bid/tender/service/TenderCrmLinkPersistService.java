package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CO-501：关联商机的落库事务（独立 @Service，Spring AOP 代理生效）。
 *
 * <p><b>为何独立成 Service</b>：原本想用 TenderCommandService 内部自调用 persistCrmLink，
 * 但 Spring AOP 基于代理，{@code this.internalMethod()} 不走代理，方法级 {@code @Transactional}
 * 会失效。把落库逻辑提取到独立 Service，通过 Spring 注入调用，AOP 代理生效，事务边界真正拆分。
 *
 * <p>事务边界：CRM HTTP 校验（在 TenderCommandService.linkCrmOpportunity 事务外）完成后再调用本类，
 * 本类方法级 {@code @Transactional} 开启新事务做落库——防 CO-325 类长事务占数据库连接。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderCrmLinkPersistService {

    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final TenderAssignmentRecordRepository assignmentRecordRepository;
    private final TenderCrmOccupancyChecker crmOccupancyChecker;
    private final TenderEvaluationBackfillService evaluationBackfillService;
    private final TenderMapper tenderMapper;
    private final TenderAuditService tenderAuditService;

    /**
     * 关联商机落库（方法级 @Transactional，独立事务）。
     *
     * <p>调用方 {@link TenderCommandService#linkCrmOpportunity} 已完成：
     * <ol>
     *   <li>权限/状态/占位校验</li>
     *   <li>CRM 远程招标主体校验（事务外，不占数据库连接）</li>
     *   <li>本地一致性校验</li>
     * </ol>
     */
    @Transactional
    public TenderDTO persistCrmLink(Long tenderId, Tender existingTender, String crmOpportunityId,
                                     String crmOpportunityName, TenderEvaluationSubmitRequest evaluationPayload,
                                     Long userId, long purchaserId) {
        existingTender.setCrmOpportunityId(crmOpportunityId);
        existingTender.setCrmOpportunityName(crmOpportunityName);
        existingTender.setPurchaserId(purchaserId); // CO-464: CRM 校验返回的招标主体 ID 落库
        existingTender.setEvaluationSource(Tender.EvaluationSource.BID_SYSTEM_LINK);
        Tender updatedTender;
        try {
            updatedTender = tenderRepository.save(existingTender);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            crmOccupancyChecker.translateUniqueConstraintViolation(ex);
            throw new BusinessException(409, "CRM 商机已被其他标讯关联（并发冲突），请刷新后重试");
        }
        assignOnCrmLink(tenderId, userId);
        log.info("Linked CRM opportunity {} to tender id: {}, purchaserId: {}", crmOpportunityId, tenderId, purchaserId);

        if (evaluationPayload != null) {
            try {
                evaluationBackfillService.backfillFromCrmLink(tenderId, evaluationPayload, userId);
                log.info("CO-310: Backfilled evaluation for tender {} from CRM link", tenderId);
            } catch (BusinessException | IllegalStateException ex) {
                log.warn("CO-310: Skipped evaluation backfill for tender {} from CRM link (validation failed): {}",
                        tenderId, ex.getMessage());
            }
        }

        // R4 修复：resolveUsername 只查一次，缓存复用
        String username = resolveUsername(userId);
        String linkUsername = username != null ? username : "system";
        String linkUserId = userId != null ? String.valueOf(userId) : "system";
        tenderAuditService.logLinkCrm(tenderId, crmOpportunityName, linkUsername, linkUserId, null);

        return tenderMapper.toDTO(updatedTender);
    }

    /**
     * CO-310 两步流程：关联 CRM 商机时写一条 DISPATCH 分配记录，让关联人成为 latest assignee。
     * <p>照搬原 TenderTransferService.transfer 的 record builder 模式。sales 关联商机即视为
     * 接手该标讯的评估，需通过后续 submit() 的 canFill 实例守卫。
     */
    private void assignOnCrmLink(Long tenderId, Long assigneeId) {
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new com.xiyu.bid.exception.ResourceNotFoundException(
                        "User", String.valueOf(assigneeId)));
        String assigneeName = assignee.getFullName() != null
                ? assignee.getFullName() : assignee.getUsername();
        TenderAssignmentRecord record = TenderAssignmentRecord.builder()
                .tenderId(tenderId)
                .assigneeId(assigneeId)
                .assigneeName(assigneeName)
                .assignedById(assigneeId)
                .assignedByName(assigneeName)
                .type(TenderAssignmentRecord.AssignmentType.DISPATCH)
                .remark("CRM商机关联，自动接手评估")
                .assignedAt(LocalDateTime.now())
                .build();
        assignmentRecordRepository.save(record);
        log.info("CO-310: Tender {} auto-assigned to {} (id={}) on CRM link",
                tenderId, assignee.getFullName(), assigneeId);
    }

    private String resolveUsername(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getUsername).orElse(null);
    }
}
