package com.xiyu.bid.projectworkflow.dto;

import org.springframework.core.io.Resource;

/**
 * 项目文档下载结果 DTO。
 *
 * <p>表达两种互斥下载形态（由调用方判断）：
 * <ul>
 *   <li>inline 流式下载：{@code redirectUrl} 为 null，使用 {@code resource} / {@code contentType} / {@code contentLength}</li>
 *   <li>302 重定向：{@code redirectUrl} 不为 null，{@code resource} 为 null</li>
 * </ul>
 *
 * <p>不使用 sealed interface 以减小 DTO 文件数量；调用方通过 {@code redirectUrl} 是否为空做分派。
 *
 * @param fileName      文件名（Content-Disposition attachment filename）
 * @param contentType   MIME 类型（inline 场景使用）
 * @param contentLength 内容字节长度（inline 场景使用）
 * @param resource      文件内容 Resource（inline 场景使用）
 * @param redirectUrl   OBS 预签名下载 URL（重定向场景使用，inline 时为 null）
 */
public record ProjectDocumentDownloadFile(
        String fileName,
        String contentType,
        Long contentLength,
        Resource resource,
        String redirectUrl
) {
}
