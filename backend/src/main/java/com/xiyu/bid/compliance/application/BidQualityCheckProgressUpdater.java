package com.xiyu.bid.compliance.application;

import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 标书质量核查任务进度与状态独立事务更新器.
 * 每个状态与进度更新均在独立事务（REQUIRES_NEW）中立即提交，确保前端轮询秒级感知.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidQualityCheckProgressUpdater {

    private final ComplianceCheckResultRepository resultRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(
            final Long resultId,
            final ComplianceCheckResult.TaskStatus status,
            final int progress,
            final String currentStep) {
        resultRepository.findById(resultId).ifPresent(entity -> {
            entity.setStatus(status);
            entity.setProgress(progress);
            entity.setCurrentStep(currentStep);
            resultRepository.save(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFileMetadata(
            final Long resultId,
            final String bidFileName,
            final String bidFileHash,
            final String tenderFileHash) {
        resultRepository.findById(resultId).ifPresent(entity -> {
            entity.setBidFileName(bidFileName);
            entity.setBidFileHash(bidFileHash);
            entity.setTenderFileHash(tenderFileHash);
            resultRepository.save(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTask(
            final Long resultId,
            final ComplianceCheckResult.Status overallStatus,
            final int riskScore,
            final String checkDetailsJson,
            final int passed,
            final int failed,
            final int manual,
            final int total) {
        resultRepository.findById(resultId).ifPresent(entity -> {
            entity.setOverallStatus(overallStatus);
            entity.setRiskScore(riskScore);
            entity.setCheckDetails(checkDetailsJson);
            entity.setPassedCount(passed);
            entity.setFailedCount(failed);
            entity.setManualReviewCount(manual);
            entity.setTotalCount(total);
            entity.setCheckedAt(LocalDateTime.now());
            entity.setStatus(ComplianceCheckResult.TaskStatus.COMPLETED);
            entity.setProgress(100);
            entity.setCurrentStep("核查完成");
            resultRepository.save(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyCachedResult(final Long resultId, final ComplianceCheckResult cached) {
        resultRepository.findById(resultId).ifPresent(entity -> {
            entity.setBidFileName(cached.getBidFileName());
            entity.setBidFileHash(cached.getBidFileHash());
            entity.setTenderFileHash(cached.getTenderFileHash());
            entity.setOverallStatus(cached.getOverallStatus());
            entity.setRiskScore(cached.getRiskScore());
            entity.setCheckDetails(cached.getCheckDetails());
            entity.setPassedCount(cached.getPassedCount());
            entity.setFailedCount(cached.getFailedCount());
            entity.setManualReviewCount(cached.getManualReviewCount());
            entity.setTotalCount(cached.getTotalCount());
            entity.setCheckedAt(LocalDateTime.now());
            entity.setStatus(ComplianceCheckResult.TaskStatus.COMPLETED);
            entity.setProgress(100);
            entity.setCurrentStep("核查完成（复用缓存）");
            resultRepository.save(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final Long resultId, final String errorMessage) {
        resultRepository.findById(resultId).ifPresent(entity -> {
            entity.setStatus(ComplianceCheckResult.TaskStatus.FAILED);
            entity.setProgress(100);
            entity.setCurrentStep("核查失败");
            entity.setErrorMessage(errorMessage != null ? errorMessage : "核查过程中发生未知异常");
            resultRepository.save(entity);
        });
    }
}
