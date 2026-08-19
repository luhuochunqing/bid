// Input: 无（由 SidecarImageExtractor 响应字节计数超限抛出）
// Output: 语义异常（调用层降级纯文本，不阻断打分任务，FR-005/Principle VII）
// Pos: scoreparse/application — 图片抽取响应超上限（spec 048）
package com.xiyu.bid.scoreparse.application;

/**
 * 投标文件图片抽取响应整包超过 max-extract-response-bytes。
 * <p>与 OversizedBidTextException 语义不同：本异常不打断打分任务，
 * 调用层捕获后该任务降级纯文本打分（log.warn）。
 */
public class OversizedImageResponseException extends RuntimeException {

    public static final String MESSAGE = "投标文件图片抽取响应超过上限，图片评分降级为纯文本";

    OversizedImageResponseException() {
        super(MESSAGE);
    }
}
