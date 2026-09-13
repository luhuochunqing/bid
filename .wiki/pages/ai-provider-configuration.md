---
title: AI Provider 配置与陷阱指南
space: engineering
category: guide
tags: [AI, Provider, DeepSeek, Doubao, qwen, activeProvider, 降级策略, Sidecar, textutil, mock, 配置默认值陷阱]
created: 2026-07-10
updated: 2026-08-25
health_checked: 2026-09-13
sources:
  - backend/src/main/java/com/xiyu/bid/ai/
  - backend/src/main/resources/application.yml
  - .wiki/pages/ai-capabilities.md
backlinks:
  - _index
  - engineering-discipline
---
# AI Provider 配置与陷阱指南

> 从 8 个工作区历史对话中提取的 AI Provider 配置实战陷阱。
> 涵盖 activeProvider 硬编码、Doubao URL 标准化、降级策略、Sidecar 文件转换等。
> 产品视角的 AI 能力总览见 [[ai-capabilities]]，本文只记录工程陷阱。

---

## 1. AI Provider 必须读取 activeProvider 而非硬编码

### 1.1 事故

AI 服务类硬编码使用 DeepSeek，但系统配置的 activeProvider 是 doubao，导致调用错误的 provider。

### 1.2 反模式

```java
// ❌ 错误：硬编码 provider
@Service
public class AiAnalysisService {
    public String analyze(String text) {
        return deepSeekClient.chat(text);  // 总是调 DeepSeek
    }
}
```

### 1.3 正确做法

```java
// ✅ 正确：读取 activeProvider
@Service
public class AiAnalysisService {
    private final AiProviderRouter providerRouter;

    public String analyze(String text) {
        AiProvider provider = providerRouter.getActiveProvider();
        // activeProvider 来自系统配置（数据库或环境变量）
        return provider.chat(text);
    }
}

// 配置
// backend.env: AI_PROVIDER=custom
// 或数据库: ai.provider = doubao
```

### 1.4 支持的 Provider

| Provider code | 名称 | 配置项 |
|---------------|------|--------|
| `openai` | OpenAI | OPENAI_API_KEY |
| `deepseek` | DeepSeek | DEEPSEEK_API_KEY |
| `qwen` | 通义千问 | QWEN_API_KEY |
| `doubao` | 豆包 | DOUBAO_API_KEY |
| `custom` | 自定义（ehsy AI 网关） | CUSTOM_AI_BASE_URL + CUSTOM_AI_API_KEY |

### 1.5 教训

- **AI Provider 必须从系统配置读取 activeProvider**
- **不要硬编码任何 provider**
- **新增 provider 时要更新 router 和配置项**
- **`AI_PROVIDER=custom` 用于使用 ehsy AI 网关**

---

## 2. Doubao API URL 标准化错误：去除 /v3 前缀导致 404

### 2.1 事故

调用 Doubao API 时，URL 标准化逻辑错误地去除了 `/v3` 前缀，导致请求 404。

### 2.2 根因

```java
// ❌ 错误：URL 标准化时去掉 /v3
String url = baseUrl.replaceAll("/v\\d+$", "");  // 把 /v3 去掉了
// 结果：https://ark.cn-beijing.volces.com/api/chat/completions
// 但正确 URL 应该是：https://ark.cn-beijing.volces.com/api/v3/chat/completions
```

### 2.3 修复

```java
// ✅ 正确：保留 Doubao 的 /v3
String url = baseUrl;
if (!url.endsWith("/v3") && !url.endsWith("/v3/")) {
    url = url.endsWith("/") ? url + "v3" : url + "/v3";
}
```

### 2.4 教训

- **不同 AI Provider 的 URL 路径结构不同**，不能套用统一的标准化逻辑
- **Doubao (Ark) 的 API 需要 /v3 前缀**
- **URL 标准化要按 provider 区分**
- **测试要覆盖每个 provider 的 URL 构造**

---

## 3. AI 分析失败应降级而非阻断主流程

### 3.1 事故

AI 文档分析失败时，整个文件上传流程被阻断，用户无法上传标书。

### 3.2 反模式

```java
// ❌ 错误：AI 失败阻断主流程
public UploadResult upload(MultipartFile file) {
    saveFile(file);
    AiAnalysisResult aiResult = aiService.analyze(file);  // 失败抛异常
    saveAnalysis(aiResult);
    return UploadResult.success(aiResult);
}
```

