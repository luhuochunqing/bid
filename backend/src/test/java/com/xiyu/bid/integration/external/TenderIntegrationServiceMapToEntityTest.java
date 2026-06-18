package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link TenderIntegrationService#mapToEntity} 的单元测试。
 *
 * <p>验证 CRM 转入与第三方平台转入的来源/状态区分逻辑：
 * <ul>
 *   <li>带 crmId → sourceType=CRM_OPPORTUNITY, source="CRM 商机", status=EVALUATED</li>
 *   <li>不带 crmId → sourceType=EXTERNAL_PLATFORM, source="第三方平台", status=PENDING_ASSIGNMENT</li>
 *   <li>crmId 为空字符串 → 视为非 CRM 转入</li>
 *   <li>crmId 为空白字符 → 视为非 CRM 转入</li>
 * </ul>
 *
 * <p>用反射调用 private mapToEntity，聚焦测试来源/状态判断逻辑，
 * 不依赖 JPA 仓储。
 */
class TenderIntegrationServiceMapToEntityTest {

    private TenderIntegrationService service;

    @BeforeEach
    void setUp() {
        // mapToEntity 不使用任何依赖，传 mock 即可
        service = new TenderIntegrationService(
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock()
        );
    }

    private Tender invokeMapToEntity(TenderPushRequest request) throws Exception {
        Method method = TenderIntegrationService.class.getDeclaredMethod("mapToEntity", TenderPushRequest.class);
        method.setAccessible(true);
        return (Tender) method.invoke(service, request);
    }

    private TenderPushRequest baseRequest() {
        TenderPushRequest r = new TenderPushRequest();
        r.setTitle("测试标讯");
        return r;
    }

    @Test
    @DisplayName("带 crmId → 来源=CRM 商机，状态=已评估")
    void mapToEntity_withCrmId_setsCrmSourceAndEvaluatedStatus() throws Exception {
        TenderPushRequest r = baseRequest();
        r.setCrmId("CC001");

        Tender t = invokeMapToEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.CRM_OPPORTUNITY);
        assertThat(t.getSource()).isEqualTo("CRM 商机");
        assertThat(t.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("不带 crmId → 来源=第三方平台，状态=待分配")
    void mapToEntity_withoutCrmId_setsExternalSourceAndPendingStatus() throws Exception {
        TenderPushRequest r = baseRequest();
        // crmId 保持 null

        Tender t = invokeMapToEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        assertThat(t.getSource()).isEqualTo("第三方平台");
        assertThat(t.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    @Test
    @DisplayName("crmId 为空字符串 → 视为非 CRM 转入")
    void mapToEntity_emptyCrmId_treatedAsNonCrm() throws Exception {
        TenderPushRequest r = baseRequest();
        r.setCrmId("");

        Tender t = invokeMapToEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        assertThat(t.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    @Test
    @DisplayName("crmId 为空白字符 → 视为非 CRM 转入")
    void mapToEntity_blankCrmId_treatedAsNonCrm() throws Exception {
        TenderPushRequest r = baseRequest();
        r.setCrmId("   ");

        Tender t = invokeMapToEntity(r);

        assertThat(t.getSourceType()).isEqualTo(Tender.SourceType.EXTERNAL_PLATFORM);
        assertThat(t.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }
}
