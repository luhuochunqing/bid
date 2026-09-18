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
    void releaseFile_deletesPartAndFreesQuotaByPath() throws IOException {
        Path reserved = store.reserve("task-9");
        Files.write(reserved, "spilled".getBytes());
        store.recordSpillChunk("task-9", 7);
        assertThat(store.getCurrentTotalBytes()).isEqualTo(7);

        store.releaseFile(reserved);

        assertThat(Files.exists(reserved)).isFalse();
        assertThat(store.getCurrentTotalBytes()).isEqualTo(0);
    }

    @Test
    void releaseFile_ignoresNonPartAndNull() {
        store.releaseFile(tempDir.resolve("keep.txt"));
        store.releaseFile(null);
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
    void reserve_throwsWhenCurrentTotalExceedsQuota() {
        TempBidFileStore quotaStore = new TempBidFileStore(tempDir, 100);
        quotaStore.recordSpillChunk("task-1", 60);

        // 已用 60，再写入 50 超过总量 100，抛出 BidFileQuotaException
        org.junit.jupiter.api.Assertions.assertThrows(BidFileQuotaException.class, () -> {
            quotaStore.recordSpillChunk("task-2", 50);
        });

        // 验证失败后 task-2 不占配额，超限回滚后 currentTotalBytes 仍为 60
        assertThat(quotaStore.getCurrentTotalBytes()).isEqualTo(60);
    }

    @Test
    void release_decreasesCurrentTotalBytesAndEnablesReuse() {
        TempBidFileStore quotaStore = new TempBidFileStore(tempDir, 100);
        quotaStore.recordSpillChunk("task-1", 80);
        assertThat(quotaStore.getCurrentTotalBytes()).isEqualTo(80);

        quotaStore.release("task-1");
        assertThat(quotaStore.getCurrentTotalBytes()).isEqualTo(0);

        // 配额释放后，新任务可成功分配
        quotaStore.recordSpillChunk("task-2", 80);
        assertThat(quotaStore.getCurrentTotalBytes()).isEqualTo(80);
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

    @Test
    void cleanupAll_resetsCurrentTotalBytesToZero() throws IOException {
        TempBidFileStore quotaStore = new TempBidFileStore(tempDir, 100);
        Path a = quotaStore.reserve("task-a");
        Files.write(a, "a".getBytes());
        quotaStore.recordSpillChunk("task-a", 50);
        assertThat(quotaStore.getCurrentTotalBytes()).isEqualTo(50);

        quotaStore.cleanupAll();

        assertThat(Files.exists(a)).isFalse();
        assertThat(quotaStore.getCurrentTotalBytes()).isEqualTo(0);
    }
}
