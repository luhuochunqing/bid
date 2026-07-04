# 自定义 AI Provider 设计文档

> **创建日期**: 2026-07-05
> **状态**: 已确认，待实现
> **作者**: cursor agent
> **关联模块**: `ai/client/`、`biddraftagent/infrastructure/openai/`、`settings/`、前端 `SettingsPanel`

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
| 调用路径 | 两条路径都支持 | 体验一致，避免"配了自定义但某些功能仍只能用 4 家"的怪象 |
| API 协议 | 仅 OpenAI-compatible | 复用现有客户端，4 家也都走这个协议 |
| 安全策略 | 全面放开 + SSRF 防护 | 用户可能用 OpenRouter / 内网反代 / 本地 Ollama，但不能 SSRF |
| 功能范围 | 仅自定义 Provider | YAGNI |
| 实现方案 | 方案 A（最小侵入） | 改动 ~11 个文件，向后兼容 |

---

## 3. 架构设计

### 3.1 整体架构

```
AiProviderCatalog (硬编码)
  ├── openai    (BUILTIN, 域名白名单 + HTTPS 强制)
  ├── deepseek  (BUILTIN, 域名白名单 + HTTPS 强制)
  ├── qwen      (BUILTIN, 域名白名单 + HTTPS 强制)
  ├── doubao    (BUILTIN, 域名白名单 + HTTPS 强制)
  └── custom    (CUSTOM,  SSRF 校验, baseUrl/model/apiKey 由用户填)

配置存储: system_settings.payload_json → AiModelConfig.customProvider (新增字段)
         ↓ 两条路径共享
  ┌───────────────────────────────────┐
  │ 路径1: ai/client/                  │
  │   RoutingAiProvider → 识别 custom   │
  │   → OpenAiCompatibleClient          │
  └───────────────────────────────────┘
  ┌───────────────────────────────────┐
  │ 路径2: biddraftagent/infrastructure/ │
  │   OpenAiBidAgentConfigurationResolver│
  │   → OpenAiSdkStructuredOutputTransport│
  └───────────────────────────────────┘
```

### 3.2 核心抽象

`custom` 与 4 家平等对待，作为 `AiProviderCatalog` 的第 5 个条目。差异在于：

- **BUILTIN 厂商**：默认 baseUrl/model 已知，走域名白名单 + HTTPS 强制校验
- **CUSTOM 厂商**：默认 baseUrl/model 为 null（用户填），走 SSRF 校验，无 env fallback

---

## 4. 数据模型变更

### 4.1 后端 DTO 扩展（无需 Flyway 迁移）

`SettingsResponse.AiModelConfig` 新增字段：

```java
public record AiModelConfig(
    String activeProvider,
    List<AiProviderSetting> providers,  // 现有 4 家
    AiProviderSetting customProvider    // 新增：自定义 Provider 配置
) {}
```

`SettingsUpdateRequest.AiModelConfigUpdate` 同步加 `customProvider` 字段。

`AiProviderSetting` 复用现有结构（`name` / `baseUrl` / `encryptedApiKey` / `apiKeyMasked` / `model` / `enabled` / `lastTestStatus` / `lastTestedAt` / `lastTestError`），无需新增字段。

### 4.2 AiProviderCatalog 调整

```java
public static final String CUSTOM_PROVIDER_CODE = "custom";

// 注册表加一条（baseUrl/model 都为 null，由用户填）
Map<String, ProviderDefault> PROVIDERS = Map.of(
    "openai",   new ProviderDefault("OpenAI",   "https://api.openai.com/v1/chat/completions",         "gpt-4o-mini",    List.of("OPENAI_API_KEY"),            "api.openai.com"),
    "deepseek", new ProviderDefault("DeepSeek", "https://api.deepseek.com/chat/completions",          "deepseek-chat",  List.of("DEEPSEEK_API_KEY"),          "api.deepseek.com"),
    "qwen",     new ProviderDefault("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus", List.of("DASHSCOPE_API_KEY","QWEN_API_KEY"), "dashscope.aliyuncs.com"),
    "doubao",   new ProviderDefault("豆包",     "https://ark.cn-beijing.volces.com/api/v3/chat/completions", "doubao-1-5-pro-32k-250115", List.of("ARK_API_KEY","DOUBAO_API_KEY","VOLCENGINE_API_KEY"), "ark.cn-beijing.volces.com"),
    "custom",   new ProviderDefault("自定义",   null, null, List.of(), null)  // 无默认值, 无 env 候选
);

public static boolean isCustomProvider(String code) {
    return CUSTOM_PROVIDER_CODE.equals(code);
}
```

---

## 5. 校验逻辑（核心改动）

### 5.1 新增 SsrfValidator 工具类

位于 `com.xiyu.bid.common.security.SsrfValidator`：

