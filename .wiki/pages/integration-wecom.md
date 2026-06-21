---
title: 系统集成中心 - 企业微信
space: engineering
category: integration
tags: [integration, wecom, settings, sso, message-push]
sources:
  - backend/src/main/java/com/xiyu/bid/integration/
  - src/views/System/SystemIntegration.vue
  - src/views/System/settings/integration/
  - docs/research/WORKFLOW_FORM_CENTER.md
backlinks:
  - _index
  - implementation/attachment4-gap-matrix
  - implementation/attachment6-function-list-trace
  - workflow-form-center
created: 2026-04-25
updated: 2026-06-21
health_checked: 2026-06-21
---
# 系统集成中心 - 企业微信

> 满足客户「集成性」需求条款的统一配置入口。企业微信配置已落地；泛微 OA 第一版由 [[workflow-form-center]] 承载“表单触发 OA + 回调结果”链路。

## 客户需求溯源

合同条款「集成性」要求：

- ✅ **与组织架构系统集成** — 占位（计划）
- ✅ **与 OA / 审批流集成** — 占位（计划）
- ✅ **与 CRM 系统集成** — 占位（计划）
- ✅ **开放 API 接口** — 已通过 [[api-openapi]] 落地
- ✅ **与企业微信集成** — **本次落地**：配置入口 + SSO/消息推送启用开关 + 连接测试

## 功能落地状态

| 集成对象 | 状态 | 说明 |
|---|---|---|
| **企业微信** | ✅ 配置入口 + SSO 登录 + 真实 API 连通性 | SSO 已全链路落地，支持扫码/静默登录；消息推送下一期 |
| **CRM 系统** | ⚪ 占位「即将支持」 | 待客户提供 CRM 接口规范 |
| **OA / 审批流** | ✅ 第一版流程表单触发 OA | 表单模板绑定泛微流程 ID，提交后发起 OA，回调后应用业务；详见 [[workflow-form-center]] |
| **组织架构系统** | ⚪ 占位「即将支持」 | 待客户内部架构系统接口 |

## 入口路径

`系统设置 → 系统集成` Tab（`/settings`，仅 ADMIN 角色可见，容器组件为 `src/views/System/SystemIntegration.vue`）

## 数据模型

`wecom_integration` 表（V87 迁移）单行配置（`id=1`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `corp_id` | VARCHAR(64) | 企业 CorpID |
| `agent_id` | VARCHAR(32) | 应用 AgentID（数字） |
| `encrypted_secret` | TEXT | 应用 Secret（AES-256-GCM 加密） |
| `sso_enabled` | BOOLEAN | 是否启用单点登录 |
| `message_enabled` | BOOLEAN | 是否启用应用消息推送 |
| `updated_at` | TIMESTAMP | 自动更新 |
| `updated_by` | VARCHAR(64) | 操作人 |

加密委托给现有 `com.xiyu.bid.platform.util.PasswordEncryptionUtil`（AES-256-GCM）。

## REST API

| Method | Path | 行为 |
|---|---|---|
| `GET` | `/api/admin/integrations/wecom` | 读取配置；**Secret 永不回显**，仅返回 `secretConfigured:boolean` |
| `PUT` | `/api/admin/integrations/wecom` | 保存/更新；`corpSecret` 为空时保留原值 |
| `POST` | `/api/admin/integrations/wecom/test` | 用当前配置调连通性 Probe，返回 `{success, message, probedAt}` |

所有路径已受 `SecurityConfig` ADMIN 角色保护，自动出现在 [[api-openapi]] Swagger UI 里。

## 后端模块结构（FP-Java + Split-First）

```
backend/src/main/java/com/xiyu/bid/integration/
├── domain/                    # 纯核心，零 Spring 依赖
│   ├── WeComCredential.java          # 不可变值对象（toString 屏蔽 secret）
│   ├── WeComCredentialValidation.java # 纯函数验证（防御纵深）
│   ├── WeComConnectivityResult.java
│   └── ValidationResult.java
├── application/               # 编排层，无 DTO 转换
│   ├── WeComIntegrationAppService.java   # @Service @Transactional
│   ├── WeComConnectivityProbe.java       # 接口
│   ├── WeComMockConnectivityProbe.java   # @Component 默认 Mock
│   └── WeComCredentialCipher.java        # 加解密门面
├── controller/                # 仅 Request/Response 转换 + 异常处理
│   └── WeComIntegrationController.java
├── dto/                       # 输入输出契约
│   ├── WeComIntegrationRequest.java   # @NotBlank/@Pattern 字段校验
│   ├── WeComIntegrationResponse.java  # 不含 corpSecret 字段
│   └── WeComConnectivityResponse.java
└── infrastructure/persistence/
    ├── entity/WeComIntegrationEntity.java
    └── repository/WeComIntegrationJpaRepository.java
```

