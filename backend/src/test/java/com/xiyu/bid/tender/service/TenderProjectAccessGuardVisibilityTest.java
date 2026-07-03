package com.xiyu.bid.tender.service;

import com.xiyu.bid.admin.service.DataScopeAccessProfile;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class TenderProjectAccessGuardVisibilityTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private DataScopeConfigService dataScopeConfigService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenderAssignmentRecordRepository tenderAssignmentRecordRepository;

    private TenderProjectAccessGuard guard;

    private static final Long BID_TEAM_USER_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;

    @BeforeEach
    void setUp() {
        guard = new TenderProjectAccessGuard(
                projectRepository,
                projectAccessScopeService,
                dataScopeConfigService,
                userRepository,
                tenderAssignmentRecordRepository
        );

        // bidTeam 用户的默认 stubbing（lenient：bidAdmin/项目负责人的测试会覆盖）
        User bidTeamUser = new User();
        bidTeamUser.setId(BID_TEAM_USER_ID);
        bidTeamUser.setUsername("bidteam");

        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("bidteam");
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        lenient().when(userRepository.findByUsername("bidteam")).thenReturn(java.util.Optional.of(bidTeamUser));

        DataScopeAccessProfile profile = DataScopeAccessProfile.builder()
                .dataScope("self")
                .build();
        lenient().when(dataScopeConfigService.getAccessProfile(any(User.class))).thenReturn(profile);

        lenient().when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        lenient().when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
    }

    @Test
    @DisplayName("投标专员可见：自己作为最新 assignee 的标讯")
    void bidTeamCanSeeTenderAssignedToThem() {
        Tender tender = tender(1L, "分配给我的标讯");

        TenderAssignmentRecord record = TenderAssignmentRecord.builder()
                .id(1L)
                .tenderId(1L)
                .assigneeId(BID_TEAM_USER_ID)
                .assigneeName("bidteam")
                .assignedAt(LocalDateTime.now())
                .type(TenderAssignmentRecord.AssignmentType.DISPATCH)
                .build();

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(any()))
                .thenReturn(List.of(record));
        when(projectRepository.findByTenderIdIn(any())).thenReturn(List.of());

        List<Tender> result = guard.filterVisibleTenders(List.of(tender));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("投标专员不可见：分配给其他人的标讯")
    void bidTeamCannotSeeTenderAssignedToOthers() {
        Tender tender = tender(2L, "分配给别人的标讯");

        TenderAssignmentRecord record = TenderAssignmentRecord.builder()
                .id(2L)
                .tenderId(2L)
                .assigneeId(OTHER_USER_ID)
                .assigneeName("other")
                .assignedAt(LocalDateTime.now())
                .type(TenderAssignmentRecord.AssignmentType.DISPATCH)
                .build();

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(any()))
                .thenReturn(List.of(record));
        when(projectRepository.findByTenderIdIn(any())).thenReturn(List.of());

        List<Tender> result = guard.filterVisibleTenders(List.of(tender));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("投标专员可见：自己创建的标讯（即使没有分配记录）")
    void bidTeamCanSeeSelfCreatedTender() {
        Tender tender = tender(3L, "我创建的标讯");
        tender.setCreatorId(BID_TEAM_USER_ID);

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(any()))
                .thenReturn(List.of());
        when(projectRepository.findByTenderIdIn(any())).thenReturn(List.of());

        List<Tender> result = guard.filterVisibleTenders(List.of(tender));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("投标专员不可见：既不是自己创建也没分配给自己的标讯")
    void bidTeamCannotSeeUnrelatedTender() {
        Tender tender = tender(4L, "无关的标讯");
        tender.setCreatorId(OTHER_USER_ID);

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(any()))
                .thenReturn(List.of());
        when(projectRepository.findByTenderIdIn(any())).thenReturn(List.of());

        List<Tender> result = guard.filterVisibleTenders(List.of(tender));

        assertThat(result).isEmpty();
    }

    private Tender tender(long id, String title) {
        Tender t = new Tender();
        t.setId(id);
        t.setTitle(title);
        t.setStatus(Tender.Status.TRACKING);
        return t;
    }

    // ====================================================================
    // 2.1 标讯列表契约测试（飞书《标讯中心·权限矩阵》V1.0）
    // 锁定各角色的数据范围，防止未来重构漂移
    // ====================================================================

    @Test
    @DisplayName("2.1.1 投标项目负责人可见：自己创建的标讯（dataScope=self）")
    void projectLeaderCanSeeSelfCreatedTender() {
        setUpUserWithScope("projectLeader", 300L, "self");

        Tender tender = tender(10L, "项目负责人创建的标讯");
        tender.setCreatorId(300L);

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(any())).thenReturn(List.of());
        when(projectRepository.findByTenderIdIn(any())).thenReturn(List.of());

        List<Tender> result = guard.filterVisibleTenders(List.of(tender));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("2.1.1 投标项目负责人可见：分配给自己的标讯")
    void projectLeaderCanSeeAssignedTender() {
        setUpUserWithScope("projectLeader", 300L, "self");

        Tender tender = tender(11L, "分配给项目负责人的标讯");
        TenderAssignmentRecord record = TenderAssignmentRecord.builder()
                .tenderId(11L)
                .assigneeId(300L)
                .assignedAt(LocalDateTime.now())
                .type(TenderAssignmentRecord.AssignmentType.DISPATCH)
                .build();

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(any())).thenReturn(List.of(record));
        when(projectRepository.findByTenderIdIn(any())).thenReturn(List.of());

        List<Tender> result = guard.filterVisibleTenders(List.of(tender));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("2.1.1 投标项目负责人不可见：他人创建且未分配给自己的标讯")
    void projectLeaderCannotSeeUnrelatedTender() {
        setUpUserWithScope("projectLeader", 300L, "self");

        Tender tender = tender(12L, "无关标讯");
        tender.setCreatorId(OTHER_USER_ID);

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(any())).thenReturn(List.of());
        when(projectRepository.findByTenderIdIn(any())).thenReturn(List.of());

        List<Tender> result = guard.filterVisibleTenders(List.of(tender));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("2.1.1 投标管理员/组长可见：全量标讯（dataScope=all + adminAccess）")
    void bidAdminCanSeeAllTenders() {
        // admin access 短路：projectAccessScopeService.currentUserHasAdminAccess()=true 直接返回全部
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);

        Tender t1 = tender(20L, "标讯A");
        Tender t2 = tender(21L, "标讯B");
        t2.setCreatorId(OTHER_USER_ID); // 即使他人创建也可见

        List<Tender> result = guard.filterVisibleTenders(List.of(t1, t2));
        assertThat(result).hasSize(2);
    }

    /**
     * Helper：切换当前 mock 用户与 dataScope，便于在同一测试类内测多角色。
     * 注意：BeforeEach 已设置 bidTeamUser，本 helper 用于补测其他角色时覆盖。
     */
    private void setUpUserWithScope(String username, Long userId, String dataScope) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);

        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(username);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        lenient().when(userRepository.findByUsername(username)).thenReturn(java.util.Optional.of(user));

        DataScopeAccessProfile profile = DataScopeAccessProfile.builder()
                .dataScope(dataScope)
                .build();
        lenient().when(dataScopeConfigService.getAccessProfile(any(User.class))).thenReturn(profile);
    }
}
