---
title: CRM 对接规范
space: engineering
category: integration
tags: [integration, crm, api]
sources:
  - .wiki/sources/technical/泊冉投标系统与西域对接技术相关内容.md
  - .wiki/extracts/technical__泊冉投标系统与西域对接技术相关内容.md.md
backlinks:
  - _index
  - implementation/xiyu-pending-confirmations
  - integration-organization-event-sdk
  - integration-tender-api
created: 2026-05-07
updated: 2026-06-20
health_checked: 2026-06-21
---
# CRM 对接规范

> **OA 流程对接已取消**（2026-05-28 确认），不再纳入本系统范围。
> 本文档仅保留 CRM 对接部分。

## 1. CRM 接口对接 (Customer Relationship)

用于在投标系统内直接检索客户信息、客户经理及跟进状态。

### 1.1 接入要求
- **协议**: HTTPS
- **鉴权**: Token 鉴权 (Authorization Header)
- **YAPI 项目**: `https://yapi.ehsy.com/project/406`（组织架构）、`/project/509`（CRM）、`/project/557`（消息）

### 1.2 接口规范

#### 1.2.1 通讯协议
- **协议**: HTTPS
- **编码**: UTF-8
- **鉴权**: `Authorization: Bearer <token>`

#### 1.2.2 请求方式
| 方法 | 用途 | 限制 |
|---|---|---|
| GET | 查询数据 | URL 传参，**不支持特殊字符** |
| POST | 增删改 | `Content-Type: application/json`，**不支持文件传输** |

#### 1.2.3 URL 规范
```
https://域名/[服务名]/[客户端类型]/[版本号]/路径
```

**规则**:
- 字母驼峰命名
- 服务名、客户端类型、版本号可选
- 域名由运维定义（业务简称）

**示例**: `https://cac.ehsy.com/cac/api/getInfo`

#### 1.2.4 请求头
| 字段 | 说明 |
|---|---|
| Authorization | `Bearer <token>`（基于框架选择） |
| Content-Type | `application/json`（POST 必填） |

#### 1.2.5 请求参数规范
- **命名**: 驼峰（如 `userId`）
- **必填校验**: 必填参数接口必须校验并抛异常
- **需定义**: 类型、是否必填、默认值、取值范围、参数格式、示例值、备注

#### 1.2.6 响应参数规范
- **需定义**: 名称、类型、格式、说明、取值范围、是否必填、示例值
- **强制格式**:
```json
{
  "code": "",
  "msg": "",
  "data": {},
  "success": true
}
```
- **业务成功条件**: `code == 0 && success == true`

#### 1.2.7 运行时策略
| 项目 | 要求 |
|---|---|
| **超时** | 首次 3-5 秒 |
| **重试** | 5xx/网络异常：指数退避，最多 3 次；4xx/业务失败：不重试 |
| **Token 缓存** | 内存级缓存，TTL 略短于服务端有效期，过期前异步续约 |
| **并发申请** | 单飞机制（仅一次实际 applyToken 调用） |
| **401/403 处理** | 立即清理缓存，重新 applyToken，重试一次 |
| **敏感数据** | 日志脱敏（手机、邮箱、Token、证件号等） |
| **traceId** | 请求头补本地 traceId，便于对账 |

### 1.3 接口清单

| 接口名称 | BaseUrl | YAPI 地址 | 使用场景 |
|---|---|---|---|
| 登录鉴权接口 | `https://base-oss-test.ehsy.com` | [project/406/api/23352](https://yapi.ehsy.com/project/406/interface/api/23352) | 调用接口前获取 token，缓存到本地，设置默认缓存有效期 |
| 登出接口 | `https://base-oss-test.ehsy.com` | [project/406/api/23370](https://yapi.ehsy.com/project/406/interface/api/23370) | 用户登出时作废 token |
| 根据名称模糊查询存量有效客户列表 | `https://cac-test.ehsy.com` | [project/509/api/25338](https://yapi.ehsy.com/project/509/interface/api/25338) | 根据公司名称模糊查询前 20 条，按名称长度升序 |
| 根据公司 id(支持工号)列表查询客户负责人列表 | `https://cac-test.ehsy.com` | [project/509/api/25259](https://yapi.ehsy.com/project/509/interface/api/25259) | 根据公司 id 查询客户负责人列表（支持批量公司 id、批量工号） |
| 获取菜单树 | `https://base-oss-test.ehsy.com` | [project/406/api/35642](https://yapi.ehsy.com/project/406/interface/api/35642) | 根据系统类型获取对应系统的菜单树 |
| 根据 token 获取员工信息 | `https://base-oss-test.ehsy.com` | [project/406/api/23358](https://yapi.ehsy.com/project/406/interface/api/23358) | 根据 token 获取员工的用户信息 |
| 发送企微消息 | `https://crm-api-java-test6.ehsy.com` | [project/557/api/35649](https://yapi.ehsy.com/project/557/interface/api/35649) | 发送消息（企微 + 站内信） |

### 1.4 配置项范围（待客户确认）

| # | 问题 | 客户答复 | 状态 |
|---|------|---------|------|
| 1 | 7 个 CRM 接口是否全部做成系统配置项 | **不需要** | ✅ 已确认 |
| 2 | 配置项是否包括 Token 缓存 TTL、重试策略等运行时参数 | **包括** | ✅ 已确认 |
| 3 | 配置持久化方式（Settings 表 vs 环境变量） | **未确定** | ⏳ 待跟进 |

### 1.5 与组织架构事件同步的关系（关键区分）

**CRM 查询接口 ≠ 组织架构同步**。二者是独立的集成维度：

| 维度 | CRM 查询接口 (本文档) | 组织架构事件同步 ([[integration-organization-event-sdk]]) |
|------|----------------------|--------------------------------------------------------|
| **方向** | 我们主动调 CRM 查数据 | CRM 通过事件总线推给我们 |
| **触发方式** | 页面按钮实时查询 | 事件驱动（部门/员工变更时自动推送） |
| **数据落点** | 不落库，直接返回前端 | 必须 upsert 到本地 `User` / `Department` 表 |
| **数据用途** | 客户信息检索、消息发送 | 登录鉴权、数据范围权限、审批流人员选择 |
| **幂等要求** | 无（只读查询） | 必须具备（同一事件重复投递不造成脏数据） |
| **当前状态** | ✅ PR #450 已完成 BaseUrl 配置化 | ❌ 待实现（需引入 ClientSDK） |

**常见误解**："CRM 接口做好了，组织架构就打通了"——这是错误的。页面按钮调用 `/api/xiyu/crm/customers` 只能查客户信息，无法实现员工入职/离职/调部门的自动同步。组织架构同步必须走事件库 SDK 方案（见 [[integration-organization-event-sdk]]）。

---

## 2. 验收标准

| 阶段 | 关键项 | 成功标志 |
|---|---|---|
| CRM 检索 | 实时查询 | 输入客户名能快速联想出 CRM 存量数据 |

---

## 相关文档
- [[integration-tender-api]] 标讯集成接口（外部系统 → 投标系统推送标讯，与本文档的"投标系统 → CRM 查询"方向相反）
- [[integration-organization-event-sdk]] 组织架构对接方案
- [[architecture]] §5 API 集成层
- `.wiki/sources/technical/泊冉投标系统与西域对接技术相关内容.pdf` (原始文档)
