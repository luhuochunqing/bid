package com.xiyu.bid.file.domain;

import com.xiyu.bid.file.entity.BidFile;

import java.util.Objects;

/**
 * 上传完成校验策略（纯核心）。
 *
 * <p>封装上传完成阶段的业务校验规则：归属、状态流转、大小、ETag。
 * 所有校验以 {@link ValidationResult} 返回，不抛异常做业务分支。
 * 符合 FP-Java Profile 第 5 条。</p>
 */
public final class UploadCompletionPolicy {

    /**
     * 校验操作者是否为上传记录的创建者。
     */
    public ValidationResult validateOwnership(BidFile bidFile, Long operatorId) {
        if (!Objects.equals(bidFile.getCreatorId(), operatorId)) {
            return ValidationResult.failure("无权操作该上传记录");
        }
        return ValidationResult.success();
    }

    /**
     * 校验上传记录是否可以流转到 COMPLETED 状态。
     */
    public ValidationResult validateStatusTransition(BidFile bidFile) {
        if (!bidFile.getStatus().canTransitionTo(BidFileStatus.COMPLETED)) {
            return ValidationResult.failure("上传记录状态不正确，当前状态: " + bidFile.getStatus());
        }
        return ValidationResult.success();
    }

    /**
     * 校验文件大小是否匹配。
     *
     * @param expected 期望大小（字节），来自上传记录
     * @param actual   实际大小（字节），来自 OBS 元数据；null 表示对象不存在
     */
    public ValidationResult validateSize(Long expected, Long actual) {
        if (actual == null) {
            return ValidationResult.failure("OBS 对象不存在或无法访问");
        }
        if (!Objects.equals(expected, actual)) {
            return ValidationResult.failure("文件大小不匹配，期望: " + expected + ", 实际: " + actual);
        }
        return ValidationResult.success();
    }

    /**
     * 校验 ETag/MD5 是否匹配。
     * 当期望值或实际值任一为 null 时跳过校验（视为通过）。
     *
     * @param expected 期望的文件 MD5（来自上传记录 fileHash），可为 null
     * @param actual   OBS 返回的 ETag，可为 null
     */
    public ValidationResult validateEtag(String expected, String actual) {
        if (expected != null && actual != null && !actual.equalsIgnoreCase(expected)) {
            return ValidationResult.failure("文件 ETag/MD5 校验失败");
        }
        return ValidationResult.success();
    }
}
