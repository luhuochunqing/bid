package com.xiyu.bid.projectworkflow.controller;

import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDownloadFile;
import com.xiyu.bid.projectworkflow.service.ProjectWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectDocumentControllerTest {

    @Mock
    private ProjectWorkflowService projectWorkflowService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProjectDocumentController(projectWorkflowService))
                .build();
    }

    @Test
    void downloadProjectDocument_ShouldReturnInlineStreamForLocalFile() throws Exception {
        byte[] bytes = "附件内容".getBytes(StandardCharsets.UTF_8);
        when(projectWorkflowService.getProjectDocumentFile(1001L, 3003L))
                .thenReturn(new ProjectDocumentDownloadFile(
                        "task.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        (long) bytes.length,
                        new ByteArrayResource(bytes),
                        null
                ));

        mockMvc.perform(get("/api/projects/1001/documents/3003/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''task.docx")))
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(content().bytes(bytes));

        verify(projectWorkflowService).getProjectDocumentFile(1001L, 3003L);
        verify(projectWorkflowService, never()).getProjectDocuments(1001L);
    }

    @Test
    void downloadProjectDocument_ShouldReturn302RedirectForObsDirectFile() throws Exception {
        // 正式环境 id=86 场景：OBS 直传文件返回 302 + Location 重定向到预签名 URL
        String signedUrl = "https://obs.example.com/bids/2026/07/招标文件.pdf?signature=xxx&expires=3600";
        when(projectWorkflowService.getProjectDocumentFile(1001L, 86L))
                .thenReturn(new ProjectDocumentDownloadFile("招标文件.pdf", null, null, null, signedUrl));

        mockMvc.perform(get("/api/projects/1001/documents/86/download"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", signedUrl))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''%E6%8B%9B%E6%A0%87%E6%96%87%E4%BB%B6.pdf")));
    }
}
