package com.xiyu.bid.file.application;

import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.file.entity.BidFile;
import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.BidFileStatus;
import com.xiyu.bid.file.dto.UploadCompletedRequest;
import com.xiyu.bid.file.infrastructure.obs.ObsMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 接收 OBS 上传完成通知用例。
 */
@Service
@RequiredArgsConstructor
public class CompleteUploadUseCase {

    private final BidFileRepository bidFileRepository;
    private final ObsMetadataService obsMetadataService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(noRollbackFor = BusinessException.class)
    public void execute(String uploadId, UploadCompletedRequest request, Long operatorId) {
        BidFile bidFile = bidFileRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传记录不存在"));

        if (!Objects.equals(bidFile.getCreatorId(), operatorId)) {
            throw new SecurityException("无权操作该上传记录");
        }

        if (bidFile.getStatus() != BidFileStatus.UPLOADING) {
            throw new BusinessException(409, "上传记录状态不正确，当前状态: " + bidFile.getStatus());
        }

        Long actualSize = obsMetadataService.getContentLength(request.getBucket(), request.getObjectKey());
        if (actualSize == null) {
            bidFile.fail("OBS 对象不存在");
            bidFileRepository.save(bidFile);
            throw new BusinessException(409, "OBS 对象不存在或无法访问");
        }

        if (!Objects.equals(actualSize, bidFile.getFileSize())) {
            bidFile.fail("文件大小不匹配，期望: " + bidFile.getFileSize() + ", 实际: " + actualSize);
            bidFileRepository.save(bidFile);
            throw new BusinessException(409, "文件大小校验失败");
        }

        String etag = obsMetadataService.getEtag(request.getBucket(), request.getObjectKey());
        if (bidFile.getFileHash() != null && etag != null && !etag.equalsIgnoreCase(bidFile.getFileHash())) {
            bidFile.fail("文件 ETag/MD5 校验失败");
            bidFileRepository.save(bidFile);
            throw new BusinessException(409, "文件 MD5 校验失败");
        }

        // Phase 3：直接转到 COMPLETED，跳过 VIRUS_SCANNING/OCR_PROCESSING 占位后处理。
        // 招标文件场景无需病毒扫描/OCR，且 @Async handler 的延迟会导致前端下载失败。
        bidFile.setObjectKey(request.getObjectKey());
        bidFile.transitionTo(BidFileStatus.COMPLETED);
        bidFileRepository.save(bidFile);

        eventPublisher.publishEvent(new BidFileUploadedEvent(this, uploadId));
    }
}
