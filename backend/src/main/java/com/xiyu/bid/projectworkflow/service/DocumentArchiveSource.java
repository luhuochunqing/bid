package com.xiyu.bid.projectworkflow.service;

/**
 * 文档归档来源信息（内部使用，不暴露到 JSON API）。
 *
 * <p>spec 039：multipart 上传路径在创建文档时已知本地物理路径和真实字节数，
 * 通过本对象透传给归档逻辑，保证 archive_file.file_path 是可下载的物理路径、
 * file_size 是真实大小；OBS 直传 JSON 路径无本地文件，传 null 时归档降级为
 * fileUrl（obs-direct: 伪协议，由 ArchiveFileResponseFactory 302 签发下载）和
 * {@code ARCHIVE_FILE_SIZE_UNKNOWN}。
 */
record DocumentArchiveSource(String physicalPath, Long sizeBytes) {
}
