package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.xiyu.bid.crm.config.CrmProperties;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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

    public CrmChanceService(CrmHttpClient httpClient, CrmAuthService authService,
                            CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 查询 CRM 商机列表（分页）。
     *
     * @param request 分页查询条件
     * @return 分页结果，含商机列表和分页信息；查询失败返回空列表
     */
    public CrmChancePageResult pageList(CustomerChancePageRequest request) {
        return doPageList(request);
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
     * @param request 标讯查询条件
     * @return 合并后的分页结果
     */
    public CrmChancePageResult searchByTender(CustomerChanceSearchByTenderRequest request) {
        int pageSize = Math.max(1, request.pageSize());
        CrmProperties.MatchingStrategy strategy = properties.getMatchingStrategy();
        String tenderer = request.tenderer();
        log.info("CRM searchByTender: tenderer={}, strategy={}", tenderer, strategy);

        if (strategy == CrmProperties.MatchingStrategy.ALL || tenderer == null || tenderer.isBlank()) {
            return doPageList(buildSelectAllRequest(request.pageIndex(), pageSize));
        }

        if (strategy == CrmProperties.MatchingStrategy.GROUP) {
            CrmChancePageResult groupResult = doPageList(
                    buildGroupRequest(tenderer, request.pageIndex(), pageSize));
            if (!groupResult.list().isEmpty()) {
                return groupResult;
            }
            log.info("GROUP strategy returned empty for tenderer={}, fallback to ALL", tenderer);
            return doPageList(buildSelectAllRequest(request.pageIndex(), pageSize));
        }

        // EXACT：先按日期精确匹配，再兜底 GROUP，最后 ALL
        List<LocalDate> targetDates = parseTargetDates(request.registrationDeadline(), request.bidOpeningTime());
        if (!targetDates.isEmpty()) {
            Map<Long, CustomerChanceVO> merged = new LinkedHashMap<>();
            for (LocalDate targetDate : targetDates) {
                CrmChancePageResult result = doPageList(
                        buildExactDateRequest(tenderer, targetDate, request.pageIndex(), pageSize));
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
                buildGroupRequest(tenderer, request.pageIndex(), pageSize));
        if (!groupResult.list().isEmpty()) {
            return groupResult;
        }
        log.info("GROUP fallback returned empty for tenderer={}, fallback to ALL", tenderer);
        return doPageList(buildSelectAllRequest(request.pageIndex(), pageSize));
    }

    private CrmChancePageResult doPageList(CustomerChancePageRequest request) {
        String token;
        try {
            token = authService.getValidToken();
        } catch (IllegalStateException e) {
            log.warn("CRM page-list skipped because token acquisition failed: {}", e.getMessage());
            return emptyPageResult();
        }
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getPageListPath();
        return doPageList(token, baseUrl, path, request);
    }

    private CrmChancePageResult doPageList(String token, String baseUrl, String path,
                                           CustomerChancePageRequest request) {
        log.info("CRM page-list request: baseUrl={}, path={}, body={}", baseUrl, path, request);
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, request);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            try {
                token = authService.getValidToken();
            } catch (IllegalStateException e) {
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

    private List<LocalDate> parseTargetDates(String registrationDeadline, String bidOpeningTime) {
        List<LocalDate> dates = new ArrayList<>();
        parseDate(registrationDeadline).ifPresent(dates::add);
        parseDate(bidOpeningTime).ifPresent(dates::add);
        return dates.stream().distinct().collect(Collectors.toList());
    }

    private java.util.Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        String trimmed = value.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ISO_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == DateTimeFormatter.ISO_LOCAL_DATE ||
                        (trimmed.length() <= 10 && !trimmed.contains("T"))) {
                    return java.util.Optional.of(LocalDate.parse(trimmed, formatter));
                }
                return java.util.Optional.of(LocalDateTime.parse(trimmed, formatter).toLocalDate());
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        log.warn("Unable to parse date value: {}", value);
        return java.util.Optional.empty();
    }

    private CustomerChancePageRequest buildExactDateRequest(String tenderer, LocalDate targetDate,
                                                            int pageIndex, int pageSize) {
        String start = targetDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = targetDate.atTime(23, 59, 59).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        CustomerChanceDTO body = new CustomerChanceDTO(
                List.of(tenderer), null, null, null, null, null, null,
                start, end, null, null, null, null, null, null, null, null);
        return new CustomerChancePageRequest(pageIndex, pageSize, body);
    }

    private CustomerChancePageRequest buildGroupRequest(String tenderer, int pageIndex, int pageSize) {
        CustomerChanceDTO body = new CustomerChanceDTO(
                List.of(tenderer), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        return new CustomerChancePageRequest(pageIndex, pageSize, body);
    }

    private CustomerChancePageRequest buildSelectAllRequest(int pageIndex, int pageSize) {
        CustomerChanceDTO body = new CustomerChanceDTO(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, true, null, null, null);
        return new CustomerChancePageRequest(pageIndex, pageSize, body);
    }

    /**
     * 回传标讯状态到 CRM。
     *
     * @param bidInfoSync 标讯回传请求
     * @return true 回传成功，false 回传失败
     */
    public boolean bidInfoSync(BidInfoSyncDTO bidInfoSync) {
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getBidInfoSyncPath();
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, bidInfoSync);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
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

    /**
     * 按客户名（groupName）查询 CRM 商机项目负责人。
     * <p>用于标讯自动分配：根据标讯的招标主体（purchaserName）作为 groupName 查询 CRM 商机，
     * 取出第一条商机的项目负责人信息。
     * <p>降级策略：查询失败或未找到返回 null，由调用方决定后续行为。
     *
     * @param groupName 客户名（对应标讯的 purchaserName）
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到
     */
}
