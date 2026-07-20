// Input: ProjectArchiveDetailService.getArchiveDetail 取值行为
// Output: Mockito 单元测试 — 验证「投标负责人」从 ProjectInitiationDetails.biddingLeaderName 解析
// Pos: backend test source - CO-421 回归
package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ProjectArchiveDetailResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ArchiveLog;
import com.xiyu.bid.casework.infrastructure.ArchiveLogRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectArchiveDetailServiceTest {

    @Mock private ProjectArchiveRepository archiveRepository;
    @Mock private ArchiveFileRepository fileRepository;
    @Mock private ArchiveLogRepository logRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private ProjectInitiationDetailsRepository initiationDetailsRepository;
    @Mock private UserRepository userRepository;

    private ProjectArchiveDetailService service;

    @BeforeEach
    void setUp() {
        service = new ProjectArchiveDetailService(
                archiveRepository, fileRepository, logRepository,
                projectRepository, tenderRepository,
                initiationDetailsRepository, userRepository);
        lenient().when(fileRepository.findByArchiveIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        lenient().when(logRepository.findByArchiveIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        lenient().when(userRepository.findAllByUsernameIn(java.util.Set.of())).thenReturn(List.of());
    }

    @Test
    void getArchiveDetail_returnsBiddingLeaderName_notTenderBiddingPerson() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        Tender tender = Tender.builder()
                .id(10L)
                .projectType("综合")
                .projectManagerName("李项目经理")
                .biddingPersonName("招标平台联系人")  // 不应被采用
                .purchaserName("招标主体")
                .build();
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(100L)
                .biddingLeaderName("张三")
                .build();

        when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(tender));
        when(initiationDetailsRepository.findByProjectId(100L)).thenReturn(Optional.of(details));

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(1L);

        assertThat(resp.bidManager()).isEqualTo("张三");
        assertThat(resp.projectManager()).isEqualTo("李项目经理");
    }

    @Test
    void getArchiveDetail_returnsNullBidManager_whenNoInitiationDetails() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        Tender tender = Tender.builder()
                .id(10L)
                .biddingPersonName("招标平台联系人")  // 不应被采用
                .build();

        when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(tender));
        when(initiationDetailsRepository.findByProjectId(100L)).thenReturn(Optional.empty());

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(1L);

        assertThat(resp.bidManager()).isNull();
    }

    @Test
    void getArchiveDetail_returnsNullBidManager_whenBiddingLeaderNameIsBlank() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(100L)
                .biddingLeaderName("   ")  // 空白字符串
                .build();

        when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        lenient().when(tenderRepository.findById(10L)).thenReturn(Optional.empty());
        when(initiationDetailsRepository.findByProjectId(100L)).thenReturn(Optional.of(details));

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(1L);

        assertThat(resp.bidManager()).isNull();
    }

    // ============ 档案修复：操作日志 operator 显示"姓名（工号）"============

    @Test
    void getArchiveDetail_operatorDisplaysFullNameAndEmployeeNumber_whenUserFound() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(2L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        ArchiveLog log = new ArchiveLog();
        log.setId(10L);
        log.setArchiveId(2L);
        log.setOperatorId(0L);
        log.setOperatorName("06234");  // 模拟 OSS 用户 username = 工号
        log.setActionType("预览");
        log.setActionContent("预览人06234");

        User user = User.builder()
                .id(500L)
                .username("06234")
                .fullName("郑蓉蓉")
                .employeeNumber("06234")
                .build();

        when(archiveRepository.findById(2L)).thenReturn(Optional.of(archive));
        when(logRepository.findByArchiveIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(log));
        when(userRepository.findAllByUsernameIn(java.util.Set.of("06234")))
                .thenReturn(List.of(user));

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(2L);

        assertThat(resp.logs()).hasSize(1);
        assertThat(resp.logs().get(0).operator()).isEqualTo("郑蓉蓉（06234）");
    }

    @Test
    void getArchiveDetail_operatorFallsBackToUsername_whenUserNotFound() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(3L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        ArchiveLog log = new ArchiveLog();
        log.setId(11L);
        log.setArchiveId(3L);
        log.setOperatorName("deleted_user");  // 历史数据：用户已删除
        log.setActionType("下载");
        log.setActionContent("下载人deleted_user");

        when(archiveRepository.findById(3L)).thenReturn(Optional.of(archive));
        when(logRepository.findByArchiveIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(log));
        when(userRepository.findAllByUsernameIn(java.util.Set.of("deleted_user")))
                .thenReturn(List.of());

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(3L);

        assertThat(resp.logs().get(0).operator()).isEqualTo("deleted_user");
    }

    @Test
    void getArchiveDetail_operatorFallsBackToUsername_whenFullNameBlank() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(4L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        ArchiveLog log = new ArchiveLog();
        log.setId(12L);
        log.setArchiveId(4L);
        log.setOperatorName("06234");
        log.setActionType("导出");
        log.setActionContent("导出人06234");

        User user = User.builder()
                .id(500L)
                .username("06234")
                .fullName("")  // fullName 为空
                .employeeNumber("06234")
                .build();

        when(archiveRepository.findById(4L)).thenReturn(Optional.of(archive));
        when(logRepository.findByArchiveIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(log));
        when(userRepository.findAllByUsernameIn(java.util.Set.of("06234")))
                .thenReturn(List.of(user));

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(4L);

        assertThat(resp.logs().get(0).operator()).isEqualTo("06234");
    }

    @Test
    void getArchiveDetail_operatorReturnsSystem_whenOperatorNameNull() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(6L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        ArchiveLog log = new ArchiveLog();
        log.setId(14L);
        log.setArchiveId(6L);
        log.setOperatorName(null);  // 系统/未指定
        log.setActionType("预览");
        log.setActionContent("系统操作");

        when(archiveRepository.findById(6L)).thenReturn(Optional.of(archive));
        when(logRepository.findByArchiveIdOrderByCreatedAtDesc(6L)).thenReturn(List.of(log));

        ProjectArchiveDetailResponse resp = service.getArchiveDetail(6L);

        assertThat(resp.logs().get(0).operator()).isEqualTo("系统");
    }
}
