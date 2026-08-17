// Input: 无（由 ScoreBidDocumentLookup 大小预检抛出）
// Output: 语义异常 + 用户可操作文案
// Pos: scoreparse/application — 投标文件超 50MB 上限（OOM 根因修复）
package com.xiyu.bid.scoreparse.application;

/**
 * 投标文件超过解析上限（50MB），需用户压缩后重新上传。
 * <p>与 BoundedHttpDownloader.TOO_LARGE_MESSAGE（招标文件链路）区分：
 * 投标文件链路用异常类型识别，避免消息字符串匹配的脆弱性。
 */
public class OversizedBidFileException extends RuntimeException {

    public static final String MESSAGE = "投标文件超过 50MB，无法完成打分，请压缩后重新上传";

    OversizedBidFileException() {
        super(MESSAGE);
    }
}
