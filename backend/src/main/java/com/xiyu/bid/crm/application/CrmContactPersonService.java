package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonInfoVO;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonListDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * CRM 对接人查询应用服务。
 * <p>职责：按商机 ID 查询 CRM 对接联系人。
 * <p>副作用层：负责取 Token、调用 HTTP、解析响应、异常处理。
 */
@Service
public class CrmContactPersonService {

    private static final Logger log = LoggerFactory.getLogger(CrmContactPersonService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmContactPersonService(CrmHttpClient httpClient, CrmAuthService authService,
                                   CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 按商机 ID 查询对接人列表。
     *
     * @param ccId 商机 ID
     * @return 对接人列表；查询失败返回空列表
     */
    public List<ContactPersonInfoVO> pageList(Long ccId) {
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveContactPersonBaseUrl();
        String path = properties.getContactPerson().getPageListPath();
        ContactPersonListDTO body = new ContactPersonListDTO(ccId);
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, body);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
            response = httpClient.post(baseUrl, path, token, body);
        }

        if (response.data() == null) {
            log.warn("CRM contact-person page-list returned no data: code={}, msg={}", response.code(), response.msg());
            return Collections.emptyList();
        }
        // 放宽 success 判断：部分 CRM 环境（含客户现场）对接人接口成功返回时 code 不为 0，
        // 死卡 code==0 会把已返回的对接人误判失败、客户信息矩阵带不过来（CO-329 遗留）。
        // 改为：只要 data 能解析出对接人就返回；解析为空且 code 异常时才告警。
        List<ContactPersonInfoVO> contacts = parseListResponse(response.data());
        if (contacts.isEmpty() && !response.success()) {
            log.warn("CRM contact-person page-list failed and no contacts parsed: code={}, msg={}", response.code(), response.msg());
        }
        return contacts;
    }

    private List<ContactPersonInfoVO> parseListResponse(JsonNode data) {
        try {
            // CRM 对接人 page-list 响应结构因环境/版本而异：文档契约为 {code,msg,data:[...]}，
            // 实测存在 {code,totalCount,dataList:[...]}、{code,list/rows:[...]}、{code,data:{list:[...]}}
            // 以及成功时 code 非 0 等多种形态。逐个兼容常见数组字段，避免把已返回的对接人解析成空。
            ResolvedArray resolved = resolveArrayNode(data);
            if (resolved == null || !resolved.node().isArray() || resolved.node().size() == 0) {
                log.warn("CRM contact-person response has no recognizable contact array (tried data/dataList/list/rows/records/nested); node={}",
                        summarize(data));
                return Collections.emptyList();
            }
            log.info("CRM contact-person parsed {} entries from field '{}'", resolved.node().size(), resolved.path());
            // CO-431 诊断日志：打印每条对接人的 position 原始值（从 JsonNode 取，绕过 non_null 序列化）。
            // 用于确诊 CRM 返回的 position 到底是数字 '1'~'14' 还是中文职位名 / 缺失。
            // 不改业务逻辑，仅诊断；部署后关联一次有问题的商机即可从日志确诊 position 真实格式。
            for (JsonNode c : resolved.node()) {
                String id = c.path("id").asText("");
                String name = c.path("name").asText("");
                JsonNode posNode = c.get("position");
                String position = posNode != null && !posNode.isNull() ? posNode.asText("") : "<missing>";
                log.info("CRM contact-person raw position: id={}, name={}, position={}", id, name, position);
            }
            String jsonArray = MAPPER.writeValueAsString(resolved.node());
            CollectionType collectionType = MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, ContactPersonInfoVO.class);
            return MAPPER.readValue(jsonArray, collectionType);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Failed to parse CRM contact-person response", e);
            return Collections.emptyList();
        }
    }

    /**
     * 在响应节点中定位对接人数组，兼容多种字段命名与嵌套形态。
     * 返回命中的数组节点及其字段路径（用于诊断日志）；找不到返回 null。
     */
    private ResolvedArray resolveArrayNode(JsonNode data) {
        if (data.isArray()) return new ResolvedArray(data, "<root>");
        for (String field : List.of("dataList", "list", "rows", "records")) {
            JsonNode n = data.path(field);
            if (n.isArray()) return new ResolvedArray(n, field);
        }
        JsonNode inner = data.path("data");
        if (inner.isObject()) {
            for (String field : List.of("list", "dataList", "rows", "records")) {
                JsonNode n = inner.path(field);
                if (n.isArray()) return new ResolvedArray(n, "data." + field);
            }
        }
        return null;
    }

    /** 节点概要（截断），仅用于告警日志，避免打印超长响应体。 */
    private static String summarize(JsonNode data) {
        String s = data != null ? data.toString() : "null";
        return s.length() > 300 ? s.substring(0, 300) + "...(truncated)" : s;
    }

    private record ResolvedArray(JsonNode node, String path) {}
}
