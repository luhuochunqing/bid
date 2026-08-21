# Research & Decisions: Sidecar /convert 流式落盘改造

## 1. 现状与根因分析

- **现状**：
  在 `document-converter-sidecar/app.py` 中，`/convert` 接口使用：
  ```python
  chunks: list[bytes] = []
  while True:
      chunk = await file.read(chunk_size)
      ...
      chunks.append(chunk)
  content: bytes = b"".join(chunks)
  ```
  在处理数百 MB 的大型文档时，`chunks` 列表与拼接后的 `content` 字节在内存中同时存在，峰值内存占用是原始文件大小的 2~3 倍。

- **触发条件**：
  在 2026-08-21 进行的 §143 容器加固中，Sidecar 容器被添加了 `--memory=1g` 限制。当上传 383MB 标书（`张家口银行——投标6.16.docx`）时，Python 进程内存突破 1GB，引发 `MemoryError` 并被 Docker OOM-Killer（`Killed`）。

## 2. 方案比较

| 方案 | 内存消耗 | 实现复杂度 | 哈希计算开销 | 评估结论 |
|---|---|---|---|---|
| **方案 A：流式写入临时文件 + 增量 SHA-256（选定）** | **O(1) 固定 64KB** | 极低（仅修改 15 行，复用现有范式） | 无额外开销（单遍扫描） | **最优**：完全杜绝内存溢出，保持对外契约与哈希一致性 |
| 方案 B：仅提高容器内存限制（如 4GB） | O(N) 高内存 | 无代码修改 | 无 | 否决：治标不治本，仍会挤占宿主机内存重演 §143 故障 |
| 方案 C：使用 mmap 或 SpooledTemporaryFile | O(1) | 中 | 需处理文件游标与重置 | 否决：相较于直接 NamedTemporaryFile 引入多余抽象 |

## 3. 架构与安全决策

- **一致性**：与同文件内的 `/convert-stream` 和 `/convert-images` 接口对齐，全模块统一采用流式落盘设计。
- **清理安全**：沿用 `finally` 块显式 `os.remove`，确保在任何异常或超时情况下不残留临时文件。
