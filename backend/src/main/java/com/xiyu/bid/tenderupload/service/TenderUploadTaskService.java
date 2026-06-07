// Input: 上传会话、文件校验和任务状态查询请求
// Output: upload-init/upload-complete/task-status 用例实现
// Pos: TenderUpload/Service
// 维护声明: 接口编排与幂等保障归此维护，任务执行逻辑在 worker service.
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenderUploadTaskService {

    private final TenderProcessingProperties properties;
    private final TenderFileRepository tenderFileRepository;
    private final TenderTaskRepository tenderTaskRepository;
    private final AuthService authService;
    private final StorageGuardService storageGuardService;

    @Transactional
    public TenderUploadInitResponse initUpload(TenderUploadInitRequest request, UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        String safeName = storageGuardService.sanitizeFileName(request.getFileName());
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        LocalDate today = LocalDate.now();
        String relativePath = Path.of(
                String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue()),
                String.format("%02d", today.getDayOfMonth()),
                String.valueOf(userId),
                uploadId + "_" + safeName
        ).toString();

        storageGuardService.resolveAndValidate(relativePath);
        TenderFile tenderFile = TenderFile.builder()
                .uploadId(uploadId)
                .userId(userId)
                .filePath(relativePath)
                .fileSize(request.getExpectedFileSize())
                .uploadStatus(TenderFileUploadStatus.INITIATED)
                .build();
        tenderFileRepository.save(tenderFile);

        Path absolutePath = properties.storageRootPath().resolve(relativePath).normalize();
        storageGuardService.createParentDirectory(absolutePath);

        return TenderUploadInitResponse.builder()
                .uploadId(uploadId)
                .relativePath(relativePath)
                .uploadMode("shared-storage")
                .build();
    }

    @Transactional
    public TenderUploadCompleteResponse completeUpload(TenderUploadCompleteRequest request, UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        TenderFile file = tenderFileRepository.findByUploadIdAndUserId(request.getUploadId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("上传会话不存在或不属于当前用户"));

        if (file.getUploadStatus() != TenderFileUploadStatus.INITIATED
                && file.getUploadStatus() != TenderFileUploadStatus.COMPLETED) {
            throw new IllegalStateException("当前上传状态不允许完成操作: " + file.getUploadStatus());
        }

        Path absolutePath = storageGuardService.resolveAndValidate(file.getFilePath());
        storageGuardService.ensureExists(absolutePath);
        long actualSize = storageGuardService.fileSize(absolutePath);
        if (actualSize <= 0L) {
            throw new IllegalArgumentException("上传文件为空，无法创建处理任务");
        }

        file.setFileSize(actualSize);
        file.setUploadStatus(TenderFileUploadStatus.COMPLETED);
        if (request.getPageCount() != null && request.getPageCount() > 0) {
            file.setPageCount(request.getPageCount());
        }
        TenderFile savedFile = tenderFileRepository.save(file);
        TenderTask task = enqueueIdempotently(savedFile, normalizePriority(request.getPriority()));
        return buildCompleteResponse(task, savedFile.getUploadStatus() == TenderFileUploadStatus.DUPLICATE);
    }

    @Transactional(readOnly = true)
    public TenderTaskStatusResponse getTaskStatus(Long taskId, UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        TenderTask task = tenderTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        if (!Objects.equals(task.getFile().getUserId(), userId)) {
            throw new IllegalArgumentException("无权限查看当前任务");
        }

        long queuePosition = 0L;
        LocalDateTime estimate = null;
        if (task.getStatus() == TenderTaskStatus.QUEUED || task.getStatus() == TenderTaskStatus.RETRYING) {
            queuePosition = tenderTaskRepository.countApproximateQueueDepth(
                    LocalDateTime.now(),
                    task.getPriority()
            );
            int concurrency = Math.max(1, properties.getMaxGlobalConcurrency());
            long waitSeconds = Math.max(0L, queuePosition) * properties.getEstimatedTaskSeconds() / concurrency;
            estimate = LocalDateTime.now().plusSeconds(waitSeconds);
        }

        return TenderTaskStatusResponse.builder()
                .taskId(task.getId())
                .fileId(task.getFile().getId())
                .status(task.getStatus())
                .attempts(task.getAttempts())
                .priority(task.getPriority())
                .queuePosition(queuePosition)
                .estimatedStartAt(estimate)
                .errorCode(task.getErrorCode())
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private TenderUploadCompleteResponse buildCompleteResponse(TenderTask task, boolean deduplicated) {
        return TenderUploadCompleteResponse.builder()
                .fileId(task.getFile().getId())
                .taskId(task.getId())
                .status(task.getStatus())
                .deduplicated(deduplicated)
                .build();
    }

    private int normalizePriority(Integer priority) {
        if (priority == null) {
            return 5;
        }
        return Math.max(1, Math.min(10, priority));
    }

    private Long currentUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new IllegalArgumentException("无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }

    private TenderTask enqueueIdempotently(TenderFile file, int priority) {
        TenderTask existing = tenderTaskRepository.findByFile_Id(file.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        try {
            TenderTask task = TenderTask.builder()
                    .file(file)
                    .status(TenderTaskStatus.QUEUED)
                    .priority(priority)
                    .attempts(0)
                    .availableAt(LocalDateTime.now())
                    .build();
            return tenderTaskRepository.save(task);
        } catch (DataIntegrityViolationException ex) {
            return tenderTaskRepository.findByFile_Id(file.getId())
                    .orElseThrow(() -> new IllegalStateException("任务入队冲突后未找到任务", ex));
        }
    }
}
