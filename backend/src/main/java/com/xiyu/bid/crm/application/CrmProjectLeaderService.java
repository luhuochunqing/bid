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

    public CrmProjectLeaderService(CrmChanceService crmChanceService) {
        this.crmChanceService = crmChanceService;
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
        CrmChanceService.CrmChancePageResult result = crmChanceService.pageList(request);

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
     * CRM 项目负责人查询结果。
     */
    public record ProjectLeaderResult(
            String projectLeaderName,
            String projectLeaderNo,
            String opportunityName,
            String opportunityCode
    ) {}
}
