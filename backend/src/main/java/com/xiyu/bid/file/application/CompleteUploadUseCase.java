package com.xiyu.bid.file.application;

import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.BidFileStatus;
import com.xiyu.bid.file.domain.UploadCompletionPolicy;
import com.xiyu.bid.file.domain.ValidationResult;
import com.xiyu.bid.file.domain.gateway.ObsMetadataGateway;
import com.xiyu.bid.file.dto.UploadCompletedRequest;
import com.xiyu.bid.file.entity.BidFile;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 接收 OBS 上传完成通知用例（应用编排层）。
 *
 * <p>职责仅限编排：取数、调 {@link UploadCompletionPolicy} 做纯校验、
 * 调 {@link ObsMetadataGateway} 获取 OBS 元数据、状态流转、发事件。
 * 业务校验以 {@link ValidationResult} 返回，UseCase 负责转译为异常（R2/R4 修复）。
 * D1-1 修复：Policy 无状态，直接 new，删除 FilePolicyConfig。</p>
 */
@Service
@RequiredArgsConstructor
public class CompleteUploadUseCase {

    private final BidFileRepository bidFileRepository;
    private final ObsMetadataGateway obsMetadataGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final UploadCompletionPolicy uploadCompletionPolicy = new UploadCompletionPolicy();

    @Transactional(noRollbackFor = BusinessException.class)
    public void execute(String uploadId, UploadCompletedRequest request, Long operatorId) {
        BidFile bidFile = bidFileRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传记录不存在"));

        ValidationResult ownership = uploadCompletionPolicy.validateOwnership(bidFile, operatorId);
        if (ownership.isFailure()) {
            throw new SecurityException(ownership.reason());
        }

        ValidationResult status = uploadCompletionPolicy.validateStatusTransition(bidFile);
        if (status.isFailure()) {
            throw new BusinessException(409, status.reason());
        }

        Optional<Long> actualSizeOpt = obsMetadataGateway.getContentLength(request.bucket(), request.objectKey());
        Long actualSize = actualSizeOpt.orElse(null);

        ValidationResult sizeResult = uploadCompletionPolicy.validateSize(bidFile.getFileSize(), actualSize);
        if (sizeResult.isFailure()) {
            bidFile.fail(sizeResult.reason());
            bidFileRepository.save(bidFile);
            throw new BusinessException(409, sizeResult.reason());
        }

        Optional<String> etagOpt = obsMetadataGateway.getEtag(request.bucket(), request.objectKey());
        String etag = etagOpt.orElse(null);

        ValidationResult etagResult = uploadCompletionPolicy.validateEtag(bidFile.getFileHash(), etag);
        if (etagResult.isFailure()) {
            bidFile.fail(etagResult.reason());
            bidFileRepository.save(bidFile);
            throw new BusinessException(409, etagResult.reason());
        }

        // Phase 3：直接转到 COMPLETED，跳过 VIRUS_SCANNING/OCR_PROCESSING 占位后处理。
        // 招标文件场景无需病毒扫描/OCR，且 @Async handler 的延迟会导致前端下载失败。
        bidFile.setObjectKey(request.objectKey());
        bidFile.transitionTo(BidFileStatus.COMPLETED);
        bidFileRepository.save(bidFile);

        eventPublisher.publishEvent(new BidFileUploadedEvent(this, uploadId));
    }
}
