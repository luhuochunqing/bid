# Contract: Bid Document Adapter & Safety Guard

## SidecarStreamDocumentAdapter 行为契约

### 正常路径
- Sidecar 返回 200 OK -> 解析 JSON 返回 `DocumentContent`（包含 markdown 和 sections）。

### 异常 / 降级路径
1. **纯文本小文件（.txt / .md / .json 等且 <= 5MB）**：
   - 允许本地读取字符串作为兜底降级。
2. **二进制文件（.docx / .pdf / .pptx / .zip / .doc 等）或 > 5MB 的文件**：
   - **严禁**调用 `Files.readString` 或任何全量载入内存的 API。
   - 仅计算并填充 `fileHash`（SHA-256），`fullText` 返回空字符串。
3. **顶层异常防护**：
   - 捕获 `Throwable`，记录 WARN 日志，严禁 `OutOfMemoryError` 逃逸到 Spring MVC 抛出 500。
