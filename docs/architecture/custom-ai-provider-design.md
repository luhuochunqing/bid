# 自定义 AI Provider 设计文档（简化方案 v2）

> **创建日期**: 2026-07-05
> **修订日期**: 2026-07-05（v2：简化方案，custom 作为正常 Provider 放入 providers 列表）
> **状态**: 已确认，Task 1 已完成
> **作者**: cursor agent
> **关联模块**: `ai/client/`、`biddraftagent/infrastructure/openai/`、`settings/`、前端 `AiModelSettingsPanel`

---

## 1. 背景与目标

### 1.1 现状

西域数智化投标管理平台当前 AI 大模型 API 对接限定 4 个平台：OpenAI、DeepSeek、通义千问、豆包。4 家全部走 OpenAI-compatible Chat Completions 协议，由 `AiProviderCatalog`（硬编码 Java `Map.of(...)`）注册，并通过 `validateBaseUrl()` 强制 HTTPS + 域名白名单校验，禁止自建反代。

调用链路上存在两条并行路径：

- **路径 1**（`com.xiyu.bid.ai.client.*`）：标讯/项目打分、标书质量预览。核心客户端 `OpenAiCompatibleClient`（RestTemplate 手写）。
- **路径 2**（`com.xiyu.bid.biddraftagent.infrastructure.openai.*`）：招标文件结构化抽取、草稿生成。核心客户端 `OpenAiSdkStructuredOutputTransport`（OpenAI 官方 Java SDK）。

两条路径共享同一份配置源（`system_settings.payload_json`），但各自一套 resolver + config record + client。

### 1.2 问题

第三方 AI 聚合平台越来越多（OpenRouter、硅基流动、智谱、月之暗面、Together AI、Fireworks AI、本地 Ollama、公司内网反代等），现有 4 家硬编码无法覆盖；新增厂商必须改 `AiProviderCatalog` 源码并重新部署，运维成本高。

### 1.3 目标

做一个通用的 AI 大模型 API 聚合入口，用户基于 base URL + API Key + Model 即可接入任意 OpenAI-compatible 平台，无需改代码。

### 1.4 非目标（YAGNI）

本次需求**不**包含以下能力，留作后续独立需求：

- Token 用量计量与成本统计
- 流式响应（streaming）
- 多模型路由（按任务类型路由到不同模型）
- Fallback 链（主 Provider 失败自动切备用）
- 出站限流
- 支持 Anthropic 原生 / Gemini 原生协议（仅支持 OpenAI-compatible）
- 支持多个自定义 Provider（本次仅 1 个全局唯一槽位）

---

## 2. 设计决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 集成范围 | 并存（保留 4 家 + 新增自定义） | 向后兼容，4 家用户体验不受影响 |
| 配置粒度 | 1 个全局唯一"自定义"槽位 | YAGNI，本次不做多个 |
| 调用路径 | 两条路径都支持 | 体验一致，custom 在 providers 列表中自然被两条路径的 resolver 找到 |
| API 协议 | 仅 OpenAI-compatible | 复用现有客户端，4 家也都走这个协议 |
| 安全策略 | 全面放开 + SSRF 防护 | 用户可能用 OpenRouter / 内网反代 / 本地 Ollama，但不能 SSRF |
| 功能范围 | 仅自定义 Provider | YAGNI |
| 实现方案 | 简化方案（custom 在 providers 列表中，无独立 DTO 字段） | 改动 4-5 个文件，无需改 DTO/AiConfigService/RoutingAiProvider/ConfigurationResolver |

---

## 3. 架构设计

### 3.1 整体架构

