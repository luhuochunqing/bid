# 西域 OSS 对接接口文档清单

> 对接方向：西域（EH）→ 泊冉投标系统
> Mock 基础地址：`https://yapi.ehsy.com/mock/406`
> 响应统一格式：`{ code, message, trace, data }`，`code=0` 表示成功

---

## 接口总览

| # | 接口名称 | 方法 | 路径 | 接口编号 | 认证方式 | 详细文档 |
|---|----------|------|------|----------|----------|----------|
| 1 | 登录接口 | POST | `/oauth/login` | 23353 | 无（登录获取 token） | [login](../references/xiyu-to-boran-permission-api-23353-login.md) |
| 2 | 用户 token 校验 | GET | `/oauth/getCheckToken` | — | Authorization + Query token | [getCheckToken](../references/xiyu-to-boran-permission-api-getCheckToken.md) |
| 3 | 根据 token 获取员工信息 | GET | `/oauth/getUserInfo` | 23358 | Authorization | [getUserInfo](../references/xiyu-to-boran-permission-api-23358-getUserInfo.md) |
| 4 | 根据 token 获取用户权限 | GET | `/oauth/getUserPermission` | 23484 | Authorization | [getUserPermission](../references/xiyu-to-boran-permission-api-23484-getUserPermission.md) |
| 5 | 登出接口 | POST | `/oauth/logout` | 23370 | Authorization | [logout](../references/xiyu-to-boran-permission-api-23370-logout.md) |
| 6 | 根据工号列表查询用户角色 | POST | `/oss/admin-web/v1/output/data/getUserJobListByJobNumberList` | 26325 | 无（管理端数据接口） | [getUserJobListByJobNumberList](../references/xiyu-to-boran-permission-api-26325-getUserJobListByJobNumberList.md) |

### 调用时序

```
┌──────────┐                    ┌──────────┐
│  泊冉系统  │                    │  西域 OSS  │
└────┬─────┘                    └────┬─────┘
     │                               │
     │  1. POST /oauth/login         │
     │  (username + password + system)│
     ├──────────────────────────────→│
     │←────── access_token ──────────┤
     │                               │
     │  2. GET /oauth/getCheckToken  │
     │  (token 校验有效性)             │
     ├──────────────────────────────→│
     │←──── active:true ─────────────┤
     │                               │
     │  3. GET /oauth/getUserInfo    │
     │  (获取员工信息)                 │
     ├──────────────────────────────→│
     │←──── 用户详情 ─────────────────┤
     │                               │
     │  4. GET /oauth/getUserPermission│
     │  (获取菜单权限)                 │
     ├──────────────────────────────→│
     │←──── 权限列表 ─────────────────┤
     │                               │
     │  6. POST /oss/.../getUserJobList│
     │  (批量查工号角色，独立于 token)   │
     ├──────────────────────────────→│
     │←──── 角色信息 ─────────────────┤
     │                               │
     │  5. POST /oauth/logout        │
     │  (登出)                        │
     ├──────────────────────────────→│
     │←──── 退出成功 ─────────────────┤
```

---

## 单点登录（SSO）实现方案

### 实现逻辑

引入的新系统在接收到跳转链接中的 token 后，调用 Home 的接口进行 token 校验或直接调用获取用户信息接口（**两者取其一即可**），通过返回信息判断 token 是否有效、是否登录成功。

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│  Home 平台   │         │  新接入系统   │         │  Home OSS   │
│  (跳转方)    │         │  (接收方)    │         │  (接口提供方)│
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                     │                       │
       │  1. 跳转链接携带 token │                       │
       ├────────────────────→│                       │
       │                     │                       │
       │                     │  2. 方式A：校验 token   │
       │                     │  GET /oauth/getCheckToken
       │                     ├──────────────────────→│
       │                     │←──── active:true ─────┤
       │                     │                       │
       │                     │  2. 方式B：获取用户信息 │
       │                     │  GET /oauth/getUserInfo
       │                     ├──────────────────────→│
       │                     │←──── 用户详情 ─────────┤
       │                     │                       │
       │                     │  3. 判断 token 有效性   │
       │                     │  登录成功 / 失败处理    │
       │                     │                       │
