package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * CO-501 第一步：调 CRM {@code GET /customer-chance/check-tender-subject} 校验招标主体。
 *
 * <p>校验「标讯的招标主体」是否属于「商机所在的集团或其子公司」：
 * <ul>
 *   <li>{@code code="0"} → 通过，{@code data} 返回招标主体 ID（落库为 purchaserId）</li>
 *   <li>{@code code="1"} → 不通过，按 {@code msg} 字段区分两种子错误：
 *     <ul>
 *       <li>msg 含"不存在" → 招标主体不存在 CRM 系统</li>
 *       <li>msg 含"不属于"或"集团" → 不属于商机集团/子公司</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>副作用层：取 token、调 HTTP、401 刷新重试一次、解析响应。业务失败作为 {@link CheckResult}
 * 返回，仅 CRM 不可用（网络异常/token 失败）抛 {@link BusinessException}。
 *
 * <p>参考 {@link CrmChanceService#doPageList} 的 token + 401 重试模板。
 */
@Service
public class CrmTenderSubjectChecker {

    private static final Logger log = LoggerFactory.getLogger(CrmTenderSubjectChecker.class);

    /** CO-501 原文文案：招标主体不存在 CRM 系统。 */
    public static final String MSG_NOT_IN_CRM = "招标主体不存在CRM系统，请在CRM系统创建客户！";
    /** CO-501 原文文案：不属于商机集团/子公司。 */
    public static final String MSG_NOT_IN_GROUP = "当前标讯的招标主体不属于商机所属集团或其子公司，请重新选择匹配的商机！";
    /** CO-501 兜底文案：CRM 返回 code=1 但 msg 未匹配已知模式（联调时完善匹配规则）。 */
    public static final String MSG_UNKNOWN_REJECTION = "招标主体校验未通过，请联系管理员核对或稍后重试";
    private static final String MSG_CRM_UNAVAILABLE = "招标主体校验服务暂不可用，请稍后重试";
    private static final String MSG_DATA_MISSING = "CRM 校验通过但未返回招标主体ID，请联系 CRM 管理员";

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmTenderSubjectChecker(CrmHttpClient httpClient, CrmAuthService authService,
                                   CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 调 CRM 校验招标主体。
     *
     * @param tenderSubject 标讯的招标主体名（{@code tender.purchaserName}）
     * @param ccCode        商机编号（{@code crmOpportunityId}，CC... 格式）
     * @param username      当前登录用户名（CO-152 按用户维度取 token，null 时回退共享）
     * @return 校验结果（含通过/不通过、purchaserId、错误文案）
     * @throws BusinessException 仅在 CRM 不可用时抛
     */
    public CheckResult check(String tenderSubject, String ccCode, String username) {
        String token = acquireTokenOrThrow(username);
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String fullPath = buildPath(tenderSubject, ccCode);
        log.info("CO-501 调试: 准备调用 CRM check-tender-subject, baseUrl={}, path={}, tenderSubject={}, ccCode={}",
                baseUrl, fullPath, tenderSubject, ccCode);

        CrmResponseHandler.CrmApiResponse response = httpClient.get(baseUrl, fullPath, token);
        // 调试日志：打印 CRM 完整原始响应，用于联调时确认招标主体 ID 的真实字段名
        log.info("CO-501 调试: CRM check-tender-subject 原始响应 code={}, msg={}, data={}, dataClass={}",
                response.code(), response.msg(),
                response.data(),
                response.data() != null ? response.data().getNodeType() : "null");

        // 401 → 刷 token 重试一次（参考 CrmChanceService.doPageList 行 151-161）
        if (response.isUnauthorized()) {
            authService.handleUnauthorizedForUser(username);
            token = acquireTokenOrThrow(username);
            response = httpClient.get(baseUrl, fullPath, token);
        }

        return interpret(response, tenderSubject, ccCode);
    }

    private String acquireTokenOrThrow(String username) {
        try {
            return authService.getValidTokenForUser(username);
        } catch (IllegalStateException e) {
            log.warn("CO-501: CRM token acquisition failed: {}", e.getMessage());
            throw new BusinessException(503, MSG_CRM_UNAVAILABLE);
        }
    }

    /**
     * 构造请求路径（含原始未编码 query string）。
     *
     * <p>CO-501 根因修复：原来用 {@code UriComponentsBuilder.build().encode().toUriString()}
     * 预先编码中文参数，生成 {@code tenderSubject=%E5%AE%89...}，
     * 但 {@code CrmHttpClient.get()} 把它传给 {@code restTemplate.exchange(url, ...)} 时，
     * RestTemplate 会<b>再次编码</b>（{@code %}→{@code %25}），
     * 导致 CRM 收到 {@code %25E5%25AE...}（乱码）→ 客户库查不到 → 返回"招标主体不存在"。
     *
     * <p>修复：返回<b>未编码</b>的 path + 原始中文参数，由 RestTemplate 负责一次性编码。
     * 不用 UriComponentsBuilder（它会预编码），直接手工拼接 query string。
     */
    private String buildPath(String tenderSubject, String ccCode) {
        String path = properties.getChance().getCheckTenderSubjectPath();
        return path + "?tenderSubject=" + tenderSubject + "&ccCode=" + ccCode;
    }

    /**
     * 解析 CRM 响应为 {@link CheckResult}。
     *
     * <p>CRM {@code code} 是 string（"0"/"1"），{@link CrmResponseHandler#parse} 用 {@code asInt(-1)}
     * 转换，"0"→0、"1"→1，与现有所有 CRM 接口判定逻辑一致。
     */
    private CheckResult interpret(CrmResponseHandler.CrmApiResponse response, String tenderSubject, String ccCode) {
        int code = response.code();

        // CRM 网络异常/解析失败
        if (code < 0) {
            log.warn("CO-501: CRM check-tender-subject unavailable, tenderSubject={}, ccCode={}, msg={}",
                    tenderSubject, ccCode, response.msg());
            throw new BusinessException(503, MSG_CRM_UNAVAILABLE);
        }

        // 通过：code=0，data 应返回招标主体 ID
        if (code == 0) {
            long purchaserId = response.data() != null ? response.data().asLong(0L) : 0L;
            if (purchaserId <= 0) {
                // code=0 但 data 缺失：CRM 异常情况，不落库
                log.error("CO-501: CRM returned code=0 but data missing/invalid, tenderSubject={}, ccCode={}, body={}",
                        tenderSubject, ccCode, response.data());
                throw new BusinessException(503, MSG_DATA_MISSING);
            }
            log.info("CO-501: check-tender-subject passed, tenderSubject={}, ccCode={}, purchaserId={}",
                    tenderSubject, ccCode, purchaserId);
            return CheckResult.passed(purchaserId);
        }

        // 不通过：code=1，按 msg 区分两种子错误
        if (code == 1) {
            String msg = response.msg() == null ? "" : response.msg();
            log.info("CO-501: check-tender-subject rejected, tenderSubject={}, ccCode={}, msg={}",
                    tenderSubject, ccCode, msg);
            if (msg.contains("不存在")) {
                return CheckResult.rejected(ErrorCode.NOT_IN_CRM, MSG_NOT_IN_CRM);
            }
            if (msg.contains("不属于") || msg.contains("集团")) {
                return CheckResult.rejected(ErrorCode.NOT_IN_GROUP, MSG_NOT_IN_GROUP);
            }
            // msg 不匹配已知模式：联调时需补充（lessons §12：用 CRM 源真相校验）
            log.warn("CO-501: unrecognized CRM rejection msg='{}', falling back to UNKNOWN", msg);
            return CheckResult.rejected(ErrorCode.UNKNOWN, MSG_UNKNOWN_REJECTION);
        }

        // 其他 code：CRM 异常
        log.warn("CO-501: unexpected CRM code={}, msg={}", code, response.msg());
        throw new BusinessException(503, MSG_CRM_UNAVAILABLE);
    }

    /** CRM 校验错误码（用于调用方区分错误来源，不直接暴露给前端）。 */
    public enum ErrorCode {
        /** 招标主体不存在 CRM 系统。 */
        NOT_IN_CRM,
        /** 招标主体不属于商机集团/子公司。 */
        NOT_IN_GROUP,
        /** 未知错误（msg 不匹配已知模式，联调时完善）。 */
        UNKNOWN
    }

    /**
     * 校验结果。
     *
     * <ul>
     *   <li>{@code passed=true} → {@link #purchaserId} 为 CRM 返回的招标主体 ID</li>
     *   <li>{@code passed=false} → {@link #errorCode} + {@link #errorMessage} 描述失败原因</li>
     * </ul>
     */
    public record CheckResult(boolean passed, long purchaserId, ErrorCode errorCode, String errorMessage) {

        public static CheckResult passed(long purchaserId) {
            return new CheckResult(true, purchaserId, null, null);
        }

        public static CheckResult rejected(ErrorCode errorCode, String errorMessage) {
            return new CheckResult(false, 0L, errorCode, errorMessage);
        }
    }
}
