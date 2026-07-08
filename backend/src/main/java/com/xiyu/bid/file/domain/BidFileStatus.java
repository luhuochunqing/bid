package com.xiyu.bid.file.domain;

/**
 * 文件上传记录状态机。
 *
 * <p>UPLOADING -> UPLOADED -> MD5_CHECKING -> VIRUS_SCANNING -> OCR_PROCESSING -> COMPLETED</p>
 * <p>任意环节失败可进入 FAILED。</p>
 * <p>只有 COMPLETED 状态的文件才允许下载。</p>
 */
public enum BidFileStatus {
    UPLOADING,
    UPLOADED,
    MD5_CHECKING,
    VIRUS_SCANNING,
    OCR_PROCESSING,
    COMPLETED,
    FAILED
}