```java
public final class SsrfValidator {
    /**
     * 校验自定义 Provider 的 baseUrl 是否安全。
     * 规则：
     *   - 允许 HTTP / HTTPS
     *   - 允许 localhost / 内网 IP / 任意公网域名
     *   - 禁止云元数据地址（169.254.169.254 等 link-local）
     *   - 禁止保留 IP 段（0.0.0.0/8、240.0.0.0/4），但允许 127.0.0.0/8 和 10/172.16/192.168 内网
     *   - 禁止 URL 包含 @userinfo（防止 URL 注入）
     *   - URL 必须能被 java.net.URI 解析
     *   - host 必须非空
     */
    public static void validate(String baseUrl) {
        // 1. URL 解析（scheme/host/port/path）
        // 2. scheme 必须是 http 或 https
        // 3. host 解析（域名或 IP）
        // 4. 若是 IP 字面量：禁止 link-local (169.254.0.0/16)、禁止 0.0.0.0/8、禁止 240.0.0.0/4
        // 5. 不允许 URL userinfo 段（URI.getRawUserInfo() 必须为 null）
        // 6. 不允许 host 包含特殊字符
    }
}
```

### 5.2 AiConfigService.validateBaseUrl 分流

```java
public void validateBaseUrl(String providerCode, String baseUrl) {
    if (AiProviderCatalog.isCustomProvider(providerCode)) {
        SsrfValidator.validate(baseUrl);  // custom 走 SSRF 校验
    } else {
        validateBuiltinBaseUrl(providerCode, baseUrl);  // 现有逻辑：HTTPS + 域名白名单
    }
}
```

### 5.3 SSRF 黑名单

| IP 段 | 名称 | 处置 |
|---|---|---|
| `169.254.0.0/16` | AWS / GCP / Azure 云元数据 | 禁止 |
| `0.0.0.0/8` | 本网络 | 禁止 |
| `240.0.0.0/4` | 保留（Class E） | 禁止 |
| `255.255.255.255` | 广播 | 禁止 |
| `::1` / `fc00::/7` | IPv6 本地 / ULA | 允许（本地调试） |
| `127.0.0.0/8` | IPv4 loopback | 允许（本地 Ollama） |
| `10.0.0.0/8` / `172.16.0.0/12` / `192.168.0.0/16` | 内网 | 允许（公司内网反代） |

---

## 6. 路由逻辑

### 6.1 路径 1：`RoutingAiProvider.resolveActiveConfig()`

```java
private AiProviderRuntimeConfig resolveActiveConfig() {
    var aiConfig = aiConfigService.getInternalAiModelConfig();
    String activeProvider = aiConfig.activeProvider();
    
    AiProviderSetting setting;
    if (AiProviderCatalog.isCustomProvider(activeProvider)) {
        setting = aiConfig.customProvider();
        if (setting == null || !setting.enabled()) {
            throw new ExternalServiceException("自定义 Provider 未配置或未启用");
        }
    } else {
        setting = findInProviders(aiConfig, activeProvider);
    }
    
    String baseUrl = setting.baseUrl();
    String model = setting.model();
    aiConfigService.validateBaseUrl(activeProvider, baseUrl);  // custom 走 SSRF
    
    String apiKey = aiConfigService.resolveAiApiKey(activeProvider);
    if (apiKey == null && !AiProviderCatalog.isCustomProvider(activeProvider)) {
        apiKey = resolveEnvironmentApiKey(activeProvider);  // custom 无 env fallback
    }
    if (apiKey == null || apiKey.isBlank()) {
        throw new ExternalServiceException(activeProvider + " API Key 未配置");
    }
    
    return new AiProviderRuntimeConfig(activeProvider, baseUrl, model, apiKey);
}
```

### 6.2 路径 2：`OpenAiBidAgentConfigurationResolver`

同样改造：识别 `custom` → 从 `customProvider` 取配置 → SSRF 校验 → 构造 `OpenAiBidAgentRequestConfig`。

**关键约束**：custom 的 `apiStyle` 强制为 `CHAT_COMPLETIONS`（不走 RESPONSES，因为第三方平台通常不支持 OpenAI Responses API）。

### 6.3 URL 嗅探反模式修复

顺手修复 `OpenAiTenderDocumentAnalyzer.analyze()` 里的 URL 嗅探：

```java
// 现状（反模式）
String providerLabel = config.baseUrl().contains("deepseek") ? "DeepSeek"
        : config.baseUrl().contains("ark.cn-beijing") ? "豆包"
        : config.baseUrl().contains("dashscope") ? "通义千问"
        : config.baseUrl().contains("api.openai") ? "OpenAI" : "AI";

// 改造后
String providerLabel = AiProviderCatalog.providerDisplayName(config.providerCode());
// custom 时返回 customProvider.name()（用户填的名称）
```