```
AiProviderCatalog (硬编码)
  ├── openai    (BUILTIN, 域名白名单 + HTTPS 强制)
  ├── deepseek  (BUILTIN, 域名白名单 + HTTPS 强制)
  ├── qwen      (BUILTIN, 域名白名单 + HTTPS 强制)
  ├── doubao    (BUILTIN, 域名白名单 + HTTPS 强制)
  └── custom    (CUSTOM,  SSRF 校验, 作为第 5 个正常 Provider 在 providers 列表中)

配置存储: system_settings.payload_json → AiModelConfig.providers (包含 custom)
         ↓ 两条路径自然流通
  ┌───────────────────────────────────┐
  │ 路径1: ai/client/                  │
  │   RoutingAiProvider → 从 providers  │
  │   列表中找到 custom → OpenAiCompatibleClient
  └───────────────────────────────────┘
  ┌───────────────────────────────────┐
  │ 路径2: biddraftagent/infrastructure/ │
  │   OpenAiBidAgentConfigurationResolver│
  │   → findProvider 在 providers 中匹配 │
  │   → OpenAiSdkStructuredOutputTransport│
  └───────────────────────────────────┘
```

### 3.2 核心抽象

`custom` 与 4 家**平等对待**，作为 `AiProviderCatalog` 的第 5 个条目，放入 `supportedProviderCodes()` 列表。**不需要独立的 `customProvider` DTO 字段**，不需要在两条路径的 resolver 中做特殊分支判断。

差异在于：

- **BUILTIN 厂商**：默认 baseUrl/model 已知，走域名白名单 + HTTPS 强制校验
- **CUSTOM 厂商**：默认 baseUrl/model 为 null（用户填），`validateBaseUrl` 走 SSRF 校验，`environmentKeys` 返回空（无 env fallback）

### 3.3 为什么不需要改 DTO / AiConfigService / Resolver

`AiConfigService.normalizeAiModelConfig` 遍历 `aiProviderCatalog.supportedProviderCodes()` 生成 providers 列表。custom 在 supportedProviderCodes 中 → 自动出现在 providers 列表中 → `RoutingAiProvider` 和 `ConfigurationResolver` 的 `findProvider` 自然能找到它。

**唯一需要特殊处理的是 `validateBaseUrl`**：custom 的 baseUrl 不校验域名白名单，改走 SSRF。

---

## 4. 数据模型变更

### 4.1 无需 DTO 变更

`SettingsResponse.AiModelConfig` 和 `SettingsUpdateRequest.AiModelConfigUpdate` **不需要改**。custom 作为第 5 个条目出现在 `providers` 列表中，与 4 家使用相同的 `AiProviderSetting` 结构。

### 4.2 AiProviderCatalog 调整

```java
public static final String CUSTOM_PROVIDER_CODE = "custom";

// 注册表加一条（baseUrl/model 都为 null，由用户填）
Map<String, ProviderDefault> PROVIDERS = Map.of(
    "openai",   new ProviderDefault("OpenAI",   "https://api.openai.com/v1/chat/completions", "gpt-4o-mini", List.of("OPENAI_API_KEY"), "api.openai.com"),
    "deepseek", new ProviderDefault("DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-chat", List.of("DEEPSEEK_API_KEY"), "api.deepseek.com"),
    "qwen",     new ProviderDefault("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus", List.of("DASHSCOPE_API_KEY","QWEN_API_KEY"), "dashscope.aliyuncs.com"),
    "doubao",   new ProviderDefault("豆包", "https://ark.cn-beijing.volces.com/api/v3/chat/completions", "doubao-1-5-pro-32k-250115", List.of("ARK_API_KEY","DOUBAO_API_KEY","VOLCENGINE_API_KEY"), "ark.cn-beijing.volces.com"),
    "custom",   new ProviderDefault("自定义", null, null, List.of(), null)  // 无默认值, 无 env 候选
);

public static boolean isCustomProvider(String code) {
    return CUSTOM_PROVIDER_CODE.equals(code);
}
```

---

## 5. 校验逻辑（核心改动）

### 5.1 新增 SsrfValidator 工具类（已完成）

位于 `com.xiyu.bid.common.security.SsrfValidator`，commit `5ed6a2172`。

