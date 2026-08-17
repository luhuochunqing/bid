// Input: taskId（临时文件登记/释放/全清）
// Output: reserve 路径约定 / release 幂等删除 / cleanupAll 清理断言
// Pos: scoreparse/application — TempBidFileStore 单元测试（spec 045 T003）
package com.xiyu.bid.scoreparse.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TempBidFileStoreTest {

    @TempDir
    Path tempDir;

    private TempBidFileStore store;

    @BeforeEach
    void setUp() {
        store = new TempBidFileStore(tempDir);
    }

    @Test
    void reserve_returnsPartPathUnderTempDir() {
        Path reserved = store.reserve("task-1");

        assertThat(reserved).isEqualTo(tempDir.resolve("task-1.part"));
    }

    @Test
    void release_deletesFileAndIsIdempotent() throws IOException {
        Path reserved = store.reserve("task-1");
        Files.write(reserved, "spilled-content".getBytes());
        assertThat(Files.exists(reserved)).isTrue();

        store.release("task-1");
        assertThat(Files.exists(reserved)).isFalse();

        // 二次 release 幂等 no-op，不抛异常
        store.release("task-1");
        assertThat(Files.exists(reserved)).isFalse();
    }

    @Test
    void cleanupAll_deletesAllPartFilesOnly() throws IOException {
        Path a = store.reserve("task-a");
        Path b = store.reserve("task-b");
        Files.write(a, "a".getBytes());
        Files.write(b, "b".getBytes());
        // 非 .part 文件不受影响
        Path keep = tempDir.resolve("keep.txt");
        Files.write(keep, "keep".getBytes());

        store.cleanupAll();

        assertThat(Files.exists(a)).isFalse();
        assertThat(Files.exists(b)).isFalse();
        assertThat(Files.exists(keep)).isTrue();
    }
}
