# Research: 大标书 DirectBuffer OOM 与 Docx 快速提取分析 (052)

## 1. Java DirectBuffer OOM 根因分析
- 在 Java NIO 中，`Files.readString(path)` 内部调用 `FileChannelImpl.read(DirectByteBuffer)`。
- 对于 1.66GB 的文件，JVM 试图一次性分配 1.74GB 连续堆外 DirectBuffer。
- 当 `-XX:MaxDirectMemorySize`（默认通常为最大堆内存，这里约 2GB）剩余不足时，直接抛出 `java.lang.OutOfMemoryError: Cannot reserve 1743831704 bytes of direct buffer memory`。
- `catch (Exception e)` 无法捕获 `Error`，因此异常直通到最外层 Servlet，表现为 HTTP 500。

## 2. Docx 二进制文件特性
- `.docx` 是 Zip 格式，内部 `word/document.xml` 包含完整的段落与表格文字。
- 二进制 docx 直接作为 UTF-8 文本读取只会产生乱码与巨大内存浪费，根本没有任何降级价值。
- 文本提取只需读取 `word/document.xml`（1~2MB），跳过所有 `word/media/`。
