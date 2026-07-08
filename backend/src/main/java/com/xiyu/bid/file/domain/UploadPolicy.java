package com.xiyu.bid.file.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 上传凭证生成策略（纯核心）。
 *
 * <p>负责生成 uploadId 与构造 OBS objectKey，不依赖任何框架或 IO。
 * 符合 FP-Java Split-First Rule：从 Application Service 拆出的纯核心业务规则。</p>
 */
public final class UploadPolicy {

    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    /**
     * 生成上传 ID（UUID v4）。
     */
    public String generateUploadId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 构造 OBS objectKey：{prefix}/{yyyy/MM}/{uploadId}/{safeFileName}。
     *
     * @param prefix    objectKey 前缀，如 "bids"
     * @param uploadId  上传 ID
     * @param fileName  原始文件名（自动剥离目录部分防穿越）
     * @return 完整 objectKey
     */
    public String buildObjectKey(String prefix, String uploadId, String fileName) {
        String datePath = formatMonthPath(LocalDateTime.now());
        String safeName = stripDirectoryPart(fileName);
        return String.format("%s/%s/%s/%s", prefix, datePath, uploadId, safeName);
    }

    /**
     * 格式化月份路径为 yyyy/MM。
     */
    public String formatMonthPath(LocalDateTime now) {
        return now.format(MONTH_PATH);
    }

    /**
     * 剥离文件名中的目录部分，防止路径穿越（如 ../../../etc/passwd）。
     * 同时兼容 / 与 \ 两种分隔符。
     */
    public String stripDirectoryPart(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int slash = fileName.lastIndexOf('/');
        int backslash = fileName.lastIndexOf('\\');
        int cut = Math.max(slash, backslash);
        return cut >= 0 ? fileName.substring(cut + 1) : fileName;
    }
}
