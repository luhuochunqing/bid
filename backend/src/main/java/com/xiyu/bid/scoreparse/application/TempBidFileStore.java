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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 投标文件临时落盘管理：{tempDir}/{taskId}.part 命名约定。
 * <p>OnDisk 临时文件的删除责任唯一归属本类（release）；调用方在任务终态/异常路径调用 release。
 * 并发总量配额（max-total-bytes: 默认 4GB）受本类管控（spec 045 US2/FR-002）。
 */
@Component
@Slf4j
public class TempBidFileStore {

    private final Path tempDir;
    private final long maxTotalBytes;
    private final AtomicLong currentTotalBytes = new AtomicLong(0);
    private final ConcurrentMap<String, java.util.concurrent.atomic.LongAdder> taskAllocations = new ConcurrentHashMap<>();

    @Autowired
    public TempBidFileStore(
            @Value("${app.score-parse.bid-file.temp-dir:${java.io.tmpdir}/xiyu-score-parse}") String tempDir,
            @Value("${app.score-parse.bid-file.max-total-bytes:4294967296}") long maxTotalBytes) {
        this(Path.of(tempDir), maxTotalBytes);
    }

    TempBidFileStore(Path tempDir) {
        this(tempDir, 4294967296L);
    }

    TempBidFileStore(Path tempDir, long maxTotalBytes) {
        this.tempDir = tempDir;
        this.maxTotalBytes = maxTotalBytes;
    }

    /** 登记 spill 目标路径（目录惰性创建）。 */
    public Path reserve(String taskId) {
        if (currentTotalBytes.get() >= maxTotalBytes) {
            log.warn("投标文件落盘配额超限(reserve): taskId={}, currentTotalBytes={}, maxTotalBytes={}",
                    taskId, currentTotalBytes.get(), maxTotalBytes);
            throw new BidFileQuotaException();
        }
        try {
            Files.createDirectories(tempDir);
        } catch (IOException ex) {
            throw new IllegalStateException("创建投标文件临时目录失败: " + tempDir, ex);
        }
        return tempDir.resolve(taskId + ".part");
    }

    /** 记录并累计 spill chunk 写入量，超限则抛出 BidFileQuotaException 并回滚本次 chunk */
    public void recordSpillChunk(String taskId, long chunkSize) {
        if (chunkSize <= 0) {
            return;
        }
        long newTotal = currentTotalBytes.addAndGet(chunkSize);
        if (newTotal > maxTotalBytes) {
            currentTotalBytes.addAndGet(-chunkSize);
            log.warn("投标文件落盘配额超限(chunk): taskId={}, currentTotalBytes={}, requestedChunk={}, maxTotalBytes={}",
                    taskId, currentTotalBytes.get(), chunkSize, maxTotalBytes);
            throw new BidFileQuotaException();
        }
        taskAllocations.computeIfAbsent(taskId, k -> new java.util.concurrent.atomic.LongAdder()).add(chunkSize);
    }

    /** 按文件路径释放（spec 065：调用方无需知晓 {taskId}.part 命名约定）；非托管路径 no-op。 */
    public void releaseFile(Path file) {
        if (file == null || file.getFileName() == null) {
            return;
        }
        String name = file.getFileName().toString();
        if (!name.endsWith(".part")) {
            log.warn("投标文件临时文件非托管路径，跳过释放: path={}", file);
            return;
        }
        release(name.substring(0, name.length() - ".part".length()));
    }

    /** 删除 {taskId}.part（幂等：文件不存在 no-op）并归还配额。 */
    public void release(String taskId) {
        releasePath(tempDir.resolve(taskId + ".part"));
        java.util.concurrent.atomic.LongAdder allocated = taskAllocations.remove(taskId);
        if (allocated != null) {
            long sum = allocated.sum();
            if (sum > 0) {
                currentTotalBytes.updateAndGet(cur -> Math.max(0L, cur - sum));
            }
        }
    }

    /** 启动全清：删除 tempDir 下全部 *.part（进程重启 = 无进行中任务，建立零基线）。 */
    public void cleanupAll() {
        taskAllocations.clear();
        currentTotalBytes.set(0);
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

    public long getCurrentTotalBytes() {
        return currentTotalBytes.get();
    }

    private void releasePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("投标文件临时文件删除失败: path={}", path, ex);
        }
    }
}
