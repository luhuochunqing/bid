package com.xiyu.bid.shared.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件路径安全校验工具，防止路径遍历攻击。
 *
 * <p>提供双重防护：
 * <ul>
 *   <li>文件名特征检查（不含 / \ ..）</li>
 *   <li>路径规范化 + startsWith 边界验证</li>
 * </ul>
 */
public final class FilePathGuard {

    private FilePathGuard() {
    }

    /**
     * 校验文件名是否安全（不含路径分隔符或遍历字符）。
     *
     * @param fileName 待校验的文件名
     * @return true 表示安全，false 表示包含危险字符
     */
    public static boolean isSafeFileName(String fileName) {
        return fileName != null
                && !fileName.contains("/")
                && !fileName.contains("\\")
                && !fileName.contains("..");
    }

    /**
     * 解析并验证文件路径，确保规范化后的路径在指定基础目录内。
     *
     * @param rawPath  原始路径
     * @param baseDir  允许的基础目录
     * @return 规范化后的安全路径
     * @throws IllegalArgumentException 路径为空或越界
     */
    public static Path resolveWithin(String rawPath, String baseDir) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("文件路径为空");
        }
        Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(rawPath).toAbsolutePath().normalize();
        if (!filePath.startsWith(basePath)) {
            throw new IllegalArgumentException("文件路径越界");
        }
        return filePath;
    }

    /**
     * 解析并验证绝对文件路径，确保规范化后的路径在指定基础目录内。
     * 适用于路径已经是绝对路径的场景（如数据库存储的 filePath）。
     *
     * @param rawPath  原始绝对路径
     * @param baseDir  允许的基础目录
     * @return 规范化后的安全路径
     * @throws IllegalArgumentException 路径为空或越界
     */
    public static Path resolveAbsoluteWithin(String rawPath, String baseDir) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("文件路径为空");
        }
        Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        Path filePath = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!filePath.startsWith(basePath)) {
            throw new IllegalArgumentException("文件路径越界");
        }
        return filePath;
    }

    /**
     * 确保文件存在，不存在则抛出异常。
     *
     * @param filePath 待检查的路径
     * @param rawPath  原始路径（用于错误信息）
     * @return 已验证存在的路径
     * @throws IllegalArgumentException 文件不存在
     */
    public static Path ensureExists(Path filePath, String rawPath) {
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("文件物理路径不存在: " + rawPath);
        }
        return filePath;
    }
}
