// Input: Tender（含 crmOpportunityId）、用户提交的评估表请求、操作人用户名
// Output: 同步 CRM 最新数据后的评估表请求（basic + customerInfos 来自 CRM）
// Pos: Service/业务编排层（命令式外壳）
// 维护声明: 仅做编排：解析 code → 查 CRM 商机 → 查对接人 → 委托 CrmEvaluationMapper 映射 → 返回新 req。
//          CRM 调用失败时降级：商机查询失败 → 返回原 req；对接人查询失败 → customerInfos 为空。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.crm.application.CrmChanceService;
import com.xiyu.bid.crm.application.CrmContactPersonService;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonInfoVO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.webhook.infrastructure.CrmOpportunityCodeResolver;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CO-526: 提交评估表时同步 CRM 商机和对接人信息。
 * <p>数据流：basic + customerInfos 在前端只读展示（来自 CRM 关联时回填），项目负责人不能手工修改；
 * 项目负责人只填写 bidRecommendation（是否投标）+ evaluationRecommendation（评估建议）。
 * <p>submit 时后端主动拉取 CRM 最新数据覆盖 basic + customerInfos（保证数据新鲜度），
 * 保留 bidRecommendation + evaluationRecommendation（人工判断字段）。
 * <p>降级策略：
 * <ul>
 *   <li>tender 未关联 CRM 商机 → 返回原 req（不调 CRM）</li>
 *   <li>CRM 商机查询失败/返回空 → 返回原 req（全量降级）</li>
 *   <li>CRM 对接人查询失败 → basic 来自 CRM，customerInfos 为空列表（部分降级）</li>
 * </ul>
 * <p>映射规则与前端 {@code useCrmOpportunitySelector.js} 完全一致，由 {@link CrmEvaluationMapper} 承载。
 * <p><b>事务边界：</b>本服务纯 CRM 只读调用，无 DB 读/写，不加 {@code @Transactional}。
 * 调用方 {@link TenderEvaluationSubmissionService#submit} 持有事务，本方法在事务内同步执行。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TenderEvaluationCrmSyncService {

    private final CrmChanceService crmChanceService;
    private final CrmContactPersonService crmContactPersonService;
    private final CrmOpportunityCodeResolver crmOpportunityCodeResolver;
    private final CrmEvaluationMapper crmMapper;

    /**
     * 同步 CRM 数据到评估表提交请求。
     *
     * @param tender   当前标讯（需有 crmOpportunityId）
     * @param userReq  前端提交的评估表请求（bidRecommendation + evaluationRecommendation 为人工判断，保留；
     *                 basic + customerInfos 由本方法用 CRM 数据覆盖）
     * @param username 操作人用户名（用于按用户维度获取 CRM token）
     * @return 同步后的请求；未关联商机或 CRM 失败时返回原 userReq
     */
    public TenderEvaluationSubmitRequest syncFromCrm(Tender tender,
                                                       TenderEvaluationSubmitRequest userReq,
                                                       String username) {
        String rawOpportunityId = tender.getCrmOpportunityId();
        if (rawOpportunityId == null || rawOpportunityId.isBlank()) {
            return userReq;
        }

        String code = resolveOpportunityCode(rawOpportunityId);
        if (code == null || code.isBlank()) {
            return userReq;
        }

        // 商机查询 → 对接人查询是串行依赖（对接人查询需要 chance.id()），无法并行
        CustomerChanceVO chance = fetchCrmChance(code, username);
        if (chance == null) {
            log.warn("CO-526: CRM chance not found for code={}, degrading to user input", code);
            return userReq;
        }

        List<ContactPersonInfoVO> contacts = fetchContactPersons(chance.id(), username);
        EvaluationBasicDTO basic = crmMapper.mapChanceToBasic(chance);
        List<EvaluationCustomerInfoDTO> customerInfos = crmMapper.mapContactsToCustomerInfos(contacts);

        log.info("CO-526: Synced evaluation from CRM for tender {} (chanceId={}, contacts={})",
                tender.getId(), chance.id(), contacts.size());

        return new TenderEvaluationSubmitRequest(
                userReq.bidRecommendation(),
                basic,
                customerInfos,
                userReq.evaluationRecommendation());
    }

    private String resolveOpportunityCode(String rawOpportunityId) {
        try {
            return crmOpportunityCodeResolver.resolve(rawOpportunityId);
        } catch (RuntimeException e) {
            log.warn("CO-526: Failed to resolve CRM opportunity code '{}': {}", rawOpportunityId, e.getMessage());
            return null;
        }
    }

    private CustomerChanceVO fetchCrmChance(String code, String username) {
        try {
            return crmChanceService.findByCode(code, username);
        } catch (RuntimeException e) {
            log.warn("CO-526: CRM chance query failed for code={}: {}", code, e.getMessage());
            return null;
        }
    }

    private List<ContactPersonInfoVO> fetchContactPersons(Long chanceId, String username) {
        if (chanceId == null) {
            log.warn("CO-526: CRM chance has null id, skipping contacts sync");
            return Collections.emptyList();
        }
        try {
            return crmContactPersonService.pageList(chanceId, username);
        } catch (RuntimeException e) {
            log.warn("CO-526: CRM contact persons query failed for chanceId={}: {}", chanceId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
