package com.xiyu.bid.integration.external;

import com.xiyu.bid.crm.application.CrmProjectLeaderService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CRM 标讯自动关联服务。
 * <p>当外部系统（如 CRM）推送标讯时传入 crmId，负责：
 * <ol>
 *   <li>查询 CRM 项目负责人</li>
 *   <li>关联商机（设置 crmOpportunityId / crmOpportunityName）</li>
 *   <li>分配项目负责人（先按工号匹配本地用户，未匹配则用姓名兜底）</li>
 *   <li>将标讯状态设为 TRACKING</li>
 * </ol>
 *
 * <p>降级策略：
 * <ul>
 *   <li>CRM 接口异常：仅记录错误，保持标讯待分配状态（PENDING_ASSIGNMENT）</li>
 *   <li>未找到负责人：仍关联商机并设为跟踪中（TRACKING）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrmTenderLinkService {

    private final CrmProjectLeaderService crmProjectLeaderService;
    private final UserRepository userRepository;

    /**
     * 关联 CRM 商机并分配项目负责人。
     *
     * @param tender              标讯实体（已保存或即将保存）
     * @param crmId              CRM 商机主键 id（纯数字），用于查询项目负责人；可为空
     * @param crmOpportunityCode CRM 商机编号 code（CC... 格式），直接存入 tender；可为空
     * @param username           当前操作用户 username（用于获取 CRM token）；API Key 认证路径下
     *                           由调用方通过 userId 反查得到，null 时降级为旧行为（无 token 反查失败）
     */
    public void linkIfPresent(Tender tender, String crmId, String crmOpportunityCode, String username) {
        if ((crmId == null || crmId.isBlank()) && (crmOpportunityCode == null || crmOpportunityCode.isBlank())) return;
        applyCrmLinkAndAssignment(tender, crmId, crmOpportunityCode, username);
    }

    /**
     * 当 sourceSystem=CRM 但未传 crmId（商机编号）时，用 sourceId（标讯 ID）
     * 兜底反查商机编号，再走关联流程。
     * <p>spec 037 修正：sourceId 是 CRM 标讯 ID（bidId），不是商机主键 id（chanceId）。
     * 生产 bug tender 56 案例：external_id=CRM:7，7 是 bidId，旧代码把 7 当 chanceId
     * 调 detail 接口 → 查不到商机（实际商机 id=6, bidId=7, code=CC2026071568）。
     * 修复后改用 page-list 按 bidId 反查。
     * <p>降级策略：sourceId 不是合法数字、或 CRM 反查失败时，保持原逻辑（不关联商机），
     * 不影响现有行为。
     *
     * @param tender       标讯实体
     * @param sourceSystem 来源系统（仅 "CRM" 触发兜底）
     * @param sourceId     来源系统数据 id（CRM 标讯 ID，即 bidId）
     * @param username     当前操作用户 username（用于获取 CRM token）
     * @return true 表示已成功通过兜底关联商机；false 表示未触发或反查失败
     * @deprecated spec 037：方法名保留 chanceId 语义不准确，改用 {@link #linkByBidIdIfPresent}
     */
    @Deprecated
    public boolean linkByChanceIdIfPresent(Tender tender, String sourceSystem, String sourceId, String username) {
        return linkByBidIdIfPresent(tender, sourceSystem, sourceId, username);
    }

    /**
     * 当 sourceSystem=CRM 但未传 crmId（商机编号）时，用 sourceId（标讯 ID，bidId）
     * 兜底反查商机编号，再走关联流程。
     * <p>spec 037 新方法名，语义清晰：sourceId 是 bidId（标讯 ID），不是 chanceId（商机主键）。
     *
     * @param tender       标讯实体
     * @param sourceSystem 来源系统（仅 "CRM" 触发兜底）
     * @param sourceId     来源系统数据 id（CRM 标讯 ID，即 bidId）
     * @param username     当前操作用户 username（用于获取 CRM token）
     * @return true 表示已成功通过兜底关联商机；false 表示未触发或反查失败
     */
    public boolean linkByBidIdIfPresent(Tender tender, String sourceSystem, String sourceId, String username) {
        if (sourceSystem == null || !"CRM".equals(sourceSystem)) return false;
        if (sourceId == null || sourceId.isBlank()) return false;
        Long bidId;
        try {
            bidId = Long.parseLong(sourceId.trim());
        } catch (NumberFormatException e) {
            // sourceId 不是纯数字，不是 bidId，跳过
            return false;
        }
        log.info("linkByBidIdIfPresent: sourceId={} parsed as bidId, tender id={}, username={}",
                sourceId, tender.getId(), username);
        try {
            CrmProjectLeaderService.ProjectLeaderResult leader =
                    crmProjectLeaderService.findProjectLeaderByBidId(bidId, username);
            if (leader == null || leader.opportunityCode() == null || leader.opportunityCode().isBlank()) {
                log.warn("linkByBidIdIfPresent: no opportunity found for bidId={}", bidId);
                return false;
            }
            // 反查到商机，直接复用 leader 信息（避免再用 code 二次查询 page-list）
            applyLeaderAndStatus(tender, leader);
            return true;
        } catch (RuntimeException e) {
            log.error("linkByBidIdIfPresent failed for bidId={}: {}", bidId, e.getMessage());
            return false;
        }
    }

    /**
     * 查询项目负责人并关联商机。
     * <p>crmOpportunityCode（CC... 格式）直接存入 tender.crm_opportunity_id；
     * crmId（数字主键）仅用于调用 CRM detail 接口查询项目负责人。
     * <p><b>CO-277 字段语义警告</b>：CRM 推送方把商机主键 id（纯数字如 21364）
     * 放在 crmOpportunityId 字段传输，而非 CC 格式编号。当 crmOpportunityCode
     * 是纯数字时，不能直接存入 crm_opportunity_id 列，必须用 chanceId 反查
     * 真正的 CC 编号后再落库，否则与"关联标讯"按钮设置的 CC 编号格式不一致，
     * 导致去重校验失效（PR !2011 回归根因，tender 1646 案例）。
     *
     * @param tender              标讯实体
     * @param crmId              CRM 商机主键 id（纯数字），可为空
     * @param crmOpportunityCode CRM 商机编号 code（CC... 格式），可为空；CRM 推送可能传纯数字 id
     * @param username           当前操作用户 username（用于获取 CRM token）；可为 null（降级为无 token 反查）
     */
    public void applyCrmLinkAndAssignment(Tender tender, String crmId, String crmOpportunityCode, String username) {
        log.info("Applying CRM link for tender id={}, crmId={}, crmOpportunityCode={}, username={}",
                tender.getId(), crmId, crmOpportunityCode, username);
        // 仅当 code 是 CC 格式编号（非纯数字）时才直接存入
        // 纯数字是 CRM 推送误传的主键 id，需通过 findProjectLeaderByChanceId 反查真正的 CC 编号
        if (isCcFormatCode(crmOpportunityCode)) {
            tender.setCrmOpportunityId(crmOpportunityCode);
        }
        try {
            CrmProjectLeaderService.ProjectLeaderResult leader = tryResolveLeader(crmId, crmOpportunityCode, username);
            if (leader == null) {
                log.warn("CRM link: no project leader found, setting EVALUATED");
                tender.setStatus(Tender.Status.EVALUATED);
                return;
            }
            applyLeaderAndStatus(tender, leader);
        } catch (RuntimeException e) {
            log.error("CRM link failed for crmId={}, crmOpportunityCode={}: {}",
                    crmId, crmOpportunityCode, e.getMessage());
            // 降级：CRM 接口异常时不中断主流程，code 已存入，不影响 webhook 回传
            // spec 037 Review M3：统一降级路径 —— 异常时也设置 EVALUATED，避免副作用不一致
            tender.setStatus(Tender.Status.EVALUATED);
        }
    }

    /**
     * spec 037 Review M3：按优先级策略链查找项目负责人。
     * <p>三策略按优先级依次尝试，首个非 null 结果即返回（责任链模式）：
     * <ol>
     *   <li>crmId 是数字主键 → {@code findProjectLeaderByChanceId(chanceId)}</li>
     *   <li>crmOpportunityCode 是纯数字（CRM 推送误传的 id）→ 同上，作为 chanceId 反查</li>
     *   <li>crmOpportunityCode 是 CC 格式编号 → {@code findProjectLeaderByChanceCode(code)}</li>
     * </ol>
     * <p>原实现用 3 个 if-else 级联 + 2 个内层 try/catch NumberFormatException，圈复杂度 8-9；
     * 重构后用策略链 + 局部变量 {@code codeIsCcFormat} 避免重复判断，圈复杂度降至 4-5。
     *
     * @param crmId              CRM 商机主键 id（纯数字字符串），可为空
     * @param crmOpportunityCode CRM 商机编号 code（CC... 格式）或纯数字 id，可为空
     * @param username           当前操作用户 username
     * @return 项目负责人信息；null 表示所有策略都未命中
     */
    private CrmProjectLeaderService.ProjectLeaderResult tryResolveLeader(
            String crmId, String crmOpportunityCode, String username) {
        // 预计算格式判断，避免重复调用（原实现 isCcFormatCode 调用 3 次）
        boolean codeIsCcFormat = isCcFormatCode(crmOpportunityCode);
        boolean codeIsNumericId = isNumericId(crmOpportunityCode);

        // 策略 1：crmId 是数字主键 → chanceId 查 detail
        if (crmId != null && !crmId.isBlank()) {
            try {
                Long chanceId = Long.parseLong(crmId.trim());
                CrmProjectLeaderService.ProjectLeaderResult leader =
                        crmProjectLeaderService.findProjectLeaderByChanceId(chanceId, username);
                if (leader != null) return leader;
            } catch (NumberFormatException e) {
                log.warn("CRM link: crmId '{}' is not numeric, skipping chanceId lookup", crmId);
            }
        }

        // 策略 2：crmOpportunityCode 是纯数字（CRM 推送误传的 id）→ 作为 chanceId 反查
        if (codeIsNumericId) {
            try {
                Long chanceId = Long.parseLong(crmOpportunityCode.trim());
                CrmProjectLeaderService.ProjectLeaderResult leader =
                        crmProjectLeaderService.findProjectLeaderByChanceId(chanceId, username);
                if (leader != null) {
                    log.info("CRM link: crmOpportunityCode '{}' is numeric, resolved via chanceId lookup", crmOpportunityCode);
                    return leader;
                }
            } catch (NumberFormatException e) {
                log.warn("CRM link: crmOpportunityCode '{}' is not numeric", crmOpportunityCode);
            }
        }

        // 策略 3：crmOpportunityCode 是 CC 格式编号 → code 查 page-list
        if (codeIsCcFormat) {
            return crmProjectLeaderService.findProjectLeaderByChanceCode(crmOpportunityCode, username);
        }

        return null;
    }

    /**
     * 用已查到的 leader 信息关联商机、分配负责人、设置状态。
     * <p>供 {@link #applyCrmLinkAndAssignment}（按 code 查）和
     * {@link #linkByChanceIdIfPresent}（按 id 查）共用，避免重复查询。
     */
    private void applyLeaderAndStatus(Tender tender, CrmProjectLeaderService.ProjectLeaderResult leader) {
        String crmId = leader.opportunityCode();
        // 设置商机关联（仅当 code 非空时才设置 id 和 name，避免"半关联"状态导致去重校验失效）
        if (crmId != null && !crmId.isBlank()) {
            tender.setCrmOpportunityId(crmId);
            tender.setCrmOpportunityName(leader.opportunityName());
        }

        // 解析项目负责人：先按工号匹配本地用户
        if (leader.projectLeaderNo() != null && !leader.projectLeaderNo().isBlank()) {
            userRepository.findByEmployeeNumber(leader.projectLeaderNo()).ifPresentOrElse(
                user -> {
                    tender.setProjectManagerId(user.getId());
                    tender.setProjectManagerName(user.getFullName());
                    log.info("CRM link: assigned project manager id={}, name={} for crmId={}",
                            user.getId(), user.getFullName(), crmId);
                },
                () -> {
                    // 工号未匹配到本地用户，用姓名作为兜底
                    tender.setProjectManagerName(leader.projectLeaderName());
                    log.warn("CRM link: employeeNo={} not found locally, using name={} for crmId={}",
                            leader.projectLeaderNo(), leader.projectLeaderName(), crmId);
                }
            );
        } else {
            // 无工号时直接用姓名
            tender.setProjectManagerName(leader.projectLeaderName());
            log.info("CRM link: no employeeNo, using name={} for crmId={}",
                    leader.projectLeaderName(), crmId);
        }

        // 将标讯状态设置为已评估
        tender.setStatus(Tender.Status.EVALUATED);
        log.info("CRM link: tender status set to EVALUATED for crmId={}", crmId);
    }

    /**
     * 判断值是否是 CC 格式编号（非空、非纯数字）。
     * <p>CRM 推送方把商机主键 id 放在 crmOpportunityId 字段传输（CO-277），
     * 纯数字值不能直接存入 crm_opportunity_id 列。
     */
    private static boolean isCcFormatCode(String value) {
        return value != null && !value.isBlank() && !value.trim().matches("\\d+");
    }

    /**
     * 判断值是否是纯数字主键 id（CRM 推送误传到 crmOpportunityId 字段的情况）。
     */
    private static boolean isNumericId(String value) {
        return value != null && !value.isBlank() && value.trim().matches("\\d+");
    }
}
