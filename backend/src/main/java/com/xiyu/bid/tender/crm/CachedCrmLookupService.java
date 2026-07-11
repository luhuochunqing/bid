package com.xiyu.bid.tender.crm;

import com.xiyu.bid.crm.application.CompanySearchResult;
import com.xiyu.bid.crm.application.CrmCompanySearchService;
import com.xiyu.bid.crm.application.CrmCustomerManagerLookupService;
import com.xiyu.bid.crm.application.CustomerManagerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 批次内 CRM 反查缓存（spec 031 R-007 / FR-008）。
 *
 * <p>生命周期由调用方控制：
 * <ol>
 *   <li>{@link #openBatch()} — 批量导入异步循环前</li>
 *   <li>多次 {@link #searchByName} / {@link #findByCompanyId} — 同 key 只打一次 CRM</li>
 *   <li>{@link #closeBatch()} — finally 清理 ThreadLocal，防泄漏</li>
 * </ol>
 *
 * <p>无 openBatch 时透传底层服务，行为与直连一致（人工录入、单条 create 不受影响）。
 * 缓存「未命中」为 {@link Optional#empty()}，避免同一招标主体重复打空结果。
 */
@Service
public class CachedCrmLookupService {

    private static final Logger log = LoggerFactory.getLogger(CachedCrmLookupService.class);

    private final CrmCompanySearchService companySearchService;
    private final CrmCustomerManagerLookupService customerManagerLookupService;

    private static final ThreadLocal<BatchCache> BATCH = new ThreadLocal<>();

    public CachedCrmLookupService(
            CrmCompanySearchService companySearchService,
            CrmCustomerManagerLookupService customerManagerLookupService) {
        this.companySearchService = companySearchService;
        this.customerManagerLookupService = customerManagerLookupService;
    }

    /** 开启当前线程批次缓存（批量导入入口调用）。 */
    public void openBatch() {
        if (BATCH.get() != null) {
            log.warn("CachedCrmLookupService.openBatch: batch already open, replacing");
        }
        BATCH.set(new BatchCache());
    }

    /** 关闭并清理当前线程批次缓存（必须在 finally 中调用）。 */
    public void closeBatch() {
        BatchCache cache = BATCH.get();
        if (cache != null) {
            log.info("CRM batch cache closed: company hits={} misses={} managers hits={} misses={}",
                    cache.companyHits, cache.companyMisses, cache.managerHits, cache.managerMisses);
        }
        BATCH.remove();
    }

    public boolean isBatchOpen() {
        return BATCH.get() != null;
    }

    /**
     * 按公司名查询（精确匹配语义由底层 {@link CrmCompanySearchService} 保证）。
     */
    public Optional<CompanySearchResult> searchByName(String name, String username) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        String key = name.trim();
        BatchCache cache = BATCH.get();
        if (cache == null) {
            return companySearchService.searchByName(key, username);
        }
        // 命中：用 computeIfAbsent 取缓存（避免 Map.get 触发 java-standards Optional.get 误报）
        if (cache.companies.containsKey(key)) {
            cache.companyHits++;
            return cache.companies.computeIfAbsent(key, ignored -> Optional.empty());
        }
        cache.companyMisses++;
        Optional<CompanySearchResult> result = companySearchService.searchByName(key, username);
        cache.companies.put(key, result);
        return result;
    }

    /**
     * 按公司 ID 查集团项目经理。
     */
    public Optional<CustomerManagerResult> findByCompanyId(Long companyId, String username) {
        if (companyId == null) {
            return Optional.empty();
        }
        BatchCache cache = BATCH.get();
        if (cache == null) {
            return customerManagerLookupService.findByCompanyId(companyId, username);
        }
        if (cache.managers.containsKey(companyId)) {
            cache.managerHits++;
            return cache.managers.computeIfAbsent(companyId, ignored -> Optional.empty());
        }
        cache.managerMisses++;
        Optional<CustomerManagerResult> result =
                customerManagerLookupService.findByCompanyId(companyId, username);
        cache.managers.put(companyId, result);
        return result;
    }

    private static final class BatchCache {
        private final Map<String, Optional<CompanySearchResult>> companies = new HashMap<>();
        private final Map<Long, Optional<CustomerManagerResult>> managers = new HashMap<>();
        private int companyHits;
        private int companyMisses;
        private int managerHits;
        private int managerMisses;
    }
}
