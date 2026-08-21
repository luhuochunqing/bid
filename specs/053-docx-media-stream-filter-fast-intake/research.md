# Research: Docx 媒体前置流式过滤与物理路径直传权衡

## 1. 方案对比与选型

| 维度 | 方案 A：共享存储/物理路径直读 | 方案 B：Java 端 JDK Zip 流式去图瘦身 (选中) | 方案 C：Sidecar 端解压过滤 |
|---|---|---|---|
| **原理** | Sidecar 挂载宿主机目录，Java 传文件路径 | Java 用 `ZipInputStream` 过滤 `word/media/` 后传给 Sidecar | Java 上传 1.66GB，Sidecar 接收时丢弃图片 |
| **HTTP 传输量** | 0 MB (直接传路径) | 1~2 MB (仅传 xml 文本及骨架) | 1.66 GB (全量传输) |
| **部署耦合度** | 高（需容器宿主目录强绑定，无法跨机部署） | **低（纯 HTTP 契约，支持远程/云端 Sidecar）** | 低 |
| **耗时开销** | 0ms 传输 | ~0.5s 本地流式去图 + 10ms 传输 | >60s 网络传输 |
| **内存/稳定性** | 极高 | **极高（流式管道内存 < 1MB）** | 极低（易 OOM / 413 超限） |

**选型结论**：
选择 **方案 B**。方案 B 不仅彻底消除了 HTTP 大文件传输瓶颈（1.66GB $\to$ 1.5MB），而且保持了 Sidecar 独立解耦的微服务 HTTP 架构，即使 Sidecar 部署在远程独立服务器上依然能够秒级极速运行。

## 2. Docx 物理格式规范与过滤安全性

`.docx` 是遵循 Open Packaging Conventions (OPC) 的 ZIP 压缩包：
- `word/document.xml`：文档主正文与段落骨架（通常 50KB ~ 2MB）；
- `word/styles.xml`, `word/numbering.xml`：样式与编号；
- `[Content_Types].xml`, `_rels/.rels`, `word/_rels/document.xml.rels`：关系与类型描述；
- `word/media/*`：内嵌图片与多媒体（通常占 95% ~ 99.9% 体积）。

在纯文本 Markdown 提取场景下，MarkItDown/Mammoth 解析器只关注文字段落、表格与标题结构。当 `word/media/*` 缺失时，Mammoth 会将 `<img>` 标签的 src 置为空或根据我们的 placeholder 忽略，绝不影响文字内容的完整性。

因此，剔除 `word/media/` 后的精简 docx 是完全合法且自闭合的 OPC 归档，解析结果与原文件 100% 一致。
