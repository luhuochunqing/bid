# 自定义 AI Provider 实现计划（简化方案 v2）

> **创建日期**: 2026-07-05
> **修订日期**: 2026-07-05（v2：简化方案，从 12 task 降到 5 task）
> **状态**: Task 1 已完成，Task 2 待派发
> **关联设计文档**: [docs/architecture/custom-ai-provider-design.md](file:///Users/user/xiyu/worktrees/cursor/docs/architecture/custom-ai-provider-design.md)

---

## 简化要点

custom 作为第 5 个正常 Provider，放入 `AiProviderCatalog.supportedProviderCodes()` 列表。**不需要改 DTO、不需要改 AiConfigService、不需要改 RoutingAiProvider、不需要改 ConfigurationResolver**。唯一特殊处理：`validateBaseUrl` 对 custom 走 SSRF 校验而非域名白名单。

| 对比 | 方案 A（弯路） | 简化方案（直线） |
|---|---|---|
| 改动文件 | 11+ 后端 + 2 前端 | 2 后端（含 1 新增）+ 2 前端 |
| DTO 变更 | 两个 DTO 类加 customProvider 字段 | **不需要** |
| AiConfigService | 6 个方法加 custom 分支 | **不需要** |
| RoutingAiProvider | 加 isCustomProvider 分支 | **不需要**（custom 在 providers 列表中自然找到） |
| ConfigurationResolver | 加 isCustomProvider 分支 | **不需要** |
| HealthIndicator/StartupChecker | 加 custom 分支 | **不需要** |

---

## Task 1: 新增 SsrfValidator 工具类 ✅ 已完成

Commit: `5ed6a2172`

---

## Task 2: AiProviderCatalog 加 custom 条目 + validateBaseUrl 分流

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/settings/service/AiProviderCatalog.java`
- Modify: `backend/src/test/java/com/xiyu/bid/settings/service/AiProviderCatalogTest.java`

### Step 1: 读当前文件

读 `AiProviderCatalog.java` 和 `AiProviderCatalogTest.java`，确认现有结构。

### Step 2: 写失败测试

在 `AiProviderCatalogTest.java` 末尾追加：

```java
    @Test
    void isCustomProviderReturnsTrueForCustomCode() {
        assertThat(AiProviderCatalog.isCustomProvider("custom")).isTrue();
        assertThat(AiProviderCatalog.isCustomProvider("openai")).isFalse();
        assertThat(AiProviderCatalog.isCustomProvider(null)).isFalse();
        assertThat(AiProviderCatalog.isCustomProvider("")).isFalse();
    }

    @Test
    void customProviderIsSupported() {
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
```

### Step 3: 跑测试验证失败

```bash
cd /Users/user/xiyu/worktrees/cursor/backend
mvn test -Dtest=AiProviderCatalogTest
```

Expected: 编译失败，`isCustomProvider` 方法不存在

### Step 4: 实现

修改 `AiProviderCatalog.java`：

1. 在 providers Map 中追加 custom 条目
2. 在 providerOrder 列表中追加 "custom"
3. 加 `isCustomProvider` 静态方法
4. 修改 `validateBaseUrl` 分流

关键代码：

```java
public static final String CUSTOM_PROVIDER_CODE = "custom";

private static final Map<String, AiProviderDefinition> PROVIDERS = Map.of(
    // ... 4 家不变 ...
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
```

修改 `validateBaseUrl`：

```java
public void validateBaseUrl(String providerCode, String baseUrl) {
    if (isCustomProvider(providerCode)) {
        SsrfValidator.validate(baseUrl);
        return;
    }
    // 现有逻辑不变：HTTPS + 域名白名单
    // ...
}
```

### Step 5: 跑测试验证通过

```bash
cd backend && mvn test -Dtest=AiProviderCatalogTest
```

Expected: 所有测试 PASS

### Step 6: Commit

```bash
git add backend/src/main/java/com/xiyu/bid/settings/service/AiProviderCatalog.java \
        backend/src/test/java/com/xiyu/bid/settings/service/AiProviderCatalogTest.java
git commit -m "feat(settings): AiProviderCatalog 加 custom 条目并 validateBaseUrl 分流

- custom 作为第 5 个 Provider 加入 supportedProviderCodes
- validateBaseUrl 对 custom 走 SsrfValidator，4 家走原域名白名单
- isCustomProvider() 静态判别方法

scope: settings/service/AiProviderCatalog*"
```

---

## Task 3: 前端 useAiModelSettings.js 加 custom

**Files:**
- Modify: `src/views/System/settings/useAiModelSettings.js`

### Step 1: 读当前文件

读 useAiModelSettings.js，确认 `AI_PROVIDER_OPTIONS` 和 `DEFAULT_PROVIDER_CONFIG` 结构。

### Step 2: 修改 AI_PROVIDER_OPTIONS 加 custom

```javascript
const AI_PROVIDER_OPTIONS = [
  { code: 'openai', name: 'OpenAI' },
  { code: 'deepseek', name: 'DeepSeek' },
  { code: 'qwen', name: '通义千问' },
  { code: 'doubao', name: '豆包' },
  { code: 'custom', name: '自定义' },
]
```

### Step 3: 修改 DEFAULT_PROVIDER_CONFIG 加 custom

```javascript
const DEFAULT_PROVIDER_CONFIG = {
  openai: { ... },
  deepseek: { ... },
  qwen: { ... },
  doubao: { ... },
  custom: {
    providerCode: 'custom',
    providerName: '自定义',
    enabled: false,
    baseUrl: '',
    model: '',
  },
}
```

### Step 4: 修改 validateProvider 对 custom 放宽 baseUrl 校验

如果 custom 的 enabled 为 false，跳过 baseUrl 必填校验；只有 enabled 为 true 时才校验：

```javascript
const validateProvider = (provider) => {
  if (provider.providerCode === 'custom' && !provider.enabled) return
  if (!provider?.baseUrl?.trim()) throw new Error('请填写 API 地址')
  if (!provider?.model?.trim()) throw new Error('请填写模型名称')
}
```

### Step 5: 构建并跑测试

```bash
npm run build
npm run test:unit
```

### Step 6: Commit

```bash
git add src/views/System/settings/useAiModelSettings.js
git commit -m "feat(frontend): useAiModelSettings 加 custom provider 配置

- AI_PROVIDER_OPTIONS 加 custom
- DEFAULT_PROVIDER_CONFIG 加 custom，enabled 默认 false
- validateProvider 对 custom 放宽校验（enabled=false 时不要求必填）

scope: views/System/settings/useAiModelSettings.js"
```

---

## Task 4: 前端 AiModelSettingsPanel.vue 适配 custom

**Files:**
- Modify: `src/views/System/settings/AiModelSettingsPanel.vue`

### Step 1: 读当前文件

读 AiModelSettingsPanel.vue，确认 provider 卡片渲染逻辑、activeProvider radio-group。

### Step 2: 修改 provider 卡片渲染

对于 `providerCode === 'custom'` 的卡片：
- `providerName` 改为可编辑的 `el-input`（而非只读文本）
- 添加提示文字："填写任意 OpenAI-compatible 平台的配置"
- `enabled` 开关默认 false

```vue
<!-- custom 卡片的 providerName 可编辑 -->
<el-input
  v-if="provider.providerCode === 'custom'"
  v-model="provider.providerName"
  placeholder="如 OpenRouter"
/>
<h3 v-else>{{ provider.providerName }}</h3>
```

### Step 3: 修改 activeProvider radio-group

在 radio-group 末尾追加 custom 按钮：

```vue
<el-radio-button
  value="custom"
  :disabled="!getCustomProviderEnabled()"
>
  自定义
</el-radio-button>
```

### Step 4: 构建

```bash
npm run build
```

### Step 5: Commit

```bash
git add src/views/System/settings/AiModelSettingsPanel.vue
git commit -m "feat(frontend): AiModelSettingsPanel 适配 custom provider

- custom 卡片 providerName 可编辑
- activeProvider radio-group 追加 custom 选项

scope: views/System/settings/AiModelSettingsPanel.vue"
```

---

## Task 5: 端到端验证 + 回归测试

### Step 1: 后端全量测试

```bash
cd backend && mvn test
```

### Step 2: 架构测试

```bash
cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

### Step 3: 前端构建

```bash
npm run build && npm run test:unit
```

### Step 4: 推送

```bash
git push -u origin agent/cursor/custom-ai-provider
```

### Step 5: 人工 E2E（在主工作区 trae）

```bash
cd /Users/user/xiyu/worktrees/trae
export XIYU_DEV_CONFIRMED=1
npm run dev:all
```

验证：
1. 系统设置 → AI 模型配置 → 看到第 5 个卡片"自定义"
2. 填写 name/baseUrl/apiKey/model → 启用 → 测试连接
3. 切换 activeProvider 为"自定义"
4. 触发 AI 调用验证生效
5. 切回 deepseek 验证 4 家不受影响
6. 填 `http://169.254.169.254/` → 保存被拒绝