// Input: ProjectArchiveResponseMapper 列表/Stats 选项取值行为
// Output: Mockito 单元测试 — 验证「投标负责人」从 ProjectInitiationDetails.biddingLeaderName 解析
// Pos: backend test source - CO-421 回归
package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ProjectArchiveResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    // ============ 档案修复：归档文件数 tooltip 分类统计归一化 ============

    @Test
    void toResponseList_normalizesNonStandardCategoriesToOther_andSumEqualsFileCount() {
        // 1 个标准 + 3 个非标准（业务分类/历史废弃值）→ OTHER 应累计 3，6 个分类 sum == fileCount
        ProjectArchive archive = new ProjectArchive();
        archive.setId(1L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        LocalDateTime ts = LocalDateTime.of(2026, 7, 20, 10, 0, 0);
        // [documentCategory, fileName] 对：标准 + 业务分类 + 未知值
        String[][] cats = {
                {"TENDER", "招标文件"},
                {"BID_RESULT_NOTICE", "中标结果公告"},
                {"TASK_ATTACHMENT", "任务附件"},
                {"FOO_BAR", "历史废弃值"},
        };
        List<ArchiveFile> files = Arrays.stream(cats).map(pair -> {
            ArchiveFile f = new ArchiveFile();
            f.setId((long) (101 + Arrays.asList(cats).indexOf(pair)));
            f.setArchiveId(1L);
            f.setFileName(pair[1]);
            f.setDocumentCategory(pair[0]);
            f.setFileSize(1024L);
            f.setUploadUserId(500L);
            f.setUploadUserName("郑蓉蓉");
            f.setCreatedAt(ts);
            return f;
        }).toList();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of());
        when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(List.of(1L)))
                .thenReturn(files);

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        assertThat(result).hasSize(1);
        ProjectArchiveResponse resp = result.get(0);
        assertThat(resp.fileCount()).isEqualTo(4);
        Map<String, Long> details = resp.fileCategoryDetails();
        // 关键断言：只有 6 个固定 key（前端 FileCategoryPopover 只渲染这 6 个）
        assertThat(details).containsOnlyKeys("TENDER", "BID", "OPEN_LIST", "WIN_NOTICE", "DEPOSIT_RECEIPT", "OTHER");
        assertThat(details.get("TENDER")).isEqualTo(1L);
        assertThat(details.get("BID")).isEqualTo(0L);
        assertThat(details.get("OPEN_LIST")).isEqualTo(0L);
        assertThat(details.get("WIN_NOTICE")).isEqualTo(0L);
        assertThat(details.get("DEPOSIT_RECEIPT")).isEqualTo(0L);
        assertThat(details.get("OTHER")).isEqualTo(3L); // BID_RESULT_NOTICE + TASK_ATTACHMENT + FOO_BAR
        // 关键断言：6 个分类求和必须等于 fileCount，避免 tooltip"分类和 ≠ 总计"
        long sum = details.values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(resp.fileCount().longValue());
    }

    @Test
    void toResponseList_standardCategoriesCountNormally_noFalseOtherBucket() {
        // 6 个标准枚举各 1 个 → 每个分类 = 1，OTHER 只算它自己（不应把标准枚举误归到 OTHER）
        ProjectArchive archive = new ProjectArchive();
        archive.setId(2L);
        archive.setProjectId(100L);
        archive.setProjectName("测试项目");
        archive.setArchiveStatus("ACTIVE");

        LocalDateTime ts = LocalDateTime.of(2026, 7, 20, 10, 0, 0);
        String[] cats = {"TENDER", "BID", "OPEN_LIST", "WIN_NOTICE", "DEPOSIT_RECEIPT", "OTHER"};
        List<ArchiveFile> files = Arrays.stream(cats).map(cat -> {
            ArchiveFile f = new ArchiveFile();
            f.setId(200L + Arrays.asList(cats).indexOf(cat));
            f.setArchiveId(2L);
            f.setFileName(cat);
            f.setDocumentCategory(cat);
            f.setFileSize(1024L);
            f.setUploadUserId(500L);
            f.setUploadUserName("郑蓉蓉");
            f.setCreatedAt(ts);
            return f;
        }).toList();

        when(projectRepository.findAllById(List.of(100L))).thenReturn(List.of());
        when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(List.of(2L)))
                .thenReturn(files);

        List<ProjectArchiveResponse> result = mapper.toResponseList(List.of(archive));

        ProjectArchiveResponse resp = result.get(0);
        assertThat(resp.fileCount()).isEqualTo(6);
        Map<String, Long> details = resp.fileCategoryDetails();
        for (String cat : cats) {
            assertThat(details.get(cat)).isEqualTo(1L);
        }
        long sum = details.values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(resp.fileCount().longValue());
    }
}