规则：
- 允许 HTTP / HTTPS
- 允许 localhost / 内网 IP / 任意公网域名
- 禁止云元数据地址（169.254.0.0/16 link-local）
- 禁止保留 IP 段（0.0.0.0/8、240.0.0.0/4）
- 禁止广播地址 255.255.255.255
- 禁止 URL 包含 @userinfo
- URL 必须能被 java.net.URI 解析

### 5.2 AiProviderCatalog.validateBaseUrl 分流

```java
public void validateBaseUrl(String providerCode, String baseUrl) {
    if (isCustomProvider(providerCode)) {
        SsrfValidator.validate(baseUrl);  // custom 走 SSRF 校验
        return;
    }
    // 现有逻辑：HTTPS + 域名白名单（4 家不变）
    // ...
}
```

### 5.3 SSRF 黑名单

| IP 段 | 名称 | 处置 |
|---|---|---|
| `169.254.0.0/16` | AWS / GCP / Azure 云元数据 | 禁止 |
| `0.0.0.0/8` | 本网络 | 禁止 |
| `240.0.0.0/4` | 保留（Class E） | 禁止 |
| `255.255.255.255` | 广播 | 禁止 |
| `::` | IPv6 未指定 | 禁止 |
| `fe80::/10` | IPv6 link-local | 禁止 |
| `127.0.0.0/8` | IPv4 loopback | 允许（本地 Ollama） |
| `10.0.0.0/8` / `172.16.0.0/12` / `192.168.0.0/16` | 内网 | 允许（公司内网反代） |

**已知简化范围**（不防）：DNS rebinding、非标准 IPv4 格式（十六进制/十进制/八进制）、IPv4-mapped IPv6 绕过。已记录在 SsrfValidator Javadoc 中。

---

## 6. 路由逻辑（无需改动）

### 6.1 路径 1：`RoutingAiProvider.resolveActiveConfig()`

**无需改动**。custom 在 `aiModelConfig.providers` 列表中，`findProvider` 自然匹配。

### 6.2 路径 2：`OpenAiBidAgentConfigurationResolver`

**无需改动**。custom 在 `aiModelConfig.providers` 列表中，`findProvider` 自然匹配。custom 的 `apiStyle` 由 `buildRequestConfig` 统一设为 `CHAT_COMPLETIONS`（现有逻辑不变）。

---

## 7. HTTP 客户端（复用，不新增）

- 路径 1：继续用 `OpenAiCompatibleClient`（RestTemplate 手写），无需改动
- 路径 2：继续用 `OpenAiSdkStructuredOutputTransport`（OpenAI SDK），无需改动

---

## 8. 配置存储

| 维度 | 方案 |
|---|---|
| 存储位置 | `system_settings.payload_json` 的 `aiModelConfig.providers` 列表中 |
| 迁移需求 | 无（JSON 字段自然扩展，旧数据中不存在 custom，normalize 自动补全） |
| 加密 | custom 的 `encryptedApiKey` 用 `PasswordEncryptionUtil.encrypt/decrypt`（和 4 家一致） |
| 脱敏 | custom 的 `apiKeyMasked` 用 `maskApiKey()`（和 4 家一致） |
| 环境变量 | custom 不支持 env fallback（`AiProviderCatalog.environmentKeys("custom")` 返回空 List） |

---

## 9. 健康检查 & 启动检查（无需改动）

- `AiProviderHealthIndicator`：custom 在 providers 列表中，健康检查逻辑自然覆盖
- `AiConfigurationStartupChecker`：custom 作为普通 provider 被检查，无需特殊处理

---

## 10. 前端 UI

在现有 4 家 Provider 卡片之后追加"自定义 Provider"卡片。与 4 家同在一个 provider grid 中渲染。

差异点：
- `providerName` 可编辑（用户可改名，如 "OpenRouter"）
- `baseUrl` / `model` 默认空值，用户填写
- `enabled` 默认 `false`（避免未配置时被误激活）
- 对于 4 家，`baseUrl` 有默认值兜底；对于 custom，`baseUrl` 必填且校验走 SSRF

