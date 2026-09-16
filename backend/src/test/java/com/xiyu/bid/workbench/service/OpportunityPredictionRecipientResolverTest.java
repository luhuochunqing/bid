// Input: 本地用户身份缓存 + OSS 批量实时岗位/角色结果
// Output: 一轮扫描共用的实时在职用户与核心角色接收人快照
// Pos: workbench/service - 商机预测实时接收人解析集成边界测试
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.application.OrganizationDirectoryGateway;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpportunityPredictionRecipientResolver - 实时收件人快照")
class OpportunityPredictionRecipientResolverTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganizationDirectoryGateway directoryGateway;

    private OpportunityPredictionRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OpportunityPredictionRecipientResolver(userRepository, directoryGateway);
    }

    @Test
    @DisplayName("一次批量查询全部工号，以 OSS 状态和多角色解析经理与核心角色")
    void resolve_usesSingleLiveBatchAndIgnoresStaleLocalFlags() {
        when(userRepository.findAll()).thenReturn(List.of(
                user(10L, "100", false),
                user(11L, "101", true),
                user(50L, "200", false),
                user(60L, "300", true),
                user(70L, " ", true)));
        when(directoryGateway.getUserJobAndRoleListByJobNumbersStrict(List.of("100", "101", "200", "300")))
                .thenReturn(Map.of(
                        "100", lookup("100", "1", List.of("/bidAdmin", "CRM_ADMIN")),
                        "101", lookup("101", "1", List.of("bid-TeamLeader", "投标管理员")),
                        "200", lookup("200", "1", List.of()),
                        "300", lookup("300", "0", List.of("/bidAdmin")),
                        "999", lookup("999", "1", List.of("/bidAdmin"))));

        OpportunityPredictionRecipientResolver.RecipientSnapshot snapshot = resolver.resolve();

        assertThat(snapshot.activeUserIds()).containsExactlyInAnyOrder(10L, 11L, 50L);
        assertThat(snapshot.roleRecipientIds()).containsExactly(10L, 11L);
        assertThat(snapshot.recipientsFor(50L)).containsExactly(10L, 11L, 50L);
        assertThat(snapshot.recipientsFor(10L)).containsExactly(10L, 11L);
        assertThat(snapshot.recipientsFor(60L)).containsExactly(10L, 11L);
        assertThat(snapshot.requestedJobNumbers()).isEqualTo(4);
        assertThat(snapshot.returnedJobNumbers()).isEqualTo(4);
        assertThat(snapshot.lookupSucceeded()).isTrue();

        ArgumentCaptor<List<String>> jobs = ArgumentCaptor.forClass(List.class);
        verify(directoryGateway).getUserJobAndRoleListByJobNumbersStrict(jobs.capture());
        assertThat(jobs.getValue()).containsExactly("100", "101", "200", "300");
    }

    @Test
    @DisplayName("没有可查询工号时返回空快照且不调用 OSS")
    void resolve_withoutJobNumbersReturnsEmptySnapshot() {
        when(userRepository.findAll()).thenReturn(List.of(user(70L, null, true)));

        OpportunityPredictionRecipientResolver.RecipientSnapshot snapshot = resolver.resolve();

        assertThat(snapshot.activeUserIds()).isEmpty();
        assertThat(snapshot.roleRecipientIds()).isEmpty();
        assertThat(snapshot.requestedJobNumbers()).isZero();
        assertThat(snapshot.returnedJobNumbers()).isZero();
        assertThat(snapshot.lookupSucceeded()).isTrue();
        verify(directoryGateway, never()).getUserJobAndRoleListByJobNumbersStrict(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("OSS 查询异常时失败关闭，不回退本地 enabled 或角色")
    void resolve_whenLiveLookupFailsReturnsEmptySnapshot() {
        when(userRepository.findAll()).thenReturn(List.of(user(10L, "100", true)));
        when(directoryGateway.getUserJobAndRoleListByJobNumbersStrict(List.of("100")))
                .thenThrow(new IllegalStateException("OSS unavailable"));

        OpportunityPredictionRecipientResolver.RecipientSnapshot snapshot = resolver.resolve();

        assertThat(snapshot.activeUserIds()).isEmpty();
        assertThat(snapshot.roleRecipientIds()).isEmpty();
        assertThat(snapshot.recipientsFor(10L)).isEmpty();
        assertThat(snapshot.requestedJobNumbers()).isEqualTo(1);
        assertThat(snapshot.returnedJobNumbers()).isZero();
        assertThat(snapshot.lookupSucceeded()).isFalse();
    }

    @Test
    @DisplayName("OSS 响应对象缺少工号时使用响应 Map 键关联本地身份")
    void resolve_whenResponseJobNumberBlankUsesMapKey() {
        when(userRepository.findAll()).thenReturn(List.of(user(10L, "100", true)));
        when(directoryGateway.getUserJobAndRoleListByJobNumbersStrict(List.of("100")))
                .thenReturn(Map.of("100", lookup("", "1", List.of("/bidAdmin"))));

        OpportunityPredictionRecipientResolver.RecipientSnapshot snapshot = resolver.resolve();

        assertThat(snapshot.activeUserIds()).containsExactly(10L);
        assertThat(snapshot.roleRecipientIds()).containsExactly(10L);
        assertThat(snapshot.returnedJobNumbers()).isEqualTo(1);
        assertThat(snapshot.lookupSucceeded()).isTrue();
    }

    @Test
    @DisplayName("部分响应只统计本次请求命中，缺失人员不回退本地状态")
    void resolve_whenResponseIsPartialCountsOnlyRequestedMatches() {
        when(userRepository.findAll()).thenReturn(List.of(
                user(10L, "100", true),
                user(11L, "101", true)));
        when(directoryGateway.getUserJobAndRoleListByJobNumbersStrict(List.of("100", "101")))
                .thenReturn(Map.of(
                        "100", lookup("100", "1", List.of("/bidAdmin")),
                        "999", lookup("999", "1", List.of("bid-TeamLeader"))));

        OpportunityPredictionRecipientResolver.RecipientSnapshot snapshot = resolver.resolve();

        assertThat(snapshot.activeUserIds()).containsExactly(10L);
        assertThat(snapshot.roleRecipientIds()).containsExactly(10L);
        assertThat(snapshot.recipientsFor(11L)).containsExactly(10L);
        assertThat(snapshot.requestedJobNumbers()).isEqualTo(2);
        assertThat(snapshot.returnedJobNumbers()).isEqualTo(1);
        assertThat(snapshot.lookupSucceeded()).isTrue();
    }

    private static User user(Long id, String employeeNumber, boolean enabled) {
        return User.builder().id(id).employeeNumber(employeeNumber).enabled(enabled).build();
    }

    private static OssUserJobAndRoleDto lookup(String jobNumber, String status, List<String> roles) {
        return new OssUserJobAndRoleDto(jobNumber, "", roles, "3", status, "用户" + jobNumber);
    }
}
