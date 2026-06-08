# 组织架构 YAPI 契约映射

本文冻结西域数智化投标管理平台与客户组织架构系统（EHSY）的对接口径。

**最后更新**: 2026-06-08（对齐 YAPI 实测结果：POST + form-urlencoded + EHSY 头，无需 Bearer token）

---

## 架构决策（已冻结）

| 决策 | 结论 | 状态 | 依据 |
|---|---|---|---|
| SDK 接入方式 | `@AcceptEvent` 直连，HTTP fallback 已删除（FR-012） | ✅ 冻结 | 客户 SDK 文档 |
| YAPI 鉴权方式 | **无需 Bearer token**，仅携带链路追踪 Header（`EHSY-TraceID`、`EHSY-SRCAPP`） | ✅ 冻结 | YAPI 项目 `api/23312` 实测确认 |
| Bearer token 换取 | **不需要** — YAPI 部署在 EHSY 内网，基于网络白名单安全 | ❌ 已废弃 | 旧假设 `POST /auth/applyToken` 不适用 |
| 额外 HTTP 认证 | 通过环境变量 `XIYU_ORG_DIRECTORY_AUTH_HEADER` / `XIYU_ORG_DIRECTORY_AUTH_TOKEN` 配置，默认不启用 | ✅ 冻结 | 外部环境备用路径 |
| SDK consumer group | 可配置默认 `bms` | ✅ 冻结 | `OrganizationIntegrationProperties` |
| Maven 坐标 | `com.ehsy.eventlibrary:ClientSDK:release_0.0.2` | ✅ 冻结 | 客户确认版本 |
| SDK 接入包 | `infrastructure/sdk/OrganizationEventSdkConsumerAdapter` | ✅ 冻结 | 代码已实现 |
| YAPI HTTP 方法 | **POST**（非 GET） | ✅ 已适配 | `restTemplate.postForEntity` |
| YAPI Content-Type | 详情：`form-urlencoded`；时间窗：`application/json` | ✅ 已适配 | 双模式 |
| YAPI 分页 | `data.index` 作为下一页索引 | ✅ 已适配 | 循环分页 |

## 事件订阅（已冻结）

| Event Topic | Identifier | 处理入口 |
|---|---|---|
| `BaseOssDept` | `data.deptId` | `OrganizationDirectorySyncAppService` |
| `BaseOssUser` | `data.userId` | `OrganizationDirectorySyncAppService` |

> 事件 `data` 只作为变更通知标识，不作为主数据 payload。主数据必须通过 YAPI 详情接口回查。

---

## YAPI 映射表（已确认）

> 字段基于 YAPI 项目 `api/23312`、`api/23300` 实测。

| 能力 | 方法 | 路径 | 鉴权 | 请求 | 响应字段 | 本地映射 |
|---|---|---|---|---|---|---|
| 部门详情 | **POST** | `/subscription/msg/dept` | EHSY-TraceID + EHSY-SRCAPP | form: `deptId` | `deptId, code, name, parentId, status, del` | `deptId`→`externalDeptId`, `name`→`departmentName` |
| 员工详情 | **POST** | `/subscription/msg/user` | EHSY-TraceID + EHSY-SRCAPP | form: `userId` | `userId, name, jobNumber, email, mobilePhone, del, activationState` | `userId`→`externalUserId`, `name`→`fullName` |
| 部门时间窗 | **POST** | `/subscription/msg/getDeptByTimeWindow` | 同上 | JSON: `{ startTime, endTime, index }` | `{ index, list: [...] }` | 循环分页 |
| 员工时间窗 | **POST** | `/subscription/msg/getUserByTimeWindow` | 同上 | JSON: `{ startTime, endTime, index }` | `{ index, list: [...] }` | 循环分页 |

> **BaseUrl**: `https://base-oss-test.ehsy.com`，由 `XIYU_ORG_DIRECTORY_BASE_URL` 覆盖

---

## 字段映射

### 部门

| 领域字段 | YAPI 字段 | 本地字段 | 状态 | 说明 |
|---|---|---|---|---|
| 幂等主键 | `data.deptId` | `externalDeptId` | ✅ 冻结 | 整型数字，按字符串处理 |
| 部门编码 | `data.code` | `departmentCode` | ✅ 冻结 | |
| 部门名称 | `data.name` | `departmentName` | ✅ 冻结 | |
| 父部门 ID | `data.parentId` / `administrativeSuperiors` | `parentExternalDeptId` | ⚠️ 待确认 | 哪个字段是父部门 ID？ |
| 启用状态 | `status`(1=正常) + `del`(0=未删) | `enabled` | ⚠️ 待确认 | 组合语义待确认 |
| 父部门编码 | — | `parentDepartmentCode` | ❌ 无来源 | YAPI 无此字段 |

### 员工

| 领域字段 | YAPI 字段 | 本地字段 | 状态 | 说明 |
|---|---|---|---|---|
| 幂等主键 | `data.userId` | `externalUserId` | ✅ 冻结 | |
| 姓名 | `data.name` | `fullName` | ✅ 冻结 | |
| 工号 | `data.jobNumber` | `jobNumber` | ✅ 冻结 | |
| 邮箱 | `data.email` | — | ✅ 冻结 | 日志禁止完整值 |
| 手机号 | `data.mobilePhone` | — | ✅ 冻结 | 日志禁止完整值 |
| 删除状态 | `data.del` | `disabled` | ✅ 冻结 | `del=1` 表示离职 |
| 激活状态 | `data.activationState` | — | ✅ 冻结 | 1=激活 |

### 响应公共结构

所有接口返回 `{ code, msg, data }`：
- `code=0/200` → 成功，读 `data`
- `code≠0` 且 HTTP 2xx → 按 `OrganizationDirectoryResponsePolicy` 分类
- HTTP 404 → 查无（禁用/已删除）
- HTTP 5xx → 可重试

---

## SDK 待验证项（jar 到货后）

| 项目 | 当前假设 | 待验证 |
|---|---|---|
| `@AcceptEvent` 包名 | `com.ehsy.eventlibrary.clientsdk.common.anno.AcceptEvent` | SDK jar |
| `EventResult` 类型 | `com.ehsy.eventlibrary.clientsdk.systeminteraction.result.EventResult` | SDK jar |
| 事件 JSON 格式与 `eventTrackInfo` 归一化 | 已实现 adapter 归一化逻辑 | SDK jar |
| 生产 baseURL / broker / consumerGroup | 待客户交付 | 生产环境 |
| `parentId` vs `administrativeSuperiors` | 待客户确认 | YAPI 冻结 |
| `status + del` 组合语义 | 待客户确认 | YAPI 冻结 |

## 对应代码位置

- `OrganizationDirectoryHttpGateway`: POST + form-urlencoded/JSON ✅ 已完成
- `OrganizationDirectoryAuthHeaders`: EHSY-TraceID / EHSY-SRCAPP ✅ 已完成
- `OrganizationDirectoryJsonMapper`: YAPI JSON → 领域 snapshot ✅ 已完成
- `OrganizationDirectoryResponsePolicy`: 响应 code 分类决策 ✅ 已完成