### 3.3 正确做法

```java
// ✅ 正确：AI 失败降级，主流程继续
public UploadResult upload(MultipartFile file) {
    saveFile(file);
    try {
        AiAnalysisResult aiResult = aiService.analyze(file);
        saveAnalysis(aiResult);
        return UploadResult.success(aiResult);
    } catch (AiAnalysisException e) {
        log.warn("AI 文档分析失败，降级为手动填写", e);
        return UploadResult.withWarning(
            null,
            "AI_DOCUMENT_ANALYSIS_FAILED",
            "AI 分析失败，请手动填写标讯信息"
        );
    }
}
```

### 3.4 教训

- **AI 是辅助功能，失败不能阻断主流程**
- **AI 失败要降级**：文件正常存储，返回警告，用户可手动填写
- **降级返回值要包含 `AI_DOCUMENT_ANALYSIS_FAILED` 标识**，前端据此显示提示

---

## 4. Doubao 账号需要单独购买非原生模型访问权限

### 4.1 陷阱

Doubao 账号默认只能访问豆包原生模型，访问 `deepseek-v3-2-251201` 等非原生模型需要单独购买或授权。

### 4.2 表现

```json
{
  "error": {
    "code": "PermissionDenied",
    "message": "Model deepseek-v3-2-251201 is not available"
  }
}
```

### 4.3 解决

- 在火山引擎控制台开通对应模型的访问权限
- 或切换到 `AI_PROVIDER=custom`，使用 ehsy AI 网关（已开通所有模型）

### 4.4 教训

- **AI Provider 的账号权限要提前验证**
- **非原生模型可能需要单独购买**
- **生产环境优先用 ehsy AI 网关**（已统一开通）

---

## 5. ehsy AI 网关仅支持三个模型

### 5.1 限制

ehsy AI 网关（`CUSTOM_AI_BASE_URL`）目前仅支持：
- `deepseek-v3` 
- `deepseek-v3-2-251201`
- `doubao-pro-32k`

### 5.2 配置

```bash
# /etc/xiyu-bid/backend.env
AI_PROVIDER=custom
CUSTOM_AI_BASE_URL=https://ai.ehsy.com/v1
CUSTOM_AI_API_KEY=xxx
CUSTOM_AI_MODEL=deepseek-v3-2-251201
```

### 5.3 教训

- **ehsy AI 网关支持的模型有限**，使用前要确认
- **不要假设网关支持所有模型**
- **模型名要精确**，包括版本号

---

## 6. AI prompt 需截断防止超长输入

### 6.1 事故

用户上传超大文档（>100KB 文本），AI 接口报 `context length exceeded` 错误。

### 6.2 解决

```java
public String analyze(String text) {
    // 截断到模型支持的长度（如 32k tokens ≈ 120k 字符）
    int maxLength = 120_000;
    if (text.length() > maxLength) {
        log.warn("文档过长，截断从 {} 到 {}", text.length(), maxLength);
        text = text.substring(0, maxLength);
    }
    return provider.chat(text);
}
```

### 6.3 教训

- **AI 输入必须截断**，不能假设用户输入在模型限制内
- **不同模型的 context length 不同**（8k/32k/128k）
- **截断要记录日志**，便于排查

---

## 7. Sidecar .doc 文件转换失败：textutil 是 macOS 专有命令

### 7.1 事故

Sidecar（docinsight）在 Linux 服务器上转换 .doc 文件失败，报 `textutil: command not found`。

### 7.2 根因

`textutil` 是 macOS 专有命令，Linux 上不存在。Sidecar 容器内默认用 `textutil` 转换 .doc 文件。

### 7.3 解决

Linux 服务器需要安装替代工具：
- `libreoffice --headless --convert-to pdf file.doc`
- 或 `antiword file.doc`

并修改 Sidecar 配置使用 Linux 兼容的转换工具。

### 7.4 教训

- **macOS 专有命令不能在生产 Linux 服务器上使用**
- **文件转换工具要跨平台兼容**
- **Sidecar 容器要预装所有依赖**

---

## 8. `ai.provider` 默认值陷阱：默认 mock 让"漏配"伪装成"显式 mock"（2026-08-25 修复）

### 8.1 事故

