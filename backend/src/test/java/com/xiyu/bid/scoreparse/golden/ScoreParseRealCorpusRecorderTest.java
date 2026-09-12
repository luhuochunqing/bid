// Input: local real-corpus markdown under XIYU_EVAL_CORPUS_DIR (explicit recorder mode only)
// Output: candidates.json snapshot for Incident1358RealCorpusGoldenTest
// Pos: scoreparse/golden — test-only recorder, never stores real customer documents in repository
package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实语料金标候选快照录制器。
 *
 * <p>默认不运行；只有显式设置 {@code XIYU_RECORD_SCOREPARSE_CORPUS=true} 才会写本地文件。
 * 输入是已经由 sidecar / 文档提取链导出的 markdown 文本，真实客户文件和原文均必须位于
 * {@code XIYU_EVAL_CORPUS_DIR} 指向的本地目录，禁止入仓。
 */
@EnabledIfEnvironmentVariable(named = "XIYU_RECORD_SCOREPARSE_CORPUS", matches = "true")
class ScoreParseRealCorpusRecorderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void recordCandidatesSnapshotFromLocalMarkdown() throws Exception {
        Path corpusRoot = requiredPath("XIYU_EVAL_CORPUS_DIR");
        String caseId = envOrDefault("XIYU_SCOREPARSE_REAL_CASE", "incident-1358");
        Path caseDir = corpusRoot.resolve(caseId);
        Path markdownPath = optionalPath("XIYU_SCOREPARSE_MARKDOWN", caseDir.resolve("source.md"));
        Path outputPath = optionalPath("XIYU_SCOREPARSE_CANDIDATES", caseDir.resolve("candidates.json"));

        assertThat(Files.isRegularFile(markdownPath))
                .as("真实语料 markdown 必须先放在本地目录，禁止入仓: %s", markdownPath)
                .isTrue();

        String markdown = Files.readString(markdownPath, StandardCharsets.UTF_8);
        assertThat(markdown).as("真实语料 markdown 不能为空").isNotBlank();

        GoldenCasesReplaySupport.CandidatePool pool = GoldenCasesReplaySupport.collectCandidates(markdown);
        List<ScoreCandidate> candidates = pool.allCandidates();
        assertThat(candidates).as("录制候选不得为空；若为空说明输入不是评分章节或提取链异常").isNotEmpty();

        Files.createDirectories(outputPath.getParent());
        MAPPER.writeValue(outputPath.toFile(), candidates);

        System.out.printf(
                "[scoreparse-recorder] wrote %s candidates to %s (regex=%s regexFullText=%s table=%s)%n",
                candidates.size(),
                outputPath.toAbsolutePath(),
                pool.regexSectionsRaw(),
                pool.regexFullTextRaw(),
                pool.tableCandidates().size());
    }

    private static Path requiredPath(String envName) {
        String value = System.getenv(envName);
        assertThat(value)
                .as("%s 必须指向本地真实语料目录", envName)
                .isNotBlank();
        Path path = Path.of(value);
        assertThat(Files.isDirectory(path))
                .as("%s 必须是目录: %s", envName, path)
                .isTrue();
        return path;
    }

    private static Path optionalPath(String envName, Path defaultPath) {
        String value = System.getenv(envName);
        return value == null || value.isBlank() ? defaultPath : Path.of(value);
    }

    private static String envOrDefault(String envName, String defaultValue) {
        String value = System.getenv(envName);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
