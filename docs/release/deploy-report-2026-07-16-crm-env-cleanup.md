# 部署日志：CRM 遗留环境变量清理（技术债清理）

> **环境**：测试环境 + 生产环境（双环境同步清理）
> **操作时间**：2026-07-16 15:54 CST（测试）/ 15:55 CST（生产）
> **操作人**：AI Agent（trae worktree）
> **操作类型**：服务器配置文件清理（无代码部署，无服务重启）

## 一、操作背景

### 1.1 起因

调试 tender 1666（`https://winbid-test.ehsy.com/bidding/1666`）未关联 CRM 商机问题时，发现 `CrmAuthService` 类注释中存在误导性表述：

```java
<b>没有</b>全局/系统服务号路径。无用户、无用户 OSS 缓存时抛 TokenUnavailableException
```

该注释被误读为"技术债，可以加系统账号路径"，导致 AI Agent 推荐了被客户明确禁止的"系统服务号"方案。经用户澄清，**客户明令禁止使用系统服务号/全局账号换取 CRM JWT**，必须基于真实用户 OSS token。

### 1.2 根因

早期设计曾设想"用一个配置账号（系统服务号）全局换 CRM JWT"，并在 `backend.env` 配置了 4 个环境变量。后来客户明确禁止这条路径，代码里的字段被标为"遗留：当前不用"并最终删除，但 **`backend.env` 文件里的环境变量一直没人清理**，留下误导性残留。

### 1.3 关联代码改动

同一轮已清理代码侧残留（本部署日志只记录服务器配置清理）：
- `CrmProperties.java`：删除 `oauthUsername` / `oauthPassword` / `generateTokenNickName` / `generateTokenSalesNo` 4 个字段
- `StartupConfigurationSummaryLogger.java`：日志删除对应 4 个字段的打印
- `application.yml` / `application-dev.yml` / `application-prod.yml`：删除 4 个遗留配置项
- `CrmAuthService.java` / `WebhookCrmTokenResolver.java`：注释明确标注"客户禁令"

## 二、清理范围

### 2.1 删除的环境变量（4 个）

| 环境变量 | 原本用途 | 删除原因 |
|---|---|---|
| `XIYU_CRM_OAUTH_USERNAME` | 系统服务号的 OSS 登录用户名 | 客户禁令：禁止系统服务号路径 |
| `XIYU_CRM_OAUTH_PASSWORD` | 系统服务号的 OSS 登录密码 | 同上 |
| `XIYU_CRM_GENERATE_TOKEN_NICK_NAME` | 系统服务号调 generateToken 时的 nickName | 同上 |
| `XIYU_CRM_GENERATE_TOKEN_SALES_NO` | 系统服务号调 generateToken 时的 salesNo | 同上 |

### 2.2 操作范围

| 环境 | 服务器 | 配置文件路径 |
|---|---|---|
| 测试环境 | `172.16.38.78` (winbid-01) | `/etc/xiyu-bid/backend.env` |
| 生产环境 | `172.16.10.149` | `/etc/xiyu-bid/backend.env` |

## 三、执行步骤

### 3.1 测试环境清理

**Step 1: 备份**
```bash
sudo cp /etc/xiyu-bid/backend.env /etc/xiyu-bid/backend.env.bak.20260716_155436
```
- 备份文件：`/etc/xiyu-bid/backend.env.bak.20260716_155436`
- 权限：`-rw-r--r-- 1 root root 5238`

**Step 2: 删除 4 个环境变量**
```bash
sudo sed -i -E "/^(#.*系统服务号|#.*配置账号|#.*遗留.*OAuth|XIYU_CRM_OAUTH_USERNAME|XIYU_CRM_OAUTH_PASSWORD|XIYU_CRM_GENERATE_TOKEN_NICK_NAME|XIYU_CRM_GENERATE_TOKEN_SALES_NO)/d" /etc/xiyu-bid/backend.env
```
- 删除前匹配数：4 行（L45/L46/L50/L51）
- 删除后残留匹配数：0

**Step 3: 健康检查**
```
HTTP_STATUS: 200
status: UP
所有 10 个组件 UP（aiProvider/db/diskSpace/jwt/livenessState/ping/readinessState/redis/sidecar）
```

### 3.2 生产环境清理

**Step 1: 备份**
```bash
sudo cp /etc/xiyu-bid/backend.env /etc/xiyu-bid/backend.env.bak.20260716_155530
```
- 备份文件：`/etc/xiyu-bid/backend.env.bak.20260716_155530`
- 权限：`-rw-r----- 1 root root 4955`

**Step 2: 删除 4 个环境变量**
```bash
sudo sed -i -E "/^(#.*系统服务号|#.*配置账号|#.*遗留.*OAuth|XIYU_CRM_OAUTH_USERNAME|XIYU_CRM_OAUTH_PASSWORD|XIYU_CRM_GENERATE_TOKEN_NICK_NAME|XIYU_CRM_GENERATE_TOKEN_SALES_NO)/d" /etc/xiyu-bid/backend.env
```
- 删除前匹配数：4 行（L89/L90/L92/L93）
- 删除后残留匹配数：0