PR !2396 给 `RoutingAiProvider.resolveActiveConfig()` 加了「`ai.provider=mock` → 返回 null 强制回落 MockAiProvider」的 E2E 分支。
但 `application.yml` 原写法是 `provider: ${AI_PROVIDER:mock}`——**默认值就是 mock**。
任何没显式设置 `AI_PROVIDER` 的环境（backend.env 丢失/新机器重配）都会走进 mock 分支：
全部 AI 接口**静默返回假数据**，健康检查还显示 UP(mockMode)，只有启动日志一行警告。
生产靠服务器 `/etc/xiyu-bid/backend.env` 里 `AI_PROVIDER=custom`（首产记录 2026-07-09）才没触发。

同批引入的两个 mock 分支 NPE（静态证据）：

- `RoutingAiProvider.embed()` 漏 null 回落 → 案例嵌入批处理标 FAILURE_MARKER / 切片推荐 503
- `QualificationAiParserService.callAiToParse()` 直接 `config.model()` → 用户 500（而非设计中的 409 引导）

### 8.2 修复（PR 修复分支 agent/zcode/ai-mock-guard）

1. `application.yml` 默认值改空串 `${AI_PROVIDER:}`——**只认显式 mock**（e2e 栈在 application-e2e.yml 显式 `provider: mock`，不受影响）
2. `RoutingAiProvider.embed()` 补 `if (config == null) return mockAiProvider.embed(text);`
3. `QualificationAiParserService` 补 null 守卫，抛 `BusinessException("AI 当前处于 mock 模式…")`
4. 部署双断言：`scripts/release/preflight.sh`（本地部署）+ `scripts/release/remote-deploy.sh`（服务器端，任何服务变更之前读 backend.env）在 prod profile 下强制 `AI_PROVIDER` 非空且非 mock，违规中止部署且服务零影响

### 8.3 教训

- **配置默认值会让"未设置"与"显式设置"不可区分**——带行为语义的开关（如 mock）默认值必须留空，让漏配走 fail-fast 而非静默降级
- **改共享方法返回契约（新增 null 路径）必须 grep 全部调用方**逐个核对 null 处理，"接口里 5 个方法只改了 4 个"是本次 NPE 的直接根因
- **生产正确性依赖的环境变量要有部署预检断言**，不能只活在运维手册里
- mock 模式的健康检查显示 UP(mockMode) 是**掩盖信号**，排查 AI 异常时先查 `ai.provider` 实际值

---

## 9. AI Provider 配置 Checklist

### 9.1 部署前检查

> **prod 部署已由脚本自动断言**（preflight.sh + remote-deploy.sh 会拦 AI_PROVIDER 缺失/mock，见 §8），以下为手工核查路径。

```bash
# 1. 确认 activeProvider
grep AI_PROVIDER /etc/xiyu-bid/backend.env
# 期望：custom（使用 ehsy 网关）或 doubao/deepseek/qwen

# 2. 确认 API Key 已配置
grep -E "(DOUBAO_API_KEY|DEEPSEEK_API_KEY|CUSTOM_AI_API_KEY)" /etc/xiyu-bid/backend.env
# 期望：对应的 API Key 非空

# 3. 确认网关可达
curl -s -o /dev/null -w "%{http_code}" ${CUSTOM_AI_BASE_URL}/models
# 期望：200 或 401（401 表示地址可达但需认证）
```

### 9.2 部署后验证

```bash
# 健康检查不应出现 mockMode（出现即 ai.provider 解析为 mock，立即排查）
curl -s http://localhost:18080/actuator/health | grep -o '"status": *"[^"]*"'

# 测试 AI 调用
curl -X POST http://localhost:18080/api/ai/test \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"测试"}'
# 期望：返回 AI 响应，不报 500
```

---

## 10. 相关文档

- [[ai-capabilities]] — AI 能力总览（产品视角）
- [[docinsight-engine]] — DocInsight 文档智能引擎
- [[spring-pitfalls]] §2 — @Async 自调用导致代理失效（AI 分析常用 @Async）
- `backend/src/main/java/com/xiyu/bid/ai/` — AI 服务源码

---

## 11. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 8 个工作区历史对话中提取 AI Provider 配置陷阱 |
| 2026-08-25 | 新增 §8 `ai.provider` 默认值陷阱（PR !2396 审计发现，默认 mock → 静默假数据 + 两处 mock 分支 NPE）；部署 checklist 升级为脚本断言 |