```

### 方式 A：Token 校验

通过 `GET /oauth/getCheckToken` 接口校验 token 有效性，返回 `data.active = true` 表示 token 有效。

- **接口路径**：`GET /oauth/getCheckToken`
- **接口文档地址**：[xiyu-to-boran-permission-api-getCheckToken.md](../references/xiyu-to-boran-permission-api-getCheckToken.md)

### 方式 B：直接获取用户信息

通过 `GET /oauth/getUserInfo` 接口直接获取员工信息，既能校验 token 有效性，又能拿到用户详情（工号、部门、岗位等），一步到位。

- **接口路径**：`GET /oauth/getUserInfo`
- **接口文档地址**：[xiyu-to-boran-permission-api-23358-getUserInfo.md](../references/xiyu-to-boran-permission-api-23358-getUserInfo.md)

### 主动登录 Home 平台

如果接入系统中需要通过自身系统登录 Home 平台，则通过用户密码和系统参数调用 Home 登录接口获取 token：

- **接口路径**：`POST /oauth/login`
- **接口文档地址**：[xiyu-to-boran-permission-api-23353-login.md](../references/xiyu-to-boran-permission-api-23353-login.md)

> **注意事项**：
> 1. 登录参数中的 `system` 字段通过字典项配置保持统一（如 `HOME`、`SMS` 等），各接入系统使用约定值。
> 2. 登录成功的前提是 **Home 系统中已配置该用户对应系统的权限**，否则即使账密正确也会因无权限而登录失败。
> 3. 登录成功后返回 `access_token`，后续调用其他接口时通过 `Authorization: Bearer <access_token>` 携带。

### 登出

登录态使用完毕后，调用登出接口释放 token：

- **接口路径**：`POST /oauth/logout`
- **接口文档地址**：[xiyu-to-boran-permission-api-23370-logout.md](../references/xiyu-to-boran-permission-api-23370-logout.md)

---

## 接口详情

## 1. 登录接口

> 接口编号：23353
> 接口路径：`POST /oauth/login`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/login`
> 备注：登录接口，在操作日志表中记录登录信息（`sys_login_info`）

### 请求参数

**Headers**

| 参数名称     | 参数值                            | 是否必须 | 备注 |
| ------------ | --------------------------------- | -------- | ---- |
| Content-Type | application/x-www-form-urlencoded | 是       |      |

**Body**

| 参数名称 | 参数类型 | 是否必须 | 示例   | 备注 |
| -------- | -------- | -------- | ------ | ---- |
| username | 文本     | 是       | 00481  | 工号 |
| password | 文本     | 是       | 123456 | 密码 |
| system   | 文本     | 是       | HOME   | 系统标识 |

### 返回数据

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "bearer",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expires_in": 5998,
    "scope": "all",
    "jti": "QjPjAJIJ_QHAdkWyOZZd4A5bv0k"
  }
}
```

**响应字段**

| 名称            | 类型     | 是否必须 | 备注                   |
| --------------- | -------- | -------- | ---------------------- |
| code            | number   | 非必须   | 状态码                 |
| message         | string   | 非必须   | 响应消息               |
| trace           | null     | 非必须   | 追踪信息               |
| data            | object   | 非必须   |                        |
| ├ access_token  | string   | 非必须   | 访问令牌               |
| ├ token_type    | string   | 非必须   | 令牌类型，示例：bearer |
| ├ refresh_token | string   | 非必须   | 刷新令牌               |
| ├ expires_in    | number   | 非必须   | 有效期（秒）           |
| ├ scope         | string   | 非必须   | 授权范围               |
| ├ permissions   | string[] | 非必须   | 权限列表               |
| ├ id            | string   | 非必须   |                        |
| ├ username      | string   | 非必须   |                        |
| ├ jti           | string   | 非必须   | JWT ID                 |

---

## 2. 用户 token 校验接口

> 接口路径：`GET /oauth/getCheckToken`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/getCheckToken`
> 备注：校验用户 token 是否有效，返回 token 中携带的用户信息及过期时间等元数据

