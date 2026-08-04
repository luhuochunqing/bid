package com.xiyu.bid.common.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径处理工具类。
 *
 * <p>统一"相对路径按 JVM 工作目录归一化为绝对路径"和"子树校验"逻辑，
 * 避免在多个 Service / AsyncExecutor 中重复实现。
 *
 * @since CO-602 PR 设计评估修复
 */
public final class PathUtils {

    private PathUtils() {}

    /**
     * 将路径字符串归一化为绝对路径。
     * <p>相对路径按 JVM 工作目录解析为绝对路径，并 normalize 去除冗余片段（如 {@code ..}、{@code .}）。
     */
    public static Path resolveAbsolute(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        Path p = Paths.get(path);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p);
        }
        return p.normalize();
    }

    /**
     * 判断 target 是否落在 root 子树内（含 root 自身）。
     * <p>调用前应保证两个路径都已 normalize 为绝对路径。
     */
    public static boolean isWithinSubtree(Path target, Path root) {
        return target.startsWith(root);
    }
}
