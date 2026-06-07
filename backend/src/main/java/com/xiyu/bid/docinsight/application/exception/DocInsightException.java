// Input: 错误码 + 中文消息
// Output: DocInsight 领域基异常；子类按场景映射到具体 HTTP 状态码
// Pos: docinsight/application/exception — 领域异常基类
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.docinsight.application.exception;

/**
 * DocInsight 领域基异常。业务子类继承此类并携带语义化错误码，
 * 由 GlobalExceptionHandler 统一映射到对应的 HTTP 状态码。
 */
public class DocInsightException extends RuntimeException {

    private final int code;

    public DocInsightException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
