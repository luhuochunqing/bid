package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * CRM 项目负责人查询服务。
 * <p>按商机编号查询项目负责人信息，失败时返回 null（降级策略）。
 * <p>spec 037 Review M2：三个 findProjectLeaderByXxx 方法行为统一 ——
 * leaderName 为空时返回<b>半填充</b> {@link ProjectLeaderResult}（含 code/name 供调用方关联商机），
 * 而非返回 null。这样调用方无需区分"未找到商机"和"商机无负责人"两种 null 语义。
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
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到商机。
     *         若商机存在但无负责人，返回半填充 Result（code/name 非空，leader 字段为 null）。
     */
    public ProjectLeaderResult findProjectLeaderByChanceCode(String code, String username) {
        if (code == null || code.isBlank()) {
            log.warn("findProjectLeaderByChanceCode skipped: code is null/blank");
            return null;
        }
        CustomerChanceVO first = crmChanceService.findByCode(code, username);
        if (first == null) {
            log.warn("findProjectLeaderByChanceCode: no opportunity found for code={}", code);
            return null;
        }
        return resolveLeader("code", code, first);
    }

    /**
     * 按商机主键 id 查询项目负责人信息。
     * <p>用于外部系统推送标讯时只携带商机主键 id（sourceId）未携带 code（crmId）的场景：
     * 通过 CRM detail 接口反查商机详情，取出 code/name/projectLeader。
     * <p>降级策略：查询失败或未找到返回 null，由调用方决定后续行为。
     *
     * @param id       CRM 商机主键 id
     * @param username 当前操作用户（后台无上下文时传 null → 降级 null）
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到商机。
     *         若商机存在但无负责人，返回半填充 Result（code/name 非空，leader 字段为 null）。
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
        return resolveLeader("id", id, vo);
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
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到商机。
     *         若商机存在但无负责人，返回半填充 Result（code/name 非空，leader 字段为 null）。
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
        return resolveLeader("bidId", bidId, vo);
    }

    /**
     * spec 037 Review M2：统一的 leader 解析逻辑（三个 find 方法共用）。
     * <p>行为统一：leaderName 为空时返回<b>半填充</b> Result（含 code/name），
     * 因为调用方（{@code CrmTenderLinkService.applyLeaderAndStatus}）需要 vo.code() 关联商机。
     * <p>历史 bug：{@code findByChanceCode} 在 leaderName 为空时返回 null，导致调用方
     * 不关联商机直接 setStatus(EVALUATED)；而 {@code findByChanceId}/{@code findByBidId}
     * 返回半填充 Result，调用方能正确关联商机。现在统一为后者行为。
     *
     * @param contextKey   日志上下文键（"code" / "id" / "bidId"）
     * @param contextValue 日志上下文值
     * @param vo           查到的商机 VO（非 null）
     * @return ProjectLeaderResult（永不为 null，因为 vo 非 null）
     */
    private ProjectLeaderResult resolveLeader(String contextKey, Object contextValue, CustomerChanceVO vo) {
        if (vo.projectLeaderName() == null || vo.projectLeaderName().isBlank()) {
            log.info("resolveLeader: {}={} has no projectLeaderName, returning partial result", contextKey, contextValue);
            return new ProjectLeaderResult(null, null, vo.name(), vo.code());
        }
        log.info("resolveLeader: {}={}, code={}, leader={}, leaderNo={}",
                contextKey, contextValue, vo.code(), vo.projectLeaderName(), vo.projectLeaderNo());
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
