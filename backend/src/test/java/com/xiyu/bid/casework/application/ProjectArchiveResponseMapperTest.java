// Input: ProjectArchiveResponseMapper 列表/Stats 选项取值行为
// Output: Mockito 单元测试 — 验证「投标负责人」从 ProjectInitiationDetails.biddingLeaderName 解析
// Pos: backend test source - CO-421 回归
package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ProjectArchiveResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectArchiveResponseMapperTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private ArchiveFileRepository fileRepository;
    @Mock private ProjectInitiationDetailsRepository initiationDetailsRepository;

    private ProjectArchiveResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProjectArchiveResponseMapper(
                projectRepository, tenderRepository, fileRepository,
                initiationDetailsRepository);
    }

    @Test
    void toResponseList_resolvesBidManagerFromBiddingLeaderName_notTenderBiddingPerson() {
        // 已立项项目：tender.biddingPersonName="招标平台联系人"（不应被采用）
        //              ProjectInitiationDetails.biddingLeaderName="张三"（应被采用）
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        Tender tender = Tender.builder()
                .id(10L)
                .projectType("综合")
                .projectManagerName("李项目经理")
                .biddingPersonName("招标平台联系人")
                .purchaserName("招标主体")
                .build();
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(100L)
                .biddingLeaderName("张三")
                .build();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of(project));
        when(tenderRepository.findAllById(List.of(10L))).thenReturn(List.of(tender));
        when(initiationDetailsRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of(details));
        lenient().when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of());

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bidManager()).isEqualTo("张三");
        assertThat(result.get(0).projectManager()).isEqualTo("李项目经理");
    }

    @Test
    void toResponseList_returnsNullBidManager_whenNoInitiationDetails() {
        // 无 ProjectInitiationDetails → bidManager=null（降级策略，不回退 tender）
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        Tender tender = Tender.builder()
                .id(10L)
                .biddingPersonName("招标平台联系人")  // 不应被采用
                .build();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of(project));
        when(tenderRepository.findAllById(List.of(10L))).thenReturn(List.of(tender));
        when(initiationDetailsRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of());
        lenient().when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of());

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bidManager()).isNull();
    }

    @Test
    void toResponseList_returnsNullBidManager_whenBiddingLeaderNameIsBlank() {
        // ProjectInitiationDetails 存在但 biddingLeaderName 为空 → bidManager=null
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder().id(100L).tenderId(10L).status(Project.Status.BIDDING).build();
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(100L)
                .biddingLeaderName("")  // 空白字符串
                .build();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of(project));
        lenient().when(tenderRepository.findAllById(anyList())).thenReturn(List.of());
        when(initiationDetailsRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of(details));
        lenient().when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of());

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bidManager()).isNull();
    }

    @Test
    void collectBidManagers_returnsBiddingLeaderNames() {
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .projectId(100L)
                .biddingLeaderName("张三")
                .build();

        when(initiationDetailsRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of(details));

        List<String> names = mapper.collectBidManagers(List.of(archive));

        assertThat(names).containsExactly("张三");
    }

    @Test
    void collectBidManagers_emptyWhenNoInitiationDetails() {
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        when(initiationDetailsRepository.findByProjectIdIn(List.of(100L))).thenReturn(List.of());

        List<String> names = mapper.collectBidManagers(List.of(archive));

        assertThat(names).isEmpty();
    }

    @Test
    void toResponseList_returnsClosedAt_whenProjectIsClosed() {
        LocalDateTime closedAt = LocalDateTime.of(2026, 6, 15, 10, 0, 0);
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder()
                .id(100L)
                .tenderId(10L)
                .status(Project.Status.WON)
                .closedAt(closedAt)
                .build();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of(project));
        lenient().when(tenderRepository.findAllById(anyList())).thenReturn(List.of());
        lenient().when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of());

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).closedAt()).isEqualTo(closedAt);
    }

    @Test
    void toResponseList_returnsNullClosedAt_whenProjectNotClosed() {
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        Project project = Project.builder()
                .id(100L)
                .tenderId(10L)
                .status(Project.Status.BIDDING)
                .closedAt(null)
                .build();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of(project));
        lenient().when(tenderRepository.findAllById(anyList())).thenReturn(List.of());
        lenient().when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of());

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).closedAt()).isNull();
    }
}
