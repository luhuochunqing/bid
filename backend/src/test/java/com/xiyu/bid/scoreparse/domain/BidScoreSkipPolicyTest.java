package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidScoreSkipPolicyTest {

    @Test
    void skipsOnlyWhenBothHashesMatch() {
        assertThat(BidScoreSkipPolicy.shouldSkip("aa", "bb", "aa", "bb")).isTrue();
        assertThat(BidScoreSkipPolicy.shouldSkip("aa", "bb", "aa", "cc")).isFalse();
        assertThat(BidScoreSkipPolicy.shouldSkip("aa", "bb", null, "bb")).isFalse();
    }

    @Test
    void hashesAreStable() {
        String first = BidScoreSkipPolicy.hashBytes("hello".getBytes());
        String second = BidScoreSkipPolicy.hashItems(List.of(
                BidScoreSkipPolicy.itemFingerprint(1L, 10, "资质")));
        assertThat(first).hasSize(64);
        assertThat(first).isEqualTo(BidScoreSkipPolicy.hashBytes("hello".getBytes()));
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void hashFile_matchesHashBytesForSameContent() throws Exception {
        // spec 045 不变式：OnDisk 流式哈希与 InMemory 哈希同口径，
        // 否则 skip 策略对同一文件因加载路径不同而失配
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("skip-hash");
        java.nio.file.Path file = tempDir.resolve("bid.part");
        byte[] content = "同一份投标文件内容".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(file, content);
        try {
            assertThat(BidScoreSkipPolicy.hashFile(file))
                    .isEqualTo(BidScoreSkipPolicy.hashBytes(content));
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }
}
