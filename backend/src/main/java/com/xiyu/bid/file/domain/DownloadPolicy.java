package com.xiyu.bid.file.domain;

import com.xiyu.bid.file.entity.BidFile;

import java.util.Objects;

/**
 * 下载链接生成策略（纯核心）。
 *
 * <p>封装下载前的业务校验与过期时间约束：
 * 归属校验、状态校验（仅 COMPLETED 可下载）、过期秒数 clamp 到 [60, 3600]。
 * 校验以 {@link ValidationResult} 返回，不抛异常做业务分支。</p>
 */
public final class DownloadPolicy {

    /** 下载链接最小有效期（秒）。 */
    public static final int MIN_EXPIRE_SECONDS = 60;
    /** 下载链接最大有效期（秒）。 */
    public static final int MAX_EXPIRE_SECONDS = 3600;

    /**
     * 校验是否允许下载该文件。
     */
    public ValidationResult validateDownload(BidFile bidFile, Long operatorId) {
        if (!Objects.equals(bidFile.getCreatorId(), operatorId)) {
            return ValidationResult.failure("无权访问该文件");
        }
        if (!bidFile.getStatus().isDownloadable()) {
            return ValidationResult.failure("文件尚未处理完成，当前状态: " + bidFile.getStatus());
        }
        return ValidationResult.success();
    }

    /**
     * 将过期秒数约束到 [60, 3600] 区间。
     */
    public int clampExpireSeconds(int seconds) {
        int clamped = Math.min(seconds, MAX_EXPIRE_SECONDS);
        return Math.max(clamped, MIN_EXPIRE_SECONDS);
    }
}
