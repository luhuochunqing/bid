package com.xiyu.bid.file.application;

import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.BidFileStatus;
import com.xiyu.bid.file.domain.UploadCompletionPolicy;
import com.xiyu.bid.file.domain.gateway.ObsMetadataGateway;
import com.xiyu.bid.file.dto.UploadCompletedRequest;
import com.xiyu.bid.file.entity.BidFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CompleteUploadUseCase 单元测试。
 *
 * <p>Phase 3：验证上传完成后的状态机转换（直接到 COMPLETED）。
 * 重构后：依赖 {@link ObsMetadataGateway} 端口接口而非 infra 实现类（R3 修复）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CompleteUploadUseCaseTest {

    @Mock
    private BidFileRepository bidFileRepository;

    @Mock
    private ObsMetadataGateway obsMetadataGateway;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CompleteUploadUseCase completeUploadUseCase;

    private BidFile bidFile;

    @BeforeEach
    void setUp() {
        // 手动构造，注入真实 UploadCompletionPolicy（纯核心，无依赖）
        completeUploadUseCase = new CompleteUploadUseCase(
                bidFileRepository, obsMetadataGateway, eventPublisher, new UploadCompletionPolicy());
        bidFile = BidFile.builder()
                .id(1L)
                .uploadId("test-upload-123")
                .status(BidFileStatus.UPLOADING)
                .originalName("test.pdf")
                .objectKey("bids/2026/07/test-upload-123/test.pdf")
                .bucket("test-bucket")
                .fileSize(1024L)
                .creatorId(100L)
                .build();
    }

    @Test
    void execute_validUpload_transitionsToCompleted() {
        // Given
        String uploadId = "test-upload-123";
        UploadCompletedRequest request = UploadCompletedRequest.builder()
                .objectKey("bids/2026/07/test-upload-123/test.pdf")
                .etag("test-etag")
                .bucket("test-bucket")
                .build();

        when(bidFileRepository.findByUploadId(uploadId)).thenReturn(Optional.of(bidFile));
        when(obsMetadataGateway.getContentLength("test-bucket", "bids/2026/07/test-upload-123/test.pdf"))
                .thenReturn(Optional.of(1024L));
        when(obsMetadataGateway.getEtag("test-bucket", "bids/2026/07/test-upload-123/test.pdf"))
                .thenReturn(Optional.of("test-etag"));

        // When
        completeUploadUseCase.execute(uploadId, request, 100L);

        // Then: 状态直接转到 COMPLETED（Phase 3 变更）
        ArgumentCaptor<BidFile> captor = ArgumentCaptor.forClass(BidFile.class);
        verify(bidFileRepository).save(captor.capture());
        assertEquals(BidFileStatus.COMPLETED, captor.getValue().getStatus());
        verify(eventPublisher).publishEvent(any(BidFileUploadedEvent.class));
    }

    @Test
    void execute_wrongCreator_throwsSecurityException() {
        UploadCompletedRequest request = UploadCompletedRequest.builder()
                .objectKey("bids/test.pdf")
                .bucket("test-bucket")
                .build();

        when(bidFileRepository.findByUploadId("test-upload-123")).thenReturn(Optional.of(bidFile));

        assertThrows(SecurityException.class, () ->
                completeUploadUseCase.execute("test-upload-123", request, 999L));
    }

    @Test
    void execute_wrongStatus_throwsBusinessException() {
        bidFile.transitionTo(BidFileStatus.COMPLETED);

        UploadCompletedRequest request = UploadCompletedRequest.builder()
                .objectKey("bids/test.pdf")
                .bucket("test-bucket")
                .build();

        when(bidFileRepository.findByUploadId("test-upload-123")).thenReturn(Optional.of(bidFile));

        assertThrows(BusinessException.class, () ->
                completeUploadUseCase.execute("test-upload-123", request, 100L));
    }

    @Test
    void execute_fileSizeMismatch_failsBidFile() {
        UploadCompletedRequest request = UploadCompletedRequest.builder()
                .objectKey("bids/test.pdf")
                .bucket("test-bucket")
                .build();

        when(bidFileRepository.findByUploadId("test-upload-123")).thenReturn(Optional.of(bidFile));
        when(obsMetadataGateway.getContentLength("test-bucket", "bids/test.pdf"))
                .thenReturn(Optional.of(2048L)); // 期望 1024，实际 2048

        assertThrows(BusinessException.class, () ->
                completeUploadUseCase.execute("test-upload-123", request, 100L));

        // Then: 文件被标记为 FAILED
        ArgumentCaptor<BidFile> captor = ArgumentCaptor.forClass(BidFile.class);
        verify(bidFileRepository).save(captor.capture());
        assertEquals(BidFileStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void execute_objectNotFound_failsBidFile() {
        UploadCompletedRequest request = UploadCompletedRequest.builder()
                .objectKey("bids/test.pdf")
                .bucket("test-bucket")
                .build();

        when(bidFileRepository.findByUploadId("test-upload-123")).thenReturn(Optional.of(bidFile));
        when(obsMetadataGateway.getContentLength("test-bucket", "bids/test.pdf"))
                .thenReturn(Optional.empty()); // OBS 对象不存在

        assertThrows(BusinessException.class, () ->
                completeUploadUseCase.execute("test-upload-123", request, 100L));

        // Then: 文件被标记为 FAILED
        ArgumentCaptor<BidFile> captor = ArgumentCaptor.forClass(BidFile.class);
        verify(bidFileRepository).save(captor.capture());
        assertEquals(BidFileStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void execute_uploadNotFound_throwsIllegalArgument() {
        when(bidFileRepository.findByUploadId("not-exist")).thenReturn(Optional.empty());

        UploadCompletedRequest request = UploadCompletedRequest.builder()
                .objectKey("bids/test.pdf")
                .bucket("test-bucket")
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                completeUploadUseCase.execute("not-exist", request, 100L));
    }

    @Test
    void execute_etagMismatch_failsBidFile() {
        // 设置 fileHash 以触发 ETag 校验
        bidFile = BidFile.builder()
                .id(1L)
                .uploadId("test-upload-123")
                .status(BidFileStatus.UPLOADING)
                .originalName("test.pdf")
                .objectKey("bids/2026/07/test-upload-123/test.pdf")
                .bucket("test-bucket")
                .fileSize(1024L)
                .fileHash("expected-md5")
                .creatorId(100L)
                .build();

        UploadCompletedRequest request = UploadCompletedRequest.builder()
                .objectKey("bids/test.pdf")
                .bucket("test-bucket")
                .build();

        when(bidFileRepository.findByUploadId("test-upload-123")).thenReturn(Optional.of(bidFile));
        when(obsMetadataGateway.getContentLength("test-bucket", "bids/test.pdf"))
                .thenReturn(Optional.of(1024L));
        when(obsMetadataGateway.getEtag("test-bucket", "bids/test.pdf"))
                .thenReturn(Optional.of("different-etag"));

        assertThrows(BusinessException.class, () ->
                completeUploadUseCase.execute("test-upload-123", request, 100L));

        // Then: 文件被标记为 FAILED
        ArgumentCaptor<BidFile> captor = ArgumentCaptor.forClass(BidFile.class);
        verify(bidFileRepository).save(captor.capture());
        assertEquals(BidFileStatus.FAILED, captor.getValue().getStatus());
    }
}
