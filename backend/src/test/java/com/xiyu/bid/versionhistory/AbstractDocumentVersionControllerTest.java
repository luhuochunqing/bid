package com.xiyu.bid.versionhistory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.versionhistory.controller.DocumentVersionController;
import com.xiyu.bid.versionhistory.dto.DocumentVersionDTO;
import com.xiyu.bid.versionhistory.dto.VersionCreateRequest;
import com.xiyu.bid.versionhistory.dto.VersionDiffDTO;
import com.xiyu.bid.versionhistory.service.VersionHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
abstract class AbstractDocumentVersionControllerTest {

    @Mock
    protected VersionHistoryService versionHistoryService;

    protected MockMvc mockMvc;
    protected ObjectMapper objectMapper;
    protected DocumentVersionDTO testVersionDTO;
    protected DocumentVersionDTO testVersionDTO2;
    protected VersionCreateRequest createRequest;
    protected VersionDiffDTO versionDiffDTO;

    @BeforeEach
    void setUpDocumentVersionControllerFixture() {
        DocumentVersionController controller = new DocumentVersionController(versionHistoryService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();

        testVersionDTO = DocumentVersionDTO.builder()
                .id(1L)
                .projectId(100L)
                .documentId("doc-001")
                .versionNumber(1)
                .content("Initial content")
                .filePath("/path/to/file.docx")
                .changeSummary("Initial version")
                .createdBy(1L)
                .createdAt(LocalDateTime.of(2024, 3, 1, 10, 0))
                .isCurrent(true)
                .build();

        testVersionDTO2 = DocumentVersionDTO.builder()
                .id(2L)
                .projectId(100L)
                .documentId("doc-001")
                .versionNumber(2)
                .content("Updated content")
                .filePath("/path/to/file.docx")
                .changeSummary("Updated version")
                .createdBy(1L)
                .createdAt(LocalDateTime.of(2024, 3, 2, 10, 0))
                .isCurrent(false)
                .build();

        createRequest = VersionCreateRequest.builder()
                .projectId(100L)
                .documentId("doc-001")
                .content("New content")
                .filePath("/path/to/file.docx")
                .changeSummary("New version")
                .createdBy(1L)
                .build();

        versionDiffDTO = VersionDiffDTO.builder()
                .version1Id(1L)
                .version2Id(2L)
                .version1Number(1)
                .version2Number(2)
                .content1("Initial content")
                .content2("Updated content")
                .differences(List.of("Line 1 changed from 'Initial' to 'Updated'"))
                .build();
    }
}
