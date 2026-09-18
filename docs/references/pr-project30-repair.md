## 背景与问题根因
在处理项目 30 等大型招标文件时，因网络传输或存储末端截断导致 docx 文件缺失 End of Central Directory（EOCD，`PK\x05\x06`）标识。
旧逻辑存在三大系统级缺陷，引发连锁事故：
1. **容器层（Layer 0）**：标准 `zipfile` 识别为 `BadZipFile` 报异常，但文件包含的 27 个 local file header（含 2.7MB `word/document.xml`）均完好无损；
2. **Sidecar 进程安全（Layer 1）**：Sidecar 尝试在 `ProcessPoolExecutor` 中回传异常时，Python 3.11 试图 pickle 序列化 `traceback` 触发 `TypeError: cannot pickle 'traceback' object`，导致子进程崩溃假死，主进程硬超时 60s；
3. **Java 后端降级（Layer 1）**：后端超时后触发 fallback，因无 docx 原生解析器，错误执行 `new String(content, StandardCharsets.UTF_8)`，将 ZIP 二进制字节强转为 38.5% 乱码字符串，导致上层评分项识别为 0 项。

## 修复方案
1. **Sidecar DOCX 容器自愈 (`table_extractor.py`)**：
   - 增加 `repair_truncated_docx`：遇到 `BadZipFile` 时自动按 local file header 链在内存中重构 Central Directory 与 EOCD，实现透明解压；正常合法文件直接走 fast-path 零额外开销。
2. **Sidecar 异常隔离脱敏 (`app.py`)**：
   - 增加 `_safe_convert_worker` 包装层，显式捕获异常并转为安全 dict，杜绝 unpicklable traceback 破坏进程池。
3. **Java 后端 POI XWPF 原生降级 (`LocalDocumentFallbackExtractor.java`)**：
   - 拆分独立本地降级提取器，彻底废除 `new String(content, UTF_8)` 二进制强转反模式；
   - 补齐 Apache POI `XWPFDocument` 原生提取段落与 Markdown 表格。
4. **正交隔离与防回归**：
   - Layer 3 业务提取规则（`DeterministicScoreFactExtractor.java` 等）0 行改动，既有 200+ 提交沉淀的启发式规则与 790+ 单元测试 100% 保持原有逻辑。

## 验证证据
- 实机验证：生产环境真实项目 30 截断样本修复后无须改动任何提取规则，直接提取出 14 张表格，其中 Table 2 精确识别出 28 项评分项、总分 100.0 分。
- Sidecar 测试：99 passed 100% 通过（含 3 个新建破损自愈与进程安全测试）。
- 后端测试：`MarkItDownSidecarExtractorTest` (5 passed) + `LocalDocumentFallbackExtractorTest` (2 passed) 全部通过。
- 门禁合规：满足 300 行 line budget，全部 pre-commit 门禁全绿通过。

## Wiki 更新
- [x] 已更新相关 .wiki/pages/ 页面 + _index.md + log.md (更新 `score-parse-service.md` 并追加 `lessons-learned.md` §382)
- [ ] 本次无需更新（纯文档/配置/重构无新逻辑）
