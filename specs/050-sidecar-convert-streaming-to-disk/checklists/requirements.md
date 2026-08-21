# Quality Checklist: Sidecar /convert 流式落盘改造

- [x] **无内存全量驻留**：`/convert` 接口已移除 `chunks: list[bytes]` 和 `b"".join(chunks)`。
- [x] **流式磁盘写入**：采用 64KB 块读取并直接写入 `NamedTemporaryFile`。
- [x] **SHA-256 哈希增量一致**：流式计算的 `contentHash` 与文件全量 SHA-256 严格一致。
- [x] **大小保护与 413 门禁**：超出 512MB 限制立即返回 413，不无界占用磁盘。
- [x] **临时文件闭环清理**：`finally` 块中始终清理 `tmp_path` 与 `converted_path`。
- [x] **测试全量通过**：`pytest document-converter-sidecar/tests/ -v` 26 项测试 100% 通过。
