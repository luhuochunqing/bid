// Input: 无（由招标侧字节配额超限抛出）
// Output: 语义异常 + 用户可操作文案（资源类拒绝，spec 065）
// Pos: scoreparse/application — 招标文件超出服务器处理配额
package com.xiyu.bid.scoreparse.application;

/**
 * 招标文件过大超出服务器处理配额（单文件超配额或并发总量超限），用户不可自救，需联系管理员。
 * <p>对标打分侧 {@code BidFileQuotaException}；主语为招标文件解析链路。
 * 与 {@code OversizedTenderTextException}（业务类，可自救）区分。
 */
public class TenderFileQuotaException extends RuntimeException {

    public static final String MESSAGE = "招标文件过大超出服务器处理配额，请联系管理员";

    TenderFileQuotaException() {
        super(MESSAGE);
    }
}
