// Input: allowlisted document extraction failure code
// Output: safe actionable scoring failure; never includes upstream response details
// Pos: scoreparse/application — document boundary error transport
package com.xiyu.bid.scoreparse.application;

public class BidTextExtractionException extends IllegalStateException {
    public BidTextExtractionException(String code) {
        super(switch (code) {
            case "BID_OCR_UNAVAILABLE" -> "扫描件 OCR 服务不可用，请联系管理员检查中文识别组件后重试";
            case "BID_OCR_TIMEOUT" -> "扫描件 OCR 未在时限内完成，未使用部分正文打分，请联系管理员调整处理资源后重试";
            case "BID_OCR_EMPTY" -> "扫描页未识别出有效文字，未进行打分，请核对扫描清晰度";
            default -> "投标文件未提取出有效正文，未进行打分，请联系管理员检查文档识别服务";
        });
    }
}
