package com.xiyu.bid.approval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.approval.dto.ApprovalSubmitRequest;
import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.approval.enums.ApprovalStatus;
import com.xiyu.bid.approval.repository.ApprovalActionRepository;
import com.xiyu.bid.approval.repository.ApprovalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-469 第八轮 P1 审计守卫：ApprovalRequest.attachmentIds 必须写入合法 JSON 数组。
 *
 * 防复发场景：原 impl 用 Collectors.joining(",") 写入 CSV "1,2,3"，
 * 与实体注释 "JSON格式存储" 不符。未来若有人改回 CSV 写法，本测试在 CI 阶段立即失败。
 *
 * 根因（与 PersonnelImportTask 同类）：旧实现绕过 Jackson，违反"写入 JSON 字段必须用 Jackson"的纪律。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalCommandServiceAttachmentIdsSerializationTest {

    private final ObjectMapper validator = new ObjectMapper();

    @Mock
    private ApprovalRequestRepository requestRepository;
    @Mock
    private ApprovalActionRecorder actionRecorder;
    @Mock
    private ApprovalQueryService approvalQueryService;
    @Mock
    private ApprovalActionRepository actionRepository;

    private ApprovalCommandService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalCommandService(
                requestRepository, actionRecorder, approvalQueryService, new ObjectMapper());
    }

    @Test
    void submitForApproval_含attachmentIds_应写入合法JSON数组() throws Exception {
        // given
        ApprovalSubmitRequest request = new ApprovalSubmitRequest();
        request.setProjectId(100L);
        request.setProjectName("测试项目");
        request.setApprovalType("PROJECT");
        request.setApproverId(20L);
        request.setPriority(1);
        request.setTitle("测试审批");
        request.setDescription("描述");
        request.setDueDate(LocalDateTime.now());
        request.setAttachmentIds(List.of(1L, 2L, 3L));

        when(requestRepository.findByProjectIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());
        when(requestRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.submitForApproval(request, 10L, "requester");

        // then
        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).save(captor.capture());
        ApprovalRequest saved = captor.getValue();

        // CO-469 第八轮 P1 守卫：写入的 attachmentIds 必须是合法 JSON 数组
        String attachmentIds = saved.getAttachmentIds();
        assertThat(attachmentIds).isNotNull();
        assertThatNoException().isThrownBy(() -> validator.readTree(attachmentIds));

        JsonNode array = validator.readTree(attachmentIds);
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isEqualTo(3);
        assertThat(array.get(0).asLong()).isEqualTo(1L);
        assertThat(array.get(1).asLong()).isEqualTo(2L);
        assertThat(array.get(2).asLong()).isEqualTo(3L);
    }

    @Test
    void submitForApproval_attachmentIds为空_不应写入字段() {
        // given
        ApprovalSubmitRequest request = new ApprovalSubmitRequest();
        request.setProjectId(102L);
        request.setProjectName("测试项目");
        request.setApprovalType("PROJECT");
        request.setApproverId(20L);
        request.setPriority(1);
        request.setTitle("测试审批");
        request.setDescription("描述");
        request.setDueDate(LocalDateTime.now());
        request.setAttachmentIds(List.of());

        when(requestRepository.findByProjectIdOrderByCreatedAtDesc(102L)).thenReturn(List.of());
        when(requestRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.submitForApproval(request, 10L, "requester");

        // then
        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).save(captor.capture());
        ApprovalRequest saved = captor.getValue();

        // 空 list 不应写入字段，保持 null
        assertThat(saved.getAttachmentIds()).isNull();
    }

    @Test
    void submitForApproval_attachmentIds为null_不应写入字段() {
        // given
        ApprovalSubmitRequest request = new ApprovalSubmitRequest();
        request.setProjectId(103L);
        request.setProjectName("测试项目");
        request.setApprovalType("PROJECT");
        request.setApproverId(20L);
        request.setPriority(1);
        request.setTitle("测试审批");
        request.setDescription("描述");
        request.setDueDate(LocalDateTime.now());
        request.setAttachmentIds(null);

        when(requestRepository.findByProjectIdOrderByCreatedAtDesc(103L)).thenReturn(List.of());
        when(requestRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.submitForApproval(request, 10L, "requester");

        // then
        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).save(captor.capture());
        ApprovalRequest saved = captor.getValue();

        assertThat(saved.getAttachmentIds()).isNull();
    }
}
