---
title: OpenAPI/Swagger 接口规范
space: engineering
category: integration
tags: [api, openapi, swagger, integration, springdoc]
sources:
  - backend/src/main/java/com/xiyu/bid/config/OpenApiConfig.java
  - backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java
  - backend/pom.xml
backlinks:
  - _index
  - architecture
  - implementation/attachment4-gap-matrix
  - implementation/attachment6-function-list-trace
  - integration-wecom
  - workflow-form-center
created: 2026-04-25
updated: 2026-05-27
health_checked: 2026-06-05
---
# OpenAPI/Swagger 接口规范

> 用于满足客户「提供标准 API 接口」要求，输出机器可读的接口规范，供 CRM、企业微信等外部系统集成方使用（OA 集成已取消）。

## 引入背景

客户在合同与需求中明确："**集成性：提供标准 API 接口，具备与公司现有 OA 系统、CRM 及企业微信集成的能力。**"

为满足该条款，我们在 2026-04-25 引入 `springdoc-openapi`，自动从 Spring MVC 注解生成 OpenAPI 3.0 规范文档与 Swagger UI 调试门户。

## 技术栈

| 项 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 3.2.0 | 已有 |
| springdoc-openapi-starter-webmvc-ui | 2.3.0 | 新增依赖，自动扫描 `@RestController` 生成 OpenAPI 3.1 文档 |
| Java | 21 | 已有 |

## 访问入口

| 用途 | URL | 说明 |
|---|---|---|
| Swagger UI 可视化门户 | http://127.0.0.1:18080/swagger-ui.html | 给集成方在线浏览 + 调试 |
| OpenAPI JSON 规范 | http://127.0.0.1:18080/v3/api-docs | 给集成方代码生成或导入 Postman / Apifox |
| OpenAPI YAML 规范 | http://127.0.0.1:18080/v3/api-docs.yaml | 同上，YAML 格式 |

生产环境替换 host 为部署地址即可，路径完全相同。

## 配置实现

### 1. 依赖（`backend/pom.xml`）

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

### 2. 配置类（`backend/src/main/java/com/xiyu/bid/config/OpenApiConfig.java`）

提供以下元信息：

- **API 标题、版本、描述**：从 `app.version` 与 `spring.application.name` 注入
- **联系人与许可证**：研发组邮箱 + Proprietary - 西域集团
- **服务器列表**：本地开发 (`http://127.0.0.1:18080`) + 当前部署环境 (`/`)
- **JWT Bearer 安全方案**：在 Swagger UI Authorize 弹窗中输入登录 token 即可调试受保护接口

### 3. 安全白名单（`backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java`）

将以下路径加入 `WHITE_LIST_URL`，确保未登录也可访问文档：

```java
"/v3/api-docs",
"/v3/api-docs/**",
"/v3/api-docs.yaml",
"/swagger-ui.html",
"/swagger-ui/**"
```

## 当前接口覆盖范围

springdoc 会自动扫描 `@RestController + @RequestMapping("/api/...")` 注解，生成的接口涵盖（截至 2026-04-25）：

- `/api/auth/**` 认证、会话、密码
- `/api/admin/**` 用户、角色、项目组、平台账号设置
- `/api/tenders/**` 标讯、匹配评分（`match-score`）
- `/api/projects/**` 项目、文档、报价、质量检查、标书 Agent
- `/api/knowledge/**` 资质、案例、模板
- `/api/fees`、`/api/contract-borrows`、`/api/resources/**` 财务与资源
- `/api/ai/**` 竞品、ROI、评分分析
- `/api/bid-match/**` 投标匹配评分模型
- `/api/bid-results/**` 中标结果与竞品中标
- `/api/analytics`、`/api/workbench`、`/api/dashboard` 数据看板
- `/api/audit`、`/api/alerts/**` 审计与告警
- `/api/documents/**`、`/api/export` 文档与导出
- `/api/approvals`、`/api/tasks`、`/api/calendar`、`/api/collaboration`
- `/api/customer-opportunities`、`/api/market-insight`、`/api/competition`、`/api/compliance`

共 **116 个 REST 端点**（59 个 Controller），全部统一 `ApiResponse<T> { success, code, message, data }` 响应体。

## 集成方使用流程

1. 请求 `/v3/api-docs` 获取 JSON spec，导入 Postman / Apifox / 自动生成 SDK
2. 浏览器打开 `/swagger-ui.html`，点击右上角 **Authorize**
3. 输入 `Bearer <jwt-token>`（来自 `POST /api/auth/login` 返回的 token）
4. 在线调试任意端点，请求/响应自动渲染示例

## 已知限制与后续工作

| 项 | 状态 | 后续动作 |
|---|---|---|
| OpenAPI 自动生成 | ✅ 已落地 | 在 Controller 上加 `@Operation` / `@Tag` / `@Schema` 注解，把描述、示例补全（按业务域逐步推进） |
| API 版本路径 | ❌ 未实施 | 增加 `/api/v1/...` 前缀，避免后续不兼容升级破坏集成方 |
| 机器身份认证（API Key / Client Credentials） | ❌ 未实施 | 当前 JWT 是面向用户登录的，第三方系统需要服务账号机制；规划在集成对接阶段补齐 |
| Webhook 出站事件回调 | ❌ 未实施 | 用于事件驱动的 OA / 企微通知（标讯入库、审批结果等） |
| OA / CRM / 企业微信适配器 | ❌ 未实施 | 在签订集成对接立项后开发，预计 1~2 周/系统 |

## 相关文档

- [[architecture]] §5 API 集成层
- [[deployment]] 端口与部署
- `docs/research/API_INTEGRATION.md` 前后端集成与统一响应规范
- `docs/API_MATCH_ANALYSIS.md` 接口匹配度分析

## 变更历史

| 日期 | 提交 | 变更 |
|---|---|---|
| 2026-04-25 | `5b9ffe6b` | feat: 新增 OpenAPI/Swagger 接口规范文档支持（pom + OpenApiConfig + SecurityConfig 白名单） |
