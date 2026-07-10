# implementation-notes — CRM 鉴权（回到文档本质）

## 问题本质（接口文档 `CRM (4).md`）

文档里 CRM 侧只有：

1. **步骤 2**：`POST /common/inner/generateToken`  
   - Header：`Authorization`（必须是 **OSS 的 access_token**）  
   - Body：`nickName` + `salesNo`  
   - 返回：CRM JWT 字符串  
2. **步骤 3**：商机/客户等接口  
   - Header：`Authorization: Bearer <CRM JWT>`

**步骤 1（拿 OSS token）不在这份 CRM 文档里**，来自 OSS 登录（用户密码/SSO 登录时拿到的 `access_token`）。

**没有**「系统服务号」「配置账号登录 OSS」的接口契约。

## 致命弯路（已纠正）

| 弯路 | 为何致命 |
|---|---|
| 全局 03595 配置账号登录 OSS | 生产登不上；文档未授权此身份 |
| 虚构「系统集成账号」接管后台 | **运维没有这个账号**；测试/生产都配不齐，等于再造假前提 |
| 无 operator 时 silent 降级 / 假系统号 | 掩盖「必须有真实用户 OSS」 |

## 正确模型（当前代码）

```
用户登录本系统
  → OssLoginFlowService 拿到 OSS access_token
  → 写入 OssUserTokenCache（按 username）

调用 CRM 时
  ① 取 OssUserTokenCache
  ② POST generateToken(Authorization=OSS, body=用户 nickName/salesNo)
  ③ 业务接口 Authorization=CRM JWT
```

- `getValidTokenForUser(username)`：唯一换票入口  
- username 空 / 无 OSS 缓存 → `TokenUnavailableException`（**诚实失败**）  
- 401 → 清 CRM JWT + profile + **OSS**（防坏钥匙死循环）

## 无用户上下文的后台能力

没有系统账号时，**无法**合法调 CRM。策略：

| 场景 | 行为 |
|---|---|
| 有登录用户的 API / webhook 带 operator | 走用户三步 |
| 自动分配、外部推送反查、无 operator webhook | 降级 empty / TokenUnavailable→重试死信 |

若业务将来**必须**后台调 CRM，只能由客户提供**真实可登录的服务身份**再另开需求——不能在代码里假装已有。

## 配置

`XIYU_CRM_OAUTH_*` / `GENERATE_TOKEN_*`：遗留项，**代码不再用于换 CRM token**。
