// Input: ProjectInitiationService 提交/更新/查询链路的 customFields 行为
// Output: CO-601 US1 — InitiationDto.customFields 落列 / InitiationViewDto 返回 Map / 按 scope 键整体替换
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import com.xiyu.bid.project.dto.InitiationDto;
import com.xiyu.bid.project.dto.InitiationViewDto;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectInitiationCustomFieldsTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Mock ProjectInitiationDetailsRepository repo;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectStageService projectStageService;
    @Mock ProjectAccessScopeService projectAccessScopeService;
    @Mock UserRepository userRepository;
    @Mock ProjectLeadAssignmentRepository leadAssignmentRepository;
    @Mock ProjectNotificationService notificationService;
    @Mock ProjectDocumentRepository projectDocumentRepository;
    ProjectInitiationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        ProjectInitiationMapper realMapper = new ProjectInitiationMapper(om, new CustomFieldsCodec(om));
        service = new ProjectInitiationService(repo, projectRepository, projectStageService,
                projectAccessScopeService, userRepository, realMapper, leadAssignmentRepository,
                notificationService, projectDocumentRepository);
        lenient().doNothing().when(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);
        lenient().when(projectRepository.findById(1L))
                .thenReturn(Optional.of(Project.builder().id(1L).managerId(55L).build()));
        lenient().when(repo.save(any(ProjectInitiationDetails.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(projectStageService.currentStage(1L))
                .thenReturn(com.xiyu.bid.project.core.ProjectStage.INITIATED);
        lenient().when(projectDocumentRepository.findById(1L))
                .thenReturn(Optional.of(ProjectDocument.builder().id(1L).projectId(1L).name("招标文件.pdf").build()));
    }

    private InitiationDto fullDto() {
        return InitiationDto.builder()
                .ownerUnit("国网")
                .expectedBidders(3).contractPeriodMonths(12)
                .projectType(InitiationFieldPolicy.ProjectType.OFFICE)
                .customerType(InitiationFieldPolicy.CustomerType.CENTRAL_SOE)
                .annualRevenue(new BigDecimal("100000"))
                .bidOpenTime(LocalDateTime.of(2026, 6, 1, 9, 30))
                .ownerUserId(42L).departmentSnapshot("投标部")
                .depositAmount(new BigDecimal("50000")).depositPaymentMethod("银行汇票")
                .tenderDocumentId(1L)
                .build();
    }

    private ProjectInitiationDetails fullEntity() {
        return ProjectInitiationDetails.builder()
                .id(10L).projectId(1L)
                .ownerUnit("国网")
                .expectedBidders(3).contractPeriodMonths(12)
                .projectType("OFFICE").customerType("CENTRAL_SOE")
                .annualRevenue(new BigDecimal("100000"))
                .bidOpenTime(LocalDateTime.of(2026, 6, 1, 9, 30))
                .ownerUserId(42L).departmentSnapshot("投标部")
                .depositAmount(new BigDecimal("50000")).depositPaymentMethod("银行汇票")
                .locked(Boolean.FALSE).reviewStatus("DRAFT")
                .build();
    }

    @Test
    void submit_persistsCustomFieldsToColumn_andReturnsMap() throws Exception {
        InitiationDto req = fullDto();
        req.setCustomFields(Map.of("project.initiation", Map.of("internalReviewNote", "需法务会签")));
        when(repo.findByProjectId(1L)).thenReturn(Optional.empty());

        InitiationViewDto view = service.submit(1L, req, 99L);

        ArgumentCaptor<ProjectInitiationDetails> captor = ArgumentCaptor.forClass(ProjectInitiationDetails.class);
        verify(repo).save(captor.capture());
        Map<String, Object> stored = OM.readValue(captor.getValue().getCustomFields(), Map.class);
        assertThat(stored)
                .isEqualTo(Map.of("project.initiation", Map.of("internalReviewNote", "需法务会签")));
        assertThat(view.getCustomFields())
                .isEqualTo(Map.of("project.initiation", Map.of("internalReviewNote", "需法务会签")));
    }

    @Test
    void update_replacesInitiationScope_preservesOtherScopeKeys() throws Exception {
        ProjectInitiationDetails existing = fullEntity();
        existing.setCustomFields("{\"project.initiation\":{\"oldKey\":\"旧值\"},\"legacy.import\":{\"keep\":true}}");
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));
        InitiationDto req = fullDto();
        req.setCustomFields(Map.of("project.initiation", Map.of("newKey", "新值")));

        InitiationViewDto view = service.update(1L, req, 99L);

        ArgumentCaptor<ProjectInitiationDetails> captor = ArgumentCaptor.forClass(ProjectInitiationDetails.class);
        verify(repo).save(captor.capture());
        Map<String, Object> stored = OM.readValue(captor.getValue().getCustomFields(), Map.class);
        // 按 scope 键整体替换：project.initiation 只剩新键；其他 scope 键不动
        assertThat(stored)
                .containsEntry("project.initiation", Map.of("newKey", "新值"))
                .containsEntry("legacy.import", Map.of("keep", true))
                .hasSize(2);
        assertThat(view.getCustomFields())
                .containsEntry("project.initiation", Map.of("newKey", "新值"));
    }

    @Test
    void update_nullCustomFields_keepsExistingColumnUntouched() throws Exception {
        // 请求未携带 customFields（老客户端）→ 不动已存值
        ProjectInitiationDetails existing = fullEntity();
        existing.setCustomFields("{\"project.initiation\":{\"oldKey\":\"旧值\"}}");
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(existing));

        service.update(1L, fullDto(), 99L);

        ArgumentCaptor<ProjectInitiationDetails> captor = ArgumentCaptor.forClass(ProjectInitiationDetails.class);
        verify(repo).save(captor.capture());
        Map<String, Object> stored = OM.readValue(captor.getValue().getCustomFields(), Map.class);
        assertThat(stored)
                .isEqualTo(Map.of("project.initiation", Map.of("oldKey", "旧值")));
    }

    @Test
    void getByProject_returnsCustomFieldsAsMap() {
        ProjectInitiationDetails entity = fullEntity();
        entity.setCustomFields("{\"project.initiation\":{\"internalReviewNote\":\"需法务会签\"}}");
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(entity));
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(1L, "EVALUATION_GAP", null, null))
                .thenReturn(List.of());

        Optional<InitiationViewDto> view = service.getByProject(1L);

        assertThat(view).isPresent();
        assertThat(view.get().getCustomFields())
                .isEqualTo(Map.of("project.initiation", Map.of("internalReviewNote", "需法务会签")));
    }

    @Test
    void getByProject_nullColumn_returnsEmptyMap() {
        ProjectInitiationDetails entity = fullEntity();
        entity.setCustomFields(null);
        when(repo.findByProjectId(1L)).thenReturn(Optional.of(entity));
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(1L, "EVALUATION_GAP", null, null))
                .thenReturn(List.of());

        Optional<InitiationViewDto> view = service.getByProject(1L);

        assertThat(view).isPresent();
        assertThat(view.get().getCustomFields()).isNotNull().isEmpty();
    }
}
