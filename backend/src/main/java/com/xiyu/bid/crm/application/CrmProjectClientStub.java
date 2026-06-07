package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.domain.CrmProjectMapping;
import com.xiyu.bid.crm.domain.CrmProjectMappingRepository;
import com.xiyu.bid.crm.domain.AssignmentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CRM 项目客户端 Stub 实现
 * 
 * <p>基于本地数据库映射表（crm_project_mapping）进行匹配。
 * 当本地表有匹配时直接返回结果；无匹配时可选择调用真实 CRM API。
 * 
 * <p>待真实 CRM API 就绪后，可替换为：
 * <ul>
 *   <li>优先查询本地缓存（crm_project_mapping）</li>
 *   <li>本地无匹配时调用 CRM API 获取最新数据</li>
 *   <li>API 返回结果后自动回填本地缓存表</li>
 * </ul>
 */
@Component
public class CrmProjectClientStub implements CrmProjectClient {

    private static final Logger log = LoggerFactory.getLogger(CrmProjectClientStub.class);

    private final CrmProjectMappingRepository mappingRepository;

    public CrmProjectClientStub(CrmProjectMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @Override
    public AssignmentResult findProjectByPurchaser(String purchaserName) {
        if (purchaserName == null || purchaserName.trim().isEmpty()) {
            log.debug("Skip CRM lookup: purchaserName is blank");
            return AssignmentResult.noMatch();
        }

        String normalizedName = purchaserName.trim();
        log.debug("Querying CRM project for purchaser: {}", normalizedName);

        List<CrmProjectMapping> mappings = mappingRepository.findAllByPurchaserName(normalizedName);

        if (mappings.isEmpty()) {
            log.debug("No CRM mapping for purchaser: {}", normalizedName);
            return AssignmentResult.noMatch();
        }

        if (mappings.size() > 1) {
            log.warn("Multiple CRM mappings ({} records) found for purchaser '{}'; using the first one. "
                    + "Please deduplicate the crm_project_mapping table.",
                    mappings.size(), normalizedName);
        }

        CrmProjectMapping m = mappings.get(0);
        log.info("CRM mapping found for purchaser '{}': manager={}, dept={}",
                normalizedName, m.getProjectManagerName(), m.getDepartmentName());
        return AssignmentResult.success(
                m.getCrmProjectId(),
                m.getProjectManagerId(),
                m.getProjectManagerName(),
                m.getDepartmentId(),
                m.getDepartmentName()
        );
    }
}
