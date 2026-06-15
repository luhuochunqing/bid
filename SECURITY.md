# SECURITY.md — 安全底线规范

Mock 政策、权限守卫、安全审计。

## Mock 政策（统一决策）

- **唯一支持路径**：前端、后端、E2E、演示环境均以真实后端 API 为唯一事实源。
- **前端 Mock**：已清空（`src/mock`、`src/api/mock-adapters/`、`.env.mock` 均已删除）；`src/api/config.js` 硬编码 `mode: 'api'`。
- **后端 `demo/` 包**：`backend/src/main/java/com/xiyu/bid/demo/` 是 E2E 测试的合法辅助包，用于提供 `E2eDemoDataInitializer` 等测试数据初始化，非 Mock 代码。
- **遗留代码现状**：仓库内仍可见 `frontendDemo` 适配层、`demoPersistence` 等历史遗留；这些内容当前只应被视为清理对象，不允许新增、不允许扩散。
- **执行要求**：任何新功能、Bug 修复、测试回归、截图演示都必须以真实后端联调为前提。

### Final Class Mock 策略

- **纯核心（domain / core / policy）的 `final class`**：禁止 Mockito mock。纯核心类应通过构造真实实例 + 控制输入数据来测试。
- **集成测试（IT）**：使用真实 Spring 上下文和真实 Bean 实例，不得 mock `final class`。
- **当前配置**：`mockito-extensions/org.mockito.plugins.MockMaker` 为 `mock-maker-subclass`（非 inline），不支持 `mockStatic`。如需 mock `final` / `static`，优先重构为可注入的协作对象，而非升级 mockito-inline。
- **例外**：框架适配类、配置类（如 `@ConfigurationProperties`）不受此限。

## 项目权限门禁口径

`ProjectAccessGuardCoverageTest` 扫描所有带 `projectId` 或引用项目关联 DTO/实体的 Controller/Service，必须命中 `ProjectAccessScopeService` 等统一守卫证据，或进入 `project-access-guard-baseline.txt` 显式基线并写明原因。

## 关键硬约束（一句话）

- 前端禁 Mock（真实 API 唯一源）；纯核心 `final class` 禁 Mockito mock。
- 所有带 `projectId` 的 Controller/Service 必须命中 `ProjectAccessScopeService`，否则进显式基线并写明原因。
- 富文本入库前必走 `dompurify.sanitize()`。

## 外部依赖安全注意

- `fastjson 1.2.83`：仅作 EHSY ClientSDK 传递依赖存在，本项目新代码禁止直接用（用 Jackson）。见 `docs/references/ehsy-client-sdk.md`。

## 参考文档索引

| 概念 | 位置 |
|---|---|
| API 安全审计 | `docs/security/api-security-audit-2026-06-13.md` |
| API 安全修复 | `docs/security/api-security-fix-2026-06-13.md` |
| 数据权限硬化 | `.wiki/pages/data-permission-hardening.md` |
| 权限矩阵 | `docs/permission-matrix/投标项目-权限矩阵.md` |
| 角色与权限（合成页） | `.wiki/pages/roles-and-permissions.md` |
| 数据权限覆盖审计 | `docs/reports/data-permission-coverage-audit.md` |
| EHSY ClientSDK（AI 盲区） | `docs/references/ehsy-client-sdk.md` |
