package com.xiyu.bid.alerts.domain;

import com.xiyu.bid.alerts.entity.AlertRule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AlertMessagePolicy} 纯核心单元测试。
 *
 * <p>覆盖点：</p>
 * <ul>
 *   <li>9 种 {@link AlertRule.AlertType} 各自的 notificationType 正确</li>
 *   <li>CA_EXPIRY 的 "已过期" vs "即将到期" 分支</li>
 *   <li>relatedId 解析："Project:123" → sourceEntityType/sourceEntityId</li>
 *   <li>relatedId 为 null/空/格式错误时 → 字段为 null</li>
 *   <li>targetUrl 从 extraPayload 取</li>
 *   <li>body 等于传入的 alertMessage</li>
 *   <li>title 中文标题正确</li>
 *   <li>方法不返回 null</li>
 * </ul>
 */
class AlertMessagePolicyTest {

    private static final String SAMPLE_MESSAGE = "项目 XXX 投标截止日期还剩 3 天";
    private static final String RELATED_ID = "Project:123";

    @Nested
    class NotificationType_分支 {
        @Test
        void DEADLINE_应映射为_DEADLINE() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.notificationType()).isEqualTo("DEADLINE");
        }

        @Test
        void RISK_应映射为_SYSTEM() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.RISK, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.notificationType()).isEqualTo("SYSTEM");
        }

        @Test
        void DOCUMENT_应映射为_DOCUMENT_CHANGE() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DOCUMENT, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.notificationType()).isEqualTo("DOCUMENT_CHANGE");
        }

        @Test
        void BUDGET_应映射为_SYSTEM() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.BUDGET, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.notificationType()).isEqualTo("SYSTEM");
        }

        @Test
        void DEPOSIT_RETURN_应映射为_DEADLINE() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEPOSIT_RETURN, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.notificationType()).isEqualTo("DEADLINE");
        }

        @Test
        void PERFORMANCE_EXPIRY_应映射为_DEADLINE() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.PERFORMANCE_EXPIRY, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.notificationType()).isEqualTo("DEADLINE");
        }

        @Test
        void QUALIFICATION_EXPIRY_应映射为_DEADLINE() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.QUALIFICATION_EXPIRY, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.notificationType()).isEqualTo("DEADLINE");
        }

        @Test
        void CA_BORROW_OVERDUE_应映射为_CA_BORROW_OVERDUE() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.CA_BORROW_OVERDUE, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.notificationType()).isEqualTo("CA_BORROW_OVERDUE");
        }
    }

    @Nested
    class CA_EXPIRY_分支 {
        @Test
        void payload中alertSubType为EXPIRED_应映射为_CA_EXPIRED() {
            // P1-10: CA_EXPIRY 子类型由 payload alertSubType 决定，不再依赖消息文案匹配
            Map<String, Object> payload = Map.of("alertSubType", "EXPIRED");
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.CA_EXPIRY, "CA 证书剩余 5 天", RELATED_ID, payload);

            assertThat(info.notificationType()).isEqualTo("CA_EXPIRED");
        }

        @Test
        void payload中alertSubType为EXPIRING_应映射为_CA_EXPIRING() {
            Map<String, Object> payload = Map.of("alertSubType", "EXPIRING");
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.CA_EXPIRY, "CA 证书即将到期", RELATED_ID, payload);

            assertThat(info.notificationType()).isEqualTo("CA_EXPIRING");
        }

        @Test
        void payload无alertSubType_默认映射为_CA_EXPIRING() {
            // 无 alertSubType 时默认为 CA_EXPIRING
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.CA_EXPIRY, "CA 证书剩余 5 天", RELATED_ID, Map.of());

            assertThat(info.notificationType()).isEqualTo("CA_EXPIRING");
        }
    }

    @Nested
    class Title_中文标题 {
        @Test
        void DEADLINE_标题应为_投标截止日期提醒() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.title()).isEqualTo("投标截止日期提醒");
        }

        @Test
        void RISK_标题应为_风险评分提醒() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.RISK, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.title()).isEqualTo("风险评分提醒");
        }

        @Test
        void DOCUMENT_标题应为_文档缺失提醒() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DOCUMENT, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.title()).isEqualTo("文档缺失提醒");
        }

        @Test
        void BUDGET_标题应为_预算告警() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.BUDGET, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.title()).isEqualTo("预算告警");
        }

        @Test
        void DEPOSIT_RETURN_标题应为_保证金退还提醒() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEPOSIT_RETURN, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.title()).isEqualTo("保证金退还提醒");
        }

        @Test
        void PERFORMANCE_EXPIRY_标题应为_业绩到期提醒() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.PERFORMANCE_EXPIRY, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.title()).isEqualTo("业绩到期提醒");
        }

        @Test
        void CA_EXPIRY_标题应为_CA证书到期提醒() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.CA_EXPIRY, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.title()).isEqualTo("CA证书到期提醒");
        }

        @Test
        void CA_BORROW_OVERDUE_标题应为_CA借用超期提醒() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.CA_BORROW_OVERDUE, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.title()).isEqualTo("CA借用超期提醒");
        }

        @Test
        void QUALIFICATION_EXPIRY_标题应为_资质到期提醒() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.QUALIFICATION_EXPIRY, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.title()).isEqualTo("资质到期提醒");
        }
    }

    @Nested
    class Body_正文 {
        @Test
        void body应等于传入的alertMessage() {
            String customBody = "自定义告警正文内容";
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, customBody, RELATED_ID, Map.of());

            assertThat(info.body()).isEqualTo(customBody);
        }

        @Test
        void alertMessage为null时body为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, null, RELATED_ID, Map.of());

            assertThat(info.body()).isNull();
        }
    }

    @Nested
    class RelatedId_解析 {
        @Test
        void relatedId格式正确_应解析出实体类型与ID() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, "Project:123", Map.of());

            assertThat(info.sourceEntityType()).isEqualTo("Project");
            assertThat(info.sourceEntityId()).isEqualTo(123L);
        }

        @Test
        void relatedId为null_实体字段应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, null, Map.of());

            assertThat(info.sourceEntityType()).isNull();
            assertThat(info.sourceEntityId()).isNull();
        }

        @Test
        void relatedId为空字符串_实体字段应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, "", Map.of());

            assertThat(info.sourceEntityType()).isNull();
            assertThat(info.sourceEntityId()).isNull();
        }

        @Test
        void relatedId为空白字符串_实体字段应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, "   ", Map.of());

            assertThat(info.sourceEntityType()).isNull();
            assertThat(info.sourceEntityId()).isNull();
        }

        @Test
        void relatedId缺少冒号_实体字段应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, "Project123", Map.of());

            assertThat(info.sourceEntityType()).isNull();
            assertThat(info.sourceEntityId()).isNull();
        }

        @Test
        void relatedId冒号后非数字_实体字段应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, "Project:abc", Map.of());

            assertThat(info.sourceEntityType()).isNull();
            assertThat(info.sourceEntityId()).isNull();
        }

        @Test
        void relatedId实体类型为空_实体字段应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, ":123", Map.of());

            assertThat(info.sourceEntityType()).isNull();
            assertThat(info.sourceEntityId()).isNull();
        }

        @Test
        void relatedId实体ID为空_实体字段应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, "Project:", Map.of());

            assertThat(info.sourceEntityType()).isNull();
            assertThat(info.sourceEntityId()).isNull();
        }

        @Test
        void relatedId带多个冒号_实体字段应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, "Project:123:456", Map.of());

            assertThat(info.sourceEntityType()).isNull();
            assertThat(info.sourceEntityId()).isNull();
        }
    }

    @Nested
    class TargetUrl_跳转链接 {
        @Test
        void extraPayload包含targetUrl_应取出() {
            Map<String, Object> payload = Map.of("targetUrl", "/project/123/deadline");
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, RELATED_ID, payload);

            assertThat(info.targetUrl()).isEqualTo("/project/123/deadline");
        }

        @Test
        void extraPayload不包含targetUrl_应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info.targetUrl()).isNull();
        }

        @Test
        void extraPayload为null_应为null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, RELATED_ID, null);

            assertThat(info.targetUrl()).isNull();
        }

        @Test
        void extraPayload中targetUrl值为非String类型_应为null() {
            Map<String, Object> payload = Map.of("targetUrl", 12345);
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, RELATED_ID, payload);

            assertThat(info.targetUrl()).isNull();
        }
    }

    @Nested
    class 非空保证 {
        @Test
        void buildNotification_不应返回null() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, SAMPLE_MESSAGE, RELATED_ID, Map.of());

            assertThat(info).isNotNull();
        }

        @Test
        void 所有参数为null时_仍返回非null对象() {
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    AlertRule.AlertType.DEADLINE, null, null, null);

            assertThat(info).isNotNull();
            assertThat(info.notificationType()).isEqualTo("DEADLINE");
            assertThat(info.title()).isEqualTo("投标截止日期提醒");
        }
    }
}
