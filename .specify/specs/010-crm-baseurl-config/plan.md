# Implementation Plan: CRM BaseUrl 配置重构

**Branch**: `010-crm-baseurl-config` | **Date**: 2026-05-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-crm-baseurl-config/spec.md`

## Summary

重构 CRM 客户端配置，支持多域名路由（鉴权/客户/消息）、对接 YAPI 真实路径、运行时参数纳入系统设置管理。

## Technical Context

**Language/Version**: Java 17 + Spring Boot 3.x

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Cache (Caffeine), Vue 3 + Element Plus

**Storage**: MySQL 8.0, Redis (可选，Token 缓存默认内存级)

**Testing**: JUnit 5, Mockito, ArchUnit, Playwright (E2E)

**Target Platform**: Linux server / macOS dev

**Project Type**: Web application (frontend + backend)

**Performance Goals**: CRM 接口调用 P95 < 3s（含重试）

**Constraints**: 
- 向后兼容：未升级配置的旧环境仍能运行
- 配置实时生效：修改 Settings 后 5 秒内生效
- 敏感数据脱敏：日志中 Token/密钥不可明文

**Scale/Scope**: 
- 7 个 CRM 接口
- 3 个 BaseUrl 域名
- 影响后端 `com.xiyu.bid.crm` 包 + 前端系统设置页面

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| FP-Java Architecture | ✅ Pass | 保持现有分层：domain/application/infrastructure |
| Real-API Only | ✅ Pass | CRM 对接真实 API，无 Mock |
| Test-Driven Development | ✅ Pass | 需补单元测试 + ArchUnit 验证 |
| Split-First & Simplicity | ⚠️ Watch | CrmProperties 字段增加，需控制行数 |
| Boring Proven Patterns | ✅ Pass | 使用 Spring @ConfigurationProperties + Settings 表 |

## Project Structure

### Documentation (this feature)

```text
specs/010-crm-baseurl-config/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/crm/
│   ├── config/
│   │   └── CrmProperties.java          # 拆分 BaseUrl + 运行时参数
│   ├── application/
│   │   ├── CrmAuthService.java         # 使用 authBaseUrl
│   │   ├── CrmCustomerService.java     # 使用 customerBaseUrl
│   │   ├── CrmMenuService.java         # 使用 authBaseUrl
│   │   ├── CrmEmployeeService.java     # 使用 authBaseUrl
│   │   └── CrmMessageService.java      # 使用 messageBaseUrl
│   ├── infrastructure/
│   │   ├── CrmHttpClient.java          # 支持多 BaseUrl
│   │   └── CrmController.java          # REST 入口
│   └── domain/
│       └── CrmTokenCache.java          # Token 缓存
├── src/main/java/com/xiyu/bid/settings/
│   └── [扩展 Settings 实体/服务以支持 CRM 配置]
└── src/test/java/com/xiyu/bid/crm/
    └── [单元测试]

frontend/
├── src/views/Settings/
│   └── [新增 CRM 配置卡片]
└── src/api/
    └── [新增 CRM 配置 API]
```

**Structure Decision**: Web application (frontend + backend)。后端按现有 `com.xiyu.bid.crm` 包扩展，前端在系统设置页面增加 CRM 配置卡片。

## Complexity Tracking

> 无 Constitution 违规需解释。

## Research Notes

### YAPI 路径格式
- 需联调时确认真实 HTTP Path
- 当前记录：project/406（鉴权/组织架构）、project/509（CRM）、project/557（消息）

### Settings 集成方案
- 复用现有 `Settings` 表（参考 WeCom 配置实现）
- 使用 `@RefreshScope` 或事件监听实现实时生效
- 配置项序列化为 JSON 存储，或扩展独立字段

### 向后兼容策略
- `CrmProperties` 保留旧 `baseUrl` 字段
- 新字段（authBaseUrl 等）为空时回退到 baseUrl
- 日志打印警告提示迁移
