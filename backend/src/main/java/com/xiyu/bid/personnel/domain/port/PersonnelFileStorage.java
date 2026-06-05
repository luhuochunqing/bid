package com.xiyu.bid.personnel.domain.port;

/**
 * 人员证书附件存储端口（基础设施）。
 * 用于 "新增证书" / "编辑证书" h5 中 Tab3 证书附件的文件落盘。
 * 返回可用于 attachmentUrl 的可下载路径。
 * 纯接口不依赖 Spring MultipartFile / java.io（FP-Java 纯核心约束），使用 byte[] 作为中立内容载体（≤10MB 文件可接受）。
 */
public interface PersonnelFileStorage {

    /**
     * 存储指定人员的某张证书的附件文件。
     * @param content 文件字节内容
     * @param originalFilename 原始文件名（用于扩展名/审计）
     * @param contentType MIME 类型
     * @return 可访问的 attachmentUrl（相对路径，由 controller 提供下载端点）。
     */
    String storeCertAttachment(Long personnelId, Long certId, byte[] content, String originalFilename, String contentType);
}
