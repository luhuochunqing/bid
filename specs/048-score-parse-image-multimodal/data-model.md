# Data Model: 投标文件多模态图片评分

**Feature**: 048-score-parse-image-multimodal | **Date**: 2026-08-18

## 概览

本 feature **不新增 DB 表**（research D6）。数据形状分为三层：sidecar 返回的图片清单 DTO、Java 纯核心值对象、
以及打分编排传给 transport 的图片内容模型。全部为进程内对象，生命周期绑定单次打分任务。

## 1. 纯核心值对象（domain，Principle I）

### DocumentImage
图片证据的单元，携带章节归属与图像内容。

```java
public record DocumentImage(
        int index,          // 图片在清单中的序号（0 起），全局稳定
        String mimeType,    // image/png | image/jpeg ...
        String section,     // 章节标题（与 extract_headings / BidChapterDirtySet 同口径），PDF 可为空
        Integer pageHint,   // PDF 页码；其它格式为 null
        int width,          // 像素宽（供 token 估算）
        int height,         // 像素高（供 token 估算）
        String base64       // data URL 的图像裸 base64（不含 data: 前缀）；由调用方在外壳层组装 data URL
) {}
```

### ImageAssignmentPlan
某个评分项实际携带的图片子集 + 裁剪/降级信号（research R4）。

```java
public record ImageAssignmentPlan(
        List<DocumentImage> images,   // 该评分项配给的图片（已裁剪），可能为空
        boolean truncated,            // 超预算发生确定性裁剪时 true
        boolean degraded              // 走纯文本（抽图失败/损坏/模型不支持）时 true
) {
    public static ImageAssignmentPlan none() { ... } // images=[] truncated=false degraded=true
    public boolean hasImages() { return !images.isEmpty(); }
}
```

### ImageBudget
配给预算（纯核心输入，非 IO）。数值来自 application 配置，注入纯策略。

```java
public record ImageBudget(int maxImagesPerItem, long maxImageBytesPerItem) {}
```

### ImageTokenEstimate
图片相关 token 的可观测估算（research R5）。

```java
public record ImageTokenEstimate(long estimatedTokens, int imageCount) {}
```

## 2. sidecar / 打分链路 DTO（外壳 / application）

### SidecarImageDto —— sidecar `POST /convert-images` 响应中的单图条目
```java
public record SidecarImageDto(
        int index, String mimeType, String section, Integer pageHint,
        int width, int height, String base64) {}
```

### SidecarImagesResponse —— `/convert-images` 响应体映射
```java
public record SidecarImagesResponse(
        List<SidecarImageDto> images, boolean truncated, List<String> warnings) {}
```

### LoadedBidDocument（改造）
`BidFileLoadingCoordinator.LoadedBidDocument` 增加图片清单，默认空。**InMemory 与 OnDisk 两路径均填充**
（review H2：`ScoreBidDocumentLookup` 中 doc-insight:// 与本地 ProjectDocumentFileStorage 文档全量 byte[]
走 InMemory 且无字节上限，若仅 OnDisk 抽图，这类文档将静默无图）：
```java
public record LoadedBidDocument(
        String text, String contentHash, List<DocumentImage> images) {
    public LoadedBidDocument(String text, String contentHash) {
        this(text, contentHash, List.of());
    }
}
```

## 3. transport 图片内容模型

`OpenAiStructuredOutputTransport` 扩展多模态参数。图片以"DTA URL 图片对象"形式传入 transport，
由 transport 外壳组装为 SDK 的 image_url content part：

```java
public record MultimodalImage(String mimeType, String base64) {
    public String dataUrl() { return "data:" + mimeType + ";base64," + base64; }
}
```

## 4. 转换矩阵（各层职责）

| 层 | 拿到的 | 产出的 | 职责 |
|---|---|---|---|
| sidecar | 原始文件字节 | `images[]`(base64) + truncated | 抽图 + 预算截断 + 章节/页码归属 |
| Java 加载协调器 | JSON | `List<DocumentImage>` | 反序列化 + 字段映射 |
| `ImageAssigner`(纯核心) | 评分项 + 图片 + 预算 | `ImageAssignmentPlan` | 章节配给 + 确定性裁剪 |
| `OpenAiScoreAnalyzer`/AppService | `ImageAssignmentPlan` | `MultimodalImage[]` | 组装 data URL + 观测 |
| transport | `MultimodalImage[]` | SDK 参数 | 多段 content 组装 + 发请求 |

## 5. 校验规则

- `DocumentImage.base64` 非空、`mimeType` ∈ {image/png, image/jpeg, image/gif, image/webp}；
  sidecar 侧已保证，Java 侧防御式跳过非法项（Principle VII）。
- `ImageAssignmentPlan` 不变量：`truncated=true ⇒ images.size() ≤ maxImagesPerItem`；
  `degraded=true ⇒ images 为空`（纯核心断言，单测覆盖）。