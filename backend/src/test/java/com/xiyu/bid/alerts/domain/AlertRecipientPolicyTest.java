package com.xiyu.bid.alerts.domain;

import com.xiyu.bid.alerts.entity.AlertRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AlertRecipientPolicy} 纯核心单元测试。
 *
 * <p>覆盖点：</p>
 * <ul>
 *   <li>9 种 {@link AlertRule.AlertType} 各自的接收人角色码映射</li>
 *   <li>返回的 List 不可变（add 操作抛 UnsupportedOperationException）</li>
 *   <li>返回的 List 非空</li>
 *   <li>角色码与 {@code RoleProfileCatalog} 标准角色码一致</li>
 * </ul>
 */
class AlertRecipientPolicyTest {

    @Test
    void resolveRoleCodes_DEADLINE_应返回项目负责人与组长() {
        List<String> roles = AlertRecipientPolicy.resolveRoleCodes(AlertRule.AlertType.DEADLINE);

        assertThat(roles).containsExactly("bid-projectLeader", "bid-TeamLeader");
    }

    @Test
    void resolveRoleCodes_RISK_应返回投标管理员与组长() {
        List<String> roles = AlertRecipientPolicy.resolveRoleCodes(AlertRule.AlertType.RISK);

        assertThat(roles).containsExactly("/bidAdmin", "bid-TeamLeader");
    }

    @Test
    void resolveRoleCodes_DOCUMENT_应返回项目负责人与组长() {
        List<String> roles = AlertRecipientPolicy.resolveRoleCodes(AlertRule.AlertType.DOCUMENT);

        assertThat(roles).containsExactly("bid-projectLeader", "bid-TeamLeader");
    }

    @Test
    void resolveRoleCodes_BUDGET_应返回投标管理员与组长() {
        List<String> roles = AlertRecipientPolicy.resolveRoleCodes(AlertRule.AlertType.BUDGET);

        assertThat(roles).containsExactly("/bidAdmin", "bid-TeamLeader");
    }

    @Test
    void resolveRoleCodes_DEPOSIT_RETURN_应返回投标管理员与行政人员() {
        List<String> roles = AlertRecipientPolicy.resolveRoleCodes(AlertRule.AlertType.DEPOSIT_RETURN);

        assertThat(roles).containsExactly("/bidAdmin", "bid-administration");
    }

    @Test
    void resolveRoleCodes_PERFORMANCE_EXPIRY_应返回投标管理员与组长() {
        List<String> roles = AlertRecipientPolicy.resolveRoleCodes(AlertRule.AlertType.PERFORMANCE_EXPIRY);

        assertThat(roles).containsExactly("/bidAdmin", "bid-TeamLeader");
    }

    @Test
    void resolveRoleCodes_CA_EXPIRY_应返回投标管理员与组长() {
        List<String> roles = AlertRecipientPolicy.resolveRoleCodes(AlertRule.AlertType.CA_EXPIRY);

        assertThat(roles).containsExactly("/bidAdmin", "bid-TeamLeader");
    }

    @Test
    void resolveRoleCodes_CA_BORROW_OVERDUE_应返回投标管理员与组长() {
        List<String> roles = AlertRecipientPolicy.resolveRoleCodes(AlertRule.AlertType.CA_BORROW_OVERDUE);

        assertThat(roles).containsExactly("/bidAdmin", "bid-TeamLeader");
    }

    @Test
    void resolveRoleCodes_QUALIFICATION_EXPIRY_应返回行政人员_投标管理员_组长() {
        List<String> roles = AlertRecipientPolicy.resolveRoleCodes(AlertRule.AlertType.QUALIFICATION_EXPIRY);

        assertThat(roles).containsExactly("bid-administration", "/bidAdmin", "bid-TeamLeader");
    }

    @Test
    void resolveRoleCodes_每种类型返回的List均不可变() {
        for (AlertRule.AlertType type : AlertRule.AlertType.values()) {
            List<String> roles = AlertRecipientPolicy.resolveRoleCodes(type);

            assertThatThrownBy(() -> roles.add("any-role"))
                    .as("AlertType=%s 返回的 List 必须不可变", type)
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void resolveRoleCodes_每种类型返回的List均非空() {
        for (AlertRule.AlertType type : AlertRule.AlertType.values()) {
            List<String> roles = AlertRecipientPolicy.resolveRoleCodes(type);

            assertThat(roles)
                    .as("AlertType=%s 返回的角色码列表不得为空", type)
                    .isNotEmpty();
        }
    }
}
