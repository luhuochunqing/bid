// Input: 无（由 SidecarStreamTextExtractor char 计数超限抛出）
// Output: 语义异常 + 用户可操作文案（业务类拒绝，FR-004）
// Pos: scoreparse/application — 投标文件提取文本量超上限（spec 045）
package com.xiyu.bid.scoreparse.application;

/**
 * 投标文件提取后文本量超过上限（约 500 万字），用户可自救：检查误传或拆分文档。
 * <p>与 BidFileQuotaException（资源类）区分：本类是业务类拒绝，用户可执行动作不同
 * （contracts/api-error-semantics.md 语义类别表）。
 */
public class OversizedBidTextException extends RuntimeException {

    public static final String MESSAGE = "投标文件文本量超过上限（约 500 万字），无法完成打分，请检查是否误传或拆分文档";

    OversizedBidTextException() {
        super(MESSAGE);
    }
}
