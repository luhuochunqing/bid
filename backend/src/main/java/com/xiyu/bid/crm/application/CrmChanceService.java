package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.xiyu.bid.crm.config.CrmProperties;
// TokenUnavailableException same package
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceSearchByTenderRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRM 商机查询与标讯回传应用服务。
 * <p>职责：
 * <ul>
 *   <li>商机列表查询（代理客户 POST /customer-chance/page-list）</li>
 *   <li>标讯回传（代理客户 POST /customer-chance/bidInfoSync）</li>
 * </ul>
 * <p>副作用层：负责取 Token、调用 HTTP、解析响应、异常处理。
 */
@Service
public class CrmChanceService {

    private static final Logger log = LoggerFactory.getLogger(CrmChanceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;
    private final CrmChanceTenderMatcher tenderMatcher;

    public CrmChanceService(CrmHttpClient httpClient, CrmAuthService authService,
                            CrmProperties properties, CrmChanceTenderMatcher tenderMatcher) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
        this.tenderMatcher = tenderMatcher;
    }

    /**
     * 查询 CRM 商机列表（分页）。username 为空时 TokenUnavailable → 空结果（无全局 03595）。
     */
    public CrmChancePageResult pageList(CustomerChancePageRequest request, String username) {
        return doPageList(request, username);
    }