### 请求参数

**Headers**

| 参数名称      | 参数值                            | 是否必须 | 备注                         |
| ------------- | --------------------------------- | -------- | ---------------------------- |
| Content-Type  | application/x-www-form-urlencoded | 是       |                              |
| Authorization |                                   | 是       | Bearer token，或直接传 token 值 |

**Query**

| 参数名称 | 是否必须 | 示例 | 备注                   |
| -------- | -------- | ---- | ---------------------- |
| token    | 是       |      | 待校验的 access_token |

### 返回数据

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": {
    "aud": ["api"],
    "user_name": "06669",
    "scope": ["all"],
    "active": true,
    "exp": 1718333834,
    "jti": "QjPjAJIJ_QHAdkWyOZZd4A5bv0k",
    "client_id": "web"
  }
}
```

**响应字段**

| 名称        | 类型     | 是否必须 | 备注               |
| ----------- | -------- | -------- | ------------------ |
| code        | number   | 非必须   | 状态码             |
| message     | string   | 非必须   | 响应消息           |
| trace       | null     | 非必须   | 追踪信息           |
| data        | object   | 非必须   | token 校验结果数据 |
| ├ aud       | string[] | 非必须   | 受众列表           |
| ├ user_name | string   | 非必须   | 用户名（工号）     |
| ├ scope     | string[] | 非必须   | 授权范围           |
| ├ active    | boolean  | 非必须   | token 是否有效     |
| ├ exp       | number   | 非必须   | 过期时间戳（秒）   |
| ├ jti       | string   | 非必须   | JWT ID             |
| ├ client_id | string   | 非必须   | 客户端 ID          |

---

## 3. 根据 token 获取员工信息接口

> 接口编号：23358
> 接口路径：`GET /oauth/getUserInfo`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/getUserInfo`
> 备注：根据 token 获取员工的用户信息

### 请求参数

**Headers**

| 参数名称      | 参数值                            | 是否必须 | 备注                           |
| ------------- | --------------------------------- | -------- | ------------------------------ |
| Content-Type  | application/x-www-form-urlencoded | 是       |                                |
| Authorization | Bearer `<access_token>`           | 是       | 登录接口返回的 access_token    |

**Body**

无。token 通过 `Authorization` Header 传递。

### 返回数据

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": {
    "id": 5549,
    "userId": 185793021,
    "username": "09118",
    "nickName": "覃超颖",
    "phone": "15000022861",
    "email": "eva_qin@ehsy.com",
    "deptId": 2279832,
    "deptName": "项目一组",
    "deptPath": "900109664/997091/997093/997267/3264067/2279832",
    "jobId": 4948,
    "jobName": "高级产品经理",
    "isManager": true,
    "createAt": "2024-07-31 13:43:36",
    "updateAt": "2024-08-01 14:27:17",
    "status": 1,
    "deptTree": null,
    "roleList": null
  }
}
```

**响应字段**

| 名称        | 类型    | 是否必须 | 备注         |
| ----------- | ------- | -------- | ------------ |
| code        | number  | 非必须   |              |
| message     | string  | 非必须   |              |
| trace       | null    | 非必须   |              |
| data        | object  | 非必须   |              |
| ├ id        | number  | 非必须   |              |
| ├ userId    | number  | 非必须   | 用户 id      |
| ├ username  | string  | 非必须   | 用户登录账号 |
| ├ nickName  | string  | 非必须   | 用户名       |
| ├ phone     | string  | 非必须   | 手机号       |
| ├ email     | string  | 非必须   | 邮箱         |
| ├ deptId    | number  | 非必须   | 部门 id      |
| ├ deptName  | string  | 非必须   | 部门名称     |
| ├ deptPath  | string  | 非必须   | 部门路径     |
| ├ jobId     | number  | 非必须   | 岗位 id      |
| ├ jobName   | string  | 非必须   | 岗位名       |
| ├ isManager | boolean | 非必须   | 是否管理员   |
| ├ createAt  | string  | 非必须   | 创建时间     |
| ├ updateAt  | string  | 非必须   | 更新时间     |
| ├ status    | number  | 非必须   | 账号状态     |
| ├ deptTree  | null    | 非必须   |              |
| ├ roleList  | null    | 非必须   |              |

---

## 4. 根据 token 获取用户系统权限接口

> 接口编号：23484
> 接口路径：`GET /oauth/getUserPermission`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/getUserPermission`
> 备注：根据 token 获取用户在各系统中的菜单权限

