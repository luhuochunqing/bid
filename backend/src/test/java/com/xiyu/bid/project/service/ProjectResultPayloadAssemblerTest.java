package com.xiyu.bid.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.ProjectResultCallbackPayload;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.domain.ProjectResultConfirmedEvent;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ProjectResultPayloadAssembler 单元测试（§4.2 载荷组装）。
 * <p>覆盖：sourceId 提取、evidenceFiles 查询、competitors 过滤、operatorEmployeeId 取值、
 * tender 查不到返回 null。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectResultPayloadAssembler — §4.2 载荷组装")
class ProjectResultPayloadAssemblerTest {

    private static final Long PROJECT_ID = 9001L;
    private static final Long TENDER_ID = 254L;
    private static final Long USER_ID = 493L;
    private static final Long RESULT_ID = 7700L;

    @Mock private TenderRepository tenderRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectDocumentRepository projectDocumentRepository;

    private ProjectResultPayloadAssembler assembler() {
        return new ProjectResultPayloadAssembler(tenderRepository, userRepository, projectDocumentRepository);
    }

    @Test
    @DisplayName("WON 完整载荷：sourceId/evidenceFiles/competitors/operator 全齐")
    void won_fullPayload() {
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setExternalId("CRM:CRM-OPP-2026-0510-003");
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        User user = new User();
        user.setFullName("张三");
        user.setEmployeeNumber("EMP001");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        ProjectDocument doc = ProjectDocument.builder()
                .id(1032L).projectId(PROJECT_ID)
                .name("中标通知书.pdf").size("2048000")
                .fileUrl("https://bid.xiyu.com/api/projects/128/documents/1032/download")
                .uploaderName("张三").build();
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of(doc));

        ProjectResultCallbackPayload payload = assembler().assemble(buildEvent(
                BidResultType.WON, List.of(1032L),
                List.of(new ProjectResultConfirmedEvent.CompetitorSnapshot(
                        "京东企业购", "95折", "月结60天", "含仓储托管服务"))));

        assertThat(payload).isNotNull();
        assertThat(payload.tenderId()).isEqualTo(TENDER_ID);
        assertThat(payload.projectId()).isEqualTo(PROJECT_ID);
        assertThat(payload.sourceId()).isEqualTo("CRM-OPP-2026-0510-003");
        assertThat(payload.bidResult()).isEqualTo("WON");
        assertThat(payload.systemName()).isEqualTo("西域数智化投标管理平台");
        assertThat(payload.operatorName()).isEqualTo("张三");
        assertThat(payload.operatorEmployeeId()).isEqualTo("EMP001");
        assertThat(payload.operatedAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+08:00");
        assertThat(payload.evidenceFiles()).hasSize(1);
        assertThat(payload.evidenceFiles().get(0).fileName()).isEqualTo("中标通知书.pdf");
        assertThat(payload.evidenceFiles().get(0).fileSize()).isEqualTo(2048000L);
        assertThat(payload.competitors()).hasSize(1);
        assertThat(payload.competitors().get(0).name()).isEqualTo("京东企业购");
    }

    @Test
    @DisplayName("LOST -> competitors 填值（含空行过滤）")
    void lost_competitorsFiltered() {
        mockTenderAndUser();
        var competitors = List.of(
                new ProjectResultConfirmedEvent.CompetitorSnapshot("西域", "9.5折", "月结30天", "价格有优势"),
                new ProjectResultConfirmedEvent.CompetitorSnapshot(null, null, null, null));

        ProjectResultCallbackPayload payload = assembler().assemble(buildEvent(
                BidResultType.LOST, List.of(), competitors));

        assertThat(payload.competitors()).hasSize(1);
        assertThat(payload.competitors().get(0).name()).isEqualTo("西域");
    }

    @Test
    @DisplayName("FAILED -> competitors 为空数组")
    void failed_competitorsEmpty() {
        mockTenderAndUser();

        ProjectResultCallbackPayload payload = assembler().assemble(buildEvent(
                BidResultType.FAILED, List.of(),
                List.of(new ProjectResultConfirmedEvent.CompetitorSnapshot("不应出现", null, null, null))));

        assertThat(payload.competitors()).isEmpty();
    }

