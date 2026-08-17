// Input: 无（由 StreamingBidFileFetcher / TempBidFileStore 配额超限抛出）
// Output: 语义异常 + 用户可操作文案（资源类拒绝，FR-002）
// Pos: scoreparse/application — 投标文件超出服务器处理配额（spec 045）
package com.xiyu.bid.scoreparse.application;

/**
 * 投标文件过大超出服务器处理配额（单文件 >2GB 或并发总量 >4GB），用户不可自救，需联系管理员。
 * <p>与 OversizedBidTextException（业务类）区分：本类是资源类拒绝
 * （contracts/api-error-semantics.md 语义类别表）。
 */
public class BidFileQuotaException extends RuntimeException {

    public static final String MESSAGE = "投标文件过大超出服务器处理配额，请联系管理员";

    BidFileQuotaException() {
        super(MESSAGE);
    }
}
