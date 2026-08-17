// Input: taskId（临时文件登记/释放/全清）
// Output: {tempDir}/{taskId}.part 临时文件管理（单一所有权）
// Pos: scoreparse/application — spec 045 FR-003 临时文件命名约定（data-model.md §TempBidFileStore）
package com.xiyu.bid.scoreparse.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 投标文件临时落盘管理：{tempDir}/{taskId}.part 命名约定。
 * <p>OnDisk 临时文件的删除责任唯一归属本类（release）；调用方在任务终态/异常路径调用 release。
 * 配额（max-total-bytes）属 US2 范围，本类预留计数扩展点。
 */
@Component
@Slf4j
public class TempBidFileStore {

    private final Path tempDir;

    @Autowired
    public TempBidFileStore(
            @Value("${app.score-parse.bid-file.temp-dir:${java.io.tmpdir}/xiyu-score-parse}") String tempDir) {
        this(Path.of(tempDir));
    }

    TempBidFileStore(Path tempDir) {
        this.tempDir = tempDir;
    }

    /** 登记 spill 目标路径（目录惰性创建）。 */
    public Path reserve(String taskId) {
        try {
            Files.createDirectories(tempDir);
        } catch (IOException ex) {
            throw new IllegalStateException("创建投标文件临时目录失败: " + tempDir, ex);
        }
        return tempDir.resolve(taskId + ".part");
    }

    /** 删除 {taskId}.part（幂等：文件不存在 no-op）。 */
    public void release(String taskId) {
        releasePath(tempDir.resolve(taskId + ".part"));
    }

    /** 启动全清：删除 tempDir 下全部 *.part（进程重启 = 无进行中任务，建立零基线）。 */
    public void cleanupAll() {
        if (!Files.isDirectory(tempDir)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(tempDir, "*.part")) {
            for (Path entry : entries) {
                releasePath(entry);
            }
        } catch (IOException ex) {
            log.warn("投标文件临时目录全清失败: dir={}", tempDir, ex);
        }
    }

    private void releasePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("投标文件临时文件删除失败: path={}", path, ex);
        }
    }
}
