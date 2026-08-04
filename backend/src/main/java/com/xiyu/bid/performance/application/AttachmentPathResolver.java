package com.xiyu.bid.performance.application;

import java.nio.file.Path;

/**
 * 附件路径解析端口接口（application 层端口）。
 *
 * <p>由 application 层实现（如 {@code PerformanceAttachmentStorageAppService}），
 * 供 infrastructure 层（如 {@code PerformanceWordBundleBuilder}）依赖。
 *
 * <p><b>为何不放在 domain/port？</b>
 * <p>FP-Java 架构规则禁止 domain 层依赖 IO 类型（如 {@link Path}）。
 * 该接口的方法签名包含 {@link Path}，属于 application 层的端口，
 * 由 infrastructure 层依赖（infrastructure → application 是六边形架构允许的正向依赖）。
 *
 * @since CO-602 PR 设计评估修复（D2-2）
 */
public interface AttachmentPathResolver {

    /**
     * 将附件的 fileUrl（相对或绝对）解析为本地文件系统绝对路径。
     *
     * @param fileUrl 附件 URL（来自数据库存储路径）
     * @return 绝对路径；null 表示 fileUrl 为空或路径穿越被拦截
     */
    Path resolveLocalPath(String fileUrl);
}