---

## 7. HTTP 客户端（复用，不新增）

- 路径 1：继续用 `OpenAiCompatibleClient`（RestTemplate 手写），无需改动核心调用逻辑
- 路径 2：继续用 `OpenAiSdkStructuredOutputTransport`（OpenAI SDK），无需改动核心调用逻辑
- `OpenAiCompatibleClient.providerDisplayName(code)`：加 `case "custom"` → 返回 `customProvider.name()`（用户填的名称）或 fallback `"自定义"`

---

## 8. 配置存储

| 维度 | 方案 |
|---|---|
| 存储位置 | `system_settings.payload_json` 的 `aiModelConfig.customProvider` 字段 |
| 迁移需求 | 无（JSON 字段自然扩展，旧数据反序列化时 `customProvider` 为 null，前端展示"未配置"） |
| 加密 | `customProvider.encryptedApiKey` 用 `PasswordEncryptionUtil.encrypt/decrypt`（和 4 家一致） |
| 脱敏 | `customProvider.apiKeyMasked` 用 `maskApiKey()`（和 4 家一致） |
| 环境变量 | custom 不支持 env fallback（用户必须在前端填 apiKey），`AiProviderCatalog.environmentKeys("custom")` 返回空 List |

---

## 9. 健康检查 & 启动检查

### 9.1 AiProviderHealthIndicator

active provider 为 custom 时，返回：

```json
{
  "status": "configured",
  "provider": "custom",
  "model": "anthropic/claude-3.5-sonnet",
  "baseUrl": "openrouter.ai",  // host-only 脱敏
  "apiKeyConfigured": true
}
```

### 9.2 AiConfigurationStartupChecker

active 为 custom 时，检查 `customProvider` 是否已配置（baseUrl/apiKey 非空）。生产 profile 下未配置则打 ERROR 日志告警（不阻止启动）。

---

## 10. 前端 UI

系统设置页（`SettingsPanel.vue` 或对应组件）新增"自定义 Provider"区块：

```
┌─ 自定义 Provider ─────────────────────────────┐
│ 名称:     [OpenRouter              ]          │
│ Base URL: [https://openrouter.ai/api/v1/chat/completions]
│ API Key:  [sk-or-v1-****         ] [测试连接] │
│ 模型:     [anthropic/claude-3.5-sonnet]       │
│ ☑ 启用                                         │
└────────────────────────────────────────────────┘
```

### 10.1 字段

- `name`：可选，用于日志展示，默认 `"自定义"`
- `baseUrl`：必填，合法 URL
- `apiKey`：必填，前端脱敏显示
- `model`：必填
- `enabled`：启用开关

### 10.2 交互

- "测试连接"按钮：调 `POST /api/settings/ai-models/test`，后端复用 `AiModelConnectionTestService.testConnection()`（已支持任意 providerCode）
- activeProvider 切换器：在现有 4 家下拉选项后追加"自定义"选项
- 切换到"自定义"前，若 `customProvider` 未配置，前端拦截并提示"请先填写自定义 Provider 配置"

### 10.3 校验

- baseUrl 必填且合法 URL（前端校验 + 后端 SSRF 校验）
- apiKey 必填
- model 必填

---

## 11. 错误处理

| 场景 | HTTP 状态码 | 错误消息 |
|---|---|---|
| SSRF 校验失败（云元数据/保留 IP） | 400 | "自定义 Provider baseUrl 不允许指向该地址" |
| URL 格式错误 | 400 | "自定义 Provider baseUrl 格式无效" |
| customProvider 未配置但被切为 active | 400 | "自定义 Provider 未配置，请先填写 baseUrl 和 API Key" |
| 调用 custom 失败（402 余额不足） | 402 | "自定义 API 余额不足，请检查账户" |
| 调用 custom 失败（401 鉴权） | 401 | "自定义 API Key 无效或无权限" |
| 调用 custom 失败（429 限流） | 429 | "自定义 API 请求过于频繁或额度受限" |
| 调用 custom 失败（5xx） | 502 | "自定义 API 服务暂时不可用" |

复用现有 `ExternalServiceException` + `RetryableAiProviderException` 包装机制，custom 走和 4 家相同的重试策略（429/5xx 重试 3 次指数退避）。

---

## 12. 测试策略

### 12.1 后端单元测试

- `SsrfValidatorTest`：覆盖各种 URL（合法/非法 IP/云元数据/localhost/内网/特殊字符）
- `AiConfigServiceTest`：custom 校验路径、custom 加解密
- `RoutingAiProviderTest`：active=custom 时的路由逻辑
- `OpenAiBidAgentConfigurationResolverTest`：路径 2 识别 custom + 强制 CHAT_COMPLETIONS
- `AiProviderCatalogTest`：`isCustomProvider()` 判别

