package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * CRM 项目负责人查询服务。
 * <p>按商机编号查询项目负责人信息，失败时返回 null（降级策略）。
 */
@Service
public class CrmProjectLeaderService {

    private static final Logger log = LoggerFactory.getLogger(CrmProjectLeaderService.class);

    private final CrmChanceService crmChanceService;
    private final CrmChanceDetailService crmChanceDetailService;

    public CrmProjectLeaderService(CrmChanceService crmChanceService,
                                   CrmChanceDetailService crmChanceDetailService) {
        this.crmChanceService = crmChanceService;
        this.crmChanceDetailService = crmChanceDetailService;
    }

    /**
     * 按商机 code 查询项目负责人信息。
     *
     * @param code     CRM 商机编号（对应 crmId）
     * @param username 当前操作用户（后台无上下文时传 null → 降级 null）
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到
     */
    public ProjectLeaderResult findProjectLeaderByChanceCode(String code, String username) {
        if (code == null || code.isBlank()) {
            log.warn("findProjectLeaderByChanceCode skipped: code is null/blank");
            return null;
        }
        // 后台任务无登录用户上下文时 username=null → findByCode 内 token 不可用 → 降级 null
        CustomerChanceVO first = crmChanceService.findByCode(code, username);
        if (first == null) {
            log.warn("findProjectLeaderByChanceCode: no opportunity found for code={}", code);
            return null;
        }
        if (first.projectLeaderName() == null || first.projectLeaderName().isBlank()) {
            log.info("findProjectLeaderByChanceCode: code={} has no projectLeaderName", code);
            return null;
        }

        log.info("findProjectLeaderByChanceCode: code={}, leader={}, leaderNo={}",
                code, first.projectLeaderName(), first.projectLeaderNo());
        return new ProjectLeaderResult(
                first.projectLeaderName(),
                first.projectLeaderNo(),
                first.name(),
                first.code()
        );
    }

    /**
     * 按商机主键 id 查询项目负责人信息。
     * <p>用于外部系统推送标讯时只携带商机主键 id（sourceId）未携带 code（crmId）的场景：
     * 通过 CRM detail 接口反查商机详情，取出 code/name/projectLeader。
     * <p>降级策略：查询失败或未找到返回 null，由调用方决定后续行为。
     *
     * @param id       CRM 商机主键 id
     * @param username 当前操作用户（后台无上下文时传 null → 降级 null）
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到
     */
    public ProjectLeaderResult findProjectLeaderByChanceId(Long id, String username) {
        if (id == null) {
            log.warn("findProjectLeaderByChanceId skipped: id is null");
            return null;
        }
        CustomerChanceVO vo = crmChanceDetailService.getDetailById(id, username);
        if (vo == null) {
            log.warn("findProjectLeaderByChanceId: no opportunity found for id={}", id);
            return null;
        }
        if (vo.projectLeaderName() == null || vo.projectLeaderName().isBlank()) {
            log.info("findProjectLeaderByChanceId: id={} has no projectLeaderName", id);
            // 仍返回，因为调用方需要 vo.code() 来关联商机
            return new ProjectLeaderResult(null, null, vo.name(), vo.code());
        }
        log.info("findProjectLeaderByChanceId: id={}, code={}, leader={}, leaderNo={}",
                id, vo.code(), vo.projectLeaderName(), vo.projectLeaderNo());
        return new ProjectLeaderResult(
                vo.projectLeaderName(),
                vo.projectLeaderNo(),
                vo.name(),
                vo.code()
        );
    }

    /**
     * 按 CRM 标讯 ID（bidId）查询项目负责人信息。
     * <p>spec 037：CRM 推送标讯时 external_id=CRM:{bidId}，bidId 是 CRM 标讯 ID
     * （非商机主键 id）。通过 page-list 接口按 bidId 反查商机，取出 code/name/projectLeader。
     * <p>与 {@link #findProjectLeaderByChanceId} 的区别：
     * <ul>
     *   <li>findProjectLeaderByChanceId 用 chanceId 调 detail 接口（商机主键）</li>
     *   <li>findProjectLeaderByBidId 用 bidId 调 page-list 接口（标讯 ID）</li>
     * </ul>
     * <p>降级策略：查询失败或未找到返回 null，由调用方决定后续行为。
     *
     * @param bidId    CRM 标讯 ID
     * @param username 当前操作用户（后台无上下文时传 null → 降级 null）
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到
     */
    public ProjectLeaderResult findProjectLeaderByBidId(Long bidId, String username) {
        if (bidId == null) {
            log.warn("findProjectLeaderByBidId skipped: bidId is null");
            return null;
        }
        CustomerChanceVO vo = crmChanceService.findByBidId(bidId, username);
        if (vo == null) {
            log.warn("findProjectLeaderByBidId: no opportunity found for bidId={}", bidId);
            return null;
        }
        if (vo.projectLeaderName() == null || vo.projectLeaderName().isBlank()) {
            log.info("findProjectLeaderByBidId: bidId={} has no projectLeaderName", bidId);
            // 仍返回，因为调用方需要 vo.code() 来关联商机
            return new ProjectLeaderResult(null, null, vo.name(), vo.code());
        }
        log.info("findProjectLeaderByBidId: bidId={}, code={}, leader={}, leaderNo={}",
                bidId, vo.code(), vo.projectLeaderName(), vo.projectLeaderNo());
        return new ProjectLeaderResult(
                vo.projectLeaderName(),
                vo.projectLeaderNo(),
                vo.name(),
                vo.code()
        );
    }

    /**
     * CRM 项目负责人查询结果。
     */
    public record ProjectLeaderResult(
            String projectLeaderName,
            String projectLeaderNo,
            String opportunityName,
            String opportunityCode
    ) {}
}
