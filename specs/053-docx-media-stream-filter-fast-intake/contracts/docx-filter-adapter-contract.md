# Contract: Docx Media Stream Filter & Adapter Contract

## 1. 接口契约与方法签名

```java
package com.xiyu.bid.compliance.service;

import java.nio.file.Path;

public class DocxMediaFilter {
    /**
     * 判断文件是否需要进行 Docx 媒体瘦身.
     *
     * @param filePath 物理文件路径
     * @param fileName 原始文件名
     * @param thresholdBytes 触发过滤的文件大小阈值（默认 20MB）
     * @return true 若为 docx 且大于阈值
     */
    public static boolean shouldFilter(Path filePath, String fileName, long thresholdBytes);

    /**
     * 流式过滤 docx 文件的 word/media/ 条目，生成临时精简 docx 文件.
     *
     * @param inputPath 原始 docx 路径
     * @return 过滤后的临时 docx 文件路径（调用方负责在 finally 中删除）
     */
    public static Path filterMediaToTempFile(Path inputPath) throws IOException;
}
```

## 2. 行为不变量 (Invariants)

1. **SHA-256 哈希计算时机**：必须在过滤前，基于 `inputPath` 原始物理文件完整内容进行计算，`DocumentContent.fileHash()` 保持原始文件的全局唯一指纹。
2. **零泄漏保证**：`filterMediaToTempFile` 生成的临时文件必须由 `SidecarStreamDocumentAdapter.convertDocument` 在 `finally` 块中通过 `Files.deleteIfExists` 清理。
3. **异常逃逸保护**：若过滤过程遇到非标准或受损 zip，静默回退至原始文件传输，并在降级逻辑中捕获 `Throwable`。
