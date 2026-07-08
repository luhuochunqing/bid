package com.xiyu.bid.file.domain;

/**
 * 文件上传记录状态机。
 *
 * <p>UPLOADING -> UPLOADED -> MD5_CHECKING -> VIRUS_SCANNING -> OCR_PROCESSING -> COMPLETED</p>
 * <p>任意环节失败可进入 FAILED。</p>
 * <p>只有 COMPLETED 状态的文件才允许下载。</p>
 *
 * <p>状态机规则（见 {@link #canTransitionTo(BidFileStatus)}）：</p>
 * <ul>
 *   <li>FAILED 为终态，不可转出</li>
 *   <li>COMPLETED 为成功终态，不可再转（除可转 FAILED）</li>
 *   <li>任意非 FAILED 状态均可转 FAILED</li>
 *   <li>允许跳过中间步骤直接转 COMPLETED</li>
 *   <li>其他转换需满足 ordinal 递增（只能向前，不能后退）</li>
 * </ul>
 */
public enum BidFileStatus {
    UPLOADING,
    UPLOADED,
    MD5_CHECKING,
    VIRUS_SCANNING,
    OCR_PROCESSING,
    COMPLETED,
    FAILED;

    /**
     * 判断从当前状态是否可以转换到目标状态。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>FAILED 为终态，不可转出（返回 false）</li>
     *   <li>COMPLETED 为成功终态，除可转 FAILED 外不可再转</li>
     *   <li>任意非 FAILED 状态可转 FAILED</li>
     *   <li>允许跳过中间步骤直接转 COMPLETED</li>
     *   <li>其他转换需满足 ordinal 递增（只能向前）</li>
     * </ul>
     *
     * @param next 目标状态
     * @return true 表示转换合法
     */
    public boolean canTransitionTo(BidFileStatus next) {
        if (this == FAILED) {
            return false;
        }
        if (next == FAILED) {
            return true;
        }
        if (this == COMPLETED) {
            return false;
        }
        if (next == COMPLETED) {
            return true;
        }
        return this.ordinal() < next.ordinal();
    }

    /**
     * 判断当前状态是否允许下载。
     *
     * <p>只有 COMPLETED 状态的文件才允许生成预签名下载 URL。</p>
     *
     * @return 当前为 COMPLETED 时返回 true
     */
    public boolean isDownloadable() {
        return this == COMPLETED;
    }
}
