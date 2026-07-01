package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
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
     * @param code CRM 商机编号（对应 crmId）
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到
     */
    public ProjectLeaderResult findProjectLeaderByChanceCode(String code) {
        if (code == null || code.isBlank()) {
            log.warn("findProjectLeaderByChanceCode skipped: code is null/blank");
            return null;
        }
        CustomerChanceDTO filter = new CustomerChanceDTO(
                null, null, code, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null
        );
        CustomerChancePageRequest request = new CustomerChancePageRequest(1, 10, filter);
        // 后台任务无登录用户上下文，传 null 回退全局共享 token（CO-152 兼容行为）
        CrmChancePageResult result = crmChanceService.pageList(request, null);

        if (result.list() == null || result.list().isEmpty()) {
            log.warn("findProjectLeaderByChanceCode: no opportunity found for code={}", code);
            return null;
        }

        CustomerChanceVO first = result.list().get(0);
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
     * @param id CRM 商机主键 id
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到
     */
    public ProjectLeaderResult findProjectLeaderByChanceId(Long id) {
        if (id == null) {
            log.warn("findProjectLeaderByChanceId skipped: id is null");
            return null;
        }
        CustomerChanceVO vo = crmChanceDetailService.getDetailById(id);
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
     * CRM 项目负责人查询结果。
     */
    public record ProjectLeaderResult(
            String projectLeaderName,
            String projectLeaderNo,
            String opportunityName,
            String opportunityCode
    ) {}
}