### 请求参数

**Headers**

| 参数名称      | 参数值                  | 是否必须 | 备注                        |
| ------------- | ----------------------- | -------- | --------------------------- |
| Authorization | Bearer `<access_token>` | 是       | 登录接口返回的 access_token |

**Query**

| 参数名称   | 是否必须 | 示例 | 备注     |
| ---------- | -------- | ---- | -------- |
| systemName | 否       | SMS  | 系统名称 |

### 返回数据

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": {
    "OPC": ["sale_log", "sale_order"],
    "SMS": ["sys_org", "sys_user"]
  }
}
```

**响应字段**

| 名称  | 类型     | 是否必须 | 备注                         |
| ----- | -------- | -------- | ---------------------------- |
| code  | number   | 非必须   |                              |
| message | string | 非必须   |                              |
| trace | null     | 非必须   |                              |
| data  | object   | 非必须   | 键为系统名，值为菜单路径数组 |
| ├ OPC | string[] | 非必须   | 示例系统：OPC 菜单路径       |
| ├ SMS | string[] | 非必须   | 示例系统：SMS 菜单路径       |

---

## 5. 登出接口

> 接口编号：23370
> 接口路径：`POST /oauth/logout`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/logout`
> 备注：登出接口，在操作日志表中记录登出信息（`sys_login_info`）

### 请求参数

**Headers**

| 参数名称      | 参数值                            | 是否必须 | 备注                        |
| ------------- | --------------------------------- | -------- | --------------------------- |
| Content-Type  | application/x-www-form-urlencoded | 是       |                             |
| Authorization | Bearer `<access_token>`           | 是       | 登录接口返回的 access_token |

**Body**

无。

### 返回数据

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": "退出成功!"
}
```

**响应字段**

| 名称    | 类型   | 是否必须 | 备注 |
| ------- | ------ | -------- | ---- |
| code    | number | 非必须   |      |
| message | string | 非必须   |      |
| trace   | null   | 非必须   |      |
| data    | string | 非必须   |      |

---

## 6. 根据工号列表查询用户角色

> 接口编号：26325
> 接口路径：`POST /oss/admin-web/v1/output/data/getUserJobListByJobNumberList`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oss/admin-web/v1/output/data/getUserJobListByJobNumberList`
> 备注：根据工号列表批量查询用户角色信息

### 请求参数

**Headers**

| 参数名称     | 参数值           | 是否必须 | 备注 |
| ------------ | ---------------- | -------- | ---- |
| Content-Type | application/json | 是       |      |

**Body**

| 名称 | 类型     | 是否必须 | 备注     |
| ---- | -------- | -------- | -------- |
| data | string[] | 非必须   | 工号列表 |

```json
{
  "data": ["08402", "08640"]
}
```

### 返回数据

```json
{
  "msg": "操作成功",
  "code": 0,
  "data": {
    "08402": {
      "jobName": "Java开发工程师",
      "sysRoleList": [
        {
          "id": 18,
          "roleName": "员工测试",
          "status": 0,
          "isDefault": 0,
          "createAt": "2024-07-17 16:32:42",
          "createBy": "08384",
          "updateAt": "2024-07-17 16:32:42",
          "updateBy": "08384",
          "del": false
        }
      ],
      "employeeStatus": 3,
      "jobNumber": "08402",
      "username": "张锡臣",
      "status": 1
    }
  },
  "timestamp": 1747292370541
}
```

