package com.xiyu.bid.docinsight.application;

import java.util.Optional;

public interface DocumentStorage {
    StoredDocument store(String category, String entityId, String fileName, String contentType, byte[] content);
    Optional<byte[]> load(String storagePath);

    /**
     * 根据 storagePath 查找已存储文档的元数据（fileUrl + contentHash）。
     * 若文件不存在或元数据无法推导，返回 {@link Optional#empty()}。
     */
    Optional<StoredDocument> lookup(String storagePath);
}
