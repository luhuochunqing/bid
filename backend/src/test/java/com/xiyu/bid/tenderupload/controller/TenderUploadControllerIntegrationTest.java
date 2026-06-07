package com.xiyu.bid.tenderupload.controller;

import com.xiyu.bid.tenderupload.dto.TenderTaskStatusResponse;
import com.xiyu.bid.tenderupload.dto.TenderUploadCompleteResponse;
import com.xiyu.bid.tenderupload.dto.TenderUploadInitResponse;
import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import com.xiyu.bid.tenderupload.service.TenderUploadTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenderUploadControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenderUploadTaskService tenderUploadTaskService;

    @Test
    void initUpload_shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/tenders/upload-init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"招标文件.pdf","expectedFileSize":1024}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"USER"})
    void initUpload_shouldRejectUnsupportedRole() throws Exception {
        mockMvc.perform(post("/api/tenders/upload-init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"招标文件.pdf","expectedFileSize":1024}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff", roles = {"STAFF"})
    void initUpload_shouldValidateRequiredFields() throws Exception {
        mockMvc.perform(post("/api/tenders/upload-init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedFileSize":1024}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @WithMockUser(username = "staff", roles = {"STAFF"})
    void initUpload_shouldSupportV1Alias() throws Exception {
        when(tenderUploadTaskService.initUpload(any(), any(UserDetails.class)))
                .thenReturn(TenderUploadInitResponse.builder()
                        .uploadId("up-123")
                        .relativePath("2026/04/22/1/up-123_招标文件.pdf")
                        .uploadMode("shared-storage")
                        .build());

        mockMvc.perform(post("/v1/tenders/upload-init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"招标文件.pdf","expectedFileSize":2048}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadId").value("up-123"))
                .andExpect(jsonPath("$.data.uploadMode").value("shared-storage"));
    }

    @Test
    @WithMockUser(username = "manager", roles = {"MANAGER"})
    void uploadComplete_shouldMapIllegalArgumentTo400() throws Exception {
        when(tenderUploadTaskService.completeUpload(any(), any(UserDetails.class)))
                .thenThrow(new IllegalArgumentException("共享存储中未找到上传文件，请确认上传已完成"));

        mockMvc.perform(post("/api/tenders/upload-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"uploadId":"up-missing","pageCount":120,"priority":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("共享存储中未找到上传文件，请确认上传已完成"));
    }

    @Test
    @WithMockUser(username = "manager", roles = {"MANAGER"})
    void uploadComplete_shouldMapIllegalStateTo409() throws Exception {
        when(tenderUploadTaskService.completeUpload(any(), any(UserDetails.class)))
                .thenThrow(new IllegalStateException("当前上传状态不允许完成操作: FAILED"));

        mockMvc.perform(post("/api/tenders/upload-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"uploadId":"up-1","pageCount":120,"priority":5}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @WithMockUser(username = "staff", roles = {"STAFF"})
    void uploadComplete_shouldReturnQueuedTask() throws Exception {
        when(tenderUploadTaskService.completeUpload(any(), any(UserDetails.class)))
                .thenReturn(TenderUploadCompleteResponse.builder()
                        .fileId(10L)
                        .taskId(88L)
                        .status(TenderTaskStatus.QUEUED)
                        .deduplicated(false)
                        .build());

        mockMvc.perform(post("/api/tenders/upload-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"uploadId":"up-1","pageCount":200,"priority":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value(88))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    @WithMockUser(username = "staff", roles = {"STAFF"})
    void taskStatus_shouldReturnTaskPayload() throws Exception {
        when(tenderUploadTaskService.getTaskStatus(eq(88L), any(UserDetails.class)))
                .thenReturn(TenderTaskStatusResponse.builder()
                        .taskId(88L)
                        .fileId(10L)
                        .status(TenderTaskStatus.RETRYING)
                        .attempts(2)
                        .priority(5)
                        .queuePosition(12L)
                        .estimatedStartAt(LocalDateTime.of(2026, 4, 22, 23, 0))
                        .errorCode("PROCESSING_ERROR")
                        .errorMessage("OCR timeout")
                        .createdAt(LocalDateTime.of(2026, 4, 22, 22, 0))
                        .updatedAt(LocalDateTime.of(2026, 4, 22, 22, 30))
                        .build());

        mockMvc.perform(get("/api/tenders/tasks/{taskId}", 88L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value(88))
                .andExpect(jsonPath("$.data.status").value("RETRYING"))
                .andExpect(jsonPath("$.data.errorCode").value("PROCESSING_ERROR"));

        verify(tenderUploadTaskService).getTaskStatus(eq(88L), any(UserDetails.class));
    }

    @Test
    void taskStatus_shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/tenders/tasks/{taskId}", 88L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff", roles = {"STAFF"})
    void uploadComplete_shouldSupportV1Alias() throws Exception {
        when(tenderUploadTaskService.completeUpload(any(), any(UserDetails.class)))
                .thenReturn(TenderUploadCompleteResponse.builder()
                        .fileId(12L)
                        .taskId(102L)
                        .status(TenderTaskStatus.QUEUED)
                        .deduplicated(false)
                        .build());

        mockMvc.perform(post("/v1/tenders/upload-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"uploadId":"up-v1","pageCount":88,"priority":6}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value(102))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }
}
