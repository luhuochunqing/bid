// Input: 无（由招标侧文本提取 char 计数超限抛出）
// Output: 语义异常 + 用户可操作文案（业务类拒绝，spec 065）
// Pos: scoreparse/application — 招标文件提取文本量超上限
package com.xiyu.bid.scoreparse.application;

/**
 * 招标文件提取后文本量超过上限（约 500 万字），用户可自救：检查误传或拆分文档。
 * <p>对标打分侧 {@code OversizedBidTextException}；主语为招标文件解析链路。
 * 与 {@code BidFileQuotaException}（资源类，需联系管理员）区分。
 */
public class OversizedTenderTextException extends RuntimeException {

    public static final String MESSAGE = "招标文件文本量超过上限（约 500 万字），无法解析，请检查是否误传或拆分文档";

    OversizedTenderTextException() {
        super(MESSAGE);
    }
}
