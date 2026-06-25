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

        User bidTeamUser = new User();
        bidTeamUser.setId(BID_TEAM_USER_ID);
        bidTeamUser.setUsername("bidteam");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("bidteam");
        when(auth.isAuthenticated()).thenReturn(true);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("bidteam")).thenReturn(java.util.Optional.of(bidTeamUser));

        DataScopeAccessProfile profile = new DataScopeAccessProfile("self", null, null);
        when(dataScopeConfigService.getAccessProfile(any(User.class))).thenReturn(profile);

        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
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

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(List.of(1L)))
                .thenReturn(List.of(record));
        when(projectRepository.findByTenderIdIn(List.of(1L))).thenReturn(List.of());

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

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(List.of(2L)))
                .thenReturn(List.of(record));
        when(projectRepository.findByTenderIdIn(List.of(2L))).thenReturn(List.of());

        List<Tender> result = guard.filterVisibleTenders(List.of(tender));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("投标专员可见：自己创建的标讯（即使没有分配记录）")
    void bidTeamCanSeeSelfCreatedTender() {
        Tender tender = tender(3L, "我创建的标讯");
        tender.setCreatorId(BID_TEAM_USER_ID);

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(List.of(3L)))
                .thenReturn(List.of());
        when(projectRepository.findByTenderIdIn(List.of(3L))).thenReturn(List.of());

        List<Tender> result = guard.filterVisibleTenders(List.of(tender));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("投标专员不可见：既不是自己创建也没分配给自己的标讯")
    void bidTeamCannotSeeUnrelatedTender() {
        Tender tender = tender(4L, "无关的标讯");
        tender.setCreatorId(OTHER_USER_ID);

        when(tenderAssignmentRecordRepository.findLatestByTenderIds(List.of(4L)))
                .thenReturn(List.of());
        when(projectRepository.findByTenderIdIn(List.of(4L))).thenReturn(List.of());

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
}
