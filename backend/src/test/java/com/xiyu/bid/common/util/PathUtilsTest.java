package com.xiyu.bid.common.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PathUtils} 单元测试。
 *
 * @since CO-602 PR 设计评估修复
 */
class PathUtilsTest {

    @Test
    void resolveAbsolute_绝对路径_原样返回并normalize() {
        Path result = PathUtils.resolveAbsolute("/data/exports/../exports/./file.docx");
        assertThat(result).isEqualTo(Paths.get("/data/exports/file.docx"));
        assertThat(result.isAbsolute()).isTrue();
    }

    @Test
    void resolveAbsolute_相对路径_按userDir解析为绝对路径() {
        Path result = PathUtils.resolveAbsolute("tmp/exports/file.docx");
        assertThat(result.isAbsolute()).isTrue();
        assertThat(result.toString()).endsWith("tmp/exports/file.docx");
    }

    @Test
    void resolveAbsolute_空字符串_抛异常() {
        assertThatThrownBy(() -> PathUtils.resolveAbsolute(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void resolveAbsolute_null_抛异常() {
        assertThatThrownBy(() -> PathUtils.resolveAbsolute(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveAbsolute_纯空白_抛异常() {
        assertThatThrownBy(() -> PathUtils.resolveAbsolute("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isWithinSubtree_target在root下_返回true() {
        Path root = Paths.get("/data/exports").toAbsolutePath();
        Path target = root.resolve("sub/file.docx");
        assertThat(PathUtils.isWithinSubtree(target, root)).isTrue();
    }

    @Test
    void isWithinSubtree_target等于root_返回true() {
        Path root = Paths.get("/data/exports").toAbsolutePath();
        assertThat(PathUtils.isWithinSubtree(root, root)).isTrue();
    }

    @Test
    void isWithinSubtree_target在root外_返回false() {
        Path root = Paths.get("/data/exports").toAbsolutePath();
        Path target = Paths.get("/data/other").toAbsolutePath();
        assertThat(PathUtils.isWithinSubtree(target, root)).isFalse();
    }

    @Test
    void isWithinSubtree_前缀匹配但不分隔_返回false() {
        // /data/exports-evil 不在 /data/exports 子树内（startsWith 基于路径段）
        Path root = Paths.get("/data/exports").toAbsolutePath();
        Path target = Paths.get("/data/exports-evil").toAbsolutePath();
        assertThat(PathUtils.isWithinSubtree(target, root)).isFalse();
    }
}
