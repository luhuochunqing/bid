# 自定义 AI Provider 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 4 家硬编码 AI Provider 之外，新增一个"自定义 Provider"槽位，用户可基于 base URL + API Key + Model 接入任意 OpenAI-compatible 平台（OpenRouter / 硅基流动 / 本地 Ollama / 公司内网反代等）。

**Architecture:** 方案 A（最小侵入）。在 `AiProviderCatalog` 加 `custom` 条目作为第 5 个内置 Provider，但走差异化校验（SSRF 校验替代域名白名单）。两条 AI 调用路径（`ai/client/` + `biddraftagent/infrastructure/openai/`）都识别 `custom` 并从 `SettingsResponse.AiModelConfig.customProvider` 字段取配置。配置存储复用 `system_settings.payload_json`，无需 Flyway 迁移。

**Tech Stack:** Java 21 + Spring Boot 3.2 + JPA + Lombok | Vue 3 + Element Plus | JUnit 5 + Mockito

**关联设计文档:** [docs/architecture/custom-ai-provider-design.md](file:///Users/user/xiyu/worktrees/cursor/docs/architecture/custom-ai-provider-design.md)

---

## File Structure

### 后端新增

| 文件 | 职责 |
|---|---|
| `backend/src/main/java/com/xiyu/bid/common/security/SsrfValidator.java` | SSRF 校验工具类，校验自定义 Provider baseUrl 是否安全 |
| `backend/src/test/java/com/xiyu/bid/common/security/SsrfValidatorTest.java` | SsrfValidator 单元测试 |

### 后端修改

| 文件 | 改动 |
|---|---|
| `backend/src/main/java/com/xiyu/bid/settings/service/AiProviderCatalog.java` | 加 `custom` 条目 + `isCustomProvider()` + `providerDisplayName()` 静态方法 |
| `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsResponse.java` | `AiModelConfig` 加 `customProvider` 字段 |
| `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsUpdateRequest.java` | `AiModelConfigUpdate` 加 `customProvider` 字段 |
| `backend/src/main/java/com/xiyu/bid/settings/service/AiConfigService.java` | `validateBaseUrl` 分流 + `normalizeAiModelConfig`/`mergeAiModelConfig`/`copyAiModelConfigForResponse`/`findAiProvider`/`resolveAiApiKey`/`saveSuccessfulAiProviderTestConfig`/`updateAiProviderTestResult` 支持 custom |
| `backend/src/main/java/com/xiyu/bid/ai/client/RoutingAiProvider.java` | `resolveActiveConfig()` 识别 custom |
| `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolver.java` | 识别 custom + 强制 CHAT_COMPLETIONS |
| `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiTenderDocumentAnalyzer.java` | 修复 URL 嗅探反模式，改用 `AiProviderCatalog.providerDisplayName()` |
| `backend/src/main/java/com/xiyu/bid/ai/config/AiProviderHealthIndicator.java` | active=custom 时返回 customProvider 状态 |
| `backend/src/main/java/com/xiyu/bid/bootstrap/AiConfigurationStartupChecker.java` | active=custom 时检查 customProvider 配置 |

### 前端修改

| 文件 | 改动 |
|---|---|
| `src/views/System/settings/useAiModelSettings.js` | `normalizeAiModelConfig`/`buildPayload`/`validateProvider` 支持 customProvider 字段 |
| `src/views/System/settings/AiModelSettingsPanel.vue` | 加"自定义 Provider"表单区块 + activeProvider 选项追加"自定义" |

---

## Task 1: 新增 SsrfValidator 工具类

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/common/security/SsrfValidator.java`
- Test: `backend/src/test/java/com/xiyu/bid/common/security/SsrfValidatorTest.java`

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/xiyu/bid/common/security/SsrfValidatorTest.java`:

```java
package com.xiyu.bid.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfValidatorTest {

    @Test
    void shouldAcceptHttpsUrl() {
        assertThatCode(() -> SsrfValidator.validate("https://openrouter.ai/api/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptHttpUrl() {
        assertThatCode(() -> SsrfValidator.validate("http://localhost:11434/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptInternalNetworkIp() {
        assertThatCode(() -> SsrfValidator.validate("http://10.0.0.5:8080/v1/chat/completions"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SsrfValidator.validate("http://192.168.1.10:8080/v1/chat/completions"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SsrfValidator.validate("http://172.16.5.5:8080/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptLoopbackIp() {
        assertThatCode(() -> SsrfValidator.validate("http://127.0.0.1:11434/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectCloudMetadataAddress() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许指向该地址");
    }

    @Test
    void shouldRejectLinkLocalRange() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://169.254.0.1/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectZeroNetwork() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://0.0.0.0/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectClassEReserved() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://240.0.0.1/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBroadcastAddress() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://255.255.255.255/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUrlWithUserInfo() {
        assertThatThrownBy(() -> SsrfValidator.validate("https://user:pass@openrouter.ai/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许包含 userinfo");
    }

    @Test
    void shouldRejectNonHttpScheme() {
        assertThatThrownBy(() -> SsrfValidator.validate("ftp://example.com/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 http 或 https");
    }

    @Test
    void shouldRejectInvalidUrl() {
        assertThatThrownBy(() -> SsrfValidator.validate("not a url"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式无效");
    }

    @Test
    void shouldRejectNullUrl() {
        assertThatThrownBy(() -> SsrfValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldRejectBlankUrl() {
        assertThatThrownBy(() -> SsrfValidator.validate("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldRejectUrlWithoutHost() {
        assertThatThrownBy(() -> SsrfValidator.validate("https:///v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd backend && mvn test -Dtest=SsrfValidatorTest`
Expected: 编译失败，`SsrfValidator` 类不存在

- [ ] **Step 3: 实现 SsrfValidator**

创建 `backend/src/main/java/com/xiyu/bid/common/security/SsrfValidator.java`:

```java
package com.xiyu.bid.common.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 校验外部 AI Provider 的 baseUrl 是否安全（SSRF 防护）。
 *
 * <p>允许：HTTP/HTTPS、任意公网域名、loopback、内网 IP（10/172.16/192.168）。
 * <p>禁止：云元数据地址（169.254.0.0/16）、0.0.0.0/8、240.0.0.0/4、广播地址、URL userinfo。
 */
public final class SsrfValidator {

    private SsrfValidator() {
    }

    /**
     * 校验 baseUrl，非法时抛 IllegalArgumentException。
     */
    public static void validate(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 不能为空");
        }

        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 格式无效", exception);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 必须是 http 或 https 协议");
        }

        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许包含 userinfo（user:pass@）");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 缺少 host");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        validateHost(normalizedHost);
    }

    private static void validateHost(String host) {
        if (isIpv4Literal(host)) {
            validateIpv4(host);
            return;
        }
        if (isIpv6Literal(host)) {
            validateIpv6(host);
            return;
        }
        // 域名：不做白名单限制
    }

    private static boolean isIpv4Literal(String host) {
        if (!host.contains(".")) return false;
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String host) {
        return host.contains(":");
    }

    private static void validateIpv4(String host) {
        long ip = parseIpv4ToLong(host);
        if (inRange(ip, 169L << 24 | 254L << 16, 0xFFFF0000L)) {       // 169.254.0.0/16
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（link-local 云元数据）");
        }
        if (inRange(ip, 0L, 0xFF000000L)) {                            // 0.0.0.0/8
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（本网络）");
        }
        if (inRange(ip, 240L << 24, 0xF0000000L)) {                    // 240.0.0.0/4
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（保留地址）");
        }
        if (ip == 0xFFFFFFFFL) {                                       // 255.255.255.255
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（广播地址）");
        }
        // 允许：127.0.0.0/8、10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、公网 IP
    }

    private static void validateIpv6(String host) {
        // 简化处理：禁止 ::（未指定）和 fe80::/10（link-local）
        String compressed = host.toLowerCase(Locale.ROOT);
        if (compressed.equals("::")) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（未指定地址）");
        }
        if (compressed.startsWith("fe80:") || compressed.startsWith("fe9") || compressed.startsWith("fea") || compressed.startsWith("feb")) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（link-local）");
        }
        // 允许 ::1 loopback、fc00::/7 ULA、公网 IPv6
    }

    private static long parseIpv4ToLong(String host) {
        String[] parts = host.split("\\.");
        long result = 0;
        for (String part : parts) {
            result = (result << 8) | Integer.parseInt(part);
        }
        return result;
    }

    private static boolean inRange(long ip, long networkStart, long mask) {
        return (ip & mask) == (networkStart & mask);
    }
}
```

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend && mvn test -Dtest=SsrfValidatorTest`
Expected: 15 tests PASS

- [ ] **Step 5: 跑架构测试确保不破坏边界**

Run: `cd backend && mvn test -Dtest=ArchitectureTest`
Expected: PASS（确认 `common.security` 包不违反现有规则）

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/common/security/SsrfValidator.java \
        backend/src/test/java/com/xiyu/bid/common/security/SsrfValidatorTest.java
git commit -m "feat(security): 新增 SsrfValidator 工具类用于自定义 AI Provider baseUrl 校验

scope: common/security/SsrfValidator*"
```

---

## Task 2: AiProviderCatalog 加 custom 条目

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/settings/service/AiProviderCatalog.java`
- Modify: `backend/src/test/java/com/xiyu/bid/settings/service/AiProviderCatalogTest.java`

- [ ] **Step 1: 写失败测试**

在 `backend/src/test/java/com/xiyu/bid/settings/service/AiProviderCatalogTest.java` 末尾追加：

```java
    @Test
    void isCustomProviderReturnsTrueForCustomCode() {
        assertThat(AiProviderCatalog.isCustomProvider("custom")).isTrue();
        assertThat(AiProviderCatalog.isCustomProvider("openai")).isFalse();
        assertThat(AiProviderCatalog.isCustomProvider(null)).isFalse();
        assertThat(AiProviderCatalog.isCustomProvider("")).isFalse();
    }

    @Test
    void customProviderIsSupportedAndIncludedInProviderOrder() {
        assertThat(catalog.isSupported("custom")).isTrue();
        assertThat(catalog.supportedProviderCodes()).contains("custom");
    }

    @Test
    void customProviderDefaultSettingHasNullBaseUrlAndModel() {
        SettingsResponse.AiProviderSetting setting = catalog.defaultProviderSetting("custom");
        assertThat(setting.getProviderCode()).isEqualTo("custom");
        assertThat(setting.getProviderName()).isEqualTo("自定义");
        assertThat(setting.getBaseUrl()).isNull();
        assertThat(setting.getModel()).isNull();
        assertThat(setting.getEnabled()).isTrue();
    }

    @Test
    void customProviderHasNoEnvironmentKeys() {
        assertThat(catalog.environmentKeys("custom")).isEmpty();
    }

    @Test
    void validateBaseUrlForCustomRejectsCloudMetadata() {
        assertThatThrownBy(() -> catalog.validateBaseUrl("custom", "http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateBaseUrlForCustomAcceptsOpenRouter() {
        catalog.validateBaseUrl("custom", "https://openrouter.ai/api/v1/chat/completions");
    }

    @Test
    void validateBaseUrlForCustomAcceptsLocalhost() {
        catalog.validateBaseUrl("custom", "http://localhost:11434/v1/chat/completions");
    }

    @Test
    void providerDisplayNameReturnsNameForKnownCode() {
        assertThat(AiProviderCatalog.providerDisplayName("openai")).isEqualTo("OpenAI");
        assertThat(AiProviderCatalog.providerDisplayName("deepseek")).isEqualTo("DeepSeek");
        assertThat(AiProviderCatalog.providerDisplayName("qwen")).isEqualTo("通义千问");
        assertThat(AiProviderCatalog.providerDisplayName("doubao")).isEqualTo("豆包");
        assertThat(AiProviderCatalog.providerDisplayName("custom")).isEqualTo("自定义");
        assertThat(AiProviderCatalog.providerDisplayName("unknown")).isEqualTo("AI");
    }
```

注意：在文件顶部补 import `import static org.assertj.core.api.Assertions.assertThatThrownBy;`（如未有）。

- [ ] **Step 2: 跑测试验证失败**

Run: `cd backend && mvn test -Dtest=AiProviderCatalogTest`
Expected: 编译失败，`isCustomProvider` / `providerDisplayName` 方法不存在

- [ ] **Step 3: 修改 AiProviderCatalog**

修改 `backend/src/main/java/com/xiyu/bid/settings/service/AiProviderCatalog.java`：

(a) 在 `providers` Map 中追加 custom 条目：

```java
    private final Map<String, AiProviderDefinition> providers = Map.of(
            "openai", new AiProviderDefinition(
                    "openai", "OpenAI",
                    "https://api.openai.com/v1/chat/completions",
                    "gpt-4o-mini",
                    List.of("OPENAI_API_KEY"),
                    Set.of("api.openai.com")
            ),
            "deepseek", new AiProviderDefinition(
                    "deepseek", "DeepSeek",
                    "https://api.deepseek.com/chat/completions",
                    "deepseek-chat",
                    List.of("DEEPSEEK_API_KEY"),
                    Set.of("api.deepseek.com")
            ),
            "qwen", new AiProviderDefinition(
                    "qwen", "通义千问",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                    "qwen-plus",
                    List.of("DASHSCOPE_API_KEY", "QWEN_API_KEY"),
                    Set.of("dashscope.aliyuncs.com")
            ),
            "doubao", new AiProviderDefinition(
                    "doubao", "豆包",
                    "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                    "doubao-1-5-pro-32k-250115",
                    List.of("ARK_API_KEY", "DOUBAO_API_KEY", "VOLCENGINE_API_KEY"),
                    Set.of("ark.cn-beijing.volces.com")
            ),
            "custom", new AiProviderDefinition(
                    "custom", "自定义",
                    null, null,
                    List.of(),
                    Set.of()
            )
    );

    private final List<String> providerOrder = List.of("openai", "deepseek", "qwen", "doubao", "custom");
```

(b) 修改 `defaultProviderSetting` 以支持 custom 的 null 默认值：

```java
    public SettingsResponse.AiProviderSetting defaultProviderSetting(String providerCode) {
        AiProviderDefinition provider = get(providerCode);
        return SettingsResponse.AiProviderSetting.builder()
                .providerCode(provider.code())
                .providerName(provider.name())
                .enabled(true)
                .baseUrl(provider.defaultBaseUrl())
                .model(provider.defaultModel())
                .build();
    }
```

（无需改动，`provider.defaultBaseUrl()` 为 null 时直接传 null，builder 接受 null）

(c) 修改 `validateBaseUrl` 方法分流：

```java
    public void validateBaseUrl(String providerCode, String baseUrl) {
        if (isCustomProvider(providerCode)) {
            SsrfValidator.validate(baseUrl);
            return;
        }
        AiProviderDefinition provider = get(providerCode);
        URI uri;
        try {
            uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AI API 地址格式不正确", exception);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null || host.isBlank()) {
            throw new IllegalArgumentException("AI API 地址必须是 HTTPS 完整地址");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!provider.allowedHosts().contains(normalizedHost)) {
            throw new IllegalArgumentException("AI API 地址必须匹配当前厂商的官方域名");
        }
    }
```

(d) 在类末尾加 `isCustomProvider` 静态方法和 `providerDisplayName` 静态方法。需要把 `AiProviderDefinition` 改为可被静态方法访问——把 providers Map 改为 `static final`，或把 `isCustomProvider`/`providerDisplayName` 改为实例方法。

为最小改动，把 `providers` Map 改为 `static final`，并把 `providerOrder` 也改为 `static final`。同时保留实例方法 `isSupported`/`environmentKeys`/`defaultProviderSetting`/`validateBaseUrl`/`normalize` 不变。

完整改动后的文件结构：

```java
package com.xiyu.bid.settings.service;

import com.xiyu.bid.common.security.SsrfValidator;
import com.xiyu.bid.settings.dto.SettingsResponse;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AiProviderCatalog {

    public static final String CUSTOM_PROVIDER_CODE = "custom";

    private static final String DEFAULT_ACTIVE_PROVIDER = "deepseek";

    private static final Map<String, AiProviderDefinition> PROVIDERS = Map.of(
            "openai", new AiProviderDefinition(
                    "openai", "OpenAI",
                    "https://api.openai.com/v1/chat/completions",
                    "gpt-4o-mini",
                    List.of("OPENAI_API_KEY"),
                    Set.of("api.openai.com")
            ),
            "deepseek", new AiProviderDefinition(
                    "deepseek", "DeepSeek",
                    "https://api.deepseek.com/chat/completions",
                    "deepseek-chat",
                    List.of("DEEPSEEK_API_KEY"),
                    Set.of("api.deepseek.com")
            ),
            "qwen", new AiProviderDefinition(
                    "qwen", "通义千问",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                    "qwen-plus",
                    List.of("DASHSCOPE_API_KEY", "QWEN_API_KEY"),
                    Set.of("dashscope.aliyuncs.com")
            ),
            "doubao", new AiProviderDefinition(
                    "doubao", "豆包",
                    "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                    "doubao-1-5-pro-32k-250115",
                    List.of("ARK_API_KEY", "DOUBAO_API_KEY", "VOLCENGINE_API_KEY"),
                    Set.of("ark.cn-beijing.volces.com")
            ),
            "custom", new AiProviderDefinition(
                    "custom", "自定义",
                    null, null,
                    List.of(),
                    Set.of()
            )
    );

    private static final List<String> PROVIDER_ORDER = List.of("openai", "deepseek", "qwen", "doubao", "custom");

    public static boolean isCustomProvider(String providerCode) {
        return providerCode != null && CUSTOM_PROVIDER_CODE.equals(providerCode.trim().toLowerCase(Locale.ROOT));
    }

    public static String providerDisplayName(String providerCode) {
        if (providerCode == null) return "AI";
        AiProviderDefinition provider = PROVIDERS.get(providerCode.trim().toLowerCase(Locale.ROOT));
        return provider != null ? provider.name() : "AI";
    }

    public String defaultActiveProvider() {
        return DEFAULT_ACTIVE_PROVIDER;
    }

    public List<String> supportedProviderCodes() {
        return PROVIDER_ORDER;
    }

    public boolean isSupported(String providerCode) {
        return PROVIDERS.containsKey(normalize(providerCode));
    }

    public SettingsResponse.AiProviderSetting defaultProviderSetting(String providerCode) {
        AiProviderDefinition provider = get(providerCode);
        return SettingsResponse.AiProviderSetting.builder()
                .providerCode(provider.code())
                .providerName(provider.name())
                .enabled(true)
                .baseUrl(provider.defaultBaseUrl())
                .model(provider.defaultModel())
                .build();
    }

    public List<String> environmentKeys(String providerCode) {
        return get(providerCode).environmentKeys();
    }

    public void validateBaseUrl(String providerCode, String baseUrl) {
        if (isCustomProvider(providerCode)) {
            SsrfValidator.validate(baseUrl);
            return;
        }
        AiProviderDefinition provider = get(providerCode);
        URI uri;
        try {
            uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AI API 地址格式不正确", exception);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null || host.isBlank()) {
            throw new IllegalArgumentException("AI API 地址必须是 HTTPS 完整地址");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!provider.allowedHosts().contains(normalizedHost)) {
            throw new IllegalArgumentException("AI API 地址必须匹配当前厂商的官方域名");
        }
    }

    public String normalize(String providerCode) {
        return providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
    }

    private AiProviderDefinition get(String providerCode) {
        String normalizedCode = normalize(providerCode);
        AiProviderDefinition provider = PROVIDERS.get(normalizedCode);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported AI provider: " + providerCode);
        }
        return provider;
    }

    private record AiProviderDefinition(
            String code,
            String name,
            String defaultBaseUrl,
            String defaultModel,
            List<String> environmentKeys,
            Set<String> allowedHosts
    ) {
    }
}
```

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend && mvn test -Dtest=AiProviderCatalogTest`
Expected: 所有测试 PASS（包括原有 4 家测试 + 新增 custom 测试）

- [ ] **Step 5: 跑相关测试确保无回归**

Run: `cd backend && mvn test -Dtest=AiConfigServiceTest,RoutingAiProviderTest,OpenAiBidAgentConfigurationResolverTest,SettingsServiceTest,SettingsControllerTest`
Expected: 可能部分失败（因为 custom 现在是 supported，但 DTO 还没加 customProvider 字段）。**记录失败点**，后续 task 修复。

如果失败是因为 `normalizeAiModelConfig` 给 custom 也生成了默认 setting，但 `AiModelConfig` 还没有 `customProvider` 字段——这是预期失败，Task 3+4 会修复。先确认编译通过。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/settings/service/AiProviderCatalog.java \
        backend/src/test/java/com/xiyu/bid/settings/service/AiProviderCatalogTest.java
git commit -m "feat(settings): AiProviderCatalog 加 custom 条目支持自定义 Provider

- 新增 isCustomProvider() / providerDisplayName() 静态方法
- validateBaseUrl 对 custom 走 SsrfValidator，4 家走原域名白名单
- supportedProviderCodes 包含 custom

scope: settings/service/AiProviderCatalog*"
```

---

## Task 3: 扩展 DTO 加 customProvider 字段

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsResponse.java`
- Modify: `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsUpdateRequest.java`

- [ ] **Step 1: SettingsResponse.AiModelConfig 加 customProvider 字段**

修改 `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsResponse.java` 第 109-112 行：

```java
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiModelConfig {
        private String activeProvider;
        private List<AiProviderSetting> providers;
        private AiProviderSetting customProvider;
    }
```

- [ ] **Step 2: SettingsUpdateRequest.AiModelConfigUpdate 加 customProvider 字段**

修改 `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsUpdateRequest.java` 第 108-111 行：

```java
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiModelConfigUpdate {
        private String activeProvider;
        private List<AiProviderSettingUpdate> providers;
        private AiProviderSettingUpdate customProvider;
    }
```

- [ ] **Step 3: 跑编译验证**

Run: `cd backend && mvn compile -q`
Expected: 编译通过

- [ ] **Step 4: 跑全量测试确认无回归**

Run: `cd backend && mvn test -Dtest=AiConfigServiceTest,RoutingAiProviderTest,OpenAiBidAgentConfigurationResolverTest,SettingsServiceTest,SettingsControllerTest`
Expected: 部分测试仍可能失败（因为 normalizeAiModelConfig 还没处理 customProvider），但编译通过

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/settings/dto/SettingsResponse.java \
        backend/src/main/java/com/xiyu/bid/settings/dto/SettingsUpdateRequest.java
git commit -m "feat(settings): AiModelConfig 加 customProvider 字段

scope: settings/dto/{SettingsResponse,SettingsUpdateRequest}"
```

---

## Task 4: AiConfigService 全面支持 customProvider

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/settings/service/AiConfigService.java`
- Modify: `backend/src/test/java/com/xiyu/bid/settings/service/AiConfigServiceTest.java`

- [ ] **Step 1: 写失败测试**

在 `backend/src/test/java/com/xiyu/bid/settings/service/AiConfigServiceTest.java` 末尾追加测试（如果文件不存在则创建；如已存在参考现有测试模式）：

```java
    @Test
    void normalizeAiModelConfigInitializesCustomProviderWithDefaults() {
        SettingsResponse.AiModelConfig source = SettingsResponse.AiModelConfig.builder()
                .activeProvider("deepseek")
                .providers(List.of())
                .build();
        SettingsResponse.AiModelConfig result = aiConfigService.normalizeAiModelConfig(source);
        assertThat(result.getCustomProvider()).isNotNull();
        assertThat(result.getCustomProvider().getProviderCode()).isEqualTo("custom");
        assertThat(result.getCustomProvider().getProviderName()).isEqualTo("自定义");
        assertThat(result.getCustomProvider().getEnabled()).isTrue();
        assertThat(result.getCustomProvider().getBaseUrl()).isNull();
        assertThat(result.getCustomProvider().getModel()).isNull();
    }

    @Test
    void normalizeAiModelConfigMergesCustomProviderFields() {
        SettingsResponse.AiProviderSetting custom = SettingsResponse.AiProviderSetting.builder()
                .providerCode("custom")
                .providerName("OpenRouter")
                .enabled(true)
                .baseUrl("https://openrouter.ai/api/v1/chat/completions")
                .model("anthropic/claude-3.5-sonnet")
                .encryptedApiKey("encrypted-key")
                .build();
        SettingsResponse.AiModelConfig source = SettingsResponse.AiModelConfig.builder()
                .activeProvider("custom")
                .providers(List.of())
                .customProvider(custom)
                .build();
        SettingsResponse.AiModelConfig result = aiConfigService.normalizeAiModelConfig(source);
        assertThat(result.getActiveProvider()).isEqualTo("custom");
        assertThat(result.getCustomProvider().getProviderName()).isEqualTo("自定义");
        assertThat(result.getCustomProvider().getBaseUrl()).isEqualTo("https://openrouter.ai/api/v1/chat/completions");
        assertThat(result.getCustomProvider().getModel()).isEqualTo("anthropic/claude-3.5-sonnet");
        assertThat(result.getCustomProvider().getEncryptedApiKey()).isEqualTo("encrypted-key");
    }

    @Test
    void mergeAiModelConfigUpdatesCustomProvider() {
        SettingsResponse.AiModelConfig current = SettingsResponse.AiModelConfig.builder()
                .activeProvider("deepseek")
                .providers(List.of())
                .build();
        SettingsUpdateRequest.AiProviderSettingUpdate customUpdate = SettingsUpdateRequest.AiProviderSettingUpdate.builder()
                .providerCode("custom")
                .enabled(true)
                .baseUrl("https://openrouter.ai/api/v1/chat/completions")
                .model("anthropic/claude-3.5-sonnet")
                .apiKeyPlaintext("sk-or-v1-test-key")
                .build();
        SettingsUpdateRequest.AiModelConfigUpdate update = SettingsUpdateRequest.AiModelConfigUpdate.builder()
                .activeProvider("custom")
                .customProvider(customUpdate)
                .build();
        SettingsResponse.AiModelConfig result = aiConfigService.mergeAiModelConfig(current, update);
        assertThat(result.getActiveProvider()).isEqualTo("custom");
        assertThat(result.getCustomProvider().getBaseUrl()).isEqualTo("https://openrouter.ai/api/v1/chat/completions");
        assertThat(result.getCustomProvider().getModel()).isEqualTo("anthropic/claude-3.5-sonnet");
        assertThat(result.getCustomProvider().getEncryptedApiKey()).isNotBlank();
    }

    @Test
    void resolveAiApiKeyForCustomReturnsDecryptedKey() {
        SettingsResponse.AiProviderSetting custom = SettingsResponse.AiProviderSetting.builder()
                .providerCode("custom")
                .providerName("自定义")
                .enabled(true)
                .baseUrl("https://openrouter.ai/api/v1/chat/completions")
                .model("test-model")
                .encryptedApiKey(passwordEncryptionUtil.encrypt("sk-test-key"))
                .build();
        SettingsResponse.AiModelConfig config = SettingsResponse.AiModelConfig.builder()
                .activeProvider("custom")
                .providers(List.of())
                .customProvider(custom)
                .build();
        when(settingsService.getSettingsInternal()).thenReturn(SettingsResponse.builder()
                .aiModelConfig(config)
                .build());
        String apiKey = aiConfigService.resolveAiApiKey("custom");
        assertThat(apiKey).isEqualTo("sk-test-key");
    }

    @Test
    void copyAiModelConfigForResponseMasksCustomProviderApiKey() {
        SettingsResponse.AiProviderSetting custom = SettingsResponse.AiProviderSetting.builder()
                .providerCode("custom")
                .providerName("自定义")
                .enabled(true)
                .baseUrl("https://openrouter.ai/api/v1/chat/completions")
                .model("test-model")
                .encryptedApiKey(passwordEncryptionUtil.encrypt("sk-test-key-12345"))
                .build();
        SettingsResponse.AiModelConfig config = SettingsResponse.AiModelConfig.builder()
                .activeProvider("custom")
                .providers(List.of())
                .customProvider(custom)
                .build();
        SettingsResponse.AiModelConfig result = aiConfigService.copyAiModelConfigForResponse(config);
        assertThat(result.getCustomProvider()).isNotNull();
        assertThat(result.getCustomProvider().getApiKeyMasked()).startsWith("sk-t");
        assertThat(result.getCustomProvider().getApiKeyMasked()).endsWith("2345");
        assertThat(result.getCustomProvider().getApiKeyMasked()).contains("****");
        assertThat(result.getCustomProvider().getApiKeyConfigured()).isTrue();
        assertThat(result.getCustomProvider().getEncryptedApiKey()).isNull();
    }
```

注意：根据现有测试类的依赖注入方式调整 `when(settingsService.getSettingsInternal())` 和 `passwordEncryptionUtil` 字段名。

- [ ] **Step 2: 跑测试验证失败**

Run: `cd backend && mvn test -Dtest=AiConfigServiceTest`
Expected: 测试失败，`normalizeAiModelConfig`/`mergeAiModelConfig`/`resolveAiApiKey`/`copyAiModelConfigForResponse` 还没处理 customProvider

- [ ] **Step 3: 修改 AiConfigService**

修改 `backend/src/main/java/com/xiyu/bid/settings/service/AiConfigService.java`：

(a) `findAiProvider` 改为同时支持 custom：

```java
    private SettingsResponse.AiProviderSetting findAiProvider(SettingsResponse.AiModelConfig config, String providerCode) {
        String normalizedCode = normalizeProviderCode(providerCode);
        if (config == null) return null;
        if (AiProviderCatalog.isCustomProvider(normalizedCode)) {
            return config.getCustomProvider();
        }
        if (config.getProviders() == null) return null;
        return config.getProviders().stream()
                .filter(p -> normalizedCode.equals(p.getProviderCode()))
                .findFirst().orElse(null);
    }
```

(b) `normalizeAiModelConfig` 加 customProvider 处理：

```java
    SettingsResponse.AiModelConfig normalizeAiModelConfig(SettingsResponse.AiModelConfig source) {
        SettingsResponse.AiModelConfig defaults = defaultAiModelConfig();
        if (source == null) return defaults;

        Map<String, SettingsResponse.AiProviderSetting> sourceProviders = new HashMap<>();
        if (source.getProviders() != null) {
            for (SettingsResponse.AiProviderSetting provider : source.getProviders()) {
                String providerCode = normalizeProviderCode(provider.getProviderCode());
                if (aiProviderCatalog.isSupported(providerCode)) continue;
                if (AiProviderCatalog.isCustomProvider(providerCode)) continue;  // custom 不在 providers list
                SettingsResponse.AiProviderSetting merged = defaultAiProviderSetting(providerCode);
                merged.setEnabled(provider.getEnabled() != null ? provider.getEnabled() : merged.getEnabled());
                merged.setBaseUrl(nonBlankOrDefault(provider.getBaseUrl(), merged.getBaseUrl()));
                merged.setModel(nonBlankOrDefault(provider.getModel(), merged.getModel()));
                merged.setEncryptedApiKey(provider.getEncryptedApiKey());
                merged.setLastTestStatus(provider.getLastTestStatus());
                merged.setLastTestMessage(provider.getLastTestMessage());
                merged.setLastTestAt(provider.getLastTestAt());
                sourceProviders.put(providerCode, merged);
            }
        }

        // 处理 customProvider
        SettingsResponse.AiProviderSetting customMerged = defaultAiProviderSetting(AiProviderCatalog.CUSTOM_PROVIDER_CODE);
        if (source.getCustomProvider() != null) {
            SettingsResponse.AiProviderSetting src = source.getCustomProvider();
            customMerged.setEnabled(src.getEnabled() != null ? src.getEnabled() : customMerged.getEnabled());
            customMerged.setBaseUrl(src.getBaseUrl());  // null 也接受（用户未填）
            customMerged.setModel(src.getModel());
            customMerged.setEncryptedApiKey(src.getEncryptedApiKey());
            customMerged.setLastTestStatus(src.getLastTestStatus());
            customMerged.setLastTestMessage(src.getLastTestMessage());
            customMerged.setLastTestAt(src.getLastTestAt());
        }

        String normalizedActive = normalizeProviderCode(source.getActiveProvider());
        defaults.setActiveProvider(aiProviderCatalog.isSupported(normalizedActive) ? normalizedActive : aiProviderCatalog.defaultActiveProvider());
        defaults.setProviders(aiProviderCatalog.supportedProviderCodes().stream()
                .filter(code -> !AiProviderCatalog.isCustomProvider(code))
                .map(code -> sourceProviders.getOrDefault(code, defaultAiProviderSetting(code)))
                .toList());
        defaults.setCustomProvider(customMerged);
        return defaults;
    }
```

(c) `mergeAiModelConfig` 加 customProvider 处理：

```java
    SettingsResponse.AiModelConfig mergeAiModelConfig(
            SettingsResponse.AiModelConfig current,
            SettingsUpdateRequest.AiModelConfigUpdate update) {
        SettingsResponse.AiModelConfig normalizedCurrent = normalizeAiModelConfig(current);
        if (update.getActiveProvider() != null && !update.getActiveProvider().isBlank()) {
            normalizedCurrent.setActiveProvider(normalizeProviderCode(update.getActiveProvider()));
        }
        Map<String, SettingsResponse.AiProviderSetting> providerMap = new HashMap<>();
        for (SettingsResponse.AiProviderSetting provider : normalizedCurrent.getProviders()) {
            providerMap.put(provider.getProviderCode(), provider);
        }
        if (update.getProviders() != null) {
            for (var providerUpdate : update.getProviders()) {
                String providerCode = normalizeProviderCode(providerUpdate.getProviderCode());
                if (!aiProviderCatalog.isSupported(providerCode)) continue;
                if (AiProviderCatalog.isCustomProvider(providerCode)) continue;  // custom 不走 providers
                SettingsResponse.AiProviderSetting target = providerMap.get(providerCode);
                if (target == null) {
                    target = defaultAiProviderSetting(providerCode);
                    providerMap.put(providerCode, target);
                }
                if (providerUpdate.getEnabled() != null) target.setEnabled(providerUpdate.getEnabled());
                if (providerUpdate.getBaseUrl() != null) {
                    aiProviderCatalog.validateBaseUrl(providerCode, providerUpdate.getBaseUrl());
                    target.setBaseUrl(providerUpdate.getBaseUrl().trim());
                }
                if (providerUpdate.getModel() != null) target.setModel(providerUpdate.getModel().trim());
                if (providerUpdate.getApiKeyPlaintext() != null && !providerUpdate.getApiKeyPlaintext().isBlank()) {
                    target.setEncryptedApiKey(passwordEncryptionUtil.encrypt(providerUpdate.getApiKeyPlaintext().trim()));
                }
                if (providerUpdate.getLastTestStatus() != null) target.setLastTestStatus(providerUpdate.getLastTestStatus());
                if (providerUpdate.getLastTestMessage() != null) target.setLastTestMessage(providerUpdate.getLastTestMessage());
                if (providerUpdate.getLastTestAt() != null) target.setLastTestAt(providerUpdate.getLastTestAt());
            }
        }
        normalizedCurrent.setProviders(aiProviderCatalog.supportedProviderCodes().stream()
                .filter(code -> !AiProviderCatalog.isCustomProvider(code))
                .map(code -> providerMap.getOrDefault(code, defaultAiProviderSetting(code)))
                .toList());

        // 处理 customProvider 更新
        SettingsResponse.AiProviderSetting customTarget = normalizedCurrent.getCustomProvider();
        if (customTarget == null) {
            customTarget = defaultAiProviderSetting(AiProviderCatalog.CUSTOM_PROVIDER_CODE);
        }
        if (update.getCustomProvider() != null) {
            var customUpdate = update.getCustomProvider();
            if (customUpdate.getEnabled() != null) customTarget.setEnabled(customUpdate.getEnabled());
            if (customUpdate.getBaseUrl() != null) {
                aiProviderCatalog.validateBaseUrl(AiProviderCatalog.CUSTOM_PROVIDER_CODE, customUpdate.getBaseUrl());
                customTarget.setBaseUrl(customUpdate.getBaseUrl().trim());
            }
            if (customUpdate.getModel() != null) customTarget.setModel(customUpdate.getModel().trim());
            if (customUpdate.getApiKeyPlaintext() != null && !customUpdate.getApiKeyPlaintext().isBlank()) {
                customTarget.setEncryptedApiKey(passwordEncryptionUtil.encrypt(customUpdate.getApiKeyPlaintext().trim()));
            }
            if (customUpdate.getLastTestStatus() != null) customTarget.setLastTestStatus(customUpdate.getLastTestStatus());
            if (customUpdate.getLastTestMessage() != null) customTarget.setLastTestMessage(customUpdate.getLastTestMessage());
            if (customUpdate.getLastTestAt() != null) customTarget.setLastTestAt(customUpdate.getLastTestAt());
        }
        normalizedCurrent.setCustomProvider(customTarget);

        if (!aiProviderCatalog.isSupported(normalizedCurrent.getActiveProvider())) {
            normalizedCurrent.setActiveProvider(aiProviderCatalog.defaultActiveProvider());
        }
        return normalizedCurrent;
    }
```

(d) `copyAiModelConfigForResponse` 加 customProvider：

```java
    SettingsResponse.AiModelConfig copyAiModelConfigForResponse(SettingsResponse.AiModelConfig source) {
        SettingsResponse.AiModelConfig normalized = normalizeAiModelConfig(source);
        return SettingsResponse.AiModelConfig.builder()
                .activeProvider(normalized.getActiveProvider())
                .providers(normalized.getProviders().stream()
                        .map(this::copyAiProviderForResponse).toList())
                .customProvider(copyAiProviderForResponse(normalized.getCustomProvider()))
                .build();
    }
```

(e) `defaultAiModelConfig` 加 customProvider：

```java
    private SettingsResponse.AiModelConfig defaultAiModelConfig() {
        return SettingsResponse.AiModelConfig.builder()
                .activeProvider(aiProviderCatalog.defaultActiveProvider())
                .providers(aiProviderCatalog.supportedProviderCodes().stream()
                        .filter(code -> !AiProviderCatalog.isCustomProvider(code))
                        .map(this::defaultAiProviderSetting).toList())
                .customProvider(defaultAiProviderSetting(AiProviderCatalog.CUSTOM_PROVIDER_CODE))
                .build();
    }
```

(f) `updateAiProviderTestResult` 和 `saveSuccessfulAiProviderTestConfig` 已通过 `findAiProvider` 间接支持 custom，无需额外改动。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend && mvn test -Dtest=AiConfigServiceTest`
Expected: 所有测试 PASS

- [ ] **Step 5: 跑回归测试**

Run: `cd backend && mvn test -Dtest=SettingsServiceTest,SettingsControllerTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/settings/service/AiConfigService.java \
        backend/src/test/java/com/xiyu/bid/settings/service/AiConfigServiceTest.java
git commit -m "feat(settings): AiConfigService 全面支持 customProvider 字段

- findAiProvider 对 custom 走 customProvider 字段
- normalizeAiModelConfig 初始化/合并 customProvider
- mergeAiModelConfig 处理 customProvider 更新 + SSRF 校验
- copyAiModelConfigForResponse 脱敏 customProvider apiKey

scope: settings/service/AiConfigService*"
```

---

## Task 5: RoutingAiProvider 识别 custom

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/ai/client/RoutingAiProvider.java`
- Modify: `backend/src/test/java/com/xiyu/bid/ai/client/RoutingAiProviderTest.java`

- [ ] **Step 1: 写失败测试**

在 `backend/src/test/java/com/xiyu/bid/ai/client/RoutingAiProviderTest.java` 末尾追加：

```java
    @Test
    void resolveActiveConfigForCustomReturnsCustomProviderConfig() {
        SettingsResponse.AiProviderSetting custom = SettingsResponse.AiProviderSetting.builder()
                .providerCode("custom")
                .providerName("OpenRouter")
                .enabled(true)
                .baseUrl("https://openrouter.ai/api/v1/chat/completions")
                .model("anthropic/claude-3.5-sonnet")
                .encryptedApiKey(passwordEncryptionUtil.encrypt("sk-or-v1-test"))
                .build();
        SettingsResponse.AiModelConfig aiModelConfig = SettingsResponse.AiModelConfig.builder()
                .activeProvider("custom")
                .providers(List.of())
                .customProvider(custom)
                .build();
        when(aiConfigService.isAiEnabled()).thenReturn(true);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig);
        when(aiConfigService.resolveAiApiKey("custom")).thenReturn("sk-or-v1-test");

        AiProviderRuntimeConfig config = routingAiProvider.resolveActiveConfig();

        assertThat(config.providerCode()).isEqualTo("custom");
        assertThat(config.baseUrl()).isEqualTo("https://openrouter.ai/api/v1/chat/completions");
        assertThat(config.model()).isEqualTo("anthropic/claude-3.5-sonnet");
        assertThat(config.apiKey()).isEqualTo("sk-or-v1-test");
    }

    @Test
    void resolveActiveConfigForCustomThrowsWhenCustomProviderNotConfigured() {
        SettingsResponse.AiModelConfig aiModelConfig = SettingsResponse.AiModelConfig.builder()
                .activeProvider("custom")
                .providers(List.of())
                .customProvider(null)
                .build();
        when(aiConfigService.isAiEnabled()).thenReturn(true);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig);

        assertThatThrownBy(() -> routingAiProvider.resolveActiveConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("自定义 Provider");
    }
```

注意：参考现有 `RoutingAiProviderTest` 顶部的字段声明（`passwordEncryptionUtil` / `routingAiProvider` / `aiConfigService` 等）补全 import 和字段。

- [ ] **Step 2: 跑测试验证失败**

Run: `cd backend && mvn test -Dtest=RoutingAiProviderTest`
Expected: 测试失败，`resolveActiveConfig` 在 providers list 里找不到 "custom"

- [ ] **Step 3: 修改 RoutingAiProvider.resolveActiveConfig**

修改 `backend/src/main/java/com/xiyu/bid/ai/client/RoutingAiProvider.java` 第 71-94 行：

```java
    public AiProviderRuntimeConfig resolveActiveConfig() {
        if (!aiConfigService.isAiEnabled()) {
            throw new IllegalStateException("AI 功能已在系统设置中关闭");
        }

        SettingsResponse.AiModelConfig aiModelConfig = aiConfigService.getInternalAiModelConfig();
        String providerCode = normalize(aiModelConfig.getActiveProvider());

        SettingsResponse.AiProviderSetting provider;
        if (AiProviderCatalog.isCustomProvider(providerCode)) {
            provider = aiModelConfig.getCustomProvider();
            if (provider == null) {
                throw new IllegalStateException("自定义 Provider 未配置，请在系统设置中填写 baseUrl 和 API Key");
            }
        } else {
            provider = aiModelConfig.getProviders().stream()
                    .filter(item -> providerCode.equals(normalize(item.getProviderCode())))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Active AI provider is not configured: " + providerCode));
        }

        if (Boolean.FALSE.equals(provider.getEnabled())) {
            throw new IllegalStateException("当前 AI 厂商已停用，请在系统设置中启用或切换厂商");
        }

        aiProviderCatalog.validateBaseUrl(providerCode, provider.getBaseUrl());
        String apiKey = aiConfigService.resolveAiApiKey(providerCode);
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnvironmentApiKey(providerCode);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(providerCode + " API Key 未配置");
        }

        return new AiProviderRuntimeConfig(providerCode, provider.getBaseUrl(), provider.getModel(), apiKey);
    }
```

注意：在文件顶部加 import `import com.xiyu.bid.settings.service.AiProviderCatalog;`（如未导入）。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend && mvn test -Dtest=RoutingAiProviderTest`
Expected: 所有测试 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/ai/client/RoutingAiProvider.java \
        backend/src/test/java/com/xiyu/bid/ai/client/RoutingAiProviderTest.java
git commit -m "feat(ai): RoutingAiProvider 识别 custom provider

- activeProvider=custom 时从 customProvider 字段取配置
- custom 无 env fallback，apiKey 缺失时抛 IllegalStateException

scope: ai/client/RoutingAiProvider*"
```

---

## Task 6: OpenAiBidAgentConfigurationResolver 识别 custom

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolver.java`
- Modify: `backend/src/test/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolverTest.java`

- [ ] **Step 1: 写失败测试**

在 `backend/src/test/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolverTest.java` 末尾追加：

```java
    @Test
    void resolveReturnsCustomProviderConfigWhenActiveIsCustom() {
        SettingsResponse.AiProviderSetting custom = SettingsResponse.AiProviderSetting.builder()
                .providerCode("custom")
                .providerName("OpenRouter")
                .enabled(true)
                .baseUrl("https://openrouter.ai/api/v1/chat/completions")
                .model("anthropic/claude-3.5-sonnet")
                .encryptedApiKey(passwordEncryptionUtil.encrypt("sk-or-v1-test"))
                .build();
        SettingsResponse.AiModelConfig aiModelConfig = SettingsResponse.AiModelConfig.builder()
                .activeProvider("custom")
                .providers(List.of())
                .customProvider(custom)
                .build();
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig);
        when(aiConfigService.resolveAiApiKey("custom")).thenReturn("sk-or-v1-test");

        OpenAiBidAgentRequestConfig config = resolver.resolve("test use case");

        assertThat(config.apiKey()).isEqualTo("sk-or-v1-test");
        assertThat(config.baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(config.model()).isEqualTo("anthropic/claude-3.5-sonnet");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
    }

    @Test
    void resolveReturnsEmptyWhenCustomActiveButCustomProviderIsNull() {
        SettingsResponse.AiModelConfig aiModelConfig = SettingsResponse.AiModelConfig.builder()
                .activeProvider("custom")
                .providers(List.of())
                .customProvider(null)
                .build();
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig);

        assertThatThrownBy(() -> resolver.resolve("test use case"))
                .isInstanceOf(IllegalStateException.class);
    }
```

注意：参考现有测试文件顶部的字段声明补全 `passwordEncryptionUtil` 和 `resolver` 字段。

- [ ] **Step 2: 跑测试验证失败**

Run: `cd backend && mvn test -Dtest=OpenAiBidAgentConfigurationResolverTest`
Expected: 测试失败

- [ ] **Step 3: 修改 OpenAiBidAgentConfigurationResolver**

修改 `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolver.java` 第 58-92 行 `activeProviderRequest` 方法：

```java
    private Optional<OpenAiBidAgentRequestConfig> activeProviderRequest(Duration requestTimeout) {
        SettingsResponse.AiModelConfig aiModelConfig = aiConfigService.getInternalAiModelConfig();
        if (aiModelConfig == null) {
            return fallbackToDefaultProvider(requestTimeout);
        }

        String activeProviderCode = aiModelConfig.getActiveProvider();
        if (activeProviderCode == null || activeProviderCode.isBlank()) {
            activeProviderCode = aiProviderCatalog.defaultActiveProvider();
        }

        SettingsResponse.AiProviderSetting provider;
        String resolvedProviderCode;

        if (AiProviderCatalog.isCustomProvider(activeProviderCode)) {
            provider = aiModelConfig.getCustomProvider();
            if (provider == null || Boolean.FALSE.equals(provider.getEnabled())) {
                return Optional.empty();
            }
            resolvedProviderCode = activeProviderCode;
        } else {
            SettingsResponse.AiProviderSetting found = findProvider(aiModelConfig, activeProviderCode).orElse(null);
            if (found == null) {
                provider = firstEnabledProvider(aiModelConfig);
                if (provider == null) {
                    return fallbackToDefaultProvider(requestTimeout);
                }
                resolvedProviderCode = provider.getProviderCode();
            } else {
                provider = found;
                resolvedProviderCode = activeProviderCode;
            }
        }

        SettingsResponse.AiProviderSetting defaultSetting = aiProviderCatalog.defaultProviderSetting(resolvedProviderCode);
        SettingsResponse.AiProviderSetting finalProvider = provider;

        return resolveApiKey(resolvedProviderCode)
                .map(apiKey -> buildRequestConfig(
                        apiKey,
                        firstNonBlank(finalProvider.getBaseUrl(), defaultSetting.getBaseUrl()),
                        firstNonBlank(finalProvider.getModel(), defaultSetting.getModel()),
                        requestTimeout
                ));
    }
```

注意：在文件顶部加 import `import com.xiyu.bid.settings.service.AiProviderCatalog;`（如未导入）。`buildRequestConfig` 已强制 `CHAT_COMPLETIONS`，无需改动。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend && mvn test -Dtest=OpenAiBidAgentConfigurationResolverTest`
Expected: 所有测试 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolver.java \
        backend/src/test/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolverTest.java
git commit -m "feat(biddraftagent): ConfigurationResolver 识别 custom provider

- activeProvider=custom 时从 customProvider 字段取配置
- custom 强制走 CHAT_COMPLETIONS apiStyle（第三方平台通常不支持 RESPONSES）

scope: biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolver*"
```

---

## Task 7: 修复 OpenAiTenderDocumentAnalyzer URL 嗅探

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiTenderDocumentAnalyzer.java`

- [ ] **Step 1: 读取当前实现**

Run: `Read backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiTenderDocumentAnalyzer.java` 第 70-80 行

确认 URL 嗅探代码：
```java
String providerLabel = config.baseUrl().contains("deepseek") ? "DeepSeek"
        : config.baseUrl().contains("ark.cn-beijing") ? "豆包"
        : config.baseUrl().contains("dashscope") ? "通义千问"
        : config.baseUrl().contains("api.openai") ? "OpenAI" : "AI";
```

- [ ] **Step 2: 修改为查表**

把第 74-78 行替换为：

```java
String providerLabel = AiProviderCatalog.providerDisplayName(config.providerCode());
```

注意：`config` 这里是 `OpenAiBidAgentRequestConfig`，但该 record 没有 `providerCode` 字段（只有 apiKey/baseUrl/model/timeout/apiStyle）。需要先看 `OpenAiBidAgentRequestConfig` 是否携带 providerCode。

如未携带，最简单的修复是保留 URL 嗅探作为 fallback，但优先用 `AiProviderCatalog.providerDisplayName()` 反查——这要求 resolver 把 providerCode 传进去。

**推荐做法**：在 `OpenAiBidAgentRequestConfig` record 加 `providerCode` 字段。这需要小范围改动：

(a) `OpenAiBidAgentRequestConfig.java`：加 providerCode 字段
(b) `OpenAiBidAgentConfigurationResolver.buildRequestConfig`：传入 providerCode
(c) `OpenAiTenderDocumentAnalyzer`：用 `AiProviderCatalog.providerDisplayName(config.providerCode())`

具体实现：

修改 `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentRequestConfig.java`，加 `providerCode` 字段：

```java
public record OpenAiBidAgentRequestConfig(
        String providerCode,
        String apiKey,
        String baseUrl,
        String model,
        Duration timeout,
        OpenAiBidAgentApiStyle apiStyle
) {
}
```

修改 `OpenAiBidAgentConfigurationResolver.buildRequestConfig`：

```java
    private OpenAiBidAgentRequestConfig buildRequestConfig(
            String providerCode,
            String apiKey,
            String rawBaseUrl,
            String rawModel,
            Duration requestTimeout
    ) {
        return new OpenAiBidAgentRequestConfig(
                providerCode,
                apiKey,
                normalizedBaseUrl(rawBaseUrl),
                rawModel,
                requestTimeout,
                OpenAiBidAgentApiStyle.CHAT_COMPLETIONS
        );
    }
```

并更新所有 `buildRequestConfig(...)` 调用点（`activeProviderRequest` 和 `fallbackToDefaultProvider`）传入 `resolvedProviderCode` / `defaultProvider`。

修改 `OpenAiTenderDocumentAnalyzer` 第 74-78 行：

```java
String providerLabel = AiProviderCatalog.providerDisplayName(config.providerCode());
```

更新所有引用 `OpenAiBidAgentRequestConfig` 构造器或访问器的地方（如 `OpenAiStructuredOutputService` / `OpenAiSdkStructuredOutputTransport`）。

- [ ] **Step 3: 跑相关测试**

Run: `cd backend && mvn test -Dtest=OpenAiBidAgentConfigurationResolverTest,OpenAiTenderDocumentAnalyzerTest,OpenAiStructuredOutputServiceTest`
Expected: 部分测试可能因为 record 字段顺序变化而失败，按错误信息更新测试

- [ ] **Step 4: 跑全量 biddraftagent 测试**

Run: `cd backend && mvn test -Dtest='com.xiyu.bid.biddraftagent.**'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/
git commit -m "refactor(biddraftagent): 修复 URL 嗅探反模式，改用 AiProviderCatalog.providerDisplayName

- OpenAiBidAgentRequestConfig 加 providerCode 字段
- OpenAiTenderDocumentAnalyzer 不再通过 baseUrl.contains 判别厂商

scope: biddraftagent/infrastructure/openai/*"
```

---

## Task 8: AiProviderHealthIndicator 支持 custom

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/ai/config/AiProviderHealthIndicator.java`

- [ ] **Step 1: 读取当前实现**

Run: `Read backend/src/main/java/com/xiyu/bid/ai/config/AiProviderHealthIndicator.java`

- [ ] **Step 2: 修改 health 方法**

在解析 activeProvider 配置时，加 custom 分支。具体代码根据现有结构调整，核心逻辑：

```java
    private Health health() {
        // ... existing isAiEnabled check ...
        SettingsResponse.AiModelConfig aiModelConfig = aiConfigService.getInternalAiModelConfig();
        String activeProvider = aiModelConfig.getActiveProvider();

        SettingsResponse.AiProviderSetting provider;
        if (AiProviderCatalog.isCustomProvider(activeProvider)) {
            provider = aiModelConfig.getCustomProvider();
            if (provider == null) {
                return Health.down().withDetail("status", "misconfigured")
                        .withDetail("provider", "custom")
                        .withDetail("error", "自定义 Provider 未配置")
                        .build();
            }
        } else {
            provider = aiModelConfig.getProviders().stream()
                    .filter(p -> activeProvider.equals(p.getProviderCode()))
                    .findFirst()
                    .orElse(null);
            // ... existing logic ...
        }
        // ... rest of existing logic ...
    }
```

注意：保持现有 4 家的处理逻辑不变，只加 custom 分支。

- [ ] **Step 3: 跑测试**

Run: `cd backend && mvn test -Dtest=AiProviderHealthIndicatorTest`
Expected: PASS（如有该测试）；如无测试则跑 `mvn compile -q` 确认编译通过

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/ai/config/AiProviderHealthIndicator.java
git commit -m "feat(ai): HealthIndicator 支持 custom provider 状态返回

scope: ai/config/AiProviderHealthIndicator"
```

---

## Task 9: AiConfigurationStartupChecker 支持 custom

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/bootstrap/AiConfigurationStartupChecker.java`

- [ ] **Step 1: 读取当前实现**

Run: `Read backend/src/main/java/com/xiyu/bid/bootstrap/AiConfigurationStartupChecker.java`

- [ ] **Step 2: 修改检查逻辑**

在检查 activeProvider 配置时，加 custom 分支：

```java
    // 在检查 activeProvider 配置完整性的地方
    if (AiProviderCatalog.isCustomProvider(activeProvider)) {
        SettingsResponse.AiProviderSetting custom = aiModelConfig.getCustomProvider();
        if (custom == null || isBlank(custom.getBaseUrl()) || isBlank(custom.getEncryptedApiKey())) {
            log.error("AI 配置不完整：activeProvider=custom 但 customProvider 未配置 baseUrl/apiKey");
        }
    } else {
        // 现有 4 家检查逻辑
    }
```

具体代码根据现有结构调整。

- [ ] **Step 3: 跑测试**

Run: `cd backend && mvn test -Dtest=AiConfigurationStartupCheckerTest`
Expected: PASS；如无测试则跑 `mvn compile -q`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/bootstrap/AiConfigurationStartupChecker.java
git commit -m "feat(bootstrap): StartupChecker 支持 custom provider 启动检查

scope: bootstrap/AiConfigurationStartupChecker"
```

---

## Task 10: 前端 useAiModelSettings.js 支持 customProvider

**Files:**
- Modify: `src/views/System/settings/useAiModelSettings.js`

- [ ] **Step 1: 修改 normalizeProvider 支持 custom**

在 `DEFAULT_PROVIDER_CONFIG` 后追加 custom 默认配置：

```javascript
const DEFAULT_CUSTOM_PROVIDER_CONFIG = {
  providerCode: 'custom',
  providerName: '自定义',
  enabled: false,
  baseUrl: '',
  model: '',
}
```

- [ ] **Step 2: 修改 normalizeAiModelConfig 处理 customProvider**

```javascript
const normalizeCustomProvider = (provider = {}) => ({
  ...DEFAULT_CUSTOM_PROVIDER_CONFIG,
  ...provider,
  providerCode: 'custom',
  providerName: provider.providerName || '自定义',
  enabled: Boolean(provider.enabled ?? false),
  baseUrl: provider.baseUrl || '',
  model: provider.model || '',
  apiKeyPlaintext: '',
  apiKeyMasked: provider.apiKeyMasked || '',
  apiKeyConfigured: Boolean(provider.apiKeyConfigured),
  lastTestStatus: provider.lastTestStatus || '',
  lastTestMessage: provider.lastTestMessage || '',
  lastTestAt: provider.lastTestAt || '',
})

const normalizeAiModelConfig = (config = {}) => {
  const providerMap = new Map(
    (Array.isArray(config.providers) ? config.providers : [])
      .map(normalizeProvider)
      .map((provider) => [provider.providerCode, provider]),
  )

  return {
    activeProvider: config.activeProvider || 'deepseek',
    providers: AI_PROVIDER_OPTIONS.map((option) => normalizeProvider({
      ...DEFAULT_PROVIDER_CONFIG[option.code],
      ...providerMap.get(option.code),
    })),
    customProvider: normalizeCustomProvider(config.customProvider),
  }
}
```

- [ ] **Step 3: 修改 validateProvider 支持 custom**

```javascript
const validateProvider = (provider) => {
  if (!provider?.baseUrl?.trim()) throw new Error('请填写 API 地址')
  if (!provider?.model?.trim()) throw new Error('请填写模型名称')
}
```

（无需改动，custom provider 也走同一个 validateProvider）

- [ ] **Step 4: 修改 buildPayload 包含 customProvider**

```javascript
const buildPayload = () => {
  const custom = aiModelConfig.value.customProvider
  return {
    systemConfig: {
      ...systemConfig.value,
      enableAI: Boolean(systemConfig.value.enableAI),
    },
    aiModelConfig: {
      activeProvider: aiModelConfig.value.activeProvider,
      providers: aiModelConfig.value.providers.map((provider) => ({
        providerCode: provider.providerCode,
        enabled: provider.enabled,
        baseUrl: provider.baseUrl,
        model: provider.model,
        apiKeyPlaintext: provider.apiKeyPlaintext,
      })),
      customProvider: {
        providerCode: 'custom',
        enabled: custom.enabled,
        baseUrl: custom.baseUrl,
        model: custom.model,
        apiKeyPlaintext: custom.apiKeyPlaintext,
      },
    },
  }
}
```

- [ ] **Step 5: 修改 save 函数校验 custom（如 active=custom）**

```javascript
const save = async () => {
  saving.value = true
  try {
    const provider = activeProvider.value
    validateProvider(provider)
    // ... 现有逻辑 ...
  }
}
```

修改 `activeProvider` computed：

```javascript
const activeProvider = computed(() => {
  if (aiModelConfig.value.activeProvider === 'custom') {
    return aiModelConfig.value.customProvider
  }
  return aiModelConfig.value.providers.find(
    (provider) => provider.providerCode === aiModelConfig.value.activeProvider,
  )
})
```

修改 `testProvider` 支持 custom：

```javascript
const testProvider = async (provider) => {
  testingProvider.value = provider.providerCode
  try {
    validateProvider(provider)
    const result = await settingsApi.testAiModelConnection({
      providerCode: provider.providerCode,
      baseUrl: provider.baseUrl,
      model: provider.model,
      apiKeyPlaintext: provider.apiKeyPlaintext,
    })
    // ... 现有逻辑 ...
    // 更新 provider 的 lastTestStatus 等字段（provider 是 customProvider 引用，直接 mutate 即可）
  }
}
```

- [ ] **Step 6: 跑前端测试**

Run: `npm run test:unit -- --run Tests.spec.js`（如有 Settings 相关单元测试）
Expected: PASS

Run: `npm run build`
Expected: 编译通过

- [ ] **Step 7: Commit**

```bash
git add src/views/System/settings/useAiModelSettings.js
git commit -m "feat(frontend): useAiModelSettings 支持 customProvider 字段

- normalizeAiModelConfig 初始化 customProvider
- buildPayload 提交 customProvider
- activeProvider computed 在 active=custom 时返回 customProvider

scope: views/System/settings/useAiModelSettings.js"
```

---

## Task 11: 前端 AiModelSettingsPanel.vue 加自定义 Provider 区块

**Files:**
- Modify: `src/views/System/settings/AiModelSettingsPanel.vue`

- [ ] **Step 1: 修改 template，在 provider-selector 中加"自定义"radio button**

修改 `backend/src/views/System/settings/AiModelSettingsPanel.vue`（实际上是前端 `src/`）的 `<div class="provider-selector">` 区块：

```vue
    <div class="provider-selector">
      <span class="selector-label">当前激活厂商</span>
      <el-radio-group v-model="aiModelConfig.activeProvider">
        <el-radio-button
          v-for="provider in aiModelConfig.providers"
          :key="provider.providerCode"
          :value="provider.providerCode"
        >
          {{ provider.providerName }}
        </el-radio-button>
        <el-radio-button
          :value="'custom'"
          :disabled="!aiModelConfig.customProvider?.enabled"
        >
          {{ aiModelConfig.customProvider?.providerName || '自定义' }}
        </el-radio-button>
      </el-radio-group>
    </div>
```

- [ ] **Step 2: 在 provider-grid 后追加 custom provider 区块**

在 `</div>` （provider-grid 闭合）前或后追加：

```vue
    <section
      v-if="aiModelConfig.customProvider"
      class="provider-card custom-provider-card"
      :class="{ active: 'custom' === aiModelConfig.activeProvider }"
    >
      <div class="provider-head">
        <div>
          <p class="provider-code">custom</p>
          <h3>{{ aiModelConfig.customProvider.providerName || '自定义' }}</h3>
        </div>
        <el-switch v-model="aiModelConfig.customProvider.enabled" />
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="custom-hint"
        title="自定义 Provider 用于接入任意 OpenAI-compatible 平台（OpenRouter / 硅基流动 / 本地 Ollama 等）"
      />

      <el-form label-position="top" class="provider-form">
        <el-form-item label="名称（可选）">
          <el-input
            v-model="aiModelConfig.customProvider.providerName"
            placeholder="如 OpenRouter / 本地 Ollama"
          />
        </el-form-item>
        <el-form-item label="API 地址">
          <el-input
            v-model="aiModelConfig.customProvider.baseUrl"
            placeholder="https://openrouter.ai/api/v1/chat/completions"
          />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input
            v-model="aiModelConfig.customProvider.model"
            placeholder="如 anthropic/claude-3.5-sonnet"
          />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="aiModelConfig.customProvider.apiKeyPlaintext"
            type="password"
            show-password
            :placeholder="aiModelConfig.customProvider.apiKeyConfigured ? `已配置：${aiModelConfig.customProvider.apiKeyMasked}` : '请输入 API Key'"
          />
        </el-form-item>
      </el-form>

      <div class="provider-status">
        <el-tag :type="aiModelConfig.customProvider.apiKeyConfigured ? 'success' : 'info'" effect="plain">
          {{ aiModelConfig.customProvider.apiKeyConfigured ? 'Key 已配置' : '未配置 Key' }}
        </el-tag>
        <el-tag
          v-if="aiModelConfig.customProvider.lastTestStatus"
          :type="aiModelConfig.customProvider.lastTestStatus === 'success' ? 'success' : 'danger'"
          effect="plain"
        >
          {{ aiModelConfig.customProvider.lastTestStatus === 'success' ? '连接正常' : '连接失败' }}
        </el-tag>
      </div>

      <p v-if="aiModelConfig.customProvider.lastTestMessage" class="test-message">
        {{ aiModelConfig.customProvider.lastTestMessage }}
      </p>

      <div class="provider-actions">
        <el-button
          :loading="testingProvider === 'custom'"
          @click="testProvider(aiModelConfig.customProvider)"
        >
          <el-icon><Connection /></el-icon>
          测试连接
        </el-button>
        <el-button
          v-if="'custom' !== aiModelConfig.activeProvider && aiModelConfig.customProvider.enabled"
          text
          type="primary"
          @click="aiModelConfig.activeProvider = 'custom'"
        >
          设为当前
        </el-button>
      </div>
    </section>
```

- [ ] **Step 3: 加样式（可选）**

在 `<style scoped>` 末尾追加：

```css
.custom-provider-card {
  grid-column: 1 / -1;
}

.custom-hint {
  margin-bottom: 12px;
}
```

- [ ] **Step 4: 跑前端构建**

Run: `npm run build`
Expected: 编译通过

- [ ] **Step 5: 跑前端单元测试**

Run: `npm run test:unit`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/views/System/settings/AiModelSettingsPanel.vue
git commit -m "feat(frontend): AiModelSettingsPanel 加自定义 Provider 表单区块

- activeProvider radio group 追加'自定义'选项
- 单独区块展示 customProvider 的 name/baseUrl/apiKey/model/启用开关
- 测试连接 / 设为当前 按钮

scope: views/System/settings/AiModelSettingsPanel.vue"
```

---

## Task 12: 端到端验证 + 回归测试

**Files:**
- 无新增，仅运行验证

- [ ] **Step 1: 后端全量测试**

Run: `cd backend && mvn test`
Expected: 全部 PASS

- [ ] **Step 2: 架构测试**

Run: `cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`
Expected: PASS

- [ ] **Step 3: 前端构建**

Run: `npm run build && npm run test:unit`
Expected: PASS

- [ ] **Step 4: 数据边界检查**

Run: `npm run check:front-data-boundaries`
Expected: PASS

- [ ] **Step 5: 推送 WIP 分支**

```bash
git push -u origin agent/cursor/custom-ai-provider
```

- [ ] **Step 6: 人工 E2E 验证（在主工作区 trae）**

切换到主工作区启动开发环境：

```bash
cd /Users/user/xiyu/worktrees/trae
export XIYU_DEV_CONFIRMED=1
npm run dev:all
```

在浏览器 `http://127.0.0.1:1323` 验证：

1. 登录 admin → 系统设置 → AI 模型配置
2. 看到"自定义 Provider"区块
3. 填写 name=OpenRouter / baseUrl=https://openrouter.ai/api/v1/chat/completions / apiKey=test / model=anthropic/claude-3.5-sonnet
4. 启用自定义 Provider
5. 点击"测试连接"（应该失败，因为 apiKey 是 test）
6. 把 activeProvider 切换到"自定义"
7. 触发一次标讯分析，验证后端日志使用 customProvider 配置发起请求
8. 触发一次招标文件抽取，验证后端日志使用 customProvider 配置
9. 验证 `/actuator/health/aiProvider` 返回 custom 配置状态
10. 把 activeProvider 切回 deepseek，验证 4 家配置不受影响

- [ ] **Step 7: 验证 SSRF 防护**

尝试在自定义 Provider baseUrl 填 `http://169.254.169.254/latest/meta-data/` 并保存，应该被拒绝。

- [ ] **Step 8: 提交 PR**

通过 Gitee MCP 创建 PR：

```
标题: feat: 通用 AI 大模型 API 聚合 - 自定义 Provider 接入
描述: 基于 base URL + API Key + Model 接入任意 OpenAI-compatible 平台。
设计文档: docs/architecture/custom-ai-provider-design.md
实现计划: docs/architecture/custom-ai-provider-implementation-plan.md
```

---

## Self-Review

### Spec coverage 检查

| 设计文档章节 | 实现任务 |
|---|---|
| §3 架构设计 | Task 2,3,4,5,6 |
| §4 数据模型变更 | Task 2,3 |
| §5 校验逻辑（SsrfValidator + validateBaseUrl 分流） | Task 1,2 |
| §6.1 路径 1 路由 | Task 5 |
| §6.2 路径 2 路由 | Task 6 |
| §6.3 URL 嗅探反模式修复 | Task 7 |
| §7 HTTP 客户端复用 | 无需改动（设计文档原提到的 OpenAiCompatibleClient.providerDisplayName 实际不存在，改为放到 AiProviderCatalog.providerDisplayName 静态方法，Task 2 实现） |
| §8 配置存储 | Task 3,4 |
| §9 健康检查 & 启动检查 | Task 8,9 |
| §10 前端 UI | Task 10,11 |
| §11 错误处理 | Task 1（SSRF 错误）+ 复用现有 ExternalServiceException |
| §12 测试策略 | 每个 task 内嵌测试 + Task 12 端到端 |
| §14 风险与回滚 | 无需代码改动，回滚靠 git revert |

**GAP**: §13 改动文件清单中提到的 `OpenAiCompatibleClient.java` 改动——经调研发现 `providerDisplayName` 方法不存在于该类。改放到 `AiProviderCatalog.providerDisplayName()` 静态方法（Task 2），更合理。设计文档需在后续同步修订，但不阻塞实现。

### Placeholder scan

无 TBD/TODO。所有代码块完整。

### Type consistency

- `customProvider` 字段名在 SettingsResponse / SettingsUpdateRequest / 前端 normalizeAiModelConfig / buildPayload 中一致
- `isCustomProvider(String)` / `providerDisplayName(String)` / `CUSTOM_PROVIDER_CODE` 在 AiProviderCatalog 全局一致
- `OpenAiBidAgentRequestConfig` 加 `providerCode` 字段后，所有调用点都更新

---

## Execution Handoff

Plan complete and saved to `docs/architecture/custom-ai-provider-implementation-plan.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
