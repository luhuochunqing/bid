// Input: 文件存储路径（storagePath）
// Output: 文档不存在异常，映射到 HTTP 404
// Pos: docinsight/application/exception — 文档缺失场景
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.docinsight.application.exception;

/**
 * 指定 storagePath 的文档在存储层不存在时抛出，由 GlobalExceptionHandler 映射到 HTTP 404。
 */
public class DocumentNotFoundException extends DocInsightException {

    private final String storagePath;

    public DocumentNotFoundException(String storagePath) {
        super(404, "文档不存在: " + storagePath);
        this.storagePath = storagePath;
    }

    public String getStoragePath() {
        return storagePath;
    }
}
