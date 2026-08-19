# Quickstart: 投标文件多模态图片评分

**Feature**: 048-score-parse-image-multimodal | **Date**: 2026-08-18

> 怎样让一个含图片证据的客观评分项，真正"看图打分"。前置：AI 网关已配 gemini-3.5-flash（custom provider）。

## 配置新增（`application-{dev,prod}.yml` / env）

```yaml
app:
  score-parse:
    multimodal:
      enabled: ${APP_SCORE_PARSE_MULTIMODAL_ENABLED:true}   # 兜底开关，关闭即纯文本（FR-006）
      max-images-per-item: ${...:3}                           # 单评分项最多配几张图
      max-image-bytes-per-item: ${...:5242880}                # 单评分项图片字节预算 (5MB)
      max-extract-response-bytes: ${...:26214400}             # /convert-images 响应整包上限 (25MB)
    sidecar-url: ${APP_SCORE_PARSE_SIDECAR_URL:http://127.0.0.1:8009}  # 复用
```

sidecar 追加环境变量：`SIDECAR_MAX_IMAGE_BYTES`（默认 15728640 = 15MB，抽图预算截断；与 Java 响应上限 25MB
按 base64 ×4/3 校准，上调任一侧须同步校验另一侧）。

## 端到端验证场景

### V1 —— 客观项图片证据能看图打分（核心，对应 US1/SC-001）
用一个真实投标文件（内含"业绩证明扫描件/资质证书/技术图表"，正文仅占位文字）：

1. 上传投标文件 → 点击「AI 实际打分」（阶段2，InMemory/OnDisk 两路径均走 `/convert-images` 抽图，review H2）。
2. 校验打分结果里该客观项 `evidence`/`quote` 引用了图片内容，判定"满足"，不再是"图片在文本缺失 → 不满足"。
3. 观测：该次调用记录 `imageCount>0`。

### V2 —— 图片按评分项正确配给（US2/SC-002）
多章节多图投标文件：某章节评分项请求只携带其 `section` 命中的图子集，跨章节图不混入。用真实样本 + 单测校验。
- 含文本层 PDF：图片 `section` 非空（页码→最近标题映射），可参与章节配给（review M4）。
- 纯扫描 PDF（无文本层）：图片仅 `pageHint`、`section=null`，warnings 含"扫描件无章节归属"→ 默认不参与
  章节配给、走纯文本（透明降级，非静默）。

> **存量项目注意（review L4）**：打分 skip 指纹=投标文件 URL + 评分项集未变即直接跳过——验证多模态效果需
> 上传**新的**投标文件或修改评分项触发重打分，旧结果不会自动更新。

### V3 —— 降级与预算（US1场景3 / US3 / SC-003）
- 文档无图：零额外 token，行为与现状一致。
- 图片损坏/解码失败、模型拒绝图片：该项回纯文本，不阻断整体，`log.warn` + Sentry。
- 超预算：`ImageAssigner` 确定性裁剪，`truncated=true` 可观测。

### V4 —— 兜底开关（US3 / SC-004）
`APP_SCORE_PARSE_MULTIMODAL_ENABLED=false` 重启后端 → 再次打分，全链路零图片（纯文本）。

## 验收门禁

- 纯核心单测覆盖（对应 research R4 / SC-005）：`ImageAssigner` 覆盖无图/单图/超预算裁剪/损坏降级/多章节隔离。
- 架构测试 `mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest` 全绿（transport 改动不破坏分层）。
- `mvn test -Dtest=OpenAiScoreAnalyzerTest,ScoreItemAssessorTest` 受影响用例通过。
- 真实样本 V1 通过（一次含图打分能正确判定满足）。

## 手动联调

```bash
# 后端（主工作区 trae）
cd /Users/user/xiyu/worktrees/trae
XIYU_DEV_CONFIRMED=1 npm run dev:all
# sidecar 需含 SIDECAR_MAX_IMAGE_BYTES 与共享密钥（dev 可关鉴权）
curl -s -F "file=@/path/to/bid.docx" http://127.0.0.1:8009/convert-images | jq '.images | length'
```