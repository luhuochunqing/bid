// Input: 基准超时秒数 + 原始上传文件字节数
// Output: 生效的解析等待上限秒数（按规模线性分档，封顶 1800s）
// Pos: compliance/domain/ - 投标文件解析等待上限纯函数（FP-Java，无框架依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain;

/**
 * 投标文件解析等待上限纯函数.
 *
 * <p>分档规则（base 为基准档，默认 60s，可经
 * {@code app.compliance.sidecar.timeout-seconds} 调整）：
 * <ul>
 *   <li>0 &lt; size ≤ 50MB → base</li>
 *   <li>size &gt; 50MB → base×5 + (sizeMB−50)×3，封顶 {@link #MAX_TIMEOUT_SECONDS}</li>
 *   <li>size ≤ 0（探测失败/未知）→ base×5（大文件档下限，保守侧）</li>
 * </ul>
 *
 * <p>口径：size 必须是<b>原始上传文件</b>大小（媒体瘦身前），
 * 避免 docx 瘦身后掉回小文件档（spec 066 FR-010）。
 */
public final class SidecarTimeoutBudget {

    /** 大文件阈值：50MB. */
    public static final long LARGE_FILE_THRESHOLD_BYTES = 50L * 1024 * 1024;

    /** 大文件档下限倍数：428MB 级标书 60s 内必然转不完（1373），大文件档 = 基准档 × 5. */
    public static final long LARGE_FILE_MULTIPLIER = 5L;

    /** 超过阈值后每 MB 追加秒数：覆盖实测 1.2–2.6 s/MB 吞吐并留余量. */
    public static final long SECONDS_PER_MB_ABOVE_THRESHOLD = 3L;

    /** 等待上限硬顶：防挂死长期占用异步线程. */
    public static final long MAX_TIMEOUT_SECONDS = 1800L;

    /** 基准档非法（≤0）时的兜底秒数. */
    public static final long DEFAULT_BASE_SECONDS = 60L;

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private SidecarTimeoutBudget() {
    }

    /**
     * 按原始文件大小解析生效等待上限.
     *
     * @param baseSeconds 基准档秒数（≤0 时取 {@link #DEFAULT_BASE_SECONDS}）
     * @param originalSizeBytes 原始上传文件字节数；≤0 表示探测失败/未知
     * @return 生效等待上限秒数（≥ base，≤ {@link #MAX_TIMEOUT_SECONDS}）
     */
    public static long secondsFor(final long baseSeconds, final long originalSizeBytes) {
        long base = baseSeconds > 0 ? baseSeconds : DEFAULT_BASE_SECONDS;
        long largeFloor = Math.min(MAX_TIMEOUT_SECONDS, base * LARGE_FILE_MULTIPLIER);
        if (originalSizeBytes <= 0) {
            return largeFloor;
        }
        if (originalSizeBytes <= LARGE_FILE_THRESHOLD_BYTES) {
            return base;
        }
        long sizeMb = (originalSizeBytes + BYTES_PER_MB - 1) / BYTES_PER_MB;
        long scaled = largeFloor + (sizeMb - 50L) * SECONDS_PER_MB_ABOVE_THRESHOLD;
        return Math.min(MAX_TIMEOUT_SECONDS, scaled);
    }
}
