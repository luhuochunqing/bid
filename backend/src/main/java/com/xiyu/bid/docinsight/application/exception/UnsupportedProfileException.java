// Input: 不受任何 DocumentAnalyzer 支持的 profileCode
// Output: 不支持的分析配置异常，映射到 HTTP 400
// Pos: docinsight/application/exception — 配置不支持场景
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.docinsight.application.exception;

/**
 * 没有任何 {@link com.xiyu.bid.docinsight.application.DocumentAnalyzer} 支持指定 profileCode
 * 时抛出，由 GlobalExceptionHandler 映射到 HTTP 400。
 */
public class UnsupportedProfileException extends DocInsightException {

    private final String profileCode;

    public UnsupportedProfileException(String profileCode) {
        super(400, "不支持的文档分析配置: " + profileCode);
        this.profileCode = profileCode;
    }

    public String getProfileCode() {
        return profileCode;
    }
}
