package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import com.xiyu.bid.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-501: CrmTenderSubjectChecker 单测。
 *
 * <p>覆盖 CRM {@code check-tender-subject} 接口调用的所有分支：
 * <ul>
 *   <li>code=0 + data>0 → 通过，返回 purchaserId</li>
 *   <li>code=0 + data=0 → 异常情况（CRM 返回通过但无 ID）</li>
 *   <li>code=1 + msg 含"不存在" → NOT_IN_CRM</li>
 *   <li>code=1 + msg 含"不属于"/"集团" → NOT_IN_GROUP</li>
 *   <li>code=1 + msg 未知 → UNKNOWN（兜底为 NOT_IN_GROUP 文案）</li>
 *   <li>401 → 刷 token 重试一次</li>
 *   <li>token 获取失败 → 抛 BusinessException(503)</li>
 *   <li>code=-1（网络异常）→ 抛 BusinessException(503)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CrmTenderSubjectCheckerTest {

    @Mock private CrmHttpClient httpClient;
    @Mock private CrmAuthService authService;
    @Mock private CrmProperties properties;
    @Mock private CrmChanceService crmChanceService;

    private CrmTenderSubjectChecker checker;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        checker = new CrmTenderSubjectChecker(httpClient, authService, properties, crmChanceService);
        // lenient：code=-1 等用例不会触达这些 stub
        lenient().when(authService.getValidTokenForUser(anyString())).thenReturn("fake-token");
        lenient().when(properties.getEffectiveChanceBaseUrl()).thenReturn("https://chance-test.ehsy.com");
        lenient().when(properties.getChance()).thenReturn(new CrmProperties.CrmChancePaths());
    }

    @Test
    @DisplayName("code=0 + data=12345 → 通过，返回 purchaserId=12345")
    void check_whenCode0AndValidData_shouldPass() throws Exception {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 0, "msg": "success", "data": 12345}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);

        CrmTenderSubjectChecker.CheckResult result = checker.check("山东海化集团", "CC20260707001", "alice");

        assertThat(result.passed()).isTrue();
        assertThat(result.purchaserId()).isEqualTo(12345L);
        assertThat(result.errorCode()).isNull();
    }

    @Test
    @DisplayName("code=0 + data=0 → 抛 BusinessException（CRM 异常：通过但未返回 ID）")
    void check_whenCode0ButDataIsZero_shouldThrow() {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 0, "msg": "success", "data": 0}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);

        assertThatThrownBy(() -> checker.check("山东海化集团", "CC20260707001", "alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CRM 校验通过但未返回招标主体ID");
    }

    @Test
    @DisplayName("code=1 + msg 含\"不存在\" + fallback detail 返回 null → NOT_IN_CRM + CO-501 文案")
    void check_whenCode1AndMsgContainsNotExists_andFallbackNull_shouldReturnNotInCrm() {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 1, "msg": "招标主体不存在", "data": 0}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);
        when(crmChanceService.findByCode(eq("CC20260707001"), eq("alice"))).thenReturn(null);

        CrmTenderSubjectChecker.CheckResult result = checker.check("山东海化集团", "CC20260707001", "alice");

        assertThat(result.passed()).isFalse();
        assertThat(result.errorCode()).isEqualTo(CrmTenderSubjectChecker.ErrorCode.NOT_IN_CRM);
        assertThat(result.errorMessage()).isEqualTo("招标主体不存在CRM系统，请在CRM系统创建客户！");
    }

    @Test
    @DisplayName("code=1 \"不存在\" + fallback detail tenderSubject 匹配 + tenderSubjectId>0 → fallback 通过")
    void check_whenNotInCrm_butDetailTenderSubjectMatches_shouldFallbackPass() throws Exception {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 1, "msg": "招标主体不存在CRM系统", "data": 0}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);
        // detail 接口返回的商机中 tenderSubject 与传入一致，tenderSubjectId>0
        CustomerChanceVO chance = MAPPER.readValue("""
            {"id":21299,"code":"CC20260707001","name":"测试商机",
             "groupName":"安徽古井贡酒股份有限公司","groupId":8083699,
             "tenderSubject":"安徽古井贡酒股份有限公司","tenderSubjectId":8083699}
            """, CustomerChanceVO.class);
        when(crmChanceService.findByCode(eq("CC20260707001"), eq("alice"))).thenReturn(chance);

        CrmTenderSubjectChecker.CheckResult result = checker.check("安徽古井贡酒股份有限公司", "CC20260707001", "alice");

        assertThat(result.passed()).isTrue();
        assertThat(result.purchaserId()).isEqualTo(8083699L);
    }

    @Test
    @DisplayName("code=1 \"不存在\" + fallback detail tenderSubject 不匹配 → 仍返回 NOT_IN_CRM")
    void check_whenNotInCrm_andDetailTenderSubjectMismatch_shouldReturnNotInCrm() throws Exception {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 1, "msg": "招标主体不存在CRM系统", "data": 0}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);
        // detail 返回的 tenderSubject 与传入不一致
        CustomerChanceVO chance = MAPPER.readValue("""
            {"id":21299,"code":"CC20260707001","name":"测试商机",
             "groupName":"另一个集团","groupId":999,
             "tenderSubject":"另一个招标主体","tenderSubjectId":999}
            """, CustomerChanceVO.class);
        when(crmChanceService.findByCode(eq("CC20260707001"), eq("alice"))).thenReturn(chance);

        CrmTenderSubjectChecker.CheckResult result = checker.check("安徽古井贡酒股份有限公司", "CC20260707001", "alice");

        assertThat(result.passed()).isFalse();
        assertThat(result.errorCode()).isEqualTo(CrmTenderSubjectChecker.ErrorCode.NOT_IN_CRM);
    }

    @Test
    @DisplayName("code=1 \"不存在\" + fallback detail tenderSubject 匹配但 tenderSubjectId=0 → 仍返回 NOT_IN_CRM")
    void check_whenNotInCrm_andDetailTenderSubjectIdZero_shouldReturnNotInCrm() throws Exception {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 1, "msg": "招标主体不存在CRM系统", "data": 0}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);
        // tenderSubjectId=0，不能作为有效 purchaserId
        CustomerChanceVO chance = MAPPER.readValue("""
            {"id":21299,"code":"CC20260707001","name":"测试商机",
             "groupName":"安徽古井贡酒股份有限公司","groupId":8083699,
             "tenderSubject":"安徽古井贡酒股份有限公司","tenderSubjectId":0}
            """, CustomerChanceVO.class);
        when(crmChanceService.findByCode(eq("CC20260707001"), eq("alice"))).thenReturn(chance);

        CrmTenderSubjectChecker.CheckResult result = checker.check("安徽古井贡酒股份有限公司", "CC20260707001", "alice");

        assertThat(result.passed()).isFalse();
        assertThat(result.errorCode()).isEqualTo(CrmTenderSubjectChecker.ErrorCode.NOT_IN_CRM);
    }

    @Test
    @DisplayName("code=1 \"不存在\" + fallback detail 抛异常 → 仍返回 NOT_IN_CRM（fallback 异常不阻塞主流程）")
    void check_whenNotInCrm_andDetailThrowsException_shouldReturnNotInCrm() {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 1, "msg": "招标主体不存在CRM系统", "data": 0}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);
        when(crmChanceService.findByCode(eq("CC20260707001"), eq("alice")))
                .thenThrow(new RuntimeException("CRM detail network error"));

        CrmTenderSubjectChecker.CheckResult result = checker.check("安徽古井贡酒股份有限公司", "CC20260707001", "alice");

        assertThat(result.passed()).isFalse();
        assertThat(result.errorCode()).isEqualTo(CrmTenderSubjectChecker.ErrorCode.NOT_IN_CRM);
    }

    @Test
    @DisplayName("code=1 \"不属于集团\" → 不触发 fallback，直接返回 NOT_IN_GROUP")
    void check_whenNotInGroup_shouldNotTriggerFallback() {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 1, "msg": "招标主体不属于该商机集团", "data": 0}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);

        CrmTenderSubjectChecker.CheckResult result = checker.check("山东海化集团", "CC20260707001", "alice");

        assertThat(result.passed()).isFalse();
        assertThat(result.errorCode()).isEqualTo(CrmTenderSubjectChecker.ErrorCode.NOT_IN_GROUP);
        assertThat(result.errorMessage()).contains("不属于商机所属集团");
        // 不应该调 fallback
        verify(crmChanceService, never()).findByCode(anyString(), anyString());
    }

    @Test
    @DisplayName("code=1 + msg 含\"集团\" → NOT_IN_GROUP（关键字匹配）")
    void check_whenCode1AndMsgContainsGroup_shouldReturnNotInGroup() {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 1, "msg": "不属于商机所在集团范围", "data": 0}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);

        CrmTenderSubjectChecker.CheckResult result = checker.check("山东海化集团", "CC20260707001", "alice");

        assertThat(result.errorCode()).isEqualTo(CrmTenderSubjectChecker.ErrorCode.NOT_IN_GROUP);
    }

    @Test
    @DisplayName("code=1 + msg 未匹配已知模式 → UNKNOWN（兜底为通用文案）")
    void check_whenCode1AndUnknownMsg_shouldReturnUnknown() {
        CrmResponseHandler.CrmApiResponse response = parse("""
            {"code": 1, "msg": "未知错误", "data": 0}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(response);

        CrmTenderSubjectChecker.CheckResult result = checker.check("山东海化集团", "CC20260707001", "alice");

        assertThat(result.errorCode()).isEqualTo(CrmTenderSubjectChecker.ErrorCode.UNKNOWN);
        // R3 修复：UNKNOWN 用独立文案，不复用 NOT_IN_GROUP
        assertThat(result.errorMessage()).contains("招标主体校验未通过");
    }

    @Test
    @DisplayName("401 → 刷 token 重试一次，重试成功则返回结果")
    void check_when401ThenRetry_shouldSucceed() throws Exception {
        CrmResponseHandler.CrmApiResponse unauthorized = parse("""
            {"code": 401, "msg": "token expired", "data": null}
            """);
        CrmResponseHandler.CrmApiResponse success = parse("""
            {"code": 0, "msg": "success", "data": 999}
            """);
        when(httpClient.get(anyString(), anyString(), anyString()))
                .thenReturn(unauthorized)
                .thenReturn(success);

        CrmTenderSubjectChecker.CheckResult result = checker.check("山东海化集团", "CC20260707001", "alice");

        assertThat(result.passed()).isTrue();
        assertThat(result.purchaserId()).isEqualTo(999L);
        verify(authService).handleUnauthorizedForUser("alice");
        // 应该有两次 token 获取（首次 + 刷新后）
        verify(authService, times(2)).getValidTokenForUser("alice");
    }

    @Test
    @DisplayName("token 获取失败 → 抛 BusinessException(503)，不发 HTTP 请求")
    void check_whenTokenAcquisitionFails_shouldThrow503() {
        when(authService.getValidTokenForUser("alice"))
                .thenThrow(new IllegalStateException("CRM token cache exhausted"));

        assertThatThrownBy(() -> checker.check("山东海化集团", "CC20260707001", "alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("招标主体校验服务暂不可用");
        verify(httpClient, never()).get(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("code=-1（CRM 网络异常）→ 抛 BusinessException(503)")
    void check_whenCrmNetworkError_shouldThrow503() {
        CrmResponseHandler.CrmApiResponse networkError = CrmResponseHandler.CrmApiResponse.parseError("connection timeout");
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(networkError);

        assertThatThrownBy(() -> checker.check("山东海化集团", "CC20260707001", "alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("招标主体校验服务暂不可用");
    }

    @Test
    @DisplayName("请求路径包含编码后的 tenderSubject 和 ccCode 参数")
    void check_shouldEncodeQueryParams() {
        CrmResponseHandler.CrmApiResponse success = parse("""
            {"code": 0, "msg": "success", "data": 1}
            """);
        when(httpClient.get(anyString(), anyString(), anyString())).thenReturn(success);

        checker.check("山东海化集团", "CC20260707001", "alice");

        // 验证 path 含 check-tender-subject 路径和参数
        verify(httpClient).get(eq("https://chance-test.ehsy.com"),
                org.mockito.ArgumentMatchers.contains("check-tender-subject"),
                eq("fake-token"));
    }

    private static CrmResponseHandler.CrmApiResponse parse(String json) {
        return CrmResponseHandler.parse(json);
    }
}