每个文件 ≤ 85 行；最大 `WeComIntegrationAppService` 85 行。所有类单一职责，无任何类同时承担「规则计算 + 数据访问 + DTO 转换 + 状态写入」3 类以上。

## 前端结构

```
src/api/modules/systemIntegration.js         # axios 封装
src/views/System/Settings.vue                 # 新增 Tab 接入
src/views/System/SystemIntegration.vue             # 容器（企微 + 占位卡）
src/views/System/settings/integration/
├── WeComIntegrationCard.vue                  # 配置表单 + 测试按钮
└── IntegrationComingSoonCard.vue             # 占位卡（CRM/OA/组织架构）
src/views/System/settings/useWeComSettings.js # composable（唯一调 API 模块）
```

数据边界：组件不直接调 axios；composable 是 API 唯一入口，符合项目 `check:front-data-boundaries` 治理规则。

## 安全设计

1. **Secret 永不回显**：`WeComIntegrationResponse` DTO 已移除 `corpSecret` 字段，`secretConfigured:boolean` 替代
2. **toString 屏蔽**：`WeComCredential.toString()` 重写，输出 `corpSecret=***`
3. **存储加密**：AES-256-GCM，依赖 `PLATFORM_ENCRYPTION_KEY` 环境变量（参考 `PasswordEncryptionUtil`）
4. **单元测试断言**：`WeComCredentialTest.toString_maskSecret()` 阻止 toString 泄露回归
5. **Bean Validation + Domain Validation 双层防御**：HTTP 边界拦字段格式，绕过 Controller 的调用方仍受 domain 校验保护

## 测试覆盖

| 模块 | 测试数 | 文件 |
|---|---|---|
| `WeComCredentialTest` | 6 | 不可变性 + toString 屏蔽 |
| `WeComCredentialValidationTest` | 7 | 各字段错误分支 |
| `WeComIntegrationAppServiceTest` | 9 | 编排（加密、Secret 不泄露、连通性） |
| `WeComMockConnectivityProbeTest` | 2 | Mock probe 行为 |
| `WeComIntegrationControllerTest` | 7 | REST 状态码 + 异常处理 |
| 前端 `systemIntegration.spec.js` | 6 | API 模块行为 |
| 前端 `useWeComSettings.spec.js` | 13 | composable load/save/test/error |

## 后续工作（已知缺口）

| 项 | 说明 |
|---|---|
| SSO OAuth2 登录 | ✅ 已落地，支持 `oauth2_state` CSRF 保护与手机号自动绑定 |
| 应用消息推送 | ⚪ 计划中，接 `cgi-bin/message/send` |
| 通讯录同步 | ⚪ 计划中，拉取部门/成员到 user 表 |
| 真实连通性 Probe | ✅ 已落地，使用真实 `access_token` 探测 |
| OA 真实 HTTP 适配器细化 | 当前泛微 Gateway 已有结构和测试模式，真实字段元数据、附件和错误码需等客户接口资料补齐 |
| CRM/组织架构对接 | 等客户提供接口规范后实施 |

## 变更历史

| 2026-04-25 | - | feat: 系统集成 Tab + 企业微信配置能力 |
| 2026-05-10 | `18e29789` | feat: 落地 WeCom OAuth2 SSO 登录与真实 API 对接 |

## 💡 开发经验总结 (Lessons Learned)

### 1. 严格质量门禁的挑战与应对
在本项目的高强度 CI 环境下，新增功能必须通过 SpotBugs、Checkstyle、PMD 和 ArchUnit 的多重封锁。
*   **SpotBugs Naming**: Java Record 自动生成的访问器方法会保留字段大小写。若 WeCom API 返回 `UserId`，Record 字段也应设为 `userId` 并配合手动解析或 `@JsonProperty`，否则会违反方法命名规范。
*   **ArchUnit Line Budget**: 超过 300 行的视图组件（如 `Login.vue`）必须强制拆分。通过将 `LoginForm`、`SocialLogin` 和 `LoginBrandSection` 抽离，不仅通过了门禁，还大幅提升了 UI 的可维护性。

### 2. 跨层状态校验
*   **CSRF 保护**: OAuth2 `state` 校验由专用的 `OAuthStateService` 负责，优先使用 Redis 存储，并提供内存 Map 作为无 Redis 环境的降级方案，确保了不同部署环境的稳定性。
*   **异常处理策略**: 针对“未绑定企微账号”等业务失败，不再抛出异常，而是通过 `ApiResponse` 返回特定状态码（如 `40101`），由前端拦截器统一处理跳转到绑定引导页。

### 3. E2E 验证的重要性
*   由于企业微信 SSO 涉及复杂的 OAuth2 重定向和 Code 交换，单靠单元测试难以覆盖全链路。
*   通过 Playwright 模拟 OAuth2 回调链路，确保了 `State` 过期、`Code` 失效和“首次登录自动绑定”等关键路径的逻辑闭环。