**响应字段**

| 名称               | 类型     | 是否必须 | 备注                                                                              |
| ------------------ | -------- | -------- | --------------------------------------------------------------------------------- |
| msg                | string   | 非必须   | 返回信息                                                                          |
| code               | number   | 非必须   | 状态码，0 成功，其他失败                                                          |
| data               | object   | 非必须   | 键为工号，值为该工号对应的用户信息                                                |
| ├ `<jobNumber>`    | object   | 非必须   | 单个工号的用户信息                                                                |
| ├ ├ jobName        | string   | 非必须   | 任职角色 / 岗位名称                                                               |
| ├ ├ jobNumber      | string   | 非必须   | 工号                                                                              |
| ├ ├ username       | string   | 非必须   | 姓名                                                                              |
| ├ ├ employeeStatus | string   | 非必须   | 员工状态：1待入职；2试用；3正式；4调出；5待调入；6退休；8离职；12非正式           |
| ├ ├ status         | string   | 非必须   | 状态：0 无效；1 有效                                                              |
| ├ ├ sysRoleList    | object[] | 非必须   | 系统角色列表                                                                      |
| ├ ├ ├ id           | string   | 非必须   | 角色 id                                                                           |
| ├ ├ ├ roleName     | string   | 非必须   | 角色名称                                                                          |
| ├ ├ ├ isDefault    | string   | 非必须   | 是否默认角色                                                                      |
| ├ ├ ├ status       | string   | 非必须   | 状态：0 无效；1 有效                                                              |
| timestamp          | number   | 非必须   | 时间戳                                                                            |

---

## 附录

### 通用响应格式

所有 `/oauth/*` 接口统一返回：

```json
{
  "code": 0,        // 0 = 成功，其他 = 失败
  "message": "success",
  "trace": null,
  "data": { ... }   // 各接口不同
}
```

接口 6（`/oss/...`）使用略微不同的格式：`msg` 替代 `message`，额外有 `timestamp` 字段。

### Token 生命周期

| 阶段 | 接口 | 说明 |
|------|------|------|
| 获取 | `POST /oauth/login` | 返回 access_token + refresh_token，有效期约 6000 秒 |
| 校验 | `GET /oauth/getCheckToken` | 检查 active 状态和 exp 过期时间 |
| 使用 | `GET /oauth/getUserInfo`、`GET /oauth/getUserPermission` | 通过 Authorization Header 携带 |
| 销毁 | `POST /oauth/logout` | 登出并记录日志 |

### 相关文档

| 文档 | 位置 |
|------|------|
| 登录接口（单篇） | `docs/references/xiyu-to-boran-permission-api-23353-login.md` |
| token 校验接口（单篇） | `docs/references/xiyu-to-boran-permission-api-getCheckToken.md` |
| 员工信息接口（单篇） | `docs/references/xiyu-to-boran-permission-api-23358-getUserInfo.md` |
| 用户权限接口（单篇） | `docs/references/xiyu-to-boran-permission-api-23484-getUserPermission.md` |
| 登出接口（单篇） | `docs/references/xiyu-to-boran-permission-api-23370-logout.md` |
| 工号查角色接口（单篇） | `docs/references/xiyu-to-boran-permission-api-26325-getUserJobListByJobNumberList.md` |
| 接口时序图 | `docs/references/xiyu-to-boran-permission-api-sequence.md` |
| 差距分析 | `docs/references/xiyu-to-boran-permission-api-gap-analysis.md` |
| 实施计划 | `docs/references/xiyu-to-boran-permission-api-implementation-plan.md` |
| 开发计划 | `docs/references/xiyu-to-boran-permission-api-development-plan.md` |
| 组织目录 yapi 映射 | `docs/integration/organization-directory-yapi-mapping.md` |
| 组织目录运维手册 | `docs/integration/organization-directory-runbook.md` |