    /**
     * 按商机编号（code）查询第一条匹配商机，统一收敛"按 code 查询"样板代码。
     * code 为空、未找到或查询失败时返回 null。
     */
    public CustomerChanceVO findByCode(String code, String username) {
        if (code == null || code.isBlank()) {
            return null;
        }
        CustomerChanceDTO body = new CustomerChanceDTO(
                null, null, code, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        CustomerChancePageRequest request = new CustomerChancePageRequest(1, 1, body);
        CrmChancePageResult result = doPageList(request, username);
        return result.list().isEmpty() ? null : result.list().get(0);
    }

    /**
     * 按 CRM 标讯 ID（bidId）反查商机。
     * <p>spec 037：CRM 推送标讯时 external_id=CRM:{bidId}，bidId 是 CRM 标讯 ID
     * （非商机主键 id）。通过 page-list 接口按 bidId 查询，返回关联的商机。
     * <p>降级策略：bidId null / username null / 接口异常 → 返回 null。
     * 若返回多条商机，取第一条并 log.warn（理论上 bidId 唯一对应一条商机）。
     *
     * @param bidId    CRM 标讯 ID
     * @param username 当前操作用户 username（用于获取 CRM token）
     * @return 商机 VO；null 表示查询失败或未找到
     */
    public CustomerChanceVO findByBidId(Long bidId, String username) {
        if (bidId == null) {
            return null;
        }
        CustomerChanceDTO body = new CustomerChanceDTO(
                null, null, null, bidId, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        CustomerChancePageRequest request = new CustomerChancePageRequest(1, 10, body);
        try {
            CrmChancePageResult result = doPageList(request, username);
            if (result.list().isEmpty()) {
                log.warn("findByBidId: no opportunity found for bidId={}", bidId);
                return null;
            }
            if (result.list().size() > 1) {
                log.warn("findByBidId: bidId={} returned {} opportunities, taking first",
                        bidId, result.list().size());
            }
            CustomerChanceVO first = result.list().get(0);
            // 本地校验：若返回的商机 bidId 与查询的 bidId 不匹配，可能是 CRM 不支持 bidId 查询
            if (first.bidId() != null && !bidId.equals(first.bidId())) {
                log.warn("findByBidId: bidId mismatch, queried={} but returned={}, CRM may not support bidId query",
                        bidId, first.bidId());
                return null;
            }
            return first;
        } catch (RuntimeException e) {
            log.warn("findByBidId: failed for bidId={}: {}", bidId, e.getMessage());
            return null;
        }
    }

    /**
     * 按标讯信息查询 CRM 商机（产品蓝图匹配规则，含可配置兜底）。
     * <p>策略由 {@link CrmProperties#getMatchingStrategy()} 控制：
     * <ul>
     *   <li>{@code EXACT}：先按招标主体 + 报名截止/开标时间精确匹配 evaluationTime；
     *       若为空，依次兜底 groupName、全量。</li>
     *   <li>{@code GROUP}：按招标主体（groupName）匹配；若为空，兜底全量。</li>
     *   <li>{@code ALL}：直接拉取全量商机。</li>
     * </ul>
     *
     * @param request  标讯查询条件
     * @param username 当前登录用户名（CO-152）
     * @return 合并后的分页结果
     */
    public CrmChancePageResult searchByTender(CustomerChanceSearchByTenderRequest request, String username) {
        int pageSize = Math.max(1, request.pageSize());
        CrmProperties.MatchingStrategy strategy = properties.getMatchingStrategy();
        String tenderer = request.tenderer();
        log.info("CRM searchByTender: tenderer={}, strategy={}", tenderer, strategy);

        if (strategy == CrmProperties.MatchingStrategy.ALL || tenderer == null || tenderer.isBlank()) {
            return doPageList(tenderMatcher.buildSelectAllRequest(request.pageIndex(), pageSize), username);
        }

        if (strategy == CrmProperties.MatchingStrategy.GROUP) {
            CrmChancePageResult groupResult = doPageList(
                    tenderMatcher.buildGroupRequest(tenderer, request.pageIndex(), pageSize), username);
            if (!groupResult.list().isEmpty()) {
                return groupResult;
            }
            log.info("GROUP strategy returned empty for tenderer={}, fallback to ALL", tenderer);
            return doPageList(tenderMatcher.buildSelectAllRequest(request.pageIndex(), pageSize), username);
        }

        // EXACT：先按日期精确匹配，再兜底 GROUP，最后 ALL
        List<LocalDate> targetDates = tenderMatcher.parseTargetDates(request.registrationDeadline(), request.bidOpeningTime());
        if (!targetDates.isEmpty()) {
            Map<Long, CustomerChanceVO> merged = new LinkedHashMap<>();
            for (LocalDate targetDate : targetDates) {
                CrmChancePageResult result = doPageList(
                        tenderMatcher.buildExactDateRequest(tenderer, targetDate, request.pageIndex(), pageSize), username);
                for (CustomerChanceVO vo : result.list()) {
                    merged.putIfAbsent(vo.id(), vo);
                }
            }
            if (!merged.isEmpty()) {
                List<CustomerChanceVO> list = merged.values().stream()
                        .sorted(Comparator.comparing(CustomerChanceVO::id))
                        .collect(Collectors.toList());
                return new CrmChancePageResult(list, list.size(), pageSize, request.pageIndex());
            }
            log.info("EXACT strategy returned empty for tenderer={}, fallback to GROUP", tenderer);
        } else {
            log.info("EXACT strategy: no valid dates for tenderer={}, fallback to GROUP", tenderer);
        }

        CrmChancePageResult groupResult = doPageList(
                tenderMatcher.buildGroupRequest(tenderer, request.pageIndex(), pageSize), username);
        if (!groupResult.list().isEmpty()) {
            return groupResult;
        }
        log.info("GROUP fallback returned empty for tenderer={}, fallback to ALL", tenderer);
        return doPageList(tenderMatcher.buildSelectAllRequest(request.pageIndex(), pageSize), username);
    }

    private CrmChancePageResult doPageList(CustomerChancePageRequest request, String username) {
        String token;
        try {
            token = authService.getValidTokenForUser(username);
        } catch (IllegalStateException | TokenUnavailableException e) {
            log.warn("CRM page-list skipped because token acquisition failed: {}", e.getMessage());
            return emptyPageResult();
        }
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getPageListPath();
        return doPageList(token, baseUrl, path, request, username);
    }

    private CrmChancePageResult doPageList(String token, String baseUrl, String path,
                                           CustomerChancePageRequest request, String username) {
        log.info("CRM page-list request: baseUrl={}, path={}, body={}", baseUrl, path, request);
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, request);

        if (response.isUnauthorized()) {
            authService.handleUnauthorizedForUser(username);
            try {
                token = authService.getValidTokenForUser(username);
            } catch (IllegalStateException | TokenUnavailableException e) {
                log.warn("CRM chance page-list skipped because token refresh failed after unauthorized: {}",
                        e.getMessage());
                return emptyPageResult();
            }
            response = httpClient.post(baseUrl, path, token, request);
        }

        if (!response.success() || response.data() == null) {
            log.warn("CRM chance page-list failed: code={}, msg={}", response.code(), response.msg());
            return emptyPageResult();
        }
        return parsePageResponse(response.data());
    }

    /**
     * 回传标讯状态到 CRM。
     *
     * @param bidInfoSync 标讯回传请求
     * @param username    当前登录用户名（CO-152）
     * @return true 回传成功，false 回传失败
     */
    public boolean bidInfoSync(BidInfoSyncDTO bidInfoSync, String username) {
        String token = authService.getValidTokenForUser(username);
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getBidInfoSyncPath();
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, bidInfoSync);

        if (response.isUnauthorized()) {
            authService.handleUnauthorizedForUser(username);
            token = authService.getValidTokenForUser(username);
            response = httpClient.post(baseUrl, path, token, bidInfoSync);
        }

        if (!response.success()) {
            log.warn("CRM bidInfoSync failed: code={}, msg={}", response.code(), response.msg());
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private CrmChancePageResult parsePageResponse(JsonNode data) {
        try {
            int totalCount = data.path("totalCount").asInt(0);
            int pageSize = data.path("pageSize").asInt(0);
            int pageIndex = data.path("pageIndex").asInt(1);
            JsonNode dataListNode = data.path("dataList");

            List<CustomerChanceVO> list;
            if (dataListNode.isArray() && dataListNode.size() > 0) {
                String jsonArray = MAPPER.writeValueAsString(dataListNode);
                CollectionType collectionType = MAPPER.getTypeFactory()
                        .constructCollectionType(List.class, CustomerChanceVO.class);
                list = MAPPER.readValue(jsonArray, collectionType);
            } else {
                list = Collections.emptyList();
            }
            return new CrmChancePageResult(list, totalCount, pageSize, pageIndex);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Failed to parse CRM chance page response", e);
            return emptyPageResult();
        }
    }

    private CrmChancePageResult emptyPageResult() {
        return new CrmChancePageResult(Collections.emptyList(), 0, 0, 0);
    }
}
