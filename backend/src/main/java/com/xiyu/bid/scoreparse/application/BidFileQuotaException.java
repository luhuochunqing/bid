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

    public BidFileQuotaException(long actualBytes, long maxBytes) {
        super("标书文件大小至少为 " + actualBytes + " 字节，超过当前处理配额上限 " + maxBytes
                + " 字节。请拆分或精简文件后重新生成，或联系管理员。");
    }

    BidFileQuotaException() {
        super(MESSAGE);
    }
}
