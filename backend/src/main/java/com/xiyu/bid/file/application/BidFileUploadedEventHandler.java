package com.xiyu.bid.file.application;

import com.xiyu.bid.file.entity.BidFile;
import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.BidFileStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 文件上传到 OBS 后的异步后处理器。
 *
 * <p>当前为占位实现：MD5 校验在 complete 阶段已完成，此处预留病毒扫描、OCR 等扩展点。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidFileUploadedEventHandler {

    private final BidFileRepository bidFileRepository;

    @Async
    @EventListener
    public void handle(BidFileUploadedEvent event) {
        String uploadId = event.getUploadId();
        BidFile bidFile = bidFileRepository.findByUploadId(uploadId).orElse(null);
        if (bidFile == null) {
            log.warn("收到上传完成事件但找不到记录，uploadId={}", uploadId);
            return;
        }

        // Phase 3：CompleteUploadUseCase 已直接转到 COMPLETED，此处跳过后处理。
        // 未来如需病毒扫描/OCR，可在 CompleteUploadUseCase 中恢复 UPLOADED 状态并启用后处理。
        if (bidFile.getStatus().isDownloadable()) {
            log.info("文件已是完成状态，跳过后处理，uploadId={}", uploadId);
            return;
        }

        try {
            // 占位：病毒扫描
            bidFile.transitionTo(BidFileStatus.VIRUS_SCANNING);
            bidFileRepository.save(bidFile);
            // virusScanService.scan(bidFile);

            // 占位：OCR / 内容解析
            bidFile.transitionTo(BidFileStatus.OCR_PROCESSING);
            bidFileRepository.save(bidFile);
            // ocrService.extract(bidFile);

            // 完成
            bidFile.transitionTo(BidFileStatus.COMPLETED);
            bidFileRepository.save(bidFile);

            log.info("文件后处理完成，uploadId={}", uploadId);
        } catch (RuntimeException e) {
            log.error("文件后处理失败，uploadId={}", uploadId, e);
            bidFile.fail(e.getMessage());
            bidFileRepository.save(bidFile);
        }
    }
}
