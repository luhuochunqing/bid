package com.xiyu.bid.user.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.service.RoleProfileService;
import com.xiyu.bid.user.core.AssignmentCandidatePolicy;
import com.xiyu.bid.user.core.AssignmentContext;
import com.xiyu.bid.user.dto.AssignmentCandidateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AssignmentCandidateAppService 编排逻辑单元测试（TDD Red 阶段）。
 *
 * <p>被测类 {@link AssignmentCandidateAppService} 尚未实现，本测试编译失败属于预期行为。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssignmentCandidateAppService 编排逻辑")
class AssignmentCandidateAppServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private RoleProfileService roleProfileService;

    @Mock
    private AssignmentCandidatePolicy assignmentCandidatePolicy;

    @InjectMocks
    private AssignmentCandidateAppService service;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).fullName("当前用户").build();
    }

    @Test
    @DisplayName("context=task 时走完整调用链：UserRepository → ProjectAccessScope → RoleProfile → Policy")
    void findCandidates_TaskContext_InvokesFullCallChain() {
        AssignmentContext context = AssignmentContext.of("task", null, null);
        List<User> users = List.of(
                User.builder().id(2L).fullName("张三").build(),
                User.builder().id(3L).fullName("李四").build()
        );
        List<AssignmentCandidateDTO> expected = List.of(
                new AssignmentCandidateDTO(2L, "张三", "E001", "bid_admin", "投标管理员", "D1", "一部", true)
        );

        when(userRepository.findByEnabledTrue()).thenReturn(users);
        when(projectAccessScopeService.getAllowedDepartmentCodes(currentUser)).thenReturn(List.of("D1"));
        when(roleProfileService.hasGlobalAccess(currentUser)).thenReturn(true);
        when(assignmentCandidatePolicy.filter(anyList(), anyBoolean(), anyList(), any(), any(), any()))
                .thenReturn(expected);

        List<AssignmentCandidateDTO> result = service.findCandidates(context, currentUser);

        assertThat(result).isEqualTo(expected);
        verify(userRepository).findByEnabledTrue();
        verify(projectAccessScopeService).getAllowedDepartmentCodes(currentUser);
        verify(roleProfileService).hasGlobalAccess(currentUser);
        verify(assignmentCandidatePolicy).filter(eq(users), eq(true), anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("context=tender 时走相同调用链")
    void findCandidates_TenderContext_InvokesSameCallChain() {
        AssignmentContext context = AssignmentContext.of("tender", null, null);
        List<User> users = List.of(
                User.builder().id(2L).fullName("张三").build()
        );
        List<AssignmentCandidateDTO> expected = List.of(
                new AssignmentCandidateDTO(2L, "张三", "E001", "sales", "销售", "D2", "二部", true)
        );

        when(userRepository.findByEnabledTrue()).thenReturn(users);
        when(projectAccessScopeService.getAllowedDepartmentCodes(currentUser)).thenReturn(List.of("D2"));
        when(roleProfileService.hasGlobalAccess(currentUser)).thenReturn(false);
        when(assignmentCandidatePolicy.filter(anyList(), anyBoolean(), anyList(), any(), any(), any()))
                .thenReturn(expected);

        List<AssignmentCandidateDTO> result = service.findCandidates(context, currentUser);

        assertThat(result).isEqualTo(expected);
        verify(assignmentCandidatePolicy).filter(eq(users), eq(false), anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("无效 context（如 \"invalid\"）抛出 IllegalArgumentException")
    void findCandidates_InvalidContext_ThrowsIllegalArgumentException() {
        AssignmentContext context = AssignmentContext.of("invalid", null, null);

        assertThatThrownBy(() -> service.findCandidates(context, currentUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context");
    }

    @Test
    @DisplayName("context 为 null 抛出 IllegalArgumentException")
    void findCandidates_NullContext_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> service.findCandidates(null, currentUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context");
    }

    @Test
    @DisplayName("UserRepository 返回空列表时返回空列表")
    void findCandidates_EmptyUserList_ReturnsEmptyList() {
        AssignmentContext context = AssignmentContext.of("task", null, null);
        when(userRepository.findByEnabledTrue()).thenReturn(List.of());
        when(projectAccessScopeService.getAllowedDepartmentCodes(currentUser)).thenReturn(List.of());
        when(roleProfileService.hasGlobalAccess(currentUser)).thenReturn(true);

        List<AssignmentCandidateDTO> result = service.findCandidates(context, currentUser);

        assertThat(result).isEmpty();
    }
}
