package com.xiyu.bid.tenderupload.service;

import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tenderupload.config.TenderProcessingProperties;
import com.xiyu.bid.tenderupload.dto.TenderTaskStatusResponse;
import com.xiyu.bid.tenderupload.dto.TenderUploadCompleteRequest;
import com.xiyu.bid.tenderupload.dto.TenderUploadCompleteResponse;
import com.xiyu.bid.tenderupload.dto.TenderUploadInitRequest;
import com.xiyu.bid.tenderupload.dto.TenderUploadInitResponse;
import com.xiyu.bid.tenderupload.entity.TenderFile;
import com.xiyu.bid.tenderupload.entity.TenderFileUploadStatus;
import com.xiyu.bid.tenderupload.entity.TenderTask;
import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import com.xiyu.bid.tenderupload.repository.TenderFileRepository;
import com.xiyu.bid.tenderupload.repository.TenderTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderUploadTaskServiceTest {

    @Mock
    private TenderFileRepository tenderFileRepository;
    @Mock
    private TenderTaskRepository tenderTaskRepository;
    @Mock
    private AuthService authService;
    @Mock
    private StorageGuardService storageGuardService;
    @Mock
    private UserDetails userDetails;

    private TenderUploadTaskService service;

    @BeforeEach
    void setUp() {
        TenderProcessingProperties properties = new TenderProcessingProperties();
        properties.setStorageRoot("/tmp/shared/tenders");
        properties.setMaxGlobalConcurrency(2);
        properties.setEstimatedTaskSeconds(120);
        service = new TenderUploadTaskService(properties, tenderFileRepository, tenderTaskRepository, authService, storageGuardService);

        when(userDetails.getUsername()).thenReturn("tester");
        when(authService.resolveUserIdByUsername("tester")).thenReturn(1L);
    }

    @Test
    void initUpload_shouldCreateSessionWithoutAbsoluteDirectoryExposure() {
        TenderUploadInitRequest request = new TenderUploadInitRequest();
        request.setFileName("标书A.pdf");
        request.setExpectedFileSize(1024L);

        when(storageGuardService.sanitizeFileName("标书A.pdf")).thenReturn("标书A.pdf");
        when(storageGuardService.resolveAndValidate(any())).thenReturn(Path.of("/tmp/shared/tenders/2026/04/22/1/a.pdf"));

        TenderUploadInitResponse response = service.initUpload(request, userDetails);

        assertEquals("shared-storage", response.getUploadMode());
        assertTrue(response.getRelativePath().contains("标书A.pdf"));
    }

    @Test
    void completeUpload_shouldQueueTaskAndSkipSyncHashComputation() {
        TenderFile file = TenderFile.builder()
                .id(11L)
                .uploadId("up-1")
                .userId(1L)
                .filePath("2026/04/22/1/up-1.pdf")
                .uploadStatus(TenderFileUploadStatus.INITIATED)
                .build();
        TenderTask savedTask = TenderTask.builder()
                .id(99L)
                .file(file)
                .status(TenderTaskStatus.QUEUED)
                .priority(5)
                .attempts(0)
                .availableAt(LocalDateTime.now())
                .build();

        when(tenderFileRepository.findByUploadIdAndUserId("up-1", 1L)).thenReturn(Optional.of(file));
        when(storageGuardService.resolveAndValidate(file.getFilePath())).thenReturn(Path.of("/tmp/shared/tenders/2026/04/22/1/up-1.pdf"));
        when(storageGuardService.fileSize(any())).thenReturn(2048L);
        when(tenderFileRepository.save(any(TenderFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenderTaskRepository.findByFile_Id(11L)).thenReturn(Optional.empty());
        when(tenderTaskRepository.save(any(TenderTask.class))).thenReturn(savedTask);

        TenderUploadCompleteRequest request = new TenderUploadCompleteRequest();
        request.setUploadId("up-1");
        request.setPriority(5);

        TenderUploadCompleteResponse response = service.completeUpload(request, userDetails);

        assertEquals(99L, response.getTaskId());
        assertFalse(response.isDeduplicated());
        assertNull(file.getFileSha256());
        verify(storageGuardService, never()).sha256(any());
    }

    @Test
    void completeUpload_shouldReturnExistingTaskWhenEnqueueInsertConflicts() {
        TenderFile file = TenderFile.builder()
                .id(12L)
                .uploadId("up-2")
                .userId(1L)
                .filePath("2026/04/22/1/up-2.pdf")
                .uploadStatus(TenderFileUploadStatus.INITIATED)
                .build();
        TenderTask existing = TenderTask.builder()
                .id(100L)
                .file(file)
                .status(TenderTaskStatus.QUEUED)
                .priority(5)
                .attempts(0)
                .availableAt(LocalDateTime.now())
                .build();

        when(tenderFileRepository.findByUploadIdAndUserId("up-2", 1L)).thenReturn(Optional.of(file));
        when(storageGuardService.resolveAndValidate(file.getFilePath())).thenReturn(Path.of("/tmp/shared/tenders/2026/04/22/1/up-2.pdf"));
        when(storageGuardService.fileSize(any())).thenReturn(4096L);
        when(tenderFileRepository.save(any(TenderFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenderTaskRepository.findByFile_Id(12L)).thenReturn(Optional.empty(), Optional.of(existing));
        when(tenderTaskRepository.save(any(TenderTask.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        TenderUploadCompleteRequest request = new TenderUploadCompleteRequest();
        request.setUploadId("up-2");

        TenderUploadCompleteResponse response = service.completeUpload(request, userDetails);

        assertEquals(100L, response.getTaskId());
        assertEquals(TenderTaskStatus.QUEUED, response.getStatus());
    }

    @Test
    void getTaskStatus_shouldUseApproximateQueueDepth() {
        TenderFile file = TenderFile.builder().id(13L).userId(1L).filePath("2026/04/test.pdf").build();
        TenderTask task = TenderTask.builder()
                .id(120L)
                .file(file)
                .status(TenderTaskStatus.QUEUED)
                .priority(5)
                .attempts(1)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .updatedAt(LocalDateTime.now())
                .availableAt(LocalDateTime.now())
                .build();

        when(tenderTaskRepository.findById(120L)).thenReturn(Optional.of(task));
        when(tenderTaskRepository.countApproximateQueueDepth(any(LocalDateTime.class), eq(5))).thenReturn(8L);

        TenderTaskStatusResponse response = service.getTaskStatus(120L, userDetails);

        assertEquals(8L, response.getQueuePosition());
        assertEquals(TenderTaskStatus.QUEUED, response.getStatus());
        verify(tenderTaskRepository).countApproximateQueueDepth(any(LocalDateTime.class), eq(5));
    }
}