### 10.1 交互

- "测试连接"按钮：复用现有功能，调 `POST /api/settings/ai-models/test`
- activeProvider 切换器：`el-radio-group` 中追加 custom 选项

---

## 11. 错误处理

| 场景 | HTTP 状态码 | 错误消息 |
|---|---|---|
| SSRF 校验失败 | 400 | "自定义 Provider baseUrl 不允许指向该地址" |
| URL 格式错误 | 400 | "自定义 Provider baseUrl 格式无效" |
| custom 未配置 baseUrl/apiKey 但切为 active | 400 | "custom API Key 未配置"（由 RoutingAiProvider 现有逻辑抛出） |
| 调用 custom 失败（402/401/429/5xx） | 对应状态码 | 复用现有 `ExternalServiceException` 错误消息 |

---

## 12. 测试策略

### 12.1 后端

- `SsrfValidatorTest`（已完成，15 tests）
- `AiProviderCatalogTest`：custom 条目、isCustomProvider、validateBaseUrl 分流

### 12.2 前端

- 配置 custom → 保存 → 切换 active → 触发 AI 调用 → 验证后端用 custom 配置

### 12.3 Architecture Test

- 确认新增 `SsrfValidator` 位于 `common.security` 包，不违反现有规则

---

## 13. 改动文件清单

### 13.1 后端

| 文件 | 改动类型 |
|---|---|
| **新增** `backend/src/main/java/com/xiyu/bid/common/security/SsrfValidator.java` | SSRF 校验工具类（已完成） |
| **新增** `backend/src/test/java/com/xiyu/bid/common/security/SsrfValidatorTest.java` | 单元测试（已完成） |
| `backend/src/main/java/com/xiyu/bid/settings/service/AiProviderCatalog.java` | 加 `custom` 条目 + `isCustomProvider()` + `validateBaseUrl` 分流 |

### 13.2 前端

| 文件 | 改动类型 |
|---|---|
| `src/views/System/settings/useAiModelSettings.js` | `AI_PROVIDER_OPTIONS` 加 custom；custom 默认 enabled=false |
| `src/views/System/settings/AiModelSettingsPanel.vue` | provider grid 自然渲染 custom；custom 卡片 providerName 可编辑 |

总计 **2 个后端文件（含 1 个新增） + 2 个前端文件**。

---

## 14. 风险与回滚

### 14.1 风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| SSRF 防护不完整（非标准 IP 格式绕过） | 低 | 已知简化范围已在 Javadoc 声明；后续可加固 |
| custom 默认 baseUrl/model 为 null，normalize 可能产生 NPE | 低 | `nonBlankOrDefault(null, null)` 返回 null，前端处理空值 |

### 14.2 回滚

- 代码回滚：`git revert` 即可，无 DB schema 变更
- 数据回滚：旧版本读 `payload_json` 时 `providers` 列表中多一个 custom 条目，不影响功能
- activeProvider 回滚：若 active=custom 时回滚到旧版本，启动检查会告警 "unknown provider: custom"，需手动改回 4 家之一

---

## 15. 验收标准

1. 系统设置页出现第 5 个 Provider 卡片"自定义"
2. 用户可填写 name/baseUrl/apiKey/model，保存
3. 用户可将 activeProvider 切换为"自定义"
4. 切换后，所有 AI 调用（标讯分析/项目分析/标书质量预览/招标抽取/草稿生成）使用自定义配置
5. 测试连接按钮对自定义 Provider 生效
6. SSRF 校验：填 `http://169.254.169.254/...` 时保存被拒绝
7. 允许 `http://localhost:11434/v1/chat/completions`（本地 Ollama）
8. 允许 `https://openrouter.ai/api/v1/chat/completions`（OpenRouter）
9. API Key 加密存储，前端脱敏显示
10. 现有 4 家配置和调用不受影响（回归测试通过）