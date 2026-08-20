// Input: 匹配记录数量上限
// Output: 真实业绩记录转 KnowledgeMatch（recordId/content 来自 PerformanceRecord 字段拼接）
// Pos: performance/application/ - compliance 业绩库证据端口适配器（spec 049 US3 / T036，依赖倒置实现侧）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.performance.application;

import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.compliance.domain.policy.PerformanceEvidencePort;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 业绩库证据查询适配器（compliance 端口的 performance 实现）.
 *
 * <p>实现 compliance 定义的 {@link PerformanceEvidencePort}（依赖倒置），
 * 查询真实业绩记录并拼装 DB 字段摘要；替代误注入的 WarehouseRepository.
 */
@Component
public class PerformanceEvidenceQueryAdapter implements PerformanceEvidencePort {

    /** 业绩查询默认告警配置（对齐 PerformanceBundleExportAsyncExecutor.DEFAULT_CONFIG）. */
    private static final PerformanceAlertConfig DEFAULT_ALERT_CONFIG =
            new PerformanceAlertConfig(null, 180, 90, true);

    private final PerformanceRepository performanceRepository;

    public PerformanceEvidenceQueryAdapter(final PerformanceRepository performanceRepository) {
        this.performanceRepository = performanceRepository;
    }

    @Override
    public List<KnowledgeMatch> findPerformanceMatches(final int limit) {
        List<PerformanceRecord> records = performanceRepository.findAll(
                PerformanceSearchCriteria.empty(), DEFAULT_ALERT_CONFIG);
        List<KnowledgeMatch> matches = new ArrayList<>();
        for (PerformanceRecord r : records) {
            if (r.contractName() == null || r.contractName().isBlank()) {
                continue;
            }
            matches.add(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_PERFORMANCE,
                    String.valueOf(r.id()), describe(r), true));
            if (matches.size() >= limit) {
                break;
            }
        }
        return matches;
    }

    /** 业绩记录摘要（DB 字段拼接，禁止常量文案）. */
    private static String describe(final PerformanceRecord r) {
        StringBuilder sb = new StringBuilder("业绩: ").append(r.contractName());
        if (r.signingEntity() != null) {
            sb.append("（签约主体: ").append(r.signingEntity()).append("）");
        }
        if (r.signingDate() != null) {
            sb.append("，签约日期: ").append(r.signingDate());
        }
        return sb.toString();
    }
}
