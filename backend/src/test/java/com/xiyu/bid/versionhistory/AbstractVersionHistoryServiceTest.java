package com.xiyu.bid.versionhistory;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.versionhistory.dto.VersionCreateRequest;
import com.xiyu.bid.versionhistory.entity.DocumentVersion;
import com.xiyu.bid.versionhistory.repository.DocumentVersionRepository;
import com.xiyu.bid.versionhistory.service.VersionHistoryAccessGuard;
import com.xiyu.bid.versionhistory.service.VersionHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
abstract class AbstractVersionHistoryServiceTest {

    @Mock
    protected DocumentVersionRepository repository;

    @Mock
    protected IAuditLogService auditLogService;

    @Mock
    protected ProjectAccessScopeService projectAccessScopeService;

    protected VersionHistoryService versionHistoryService;
    protected DocumentVersion testVersion;
    protected DocumentVersion testVersion2;
    protected VersionCreateRequest createRequest;

    @BeforeEach
    void setUpVersionHistoryFixture() {
        versionHistoryService = new VersionHistoryService(
                repository,
                auditLogService,
                new VersionHistoryAccessGuard(projectAccessScopeService)
        );

        testVersion = DocumentVersion.builder()
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

        testVersion2 = DocumentVersion.builder()
                .id(2L)
                .projectId(100L)
                .documentId("doc-001")
                .versionNumber(2)
                .content("Updated content")
                .filePath("/path/to/file.docx")
                .changeSummary("Updated second line")
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
    }
}