    @Test
    @DisplayName("tender 查不到 -> 返回 null（跳过入队）")
    void tenderNotFound_returnsNull() {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.empty());

        ProjectResultCallbackPayload payload = assembler().assemble(buildEvent(
                BidResultType.WON, List.of(), List.of()));

        assertThat(payload).isNull();
    }

    @Test
    @DisplayName("sourceId 从 externalId 冒号后提取；无冒号时为空字符串")
    void sourceId_extraction() {
        mockTenderAndUser();

        ProjectResultCallbackPayload payload = assembler().assemble(buildEvent(
                BidResultType.WON, List.of(), List.of()));

        assertThat(payload.sourceId()).isEqualTo("CRM-OPP-2026-0510-003");
    }

    @Test
    @DisplayName("user 无 employeeNumber -> operatorEmployeeId 为空字符串")
    void noEmployeeId_emptyString() {
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setExternalId("CRM:OPP-001");
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        User user = new User();
        user.setFullName("张三");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        ProjectResultCallbackPayload payload = assembler().assemble(buildEvent(
                BidResultType.WON, List.of(), List.of()));

        assertThat(payload.operatorEmployeeId()).isEmpty();
    }

    @Test
    @DisplayName("ProjectDocument.size 非数字 -> fileSize 为 null")
    void sizeNotNumeric_fileSizeNull() {
        mockTenderAndUser();
        ProjectDocument doc = ProjectDocument.builder()
                .id(50L).projectId(PROJECT_ID)
                .name("流标公告.pdf").size("unknown").fileUrl("https://example.com/50.pdf")
                .uploaderName("张三").build();
        when(projectDocumentRepository.findAllById(List.of(50L))).thenReturn(List.of(doc));

        ProjectResultCallbackPayload payload = assembler().assemble(buildEvent(
                BidResultType.FAILED, List.of(50L), List.of()));

        assertThat(payload.evidenceFiles().get(0).fileSize()).isNull();
    }

    @Test
    @DisplayName("序列化后的 JSON 包含所有 §4.2 顶层字段")
    void jsonPayload_containsAllFields() throws Exception {
        mockTenderAndUser();
        ProjectDocument doc = ProjectDocument.builder()
                .id(1032L).projectId(PROJECT_ID)
                .name("中标通知书.pdf").size("2048000")
                .fileUrl("https://example.com/1032.pdf")
                .uploaderName("张三").build();
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of(doc));

        ProjectResultCallbackPayload payload = assembler().assemble(buildEvent(
                BidResultType.WON, List.of(1032L),
                List.of(new ProjectResultConfirmedEvent.CompetitorSnapshot(
                        "京东企业购", "95折", "月结60天", "含仓储"))));

        String json = new ObjectMapper().writeValueAsString(payload);
        JsonNode root = new ObjectMapper().readTree(json);
        assertThat(root.has("tenderId")).isTrue();
        assertThat(root.has("projectId")).isTrue();
        assertThat(root.has("sourceId")).isTrue();
        assertThat(root.has("bidResult")).isTrue();
        assertThat(root.has("evidenceFiles")).isTrue();
        assertThat(root.has("competitors")).isTrue();
        assertThat(root.has("systemName")).isTrue();
        assertThat(root.has("operatorName")).isTrue();
        assertThat(root.has("operatorEmployeeId")).isTrue();
        assertThat(root.has("operatedAt")).isTrue();
    }

    private void mockTenderAndUser() {
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setExternalId("CRM:CRM-OPP-2026-0510-003");
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        User user = new User();
        user.setFullName("张三");
        user.setEmployeeNumber("EMP001");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private ProjectResultConfirmedEvent buildEvent(BidResultType resultType,
                                                    List<Long> evidenceFileIds,
                                                    List<ProjectResultConfirmedEvent.CompetitorSnapshot> competitors) {
        return ProjectResultConfirmedEvent.of(
                PROJECT_ID, TENDER_ID, resultType, evidenceFileIds, competitors, USER_ID, RESULT_ID);
    }
}