**Step 3: 健康检查**
```
HTTP_STATUS: 200
status: UP
所有 10 个组件 UP（aiProvider/db/diskSpace/jwt/livenessState/ping/readinessState/redis/sidecar）
```

## 四、验证结果

| 环境 | 删除行数 | 残留匹配 | 后端健康 | 服务重启 | 备注 |
|---|---|---|---|---|---|
| 测试环境 | 4 | 0 | ✅ UP | 不需要 | 环境变量在进程启动时读取，删除文件不影响运行中进程 |
| 生产环境 | 4 | 0 | ✅ UP | 不需要 | 同上 |

### 关键说明
- **无需重启服务**：环境变量在 JVM 进程启动时读取到 `System.getenv()`，删除 `backend.env` 文件不影响运行中进程
- **下次重启安全**：Spring Boot `@ConfigurationProperties` 默认忽略未知属性（`ignoreUnknownFields=true`），进程重启时读取新文件不会报错
- **生产健康检查全绿**：10 个组件全部 UP，无任何影响

## 五、回滚方案

如需回滚（理论上不需要，因为删除的是未使用的配置）：

```bash
# 测试环境回滚
ssh jetty@172.16.38.78 'sudo cp /etc/xiyu-bid/backend.env.bak.20260716_155436 /etc/xiyu-bid/backend.env'

# 生产环境回滚
ssh jetty@172.16.10.149 'sudo cp /etc/xiyu-bid/backend.env.bak.20260716_155530 /etc/xiyu-bid/backend.env'
```

回滚后需要重启后端服务才能让环境变量重新生效（但代码已删除对应字段，即使回滚配置也不会读取）。

## 六、风险评估

| 风险点 | 评估 | 说明 |
|---|---|---|
| 功能影响 | ✅ 无 | 代码已删除对应字段，环境变量未被任何业务代码读取 |
| 服务中断 | ✅ 无 | 不需要重启服务，配置文件修改不影响运行中进程 |
| 启动失败 | ✅ 无 | Spring Boot 忽略未知属性，下次重启安全 |
| 数据影响 | ✅ 无 | 纯配置文件清理，不涉及数据库 |
| 误导风险消除 | ✅ 是 | 清理后不会再有 agent/开发者误以为"系统服务号路径还在用" |

## 七、经验沉淀

### 7.1 教训：代码与配置同步清理

**问题**：代码里删除了字段，但服务器配置文件里的环境变量一直没清理，留下 2 个月的误导性残留。

**根因**：清理技术债时只改了代码，没有同步清理服务器配置。`backend.env` 是服务器文件，不在代码审查范围内。

**预防**：以后删除 `@ConfigurationProperties` 字段时，必须同步检查并清理：
1. 代码侧：Java 字段 + yml 配置 + 测试代码引用
2. 服务器侧：`backend.env` 中的环境变量（测试 + 生产双环境）
3. 部署日志：记录清理操作

### 7.2 教训：注释要明确"客户约束" vs "技术选择"

**问题**：`CrmAuthService` 类注释写"没有全局/系统服务号路径"，被 AI Agent 误读为"技术债，可以加"。

**根因**：注释没有说明这是客户禁令还是技术选择，导致后续维护者（包括 AI）误判。

**预防**：客户约束必须在注释中明确标注"客户禁令"字样，不能只写"没有"或"不用"。已在 `CrmAuthService.java` / `WebhookCrmTokenResolver.java` 修正。

### 7.3 教训：tender 1666 / 56 调试链路

本次调试 tender 1666 未关联 CRM 商机问题，根因是 PM 08687（王凯毅）未登录系统导致 OSS token 缺失，无法换 CRM JWT 反查商机 CC 编号。完整调试链路：

1. DB 取证：tender 1666 的 `external_id=CRM:878`, `crm_opportunity_id=NULL`
2. 日志取证：`user OSS token missing, username=08687`
3. CRM 接口实测：用 06234 的 JWT 查到商机 21379 的 CC 编号 = `CC20260716754`
4. 根因确认：CRM 推送路径用 PM 个人身份换 JWT，但 PM 未登录 → 链路断裂
5. 治本方案：spec 037 fallback 版（OSS token 缺失时 fallback 到不带 Authorization）

## 八、关联文档

- spec 文档：`specs/037-crm-link-compensation/`
- 代码改动：`CrmProperties.java` / `StartupConfigurationSummaryLogger.java` / `CrmAuthService.java` / `WebhookCrmTokenResolver.java` / `application*.yml`
- 经验沉淀：`docs/lessons/lessons-learned.md`（CRM 关联失败调试 SOP）

## 九、确认清单

- [x] 测试环境备份成功（`backend.env.bak.20260716_155436`）
- [x] 测试环境清理完成（4 行删除，残留 0）
- [x] 测试环境健康检查通过（status: UP，10 组件全绿）
- [x] 生产环境备份成功（`backend.env.bak.20260716_155530`）
- [x] 生产环境清理完成（4 行删除，残留 0）
- [x] 生产环境健康检查通过（status: UP，10 组件全绿）
- [x] 代码侧清理完成（7 个文件，+9/-43 行）
- [x] 相关测试通过（CrmAuthServiceTest 15/15, ArchitectureTest 31/31）
- [x] 部署日志记录