### 12.2 后端集成测试

- `SettingsControllerIT`：保存/读取 customProvider、activeProvider 切换到 custom
- `AiModelConnectionTestServiceIT`：测试 custom 连接（mock HTTP 服务）

### 12.3 前端 E2E

- 配置 customProvider → 切换为 active → 触发一次 AI 调用（如标讯分析）→ 验证后端用 customProvider 配置发起请求

### 12.4 Architecture Test

- 确认新增 `SsrfValidator` 位于 `common.security` 包，不违反 `ArchitectureTest` 现有规则
- 确认改动后 `RoutingAiProvider` 仍保持 `@Primary`，不破坏依赖注入

---

## 13. 改动文件清单

### 13.1 后端

| 文件 | 改动类型 |
|---|---|
| `backend/src/main/java/com/xiyu/bid/settings/service/AiProviderCatalog.java` | 加 `custom` 条目 + `isCustomProvider()` |
| `backend/src/main/java/com/xiyu/bid/settings/service/AiConfigService.java` | `validateBaseUrl()` 分流（BUILTIN/CUSTOM） |
| `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsResponse.java` | `AiModelConfig` 加 `customProvider` 字段 |
| `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsUpdateRequest.java` | `AiModelConfigUpdate` 加 `customProvider` 字段 |
| `backend/src/main/java/com/xiyu/bid/ai/client/RoutingAiProvider.java` | `resolveActiveConfig()` 识别 custom |
| `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolver.java` | 识别 custom + 强制 CHAT_COMPLETIONS |
| `backend/src/main/java/com/xiyu/bid/ai/client/OpenAiCompatibleClient.java` | `providerDisplayName()` 加 custom case |
| `backend/src/main/java/com/xiyu/bid/ai/config/AiProviderHealthIndicator.java` | custom 状态返回 |
| `backend/src/main/java/com/xiyu/bid/bootstrap/AiConfigurationStartupChecker.java` | custom 启动检查 |
| `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiTenderDocumentAnalyzer.java` | 修复 URL 嗅探反模式 |
| **新增** `backend/src/main/java/com/xiyu/bid/common/security/SsrfValidator.java` | SSRF 校验工具类 |
| **新增** `backend/src/test/java/com/xiyu/bid/common/security/SsrfValidatorTest.java` | 单元测试 |

### 13.2 前端

| 文件 | 改动类型 |
|---|---|
| `src/views/settings/SettingsPanel.vue`（或对应组件） | 加 customProvider 表单区块 |
| `src/api/settings.js`（或对应模块） | 同步 customProvider 字段 |

总计约 11 个后端文件（含新增 2 个）+ 2 个前端文件。

---

## 14. 风险与回滚

### 14.1 风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| SSRF 防护不完整，被恶意用户利用打内网 | 中 | 单元测试覆盖各类 IP；后续可加 DNS 解析后再校验 |
| customProvider 配置丢失（JSON 反序列化兼容性） | 低 | Jackson 默认 null 处理；旧数据 `customProvider=null` 即"未配置" |
| 路径 2 强制 CHAT_COMPLETIONS 导致部分调用失败 | 低 | 现有 4 家也支持 CHAT_COMPLETIONS，custom 走这条是合理的 |
| 前端表单校验与后端 SSRF 校验不一致 | 低 | 前端只做必填 + URL 格式校验，安全校验全部后端兜底 |

### 14.2 回滚

- 代码回滚：`git revert` 即可，无 DB schema 变更
- 数据回滚：旧版本读 `payload_json` 时自动忽略 `customProvider` 字段，无需清理
- activeProvider 回滚：若 active=custom 时回滚到旧版本，启动检查会告警"unknown provider: custom"，需手动改回 4 家之一

---

## 15. 验收标准

1. 用户可在系统设置页填写"自定义 Provider"的 name/baseUrl/apiKey/model，并保存
2. 用户可将 activeProvider 切换为"自定义"
3. 切换后，标讯分析、项目分析、标书质量预览、招标文件抽取、草稿生成等所有 AI 调用都使用自定义 Provider 配置
4. 测试连接按钮对自定义 Provider 生效
5. SSRF 校验：填 `http://169.254.169.254/...` 时保存被拒绝
6. 允许 `http://localhost:11434/v1/chat/completions`（本地 Ollama）
7. 允许 `https://openrouter.ai/api/v1/chat/completions`（OpenRouter）
8. API Key 加密存储，前端只看到 `sk-or-v1-****` 脱敏形式
9. 健康检查 `/actuator/health/aiProvider` 返回 custom 配置状态
10. 现有 4 家配置和调用不受影响（回归测试通过）
