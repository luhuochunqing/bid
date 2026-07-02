# 通用工程教训与复盘

> 本文件记录跨模块、可复用的工程教训与流程改进，按 session 追加章节。

---

## 1. 后端接口契约变更必须同步前端所有入口

### 问题背景

CO-274 中，V130 评估表重构后 `/api/tenders/{id}/bid` 被设计为「评估-审核后创建项目」，要求请求时标讯已存在 `TenderEvaluation`。但前端标讯详情页的「投标」按钮仍走快速投标流程（`participate → bid`），该流程不会提交评估表，导致 `/bid` 返回 404 并被静默吞掉，项目未创建。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 后端 `/bid` 契约变更后，前端仍按旧流程调用 | 任何后端接口新增前置条件时，必须梳理前端所有调用方 | 变更接口契约时，在 PR 描述中列出所有前端调用点并逐一验证 |
| 前端 `catch {}` 吞掉关键错误 | 核心业务错误不应静默处理 | 对「创建项目」等关键操作，必须向用户反馈失败或降级处理 |
| 同一功能存在两条差异路径 | 列表页和详情页「投标」入口行为不一致，导致测试覆盖遗漏 | 同一业务动作尽量统一入口；无法统一时，两套路径都要覆盖 |

### 操作规范（建议固化到 CLAUDE.md / RULES.md）

1. 后端接口新增 `orElseThrow` / 前置校验时，必须在 PR 中标注「前端调用点影响范围」。
2. 前端对关键写操作禁止空 `catch`；至少记录日志、上报埋点或弹出错误提示。
3. 一个业务动作存在多个前端入口时，每条入口都应有对应的集成测试或 E2E。

### 验证命令

```bash
# 检查前端是否有空 catch 吞掉关键 API 错误
grep -R "catch {\s*}" src/views/Bidding src/views/Project
# 期望输出：无关键路径上的空 catch

# 检查 /bid 调用方是否覆盖两种入口
grep -R "proceedToBid" src/api src/views
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-274.md` — 完整根因分析
- `docs/exec-plans/tech-debt-tracker.md` — 相关技术债登记

---

## 2. 前端热更新部署时不能只保留 index.html 引用的文件

### 问题背景

2026-06-19 部署前端更新时，清理旧 assets 脚本只保留了 `index.html` 直接引用的 7 个文件，但 Vite 入口 JS 通过动态 import() 引用了大量 chunk 文件（如 `expensePageShared-*.js`、`MainLayout-*.js` 等）。这些 chunk 被误删后，用户访问页面时报 404，页面白屏。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 清理脚本只检查 index.html 引用 | Vite 动态 import 的 chunk 不在 index.html 中 | 前端部署必须保留完整 dist 目录 |
| 先清理旧文件再部署新文件 | 清理和部署顺序错误 | 先部署新文件 → 验证通过 → 再清理旧版本 |
| 缺少部署后验证 | 未检查动态 chunk 是否完整 | 部署后检查 assets 目录文件数是否与本地 dist 一致 |

### 正确做法

```bash
# 方式 1：直接覆盖整个 dist 目录（推荐热更新）
rm -rf /srv/www/xiyu-bid/*
cp -R dist/. /srv/www/xiyu-bid/

# 方式 2：版本化目录切换（推荐正式发布）
ln -sfn /opt/xiyu-bid/releases/<hash>/frontend /srv/www/xiyu-bid
```

### 验证命令

```bash
# 本地 dist 文件数
ls dist/assets/ | wc -l

# 服务器文件数（应一致）
ssh jetty@172.16.38.78 'ls /srv/www/xiyu-bid/assets/ | wc -l'
```

### 相关文档

- `docs/lessons/root-cause-analysis-frontend-404.md` — 完整根因分析

---

## 3. 字段必填性变更必须同步前后端校验策略

### 问题背景

CO-281 要求「提交立项时项目类型字段改为非必填」。后端 `InitiationFieldPolicy` 中 `projectType` 原本通过 `requireNotNull` 强制校验：

```java
// backend/src/main/java/com/xiyu/bid/project/core/InitiationFieldPolicy.java（修复前）
requireNotNull("projectType", input.projectType(), missing);
```

前端表单已允许留空，但后端拒绝，导致提交立项 422。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 后端校验策略未随产品需求及时调整 | 字段必填性变更必须同时修改后端校验规则 | 任何字段必填/可选变更，需同步检查 DTO、FieldPolicy、数据库约束 |
| 缺少针对可选字段的边界测试 | 仅测「必填缺失拒绝」不够，还要测「为空允许通过」 | 字段必填性变更时，同步调整正例/反例测试 |

### 正确做法

```java
// 修复后：从必填列表中移除 projectType
requireText("ownerUnit", input.ownerUnit(), missing);
requirePositive("expectedBidders", input.expectedBidders(), missing);
requireNotNull("customerType", input.customerType(), missing);
requirePositiveAmount("annualRevenue", input.annualRevenue(), missing);
requireNotNull("bidOpenTime", input.bidOpenTime(), missing);
requirePositive("ownerUserId", input.ownerUserId(), missing);
requireText("departmentSnapshot", input.departmentSnapshot(), missing);
```

对应测试从反例改为正例：

```java
@Test
void projectType_optional() {
    var in = new InitiationFieldPolicy.InitiationInput("国网", 3, 12, null,
            InitiationFieldPolicy.CustomerType.CENTRAL_SOE,
            new BigDecimal("1"), null, LocalDateTime.now(), 1L, "部门",
            new BigDecimal("1"), "汇票", "NO", null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
    assertTrue(InitiationFieldPolicy.validate(in).allowed());
}
```

### 验证命令

```bash
# 运行立项字段策略测试
cd backend
mvn test -Dtest=InitiationFieldPolicyTest
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-279.md` — 同期立项相关根因分析
- Issue: CO-281

---

## 4. 回滚 PR 前必须确认根因，避免回滚正确修复

### 问题背景

2026-06-20 CO-280 排查过程中，PR !884 修改 `TenderIntegrationMapper.toDownloadUrl()` 添加 `publicBaseUrl` 配置（方向正确），但当时误判根因为"下载端点不支持外部 URL"。PR !886 实现代理下载后**错误回滚**了 PR !884 的修改。部署后 CRM 实测仍失败（用户报错 URL 显示 `crm-test.ehsy.com` 域名），才重新识别真正根因是**相对路径跨域**问题。最终 PR !890 重新实现 PR !884 的方向 + 保留 PR !886 的代理下载，问题才彻底修复。

### 事故时间线

| 时间 | 操作 | 判断 |
|------|------|------|
| 初次排查 | 发现 `DocInsightController.download()` 拒绝 `http(s)://` URL 返回 400 | 误判为唯一根因 |
| PR !884 | 添加 `publicBaseUrl` 配置（方向正确） | ✅ 正确方向 |
| PR !886 | 实现代理下载 + **回滚 PR !884** | ❌ 错误回滚 |
| 部署后验证 | 西域内部下载测试通过 | ❌ 同源场景验证不可靠 |
| CRM 实测 | 用户报错 URL 显示 `crm-test.ehsy.com` 域名 | ✅ 暴露真正根因 |
| PR !890 | 重新实现 `publicBaseUrl` + 保留代理下载 | ✅ 彻底修复 |

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 误判根因后回滚了正确修复 | 回滚 PR 前必须确认根因，不能因为"看起来修了另一个问题"就回滚 | 回滚前用"五个为什么"追问根因，确认被回滚的修复与根因无关 |
| 只测同源场景就认为修复生效 | 同源场景下相对路径正常，掩盖了跨域问题 | 跨系统 bug 必须用真实外部系统场景验证 |
| 多个根因可能同时存在 | 修复一个不代表另一个不存在 | 排查时列出所有可能的根因，逐一验证，不能"修了一个就收工" |

### 操作规范（建议固化到 CLAUDE.md / RULES.md）

1. **回滚 PR 前必须确认根因**：用"五个为什么"追问，确认被回滚的修复与根因无关。如果不确定，保留修复并观察。
2. **跨系统 bug 必须用真实外部系统场景验证**：不能只测同源访问，必须模拟外部系统的调用场景（如 CRM 实际点击附件）。
3. **排查时列出所有可能的根因**：逐一验证，不能"修了一个就收工"。本次同时存在两个根因（下载端点拒绝外部 URL + 相对路径跨域），只修第一个就认为修复完成，导致问题反复。
4. **回滚操作需要显式记录理由**：commit message 或 PR 描述中必须写明"为什么回滚"、"确认了什么根因"。

### 验证方法

```bash
# 回滚前自检清单
1. 我确认了真正的根因是什么吗？（用五个为什么追问）
2. 被回滚的修复与根因无关吗？
3. 我用真实场景（非同源）验证过修复无效吗？
4. 回滚后我会重新实现这个修复吗？如果会，为什么要回滚？

# 跨系统 bug 验证清单
1. 我模拟了外部系统的调用场景吗？
2. 我检查了外部系统实际收到的数据吗？（如 CRM 拿到的 URL）
3. 我验证了端到端流程吗？（如 CRM 用户实际点击附件）
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-280.md` — CO-280 完整根因分析
- `docs/lessons/crm-integration-lessons.md` §8 — 跨系统 URL 推送通用规则

---

## 5. 部署期间并发部署导致 502：ShutdownHook 卡住 + jar 替换导致 NoClassDefFoundError

### 问题背景

2026-06-20 CO-280 修复部署后，用户报告"服务器端测试系统无法登录"，报错 `502 Bad Gateway`。排查发现：我在 14:59 部署了 `e6ea9a0cb-co280-proxy`，有人在 15:24:57 从 `172.16.86.222` 部署了新版本 `df340211-api8080-co282`。部署过程中 `systemctl restart` 触发 SIGTERM，ShutdownHook 卡住 1 分钟（jar 被替换导致 `NoClassDefFoundError`），systemd 超时 SIGKILL，新进程启动期间 Nginx 返回 502，持续约 1 分 30 秒。

### 事故时间线

| 时间 (CST) | 事件 | 影响 |
|------------|------|------|
| 14:59:34 | 我部署 `e6ea9a0cb-co280-proxy` | 服务正常 |
| 15:24:57 | 有人从 `172.16.86.222` 部署 `df340211-api8080-co282` | 开始替换 jar |
| 15:25:27 | 后端收到 SIGTERM，开始 ShutdownHook | 服务开始关闭 |
| 15:25:27-15:26:27 | ShutdownHook 执行缓慢（1 分钟），出现大量 `NoClassDefFoundError` | 服务不可用 |
| 15:26:28 | systemd 超时 SIGKILL，进程被强制终止 | 服务完全中断 |
| 15:26:29 | systemd 自动重启，新进程启动 | 服务恢复中 |
| 15:26:48 | 应用启动完成（19.679 秒） | 服务恢复 |
| 15:27+ | readiness 一度 OUT_OF_SERVICE，现已恢复 UP | 502 消失 |

### 根因分析

**直接原因**：部署期间 `systemctl restart` 触发服务重启，重启期间 Nginx 无法连接后端导致 502。

**深层原因**：

1. **jar 被替换后 ShutdownHook 失效**：部署脚本先替换 jar 文件，再执行 `systemctl restart`。Spring Boot 的 ShutdownHook 在执行时需要加载类（Tomcat/Redis/Kafka 等），但此时 jar 已被替换，classloader 引用的类已变化，导致 `NoClassDefFoundError`，ShutdownHook 卡住。

2. **systemd 超时 SIGKILL**：`TimeoutStopSec` 默认 90 秒，ShutdownHook 卡住 1 分钟后 systemd 发送 SIGKILL 强制终止。这会导致：
   - 正在处理的请求被中断
   - 数据库连接未正常关闭
   - 缓存数据可能丢失

3. **并发部署无锁机制**：多个 agent/人可以同时执行部署脚本，没有部署锁或互斥机制。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 并发部署导致 502 | 部署期间不能并发部署 | 部署前确认没有其他人在部署，或引入部署锁 |
| jar 替换后 ShutdownHook 失效 | 先停服务再替换 jar，不能先替换再 restart | 部署顺序：stop → 替换 jar → start |
| ShutdownHook 卡住 1 分钟 | NoClassDefFoundError 导致 ShutdownHook 无法正常执行 | 避免在 ShutdownHook 中加载新类 |
| systemd SIGKILL | 强制终止可能导致数据丢失 | 配置合理的 `TimeoutStopSec`，监控 ShutdownHook 执行时间 |
| 502 持续 1 分 30 秒 | 服务重启期间 Nginx 无健康检查兜底 | Nginx 配置 `proxy_next_upstream` 或维护页面 |

### 正确做法

```bash
# 1. 部署前检查是否有其他部署在进行
ssh jetty@172.16.38.78 "sudo systemctl status xiyu-bid-backend | grep 'Active:' | head -1"
# 如果服务正在重启（activating/deactivating），等待完成后再部署

# 2. 正确的部署顺序：先停服务 → 替换 jar → 启动服务
sudo systemctl stop xiyu-bid-backend
sudo cp /opt/xiyu-bid/incoming/app.jar /opt/xiyu-bid/shared/backend/app.jar
sudo systemctl start xiyu-bid-backend

# 3. 等待健康检查恢复
for i in $(seq 1 30); do
  if curl -s http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "Service is UP"
    break
  fi
  echo "Waiting for service... ($i/30)"
  sleep 5
done

# 4. 验证服务完全恢复
curl -s http://127.0.0.1:8080/actuator/health
curl -s http://127.0.0.1:8080/actuator/health/readiness
```

### systemd 配置优化建议

```ini
# /etc/systemd/system/xiyu-bid-backend.service
[Service]
# 给 ShutdownHook 足够时间优雅关闭
TimeoutStopSec=120
# 失败后自动重启，但避免频繁重启
Restart=always
RestartSec=10
# 启动失败保护
StartLimitIntervalSec=60
StartLimitBurst=3
```

### Nginx 502 兜底配置

```nginx
# /etc/nginx/conf.d/xiyu-bid.conf
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    # 后端不可用时返回维护页面，而非 502
    error_page 502 503 504 /maintenance.html;
    # 健康检查（nginx-plus 或第三方模块）
    # proxy_next_upstream off;  # 单实例部署，不要尝试下一个 upstream
}

location = /maintenance.html {
    root /srv/www/xiyu-bid;
    internal;
}
```

### 部署协作规范（建议固化到 CLAUDE.md / RULES.md）

1. **部署前确认无人正在部署**：检查 `systemctl status` 和最近部署日志，确认服务稳定运行后再开始部署。
2. **部署顺序：stop → 替换 → start**：不能先替换 jar 再 `restart`，避免 ShutdownHook 因 jar 变化而失效。
3. **部署后等待健康检查恢复**：不能部署完就离开，必须等待 `health` + `readiness` 全部 UP。
4. **部署后通知团队**：在协作群通知"已部署版本 X，服务已恢复"，避免其他人误判 502 为故障。
5. **502 排查第一步**：检查 `systemctl status` 和 `journalctl`，确认是否有人正在部署。

### 502 排查命令

```bash
# 1. 检查服务状态（是否正在重启）
sudo systemctl status xiyu-bid-backend

# 2. 检查最近部署日志（是否有人刚部署）
sudo journalctl -u xiyu-bid-backend --since "10 minutes ago" | grep -iE 'SIGTERM|SIGKILL|Started|Stopped|deploy'

# 3. 检查部署历史（是否并发部署）
ls -lt /opt/xiyu-bid/backups/ | head -5
cat /opt/xiyu-bid/deployed-release.json

# 4. 检查健康检查
curl -s http://127.0.0.1:8080/actuator/health
curl -s http://127.0.0.1:8080/actuator/health/readiness

# 5. 检查 Nginx 错误日志
sudo tail -20 /var/log/nginx/error.log
```

### 相关文档

- `scripts/release/remote-deploy.sh` — 远程部署脚本
- `scripts/release/package-release.sh` — 打包脚本
- `/etc/systemd/system/xiyu-bid-backend.service` — systemd 服务配置
- `/etc/nginx/conf.d/xiyu-bid.conf` — Nginx 配置

---

## 6. 部署后配置未生效的排查方法论

### 问题背景

PR #888 修改了 OSS 同步员工的默认密码逻辑，直接更新数据库后测试人员仍无法登录。排查过程涉及多个层面，最终发现是代码中的 `DEFAULT_PASSWORD_HASH` 本身无效，同时数据库更新时遭遇 shell 转义截断。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 部署后密码未生效，不知从何排查 | 需要系统化的部署验证清单 | 每次部署后按「代码→配置→数据→运行时」四层验证 |
| 数据库 UPDATE 含 `$` 被 shell 截断 | 命令行执行 SQL 要注意特殊字符转义 | 含特殊字符的 SQL 必须使用文件方式执行 |
| 硬编码的 BCrypt 哈希未验证有效性 | "看起来像"不等于"真的是" | 任何密码哈希必须通过 `matches()` 验证后才能入库 |

### 操作规范（部署后验证清单）

```
部署后验证四层模型：

Layer 1 — 代码层
  └─ 检查 jar 是否包含预期修改（javap / strings / jar tf）
  └─ 验证 class 文件修改时间是否新于部署时间

Layer 2 — 配置层
  └─ 检查环境变量/配置文件是否加载正确
  └─ 验证数据库连接配置指向预期实例

Layer 3 — 数据层
  └─ 检查数据库记录是否更新（COUNT / LENGTH）
  └─ 抽样验证数据值是否符合预期（不被转义截断）

Layer 4 — 运行时层
  └─ 检查服务日志是否有异常（journalctl / grep ERROR）
  └─ 直接调用 API 验证功能（curl / Postman）
  └─ 验证日志中的关键路径是否按预期执行
```

### 正确做法：验证 jar 内容

```bash
# 验证 jar 是否包含新代码
unzip -p app.jar BOOT-INF/classes/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.class | strings | grep "DEFAULT_PASSWORD_HASH"

# 验证 AuthService 是否包含本地密码回退逻辑
unzip -p app.jar BOOT-INF/classes/com/xiyu/bid/service/AuthService.class | strings | grep "isLocalPasswordValid"
```

### 正确做法：验证数据库

```bash
# 检查更新记录数
mysql -h ... -e "SELECT COUNT(*) FROM winbid.users WHERE source = 'OSS'"

# 检查密码长度（BCrypt 应为 60 字符）
mysql -h ... -e "SELECT LENGTH(password) FROM winbid.users WHERE source = 'OSS' LIMIT 1"

# 抽样验证密码值（注意：生产环境谨慎操作）
mysql -h ... -e "SELECT SUBSTRING(password, 1, 7) FROM winbid.users WHERE source = 'OSS' LIMIT 1"
# 期望输出：$2a$10$
```

### 正确做法：验证日志

```bash
# 查看服务启动日志
sudo journalctl -u xiyu-bid-backend --since "10 minutes ago" | grep -E "ERROR|WARN|Started"

# 查看认证相关日志
sudo journalctl -u xiyu-bid-backend --since "10 minutes ago" | grep -i "password\|auth\|login"
```

### 正确做法：端到端验证

```bash
# 直接调用登录 API 验证
curl -s -X POST http://172.16.38.78:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"00444","password":"123456"}' | jq '.success'
# 期望输出：true
```

### 验证命令

```bash
# 一键检查四层状态
#!/bin/bash
echo "=== Layer 1: 代码层 ==="
jar tf /opt/xiyu-bid/shared/backend/app.jar | grep -E "OrganizationUserSyncWriter|AuthService"

echo "=== Layer 2: 配置层 ==="
grep -E "DB_HOST|DB_NAME" /etc/xiyu-bid/backend.env

echo "=== Layer 3: 数据层 ==="
mysql -h winbid-01.test.rds.ehsy.com -P3306 -u ea_bid -p'ra(D7np+Z' winbid \
  -e "SELECT COUNT(*) as total, LENGTH(password) as pwd_len FROM winbid.users WHERE source = 'OSS' LIMIT 1"

echo "=== Layer 4: 运行时层 ==="
systemctl is-active xiyu-bid-backend
journalctl -u xiyu-bid-backend --since "5 minutes ago" | tail -5
```

### 相关文档

- `docs/lessons/root-cause-analysis-bcrypt-invalid-hash.md` — 完整根因分析
- `docs/lessons/shell-gotchas.md` — Shell 转义陷阱

---

## 7. 模板录入视图与真实数据回显视图必须分离

### 问题背景

CO-282 中，客户信息矩阵历史上来自「固定 15 列 × 14 行」的人工录入模板；但外部/API 创建标讯详情页需要的是「真实传入多少客户信息就展示多少」。多轮修复已经解决后端保存、字段映射、EAV/flat 转换、外部角色显示，但前端 `CustomerInfoMatrix.mergeData()` 仍先生成 `CUSTOMER_INFO_ROWS` 固定 14 行，再过滤空行，导致接口返回 0 行时 UI 仍可能显示预设角色行。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 历史模板被当作回显数据源 | 模板行是录入辅助，不是接口真实数据 | 回显组件不得无条件生成模板数据 |
| 「生成后过滤」替代了根治 | 只要模板行有默认值/残留值，就可能重新显示 | 回显场景应从源头不生成模板行 |
| 多轮修复只看数据链路 | 后端返回正确不代表前端展示正确 | 同时验证 API payload 与组件渲染结果 |

### 正确做法

```js
// ✅ 回显模式：只展示真实传入数据
function mergeData(incoming) {
  if (!Array.isArray(incoming)) return []

  return incoming
    .filter(item => item?.roleKey)
    .map(item => normalizeIncomingRow(item))
    .filter(hasCustomerInfoValue)
}
```

如果后续需要保留「人工录入固定 14 行模板」，应显式拆分模式，例如：

```js
// create/edit template mode: 可以生成 CUSTOMER_INFO_ROWS
// external readonly/detail mode: 只能展示 incoming rows
```

### 验证命令

```bash
pnpm vitest run src/views/Bidding/detail/components/CustomerInfoMatrix.spec.js

# 服务器真实数据验证：后端返回 0 行时，前端不能补 14 行
curl -s -b /tmp/xiyu-cookie.txt \
  http://localhost:18080/api/tenders/324/evaluation
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-282.md` — 完整根因分析
- `docs/lessons/root-cause-analysis-co-266-co-267.md` — 客户信息字段名不一致的前序根因

---

## 8. SPA 用户可见修复必须做四层验证：API、产物、入口缓存、真实页面

### 问题背景

CO-282 同时出现「客户信息 14 行」和「当前用户显示游客」。前者是前端固定矩阵展示策略，后者是 Header fallback 与旧 bundle/cache 风险。部署后如果只验证 API 或只替换静态文件，用户仍可能因为旧 `index.html` 加载旧 bundle 而看到旧文案。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 只验证 API | API 正确不代表 UI 不会补数据 | 验证接口返回与前端渲染两层 |
| 只验证源码 | 用户运行的是部署产物，不是源码 | 对 dist/server assets 做字面量检查 |
| `index.html` 仍可缓存 | hashed assets 更新了，旧入口仍可能引用旧 bundle | SPA 入口必须 no-store/no-cache |
| 多个症状混在一起 | 缓存问题和业务逻辑问题会互相干扰 | 用四层验证拆开判断 |

### 操作规范

```text
Layer 1 — API 数据层
  └─ 直接 curl 关键接口，确认后端真实返回。

Layer 2 — 前端产物层
  └─ strings/rg 检查 dist 或 /srv/www 中是否仍含旧字面量。

Layer 3 — 入口缓存层
  └─ curl -I /index.html，确认 Cache-Control/Pragma/Expires。

Layer 4 — 真实页面层
  └─ 浏览器访问目标页面，必要时强刷，确认用户可见结果。
```

### CO-282 的最小验证证据

```text
evaluation_324=True:200:customer_rows=0
asset_check=none
HTTP/1.1 200 OK
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Expires: 0
me=True:200:系统管理员
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-282.md` — 完整根因分析
- `docs/lessons/lessons-learned.md` §6 — 部署后配置未生效的四层模型

---

## 9. sync-env.sh stash pop 失败导致修改丢失，用 git fsck 找回

### 问题背景

2026-06-20 提交 CO-262 PR 前，执行 `./scripts/sync-env.sh .` 同步基线。脚本自动 `git stash` 保存未提交变更，rebase 后 `git stash pop` 恢复。但 stash pop 失败（原因未明），`git status` 显示所有修改消失，只剩 untracked 文件。

`git stash list` 为空，`git reflog` 显示 3 次 `reset: moving to HEAD`，修改似乎彻底丢失。

### 教训

| 问题 | 教训 | 规范 |
|------|------|------|
| stash pop 失败后修改丢失 | git 对象不会立即被 GC，dangling commits 仍可找回 | 修改丢失时第一时间跑 `git fsck --lost-found` |
| `git stash list` 为空不代表数据已删除 | stash pop 失败后 stash 可能被 drop，但 commit 对象仍在 | 用 `git fsck` 搜索 dangling commits |
| 无法区分哪个 dangling commit 是自己的 | stash commit 的 message 包含分支名和时间戳 | 用 `git log -1 --format="%ci %s" <hash>` 逐个排查 |

### 恢复方法

```bash
# 1. 列出所有 dangling commits
git fsck --no-reflogs --lost-found 2>&1 | grep "dangling commit"

# 2. 逐个检查 message，找到包含自己分支名的 stash
# stash commit message 格式: "On <branch>: <stash-message>"
for c in <hash1> <hash2> ...; do
  git log -1 --format="%ci %s" $c
done

# 3. 确认包含自己修改的 stash commit
git show --stat <hash>  # 查看包含哪些文件

# 4. 恢复修改（apply 不会删除 stash，安全）
git stash apply <hash>
```

### 验证命令

```bash
# 确认所有修改已恢复
git status
git diff --stat
```

### 相关文档

- `scripts/sync-env.sh` — 早操脚本，含 stash/rebase/pop 逻辑

---

## 10. 同一接口错误形态变化时，必须重新看真实服务器日志

### 问题背景

2026-06-21 修复 `POST /api/projects/{id}/drafting/submit-bid` 的 409 后，用户再次验证发现同一接口变成 500。第一轮根因是 `submitBid` 误复用任务完成闸门；第二轮如果继续沿用这个结论，很容易误判。按用户要求直接看服务器日志后，确认阶段已经成功切到 `EVALUATING`，新的失败发生在后置通知插入：`Column 'created_by' cannot be null`。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 同一接口从 409 变成 500 | 错误形态变化通常意味着执行路径已经越过旧故障点 | 不得把上一轮根因自动套用到新错误 |
| 只看浏览器 500 | 浏览器只告诉你结果，不告诉你数据库/事务失败点 | 500 必须查看服务端日志，定位第一条 ERROR/SQL 异常 |
| 后置通知失败掩盖主链路成功 | 日志中 `Project stage transitioned` 先出现，说明主链路已推进 | 排查时区分主业务链路和副作用链路 |

### 操作规范

1. 同一接口错误码或错误消息变化时，重新建立调用链，不沿用上轮结论。
2. 500/事务异常优先看服务器日志，特别是第一条 SQL 异常和业务日志的先后顺序。
3. 日志里若先出现主业务成功日志、再出现副作用失败，应优先检查通知、审计、异步/同步副作用写入。

### 验证命令

```bash
# 真实服务器上按时间窗口查看后端日志，定位第一条异常
ssh jetty@172.16.38.78 'journalctl -u xiyu-bid-backend --since "10 minutes ago" | grep -E "Project stage transitioned|SQL Error|created_by|sendNotification failed"'
```

### 相关文档

- `docs/lessons/root-cause-analysis-submit-bid-review-gate.md` — 第一轮 409 根因分析
- `docs/lessons/root-cause-analysis-stage-notification-created-by.md` — 第二轮 500 根因分析

---

## 11. PR 已合入后追加修复，要先确认 merge-base 再判断是更新旧 PR 还是开新 PR

### 问题背景

`submit-bid` 第一轮修复已通过 PR `!923` 合入 `origin/main`。随后针对服务器日志暴露的通知 `created_by` 500 问题继续在原任务分支提交修复。推送前查看提交图发现首个修复提交已在 `origin/main`，当前分支相对 `origin/main` 只剩后续通知修复提交。按统一脚本 `scripts/pr-create.sh` 创建 PR 时，系统创建了新的 PR `!925`，而不是更新已合入的 `!923`。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 以为仍在更新旧 PR | 旧 PR 可能已经合入，分支上的后续提交相对 main 是新变更 | 收尾前必须查看 `merge-base` 和 `origin/main..HEAD` |
| 只看本地分支名 | 分支名相同不代表 PR 状态相同 | 以提交图和远端 PR 状态为准 |
| PR 创建/更新行为不确定 | 项目统一脚本会按当前远端状态处理 Gitee PR | 不手动网页操作，使用 `scripts/pr-create.sh` 并如实记录结果 |

### 操作规范

1. 追加修复前先执行 `git fetch origin`，确认 `origin/main` 最新。
2. 推送/建 PR 前执行 `git log --oneline origin/main..HEAD`，确认本次 PR 实际包含哪些提交。
3. 如果旧 PR 已合入，后续修复应作为新 PR 说明上下文，不强行改写已合入历史。
4. PR 操作使用项目统一脚本 `scripts/pr-create.sh`，不要手工网页创建或更新。

### 验证命令

```bash
# 确认当前分支相对 main 的真实差异
git fetch origin
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD

# 查看提交图，判断旧提交是否已合入 main
git log --graph --oneline --decorate --boundary --max-count=25 --all
```

### 相关文档

- `docs/lessons/root-cause-analysis-stage-notification-created-by.md` — 后续修复的技术根因
- `scripts/pr-create.sh` — 项目统一 PR 创建脚本

---

## 12. 业务异常消息应包含系统上下文（CO-301）

### 问题背景

"标讯已存在" —— 这条错误提示在代码中三处出现（`TenderDuplicateException`、`TenderIntegrationCommandService` 中的两处 `IllegalArgumentException` 和 `PushResult.message`），服务于两个完全不同的入口：手动创建标讯和外部系统推送标讯。

| 入口 | 异常类 / 返回结构 | 原 message | 问题 |
|------|--------|-----------|------|
| POST /api/tenders（手动创建） | `TenderDuplicateException` | "标讯已存在" | 用户不知道"被谁拒绝" |
| POST /api/integration/tenders/push（外部推送） | `IllegalArgumentException` | "标讯已存在" | 集成方无法判断是投标管理系统的去重还是其他系统拒绝 |
| 同上（返回状态） | `PushResult.DUPLICATE` | "标讯已存在" | 同步回调中缺少系统标识 |

用户和测试人员看到这三个字时，会产生困惑：
1. **谁**拦截了操作？
2. **哪个系统**判定了重复？
3. 是本地数据库去重，还是外部接口返回？

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 错误消息没有"谁"的信息 | 用户需要在无日志的情况下判断问题来源 | 业务异常 message 必须包含系统/子系统前缀 |
| 多个入口共用同一条文案 | 不同入口的相同业务概念应有可区分的消息风格 | 同一业务概念在不同入口使用一致的前缀 + 差异化细节 |
| 代码中的 message 被当作"常量字符串"而非"UI 内容"审查 | Code Review 只检查异常类型和 HTTP 状态，不检查 message 可读性 | Review checklist 必须包含"错误消息是否自解释" |
| 测试断言模糊匹配 `contains("标讯已存在")` | 宽松的断言让错误消息可以被默默退化而不被发现 | 测试断言应精确匹配完整 message，或至少匹配带系统前缀的片段 |

### 正确做法

```java
// ✅ 包含系统前缀，用户无需查日志就能判断来源
throw new TenderDuplicateException("投标管理系统该标讯已存在");

// ✅ 多个入口统一风格
throw new IllegalArgumentException("投标管理系统该标讯已存在");

// ✅ 响应中也要带系统标识
return PushResult.builder()
    .status(DUPLICATE)
    .message("投标管理系统该标讯已存在")
    .build();
```

```java
// ✅ 测试断言精确匹配（不做宽松 contains）
assertThat(e.getMessage()).isEqualTo("投标管理系统该标讯已存在");
// 或
assertThat(e.getMessage()).contains("投标管理系统该标讯已存在");
```

### 操作规范（建议固化到 CLAUDE.md / RULES.md）

1. **新业务异常必须提供系统上下文**：`super("投标管理系统XXX")`，不要 `super("XXX")`
2. **异常 message 作为 UI 内容审查**：Code Review 时，对抛出的异常字符串做等同于 UI 文案的审查
3. **多入口消息一致性**：同一业务概念在不同入口（手动创建/外部推送/回调）的错误消息应使用一致的系统前缀
4. **测试断言与 message 强绑定**：不要只测异常类型，要测 message 包含预期内容；message 退化时测试应该红掉

### 验证命令

```bash
# 检查业务异常类中的 message 是否缺少系统上下文
grep -rn 'super(".*已存在")' backend/src/main/java
# 检查集成/推送路径中的硬编码错误消息
grep -rn 'throw new IllegalArgumentException("标讯' backend/src/main/java

# 验证修复后的测试是否精确匹配新消息
mvn test -Dtest=TenderDeduplicationServiceTest,TenderCommandServiceTest,TenderIntegrationServicePushEvaluationTest,GlobalExceptionHandlerTest
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-301.md` — CO-301 完整根因分析
- `docs/lessons/lessons-learned.md` §1 — 同一问题的扩展：接口契约变更同步前端所有入口

---

## 13. 服务器部署 jar 验证四原则（CO-301 部署经验）

> 来源：CO-301 部署排查（2026-06-22）

### 问题背景

代码已合入 main 并打包 jar 部署到服务器，但运行时仍返回旧文案。排查发现：
1. 打包时 Maven 缓存了旧 class 文件，jar 中实际内容未更新
2. 仅检查 jar 大小无法发现内容差异
3. SSH 终端中文编码导致 javap 输出乱码，误判为旧版本
4. 最终通过 `jar uf` 局部更新 class 文件解决

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 打包后只检查 jar 大小 | jar 大小相近时无法区分新旧版本 | **打包后必须验证 jar 内容**：用 `javap -v` 或 `unzip -p ... \| strings` 检查关键 class 文件的常量池 |
| Maven 缓存旧 class | `mvn package` 不一定触发重新编译 | **`mvn clean` 后重新打包**：确保使用最新编译结果，不要依赖增量编译 |
| SSH 终端中文乱码 | `javap` 通过 SSH 显示中文常量为 `???` | **用 `xxd` 或字节比较验证**：不依赖终端中文显示，用 `xxd \| grep` 或 `diff <(xxd) <(xxd)` 比较字节 |
| 重新打包整个 jar 耗时长 | 全量打包 + 上传 + 重启耗时大 | **用 `jar uf` 局部更新**：只更新修改的 class 文件，无需重新打包整个 jar |

### 正确做法

```bash
# 1. 打包前先 clean
cd backend && mvn clean compile spring-boot:repackage -DskipTests

# 2. 验证 jar 中关键 class 的常量池
unzip -p target/bid-poc-1.0.3.jar BOOT-INF/classes/com/xiyu/bid/exception/TenderDuplicateException.class \
  | javap -v - 2>/dev/null | grep -A5 "Constant pool"
# 应显示: #1 = String #2 // 投标管理系统该标讯已存在

# 3. 服务器上验证（用 xxd 避免中文乱码）
ssh jetty@server 'unzip -p /opt/xiyu-bid/shared/backend/app.jar \
  BOOT-INF/classes/com/xiyu/bid/exception/TenderDuplicateException.class \
  | xxd | grep -A2 "e68a 95"'  # "投" 的 UTF-8 字节

# 4. 局部更新 jar（无需重新打包）
jar uf app.jar \
  BOOT-INF/classes/com/xiyu/bid/exception/TenderDuplicateException.class \
  BOOT-INF/classes/com/xiyu/bid/integration/external/TenderIntegrationCommandService.class

# 5. 字节级比较本地与服务器 jar 中的 class
diff <(unzip -p local.jar BOOT-INF/classes/.../Foo.class | xxd) \
     <(ssh server 'unzip -p remote.jar BOOT-INF/classes/.../Foo.class | xxd')
```

### 防复发检查清单

- [ ] `mvn clean` 后重新打包，不依赖增量编译
- [ ] 打包后用 `javap -v` 验证关键 class 常量池内容
- [ ] 服务器验证用 `xxd` 字节比较，不依赖 SSH 中文显示
- [ ] 如需快速更新，用 `jar uf` 局部替换 class 文件
- [ ] 部署后通过 API 实测验证功能（而非仅检查 actuator 状态）

### 相关文档

- `docs/lessons/root-cause-analysis-co-301.md` — CO-301 完整根因分析
- `CLAUDE.md §环境坑点` — 后端启动与环境变量

---

## 14. @RequestScope Bean 与第三方 SDK 的 CacheBeanComponent.initCacheBean() 冲突导致应用启动失败

### 问题背景

2026-06-25 部署 `ffc1d09f-api8080` 时，后端服务启动失败。`systemctl restart` 后健康检查始终无法通过，Nginx 返回 502。最终回滚到上一稳定版本 `51b1c88c-api8080`。

错误日志：

```
ScopeNotActiveException: Error creating bean with name 'scopedTarget.currentUserResolver':
Scope 'request' is not active for the current thread; consider defining a scoped proxy
for this bean if you intend to refer to it from a singleton

Caused by: java.lang.IllegalStateException: No thread-bound request found
  at org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes
  at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean
  at org.springframework.beans.factory.support.DefaultListableBeanFactory.getBeansOfType
  at com.ehsy.eventlibrary.clientsdk.service.component.CacheBeanComponent.initCacheBean
  at com.ehsy.eventlibrary.clientsdk.service.callback.StartCallback.onApplicationEvent
```

### 根因分析

**直接原因**：`CurrentUserResolver` 使用了 `@RequestScope` 注解，在 HTTP 请求线程外无法实例化。

**触发链路**：
1. 组织事件 SDK 的 `StartCallback` 监听 `ApplicationReadyEvent`
2. `StartCallback.onApplicationEvent()` 调用 `CacheBeanComponent.initCacheBean()`
3. `initCacheBean()` 内部调用 `applicationContext.getBeansOfType()` 扫描所有 bean
4. Spring 尝试实例化 `@RequestScope` 的 `CurrentUserResolver`，但此时没有 request 上下文
5. 抛出 `ScopeNotActiveException`，应用启动失败

**引入时间**：commit `1404387b1` 新增 `CurrentUserResolver` 并标注 `@RequestScope`，之前服务器上 `XIYU_ORG_EVENT_SDK_ENABLED=true` 的环境中从未测试过该代码路径。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| `@RequestScope` Bean 在非 HTTP 线程中被扫描 | 第三方 SDK 可能在启动时通过 `getBeansOfType()` 扫描所有 bean | **生产环境启用 SDK 时必须测试完整启动流程**，不能只在本地 dev 环境验证 |
| `CacheBeanComponent.initCacheBean()` 不区分 bean scope | SDK 内部实现无法控制，必须从自身代码防御 | **避免使用 `@RequestScope`**，改用单例 + 直接查询或 ThreadLocal |
| 本地 dev 环境 `XIYU_ORG_EVENT_SDK_ENABLED=false` | SDK 功能被关闭时不会触发此问题 | **部署前必须确认服务器环境变量与本地测试环境一致**，特别是功能开关 |
| 只看代码无法发现启动时 scope 冲突 | 代码审查不覆盖"启动时 bean 扫描顺序" | **新增 `@RequestScope`/`@SessionScope` Bean 时，必须在 CI 中增加生产 profile 启动测试** |

### 修复方案

将 `CurrentUserResolver` 从 `@RequestScope` 改为普通单例 `@Component`：

```java
// 修复前：@RequestScope 在非 HTTP 线程中无法实例化
@Component
@RequestScope
@RequiredArgsConstructor
public class CurrentUserResolver {
    private User cachedUser;       // 请求级缓存由 Spring scope 管理
    private boolean resolved;
    // ...
}

// 修复后：单例，每次调用直接查询数据库
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {
    private final UserRepository userRepository;
    // 无缓存，避免 ThreadLocal 泄漏风险
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}
```

**为什么不使用 ThreadLocal 缓存**：Tomcat 线程池会复用线程，如果不在请求结束时清理 ThreadLocal，下一个请求可能拿到上一个用户的缓存数据，造成安全隐患。添加 `Filter` 来清理 ThreadLocal 又增加了复杂度和循环依赖风险。用户查询是走索引的简单查询，性能影响可忽略。

### 防复发检查清单

- [ ] 新增 Bean 时检查是否使用了 `@RequestScope` / `@SessionScope` 等非 singleton scope
- [ ] 如果必须使用非 singleton scope，评估是否与 SDK 的 `getBeansOfType()` 扫描冲突
- [ ] 本地测试时启用生产环境的功能开关（如 `XIYU_ORG_EVENT_SDK_ENABLED=true`）
- [ ] 部署失败时第一时间检查 `journalctl` 中的 `ScopeNotActiveException` 关键字

### 验证命令

```bash
# 检查项目中是否有 @RequestScope Bean
grep -rn "@RequestScope\|@Scope.*request" backend/src/main/java

# 本地模拟生产环境启动（启用事件 SDK）
JWT_SECRET="test" DB_PASSWORD="XiyuDB!2026" \
XIYU_ORG_EVENT_SDK_ENABLED=true \
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 服务器日志检查
ssh jetty@172.16.38.78 'sudo journalctl -u xiyu-bid-backend --since "10 min ago" | grep -i "ScopeNotActive\|request.*scope"'
```

### 相关文档

- `backend/src/main/java/com/xiyu/bid/security/CurrentUserResolver.java` — 修复后的单例实现
- `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkKafkaStarter.java` — 自定义 SDK 启动器
- `docs/lessons/lessons-learned.md` §13 — 服务器部署 jar 验证四原则
- `CLAUDE.md §环境坑点` — 后端启动与环境变量

---

## 15. 部署失败后回滚的标准化操作流程

### 问题背景

2026-06-25 部署 `ffc1d09f-api8080` 失败后，需要回滚到上一稳定版本 `51b1c88c-api8080`。回滚操作涉及恢复后端 JAR、前端资源、部署记录和服务重启，但缺少标准化流程，容易遗漏步骤。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 回滚操作分散在不同地方 | 缺少一键回滚脚本 | 每次部署前确认回滚锚点（上一版本 release 目录、部署记录、数据库备份） |
| 回滚后忘记更新部署记录 | `/opt/xiyu-bid/deployed-release.json` 未更新 | 回滚必须更新部署记录，添加 `rolledBackFrom` 字段 |
| 不确认回滚后服务是否真正恢复 | 只检查了服务 active 但未验证健康检查 | 回滚后必须验证健康检查 + API 可用性 |

### 正确做法

```bash
# 1. 确认回滚目标版本
PREV_RELEASE="51b1c88c-api8080"
FAILED_RELEASE="ffc1d09f-api8080"

# 2. 恢复后端 JAR
cp /opt/xiyu-bid/releases/${PREV_RELEASE}/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar

# 3. 恢复前端资源
rm -rf /srv/www/xiyu-bid
cp -a /opt/xiyu-bid/releases/${PREV_RELEASE}/frontend /srv/www/xiyu-bid

# 4. 更新部署记录（含回滚来源）
cat > /opt/xiyu-bid/deployed-release.json <<EOF
{
  "releaseId": "${PREV_RELEASE}",
  "rolledBackFrom": "${FAILED_RELEASE}",
  "rolledBackAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "releaseDir": "/opt/xiyu-bid/releases/${PREV_RELEASE}",
  "frontendPublicDir": "/srv/www/xiyu-bid",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "backendServiceName": "xiyu-bid-backend",
  "healthcheckUrl": "http://127.0.0.1:8080/actuator/health"
}
EOF

# 5. 重启服务
sudo systemctl restart xiyu-bid-backend

# 6. 等待并验证健康检查
for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q UP; then
    echo "Rollback successful"
    break
  fi
  sleep 2
done

# 7. 验证 API 可用性
curl -fsS http://127.0.0.1:8080/actuator/health
```

### 防复发检查清单

- [ ] 部署前确认上一版本的 release 目录存在：`ls /opt/xiyu-bid/releases/<prev-version>`
- [ ] 部署前确认数据库备份已完成：`ls -lh /opt/xiyu-bid/db-backups/`
- [ ] 回滚后更新 `deployed-release.json`，含 `rolledBackFrom` 字段
- [ ] 回滚后验证健康检查 + API 可用性，不只是 `systemctl is-active`

### 相关文档

- `docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` §12 — 回滚流程
- `docs/release/ROLLBACK.md` — 回滚手册

---

## 16. 前端同源部署模式：VITE_API_BASE_URL 设为空字符串

### 问题背景

2026-06-25 部署时，前端打包使用的 `VITE_API_BASE_URL` 值影响了前端与后端的通信模式。之前部署曾出现前端包指向 `127.0.0.1:18080`（本地开发地址），导致 CORS 错误。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 前端包中 API 地址指向 `127.0.0.1:18080` | 本地开发默认值不应进入生产包 | 前后端同源部署时，`VITE_API_BASE_URL` 必须设为空字符串 |
| 不理解同源模式 vs 跨域模式的区别 | 同源模式下前端走相对路径 `/api/`，Nginx 代理到后端 | 明确区分两种部署模式，按模式设置打包参数 |
| `package-release.sh` 的 fallback 逻辑容易踩坑 | 未设 `VITE_API_BASE_URL` 时 fallback 到 `127.0.0.1:18080` | 用 `${VITE_API_BASE_URL+x}` 区分"未设"与"显式设为空" |

### 两种部署模式

| 模式 | `VITE_API_BASE_URL` | 适用场景 | 前端请求路径 |
|------|------|------|------|
| **同源**（推荐） | `""`（空字符串） | 前后端同一 Nginx 入口 | `/api/...` → Nginx 代理到 18080 |
| **跨域** | `http://172.16.38.78:8080` | 前后端分离部署 | 完整 URL → 直接请求后端 |

### 正确做法

```bash
# 同源部署（推荐）：VITE_API_BASE_URL 设为空
export VITE_API_BASE_URL=""
bash scripts/release/package-release.sh

# 跨域部署：VITE_API_BASE_URL 设为后端公网地址
export VITE_API_BASE_URL="http://172.16.38.78:8080"
bash scripts/release/package-release.sh

# 验证前端包中的 API 地址
# 同源模式：不应包含 127.0.0.1 或具体 IP
rg "127\.0\.0\.1|172\.16\.38\.78" .release/*/frontend/assets/*.js
# 期望输出：无匹配
```

### 相关文档

- `scripts/release/package-release.sh` — 打包脚本，含 `VITE_API_BASE_URL` 解析逻辑
- `src/api/config.js` — 前端 API 配置，生产环境强制同源模式
- `docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` §4 — 本地打包规范

---

## 17. Bug 修复前必须先验证实际行为，避免"推测式修复"

### 问题背景

CO-285 附件下载文件名显示为 "download"，一个看似简单的问题花了 3 轮 PR 才真正修复：
- PR #926：修复 Content-Disposition 头编码（无效）
- PR #929：修复 CORS 配置暴露响应头（无效）
- PR #931：修改前端下载方式为 fetch+blob（有效）

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 只看代码推测问题，不验证实际行为 | 修复前必须用浏览器开发者工具验证实际的请求/响应 | Bug 修复前先复现，用 F12 Network 查看实际响应头 |
| 第一次修复无效后继续在同一方向深入 | 修复无效时立即调整方向，而不是继续修复 | 修复无效时回到"问题是什么"重新分析 |
| 忽略浏览器行为差异 | `<a>` 标签导航和 fetch 请求的下载行为不同 | 涉及文件下载时明确下载方式并验证其行为 |
| 重复造轮子 | 项目已有 4 处类似下载函数，又新增 1 处 | 新增工具函数前先 grep 搜索项目中是否已有 |

### 操作规范

1. **Bug 修复前必须先复现**：用浏览器开发者工具（F12 → Network）查看实际的请求和响应，而不是只看代码推测。
2. **修复无效时立即调整方向**：如果第一次修复无效，不要继续在同一个方向上深入，而是回到问题本身重新分析。
3. **明确下载方式**：涉及文件下载时，必须明确是 `<a>` 标签导航、`window.open`、还是 `fetch+blob`，并验证其行为。
4. **新增工具函数前先搜索**：使用 `grep` 搜索项目中是否已有类似实现，避免重复造轮子。

### 验证命令

```bash
# 检查项目中是否还有其他重复的下载工具函数
grep -r "function.*download.*blob\|triggerBlobDownload\|downloadBlob" src/

# 检查 CORS 配置是否正确暴露了 Content-Disposition 头
curl -s -D- -o /dev/null -X OPTIONS "http://172.16.38.78:8080/api/doc-insight/download" \
  -H "Origin: http://172.16.38.78:8080" \
  -H "Access-Control-Request-Method: GET" | grep -i "access-control-expose"
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-285.md` — 完整根因分析
- `src/utils/download.js` — 提取的公共下载工具函数

---

## 18. 部署前必须验证 jar 中 Flyway 迁移脚本无重复版本

### 问题背景

2026-06-25 部署 `b122e9f4-api8080` 时，后端启动失败，Flyway 检测到 V1096 版本重复。源码目录只有一个 V1096，但 `target/` 目录残留了旧的迁移文件，被一起打进 jar 包，导致 Flyway 启动时发现两个同版本脚本，直接抛异常退出。

### 事故时间线

| 时间 (CST) | 事件 | 影响 |
|------------|------|------|
| 22:30 | 打包发布包（未 clean，直接 package） | target 残留旧迁移文件 |
| 22:40 | 上传发布包到服务器 | 包内已包含重复迁移 |
| 22:45 | 执行 remote-deploy.sh 激活发布 | 后端启动失败 |
| 22:48 | Nginx 返回 502，服务不可用 | 用户无法访问 |
| 22:50 | 回滚到上一稳定版本 `03811f07-api8080` | 服务恢复 |
| 22:53 | 清理 target 后重新 clean package | 生成干净的 jar |
| 22:55 | 重新部署成功 | 服务完全恢复 |

### 根因分析

**直接原因**：`mvn package` 增量构建不会删除 target 中已存在的资源文件。旧的迁移文件（已被重命名或删除）残留在 `target/classes/db/migration-mysql/`，被一起打进 jar 包。

**深层原因**：

1. **发布打包缺少 clean 步骤**：`package-release.sh` 或手动打包时省略了 `clean`，直接 `package`
2. **部署前缺少迁移版本校验**：没有检查 jar 中是否有重复的 Flyway 版本
3. **回滚流程虽然可用，但仍有约 10 分钟服务中断**：从发现问题到回滚完成需要时间

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| `mvn package` 不清理 target 旧文件 | 增量构建不适合发布场景 | **发布打包必须 `mvn clean package`**，不能省略 clean |
| 部署后才发现迁移冲突 | 应该在部署前、甚至打包后立即发现 | **打包后必须校验 Flyway 迁移版本无重复**，作为发布门禁 |
| 只检查源码目录 | 源码干净 ≠ 产物干净 | 以 jar 包实际内容为准，不以源码目录为准 |
| 回滚导致服务中断 | 应该在部署前拦截，而不是部署后回滚 | 把 Flyway 版本校验加入 pre-deploy checklist |

### 发布前检查清单（Pre-deploy Checklist）

每次部署前，必须完成以下检查：

```
□ 1. 使用 mvn clean package 打包（不是 mvn package）
□ 2. 验证 jar 中 Flyway 迁移版本无重复
□ 3. 验证 jar 大小和关键 class 文件符合预期
□ 4. 数据库备份已完成
□ 5. 上一版本的 release 目录存在（回滚锚点）
```

### 验证命令

```bash
# 1. 检查 jar 中 Flyway 迁移版本是否有重复（核心检查）
jar tf target/bid-poc-1.0.3.jar \
  | grep "migration-mysql/V" \
  | sed 's|BOOT-INF/classes/db/migration-mysql/V||' \
  | sed 's|__.*||' \
  | sort \
  | uniq -d
# 期望输出：空（无重复版本）

# 2. 快速查看 V109x 等近期迁移是否正常
jar tf target/bid-poc-1.0.3.jar | grep "migration-mysql/V109" | sort

# 3. 检查迁移脚本总数（与源码目录对比）
echo "源码目录迁移脚本数:"
ls backend/src/main/resources/db/migration-mysql/ | wc -l
echo "jar 包内迁移脚本数:"
jar tf target/bid-poc-1.0.3.jar | grep "migration-mysql/V" | wc -l
# 两者应该相等（或 jar 包中略多，因为包含 B 基线版本）

# 4. 服务器上验证（部署前可在 incoming 目录先检查）
ssh jetty@172.16.38.78 'jar tf /opt/xiyu-bid/incoming/app.jar | grep "migration-mysql/V" | sort | tail -20'
```

### 建议固化到打包脚本

在 `scripts/release/package-release.sh` 中增加 Flyway 版本校验步骤：

```bash
# 打包后校验 Flyway 迁移无重复版本
duplicate_versions=$(jar tf "$BACKEND_JAR" \
  | grep "migration-mysql/V" \
  | sed 's|BOOT-INF/classes/db/migration-mysql/V||' \
  | sed 's|__.*||' \
  | sort \
  | uniq -d)

if [ -n "$duplicate_versions" ]; then
  echo "❌ ERROR: Found duplicate Flyway migration versions:"
  echo "$duplicate_versions"
  exit 1
fi
echo "✅ Flyway migration versions: no duplicates"
```

### 相关文档

- `docs/lessons/build-gotchas.md` §3 — Maven target 目录残留旧 Flyway 迁移文件陷阱
- `docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` — 部署运行手册
- `scripts/release/package-release.sh` — 发布打包脚本
- `backend/src/main/resources/db/migration-mysql/` — Flyway 迁移脚本目录

---

## 19. 简单 bug 多轮修不对：先定位"空值从哪来"，再改格式化逻辑

### 问题背景

PR #1162 修复"EVALUATED webhook 回调缺少操作人姓名（工号）"，但用户反馈修复后格式不对——只有姓名没有工号。随后又反馈只有工号没有姓名。一个看似简单的字符串格式化 bug，改了 3 轮：

| 轮次 | 修了什么 | 结果 | 为什么失败 |
|------|---------|------|-----------|
| 第 1 轮 (PR #1174) | `TenderSubmissionService.participateBid` 中 `User::getFullName` → `OperatorDisplayName.format()` | 没有解决用户反馈的问题 | 修错了调用方，问题在 `OperatorDisplayName` 本身的 fallback 逻辑 |
| 第 2 轮 (PR #1176) | `OperatorDisplayName.format()` 中 `fullName` 为空时 fallback 到 `username` | 正确修复 | 找到了真正的根因 |

**真正的根因**：`OperatorDisplayName.format()` 第 36-38 行，当 `user.getFullName()` 为空时直接返回工号，没有 fallback 到 `username` 作为姓名。API Key 对应的用户可能没有设置 `fullName` 字段，导致回调中只有工号没有姓名。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 用户说"格式不对"，没问清是哪种不对就直接改 | 先确认具体现象：是"有姓名无工号"还是"有工号无姓名"？方向反了白改 | 收到 bug 反馈时，先确认**实际输出**和**期望输出**的具体差异 |
| 第 1 轮修了 `TenderSubmissionService` 而不是 `OperatorDisplayName` | 修了调用方没修格式化器本身 | 格式化 bug 先看**格式化函数本身**的分支逻辑，不要先看调用方 |
| `OperatorDisplayName.format()` 有 4 个分支但只测了正常路径 | 边界分支（fullName 为空、employeeNumber 为空）缺少测试 | 格式化函数必须有**全分支测试**，特别是空值 fallback 分支 |

### 操作规范

1. **收到"格式不对"反馈时，先确认具体现象**：
   - 问用户：实际输出是什么？期望输出是什么？
   - 不要凭"格式不对"三个字就推测方向

2. **格式化 bug 先看格式化函数本身**：
   ```
   OperatorDisplayName.format() ← 先看这里
     ↓
   调用方（TenderSubmissionService 等） ← 后看这里
   ```
   格式化函数是所有调用方的公共逻辑，bug 大概率在这里。

3. **格式化函数必须有全分支测试**：
   - 正常路径：fullName + employeeNumber 都有
   - fullName 为空 → fallback 到什么？
   - employeeNumber 为空 → fallback 到什么？
   - 两者都为空 → 返回什么？
   每个分支都要有测试用例，不能只测正常路径。

4. **字符串格式化 bug 的标准排查路径**：
   ```
   1. 确认实际输出 vs 期望输出的具体差异
   2. 找到生成该字符串的格式化函数
   3. 逐分支检查：哪个分支产生了实际输出？
   4. 该分支的 fallback 逻辑是否正确？
   5. 修复 + 补全分支测试
   ```

### 验证命令

```bash
# 快速检查格式化函数的全分支覆盖
grep -A 20 "public static String format" backend/src/main/java/com/xiyu/bid/webhook/domain/OperatorDisplayName.java

# 检查测试是否覆盖了空值分支
grep -E "empty|null|blank|fallback" backend/src/test/java/com/xiyu/bid/webhook/domain/OperatorDisplayNameTest.java
```

### 相关文档

- `backend/src/main/java/com/xiyu/bid/webhook/domain/OperatorDisplayName.java` — 格式化函数
- PR #1174 — 第 1 轮修复（修了调用方，没解决根因）
- PR #1176 — 第 2 轮修复（修了格式化函数本身，正确）
- 本节 §17 — 同类教训："Bug 修复前必须先验证实际行为，避免推测式修复"

---

## 20. 分阶段修复的存量数据策略 + agent-finish-task.sh 锚点分支占用处理

### 问题背景

2026-06-26 修复"CRM 商机负责人被自动分配覆盖"问题时，bug 涉及三轮独立根因，分三个 PR（#1163/#1167/#1173/#1179）分阶段部署。每个 PR 部署后到下一 PR 部署前创建的数据，仍按旧逻辑落库，需要单独跑数据修复脚本。

同时在收尾时执行 `agent-finish-task.sh --include-remote --yes`，报错 `fatal: 'agent/mimo-init' is already used by worktree at '/Users/user/xiyu/worktrees/gemini'`——`agent/mimo-init` 锚点分支被 gemini worktree 错误占用（历史遗留问题），脚本 Step 6 切换锚点分支失败。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 分阶段修复部署后，期间新建数据仍按旧逻辑落库 | **每个 PR 部署后必须立即跑数据修复脚本**，覆盖"上一 PR 部署后到本 PR 部署前"的时间窗口 | 分阶段修复的每个 PR 都要在 PR 描述中列出"本 PR 部署后需跑的存量数据修复 SQL" |
| 存量数据修复 SQL 直接在服务器执行 | **存量数据修复脚本不进 PR**，直接在服务器 DB 执行；PR 只负责修复代码逻辑 | 在 PR 描述中单独列出"存量数据修复"章节，附 SQL 脚本，部署后在服务器执行 |
| agent-finish-task.sh 锚点分支被其他 worktree 占用 | **脚本切换锚点失败时 fallback 到 detached HEAD**，手动删除任务分支即可 | 遇到 `is already used by worktree` 报错时：`git checkout origin/main && git branch -D <任务分支>` |
| 锚点分支被其他 worktree 错误占用是历史遗留问题 | **不在收尾任务中擅自处理其他 worktree 的问题**，单独开任务协调 | 发现 `agent/{name}-init` 被错误占用时，记录到 issues 但不擅自 checkout/branch -D |

### 操作规范

1. **分阶段修复的存量数据策略**：
   - PR 部署后立即在服务器跑数据修复 SQL（覆盖"上一 PR 部署后到本 PR 部署前"窗口）
   - 修复 SQL 不进 PR（PR 只改代码逻辑），但要在 PR 描述中列出
   - 验证修复效果：DB 直查 `project_manager_id` 是否为正确的 User.id
   - 同时验证"新建数据"是否按新逻辑落库（防止"修了代码但部署不到位"）

2. **agent-finish-task.sh 锚点分支占用的 fallback**：
   ```bash
   # 报错：fatal: 'agent/mimo-init' is already used by worktree at '...'
   # Fallback：
   git checkout origin/main
   git branch -D agent/<agent-name>/<task-name>
   git push origin --delete agent/<agent-name>/<task-name>  # 远端分支（如还存在）
   git fetch --prune origin
   ```
   不要试图 `git worktree remove` 其他 worktree，那是其他 agent 的工作区。

3. **锚点分支被错误占用的根因排查**（单独开任务）：
   ```bash
   # 查看哪个 worktree 占用了哪个锚点分支
   git worktree list
   # 正确状态：每个 worktree 用自己的 agent/<name>-init 锚点
   # 错误状态：gemini worktree 占用了 agent/mimo-init
   # 修复方式：在占用的 worktree 内切回自己的锚点分支
   ```

### 验证命令

```bash
# 检查锚点分支占用情况
git worktree list | grep -E "init"

# 期望输出：每个 worktree 用自己的 init 分支
# /Users/user/xiyu/worktrees/mimo  xxx [agent/mimo-init]
# /Users/user/xiyu/worktrees/gemini xxx [agent/gemini-init]
# ...

# 异常输出：worktree 名与 init 分支名不匹配
# /Users/user/xiyu/worktrees/gemini xxx [agent/mimo-init]  ← 错误
```

### 相关文档

- `docs/lessons/root-cause-analysis-crm-leader-priority.md` — 完整根因分析
- `scripts/agent-finish-task.sh` — 收尾脚本（Step 6 切换锚点分支）
- PR #1179 — 本次修复的 PR


---

## 21. @EventListener(ApplicationReadyEvent) 阻塞主线程导致 readiness 延迟恢复 UP

### 问题背景

2026-06-27 第 8 次部署（`3bb444139`）后端重启后，`/actuator/health/readiness` 持续返回 **503 OUT_OF_SERVICE** 约 4 分钟（18:20 → 18:24），nginx 代理层报 502/503，前端无法访问 API。后端进程存活（liveness UP），所有基础设施健康检查 UP（db/redis/jwt/diskSpace），但 `readinessState` 迟迟不切换到 `ACCEPTING_TRAFFIC`。

排查期间曾临时添加 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 到 `/etc/xiyu-bid/backend.env` 获取详情，确认所有组件 UP，唯独 `readinessState` OUT_OF_SERVICE。

### 事故时间线

| 时间 (CST) | 事件 | 影响 |
|------------|------|------|
| 18:17:41 | 后端进程启动（PID 5151） | 启动中 |
| 18:20:16 | `/actuator/health/readiness` 返回 503 | readinessState 未切换 |
| 18:22:03.419 | `OrganizationEventSdkKafkaStarter.onApplicationReady()` 开始执行 | 说明 `ApplicationReadyEvent` 已发布 |
| 18:22:03.617 | Kafka consumer 启动成功（仅用 200ms） | SDK 初始化完成 |
| 18:24:08 | `/actuator/health/readiness` 恢复 200 UP | readinessState 切换到 ACCEPTING_TRAFFIC |

### 根因分析

**直接原因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class)` 监听 `ApplicationReadyEvent`，在主线程同步执行 SDK 初始化（`register()` + `initCacheBean()` + `KafkaProcessor.start()`）。Spring Boot 的 `ApplicationAvailabilityBean` 也通过 `@EventListener` 接收 `ApplicationReadyEvent` 来切换 `ReadinessState` 从 `REFUSING_TRAFFIC` 到 `ACCEPTING_TRAFFIC`。两者都在主线程同步执行，存在时序竞争。

**触发链路**：
1. Spring Boot 发布 `ApplicationReadyEvent`
2. `OrganizationEventSdkKafkaStarter.onApplicationReady()` 在主线程开始执行（`@Order(Ordered.LOWEST_PRECEDENCE)`）
3. 如果 `register()` / `initCacheBean()` / `KafkaProcessor.start()` 中任一步骤阻塞（如网络超时、Kafka broker 不可达），主线程被占用
4. `ApplicationAvailabilityBean` 的 `@EventListener` 处理被延迟（即使其 order 优先级更高，但同步事件按顺序处理）
5. `AvailabilityChangeEvent` 发布延迟 → `ReadinessState` 切换延迟 → readiness 持续 503

**历史对照**：前一次启动（排查阶段）Kafka starter 阻塞了 2.5 分钟（18:10:48 → 18:13:23），导致 readiness 长时间 OUT_OF_SERVICE。本次启动 Kafka broker 已可达，SDK 初始化仅 200ms 完成，readiness 在 2 分钟内恢复。

**关键矛盾点**：`@EventListener` 是同步的，即使 `OrganizationEventSdkKafkaStarter` 标注了 `@Order(Ordered.LOWEST_PRECEDENCE)`，它仍然在主线程执行。Spring Boot 的 `ApplicationAvailabilityBean` 的 order 是 `Ordered.LOWEST_PRECEDENCE - 1`（优先级更高），但同步事件的执行顺序是按 order 顺序处理，不是抢占式。如果 Kafka starter 在 `ApplicationAvailabilityBean` 之前执行，不会影响；但如果在之后执行，会阻塞后续事件。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| `@EventListener(ApplicationReadyEvent)` 在主线程同步执行 | **`@EventListener` 默认同步**，长时间任务会阻塞主线程 | 启动期耗时操作（SDK 初始化、网络调用）必须用 `@Async` 或独立线程池 |
| readiness 持续 OUT_OF_SERVICE 但 liveness UP | **readiness 和 liveness 是独立状态机**，readiness 由 `ApplicationReadyEvent` 触发切换 | 排查 readiness 问题时，检查 `ApplicationReadyEvent` 是否被延迟发布 |
| Kafka SDK 初始化阻塞 2.5 分钟 | **第三方 SDK 的网络调用不可控**，必须假设会超时 | 包装第三方 SDK 启动逻辑时，加超时 + 异步执行 |
| 临时修改生产配置排查问题 | **排查完必须清理临时配置** | 修改 `/etc/xiyu-bid/backend.env` 后记录到部署报告，部署完恢复 |
| `@Order(Ordered.LOWEST_PRECEDENCE)` 不能解决阻塞 | **`@Order` 只决定顺序，不改变同步性** | 需要异步用 `@Async`，不是 `@Order` |

### 正确做法

```java
// 修复前：同步执行，阻塞主线程
@Component
@ConditionalOnClass(name = "com.ehsy.eventlibrary.clientsdk.common.anno.AcceptEvent")
@ConditionalOnProperty(prefix = "xiyu.integrations.organization.event-sdk", name = "enabled", havingValue = "true")
public class OrganizationEventSdkKafkaStarter {

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onApplicationReady() {
        // 同步执行，阻塞主线程 → readiness 延迟切换
        registerComponent.register();
        cacheBean.initCacheBean();
        kafkaProcessor.start();
    }
}

// 修复后：异步执行，不阻塞主线程
@Component
@ConditionalOnClass(name = "com.ehsy.eventlibrary.clientsdk.common.anno.AcceptEvent")
@ConditionalOnProperty(prefix = "xiyu.integrations.organization.event-sdk", name = "enabled", havingValue = "true")
public class OrganizationEventSdkKafkaStarter {

    @Autowired
    private TaskExecutor taskExecutor;  // 或自定义线程池

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // 异步执行，不阻塞 ApplicationReadyEvent 发布链
        taskExecutor.execute(() -> {
            registerComponent.register();
            cacheBean.initCacheBean();
            kafkaProcessor.start();
        });
    }
}
```

### 排查命令

```bash
# 1. 检查 readiness 状态（show-details 临时开启）
curl -s http://127.0.0.1:8080/actuator/health/readiness | python3 -m json.tool

# 2. 检查启动日志中 ApplicationReadyEvent 相关事件
sudo journalctl -u xiyu-bid-backend --since "10 minutes ago" --no-pager | grep -E "ApplicationReadyEvent|AvailabilityChangeEvent|readiness|org-event-sdk-kafka"

# 3. 确认是否是 @EventListener 阻塞
# 如果日志显示 onApplicationReady() 执行时间 > 10秒，且 readiness 在其后恢复 UP，则确认是阻塞问题
sudo journalctl -u xiyu-bid-backend --since "10 minutes ago" --no-pager | grep "org-event-sdk-kafka"

# 4. 临时开启 show-details 排查（排查完必须恢复）
# 编辑 /etc/xiyu-bid/backend.env，添加：
# MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always
# sudo systemctl restart xiyu-bid-backend
# 排查完后删除该行，重启恢复
```

### 防范措施

1. **代码审查**：新增 `@EventListener(ApplicationReadyEvent.class)` 时，必须确认执行时长 < 1 秒，否则用 `@Async`
2. **启动日志监控**：`OrganizationEventSdkKafkaStarter` 的 bootstrap 日志如果 > 5 秒，告警
3. **部署后健康检查**：部署脚本必须等待 readiness UP（不是 liveness UP），超时则告警
4. **第三方 SDK 包装**：第三方 SDK 的启动初始化必须包装在独立线程池中，不阻塞主线程

### 相关文档

- `docs/release/deploy-report-2026-06-27-8th.md` — 第 8 次部署报告（本次经验来源）
- `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkKafkaStarter.java` — 问题代码
- `backend/src/main/resources/application-prod.yml` — readiness 组配置（`include: readinessState,db`）
- Spring Boot 文档：[Application Availability](https://docs.spring.io/spring-boot/docs/3.2.0/reference/htmlsingle/#features.spring-application.application-availability)

---

## 22. 外部诊断根因必须复核 + baseline-on-migrate 静默跳过 + UI 数据源互斥

> 日期: 2026-06-28
> 来源: CO-361 看板空白（PR #1270）/ 交付物重复渲染（PR #1271）
> 排查者: zcode agent

### 问题背景

本次会话连续修了两个独立 bug，共同暴露了三个工程盲区：

**Bug A — Dev 环境任务看板空白**：dev profile 启动后看板无列无卡片。根因是 `V101__task_status_dict.sql` 因版本号 101 低于 Flyway `baseline-version: 1050`（`baseline-on-migrate: true` 模式下低于 baseline 的迁移静默跳过），从未在 dev/prod 执行；表结构由 JPA ddl-auto 建出但无种子数据；唯一 seed 来源 `E2eDemoDataInitializer.seedTaskStatuses()` 又被 `@Profile("e2e")` 锁死，dev 不跑 → `task_status_dict` 永远空 → `/api/task-status-dict` 返回 `[]` → `columns=[]` → 看板空白。

**Bug B — 交付物重复渲染**：只读模式下同一份已保存交付物同时出现在 `el-upload` 禁用条目（不可下载）和 `.deliverable-list` 可下载链接里。根因是 `useTaskDeliveryForm.rebuildFileList()` 把已保存 `deliverables` 也塞进了 `el-upload` 的 `file-list`，导致同一数据被两个 UI 容器同时消费。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 外部诊断报告称根因为"V1223 三态收口"，但 V1223 不存在（迁移最大 V1105） | 任何"别人给的根因"都是假设，必须回到代码与机器真相复核 | 拿到诊断报告后，第一步是用 `grep`/文件列表验证报告中引用的迁移版本号、文件名是否真实存在 |
| `baseline-on-migrate: true` + `baseline-version: 1050` 让 `V101` 静默跳过，无报错无告警 | "迁移文件存在" ≠ "已执行"，低于 baseline 的脚本只是历史档案 | 凡是依赖某迁移脚本灌入种子的功能，必须在 PR 中验证该脚本版本号 > baseline，或确认有 ApplicationRunner 兜底 |
| `@Profile("e2e")` 把唯一 seed 来源锁死，dev profile 无数据 | profile 限定 + 唯一数据源 = 隐形空表 | 一个数据源被 `@Profile` 限定时，必须问"其他 profile 从哪获取这份数据"，答案为"没有"即潜在空表 |
| 已保存交付物同时被 `el-upload` file-list 和 `.deliverable-list` 渲染 | UI 容器的数据源必须互斥，一个数据只能有一个"渲染责任人" | 已保存数据走展示容器（可下载链接），待上传数据走上传容器（el-upload），不可混用 file-list |

### 操作规范（建议固化到 ARCHITECTURE.md / FRONTEND.md）

1. **拿到外部诊断报告先复核**：报告中引用的迁移版本号、文件名、行号，必须用 `grep`/`ls` 自己验证一遍再动手修。
2. **Flyway baseline 排查清单**：依赖某迁移脚本种子的功能，启动后若数据为空，先查 `flyway_schema_history` 表确认该版本是否真的执行过，而不是假设"文件在就跑过"。
3. **@Profile 数据源审查**：新增 `@Profile` 限定的 seed/初始化逻辑时，必须在 PR 描述中列出"其他 profile 如何获取这份数据"，缺失即技术债登记。
4. **UI 数据源互斥原则**：同一份数据只能由一个容器负责渲染。已保存数据与待上传数据必须走不同容器，禁止共用 `file-list`。

### 验证命令

```bash
# 1. 验证诊断报告引用的迁移版本号是否真实存在
ls backend/src/main/resources/db/migration-mysql/ | grep -E "V1223|V101"
# V1223 应无输出（不存在）；V101 应存在

# 2. 查 Flyway baseline 配置
grep -n "baseline" backend/src/main/resources/application-mysql.yml
# 确认 baseline-version，判断哪些迁移会被静默跳过

# 3. 查表是否真的有种子数据
mysql -e "SELECT COUNT(*) FROM xiyu_bid_main.task_status_dict"

# 4. 检查 @Profile 限定的 seed 是否有 dev/prod 兜底
grep -rn "@Profile" backend/src/main/java/com/xiyu/bid/config/ | grep -i "seed\|init"
```

### 相关文档

- `docs/lessons/root-cause-analysis-task-board-blank-and-deliverable-dup.md` — 完整根因分析（两个 bug 合并）
- 提交 6877ffe68（PR #1270）/ 1ae84f831（PR #1271）

---

## 23. 全链路日志排查 SOP（Agent 必读）

### 三层诊断体系

```
┌─────────────────────────────────────────────────┐
│  Layer 1: Sentry 自动诊断（P0，首选）              │
│  自动聚合异常 → 直接定位根因代码行 → 显示触发用户   │
│  适用：NPE、SQL异常、外部服务失败等系统缺陷         │
├─────────────────────────────────────────────────┤
│  Layer 2: 结构化日志 + TraceId 手动溯源（P1）      │
│  grep traceId → 全链路还原 → 请求参数/Body 回放    │
│  适用：业务逻辑错误、性能问题、Sentry 未覆盖场景     │
├─────────────────────────────────────────────────┤
│  Layer 3: git log + cherry-pick 追溯（P2）        │
│  代码变更历史 → 哪次 commit 引入的回归             │
│  适用：回归问题、merge 冲突导致的功能丢失            │
└─────────────────────────────────────────────────┘
```

### 问题背景

为了解决定位线上缺陷难、Agent 缺少错误上下文（特别是崩溃时的入参丢失）、以及第三方系统报错盲区等问题，系统已引入 **Sentry 自动错误诊断**（2026-06-30）和全链路日志机制（PR #1295）。AI Agent 介入排查 Bug 时，必须严格遵循以下 SOP，大幅缩短定位时间。

### Layer 1：Sentry 自动诊断（首选，5 秒定位根因）

**Sentry 是什么**：自动错误聚合 + 根因定位系统。捕获所有未处理的异常（NPE、SQL 异常、外部服务调用失败等），自动聚合相同错误，直接显示触发代码的**文件路径 + 行号 + 用户上下文**。

**Sentry 上报哪些异常**：
- 上报：NPE、SQL 异常、`ExternalServiceException`、`IllegalStateException`、`HttpMessageNotReadableException`、`OptimisticLockingFailureException`、`ConstraintViolationException` 等系统缺陷
- 不上报（`SentryConfig.NON_CRITICAL_EXCEPTIONS` 过滤）：`AccessDeniedException`（403 正常权限控制）、`AuthenticationException`（401 正常认证）、`BusinessException`（400 正常业务校验）、`ResourceNotFoundException`（404 正常查询结果）

**Agent 动作**：
1. 打开 Sentry Dashboard（`https://sentry.io` 或自建实例）
2. 查看 Issues 列表，按时间/频率排序
3. 点击具体 Issue → 直接看到：
   - 异常堆栈 + 触发代码的**文件路径 + 行号**
   - 触发用户：userId、username、roleCode、fullName
   - 请求上下文：URL、HTTP Method、Query 参数
   - 发生频率：过去 24h/7d/30d 各多少次
   - 首次出现时间 + 最近出现时间（判断是新引入还是历史遗留）
   - Release 版本（git commit hash）：知道是哪个版本引入的 bug
4. 直接定位到代码行，无需 grep 日志

**Sentry 环境要求**：
- DSN 通过 `SENTRY_DSN` 环境变量注入（无 DSN 时 Sentry 自动禁用，不影响业务）
- 生产环境：`SENTRY_DSN` 必须配置，traces-sample-rate 默认 0.1
- 开发环境：可选配置，用于本地调试

**配置文件**：
- `sentry.properties`：DSN、环境、采样率
- `SentryConfig.java`：beforeSend 过滤 + 用户上下文注入 + release 自动读取

### Layer 2：结构化日志 + TraceId 手动溯源（Sentry 未覆盖时使用）

### 操作规范（Agent 排查必读）

当你被要求调查 Bug 时，请按以下 4 步查找线索：

1. **抓取 X-Trace-Id 溯源**
   - **前端异常**：如果问题发生在前端，或者前端直接弹出了报错，去找 `FrontendLogController` 在后端打印的 ERROR 日志，里面会包含前端页面的路由、报错栈以及 `X-Trace-Id`。
   - 提取出 `X-Trace-Id` 后，以此为关键字，使用 `grep_search` 或命令行 `grep` 检索所有后端日志，即可拿到整个链路的执行情况。

2. **定位崩溃现场（GlobalExceptionHandler）**
   - 传统的 Exception handler 不打印 Request Body。现在借助 `ContentCachingRequestWrapper`，发生崩溃（如 HTTP 500）时，`GlobalExceptionHandler` 会把引发崩溃的 **HTTP 请求头、请求 URL、Body 以及 Query 参数** 全部输出在 ERROR 级别日志中。
   - **Agent 动作**：遇到后端报错，首先去看报错时间点对应的 `GlobalExceptionHandler` 输出，立刻获知“前端当时传了什么脏数据过来”。

3. **排除第三方依赖问题（LoggingClientHttpRequestInterceptor）**
   - 涉及外部系统（如企业微信、大模型接口等）调用时，一旦失败，`RestTemplate` 现在会把发出的原始请求、头部和第三方返回的原始 JSON 全部打印在日志中。
   - **Agent 动作**：遇到诸如空指针或网络错误，去检查 `LoggingClientHttpRequestInterceptor` 打印的请求响应载荷，确认是自身参数传错，还是第三方系统宕机/返回异常。

4. **禁止乱猜**
   - 在做出“因为参数没传导致空指针”的推断前，**必须先用上述方法提取真实的请求体负载数据进行佐证**。没有日志证据前，不要盲目改代码。

### 验证命令

```bash
# 1. 查找崩溃的异常请求现场（能看到入参 Body）
grep -A 20 "GlobalExceptionHandler" backend/logs/error.log

# 2. 用 Trace ID 顺藤摸瓜
grep "你的X-Trace-Id" backend/logs/app.log

# 3. 排查外部调用的出入参
grep -A 15 "LoggingClientHttpRequestInterceptor" backend/logs/app.log
```

## 24. Policy canUpload/canDelete 权限矩阵必须对称设计（CO-375/CO-383 多轮修复归纳）

### 问题背景

`ProjectDocumentWorkflowPolicy.java` 在 CO-361 → CO-373 → CO-382 → CO-375 的多轮修复中反复返工，根因是设计时没有从「同一资源的 upload/delete 权限矩阵必须对称」这个视角审视整个 Policy：

| 轮次 | 修复视角 | 解决的问题 | 遗留的问题 |
|---|---|---|---|
| CO-361 | 查看权限 | 项目任务执行人可查看 | 上传/删除未审视 |
| CO-373 | 提交权限 | 投标负责人可提交审核 | 删除未审视 |
| CO-382 | 删除权限（管理员组） | 管理员组可删除 | 上传者本人未考虑 |
| CO-375 | 删除权限（上传者） | 上传者本人可删除 | 终于对齐 |

每一轮修复都解决了真实问题，但都只看一个维度的权限，没有审视整个权限矩阵。

### 教训

1. **同一资源的 upload/delete 权限矩阵必须对称设计**：`canUpload` 放行的角色，`canDelete` 必须有明确的对应策略（要么放行，要么明确拒绝并说明原因）。如果 `canUpload` 放行 `bid-projectLeader`，`canDelete` 必须明确说明 `bid-projectLeader` 在什么条件下可以删除（如：上传者本人）。

2. **修改 Policy 时必须审视整个权限矩阵**：不能只改一个方法，必须审视 canView / canDownload / canUpload / canDelete 四类操作的权限矩阵是否一致。一个简单的检查清单：
   - canView 放行的角色，canDownload 是否覆盖？
   - canUpload 放行的角色，canDelete 是否有对应策略？
   - 管理员组在四个操作中是否一致？
   - 身份维度（uploaderId、assigneeId、reviewerId）是否在所有相关操作中考虑？

3. **权限策略必须考虑"身份维度"**：除了角色维度（roleCode），还要考虑身份维度（uploaderId、assigneeId、reviewerId 等）。同一角色在不同身份下权限可能不同。例如 bid-projectLeader 上传的文档，bid-projectLeader（作为上传者本人）应能删除，但 bid-projectLeader（作为非上传者）应被拒绝。

4. **Policy 方法签名必须包含所有决策维度**：`canDelete(roleCode)` 不够，必须是 `canDelete(roleCode, currentUserId, uploaderId)`，把所有决策维度显式传入。如果签名维度不足，Policy 内部无法做出正确决策。

5. **Controller `@PreAuthorize` 不能过度收紧**：早过滤层只做"是否登录"级别的过滤（`isAuthenticated()`），真权限交给 Service 层 Policy。如果 Controller 用 `hasAnyRole` 收紧，会挡住 Policy 内部想放行的特殊场景（如上传者本人）。详见 `docs/lessons/decisions.md` §3。

### 检查清单（修改 Policy 时必跑）

```markdown
- [ ] canView 放行的角色清单：___________
- [ ] canDownload 放行的角色清单：___________
- [ ] canUpload 放行的角色清单：___________
- [ ] canDelete 放行的角色清单：___________
- [ ] canUpload 和 canDelete 是否对称？___________
- [ ] 身份维度（uploaderId/assigneeId/reviewerId）是否在所有相关操作中考虑？___________
- [ ] Controller @PreAuthorize 是否过度收紧（应使用 isAuthenticated()）？___________
- [ ] 测试是否覆盖非管理员角色（bid-projectLeader/bid-Team）？___________
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-375-uploader-delete-permission.md` — 完整根因分析
- `docs/lessons/decisions.md` §3 — Controller @PreAuthorize 放宽为 isAuthenticated() 决策
- `backend/src/main/java/com/xiyu/bid/projectworkflow/core/ProjectDocumentWorkflowPolicy.java` — Policy 实现

---

## 25. 前端禁止 `catch { /* silent */ }` 吞掉 API 错误（CO-390 root cause）

### 问题背景

CO-390 修复绑定联系人字段升级 userId 后，投标组长/专员新增账户时无法搜索人员。根因是 `AccountFormDialog.vue` 调用 `/api/admin/users`（`@PreAuthorize("hasRole('ADMIN')")`）返回 403，但前端 `catch { /* silent */ }` 静默吞掉错误，`biddingUsers` 静默为空，用户看到的是"无法搜索"而非"权限不足"，严重误导排查方向。

```javascript
// 错误模式
const loadBiddingUsers = async () => {
  try {
    const res = await httpClient.get('/api/admin/users')
    // ...
  } catch { /* silent */ }  // ← 吞掉 403，用户看到"无法搜索"而非"权限不足"
}
```

### 教训

1. **`catch { /* silent */ }` 是权限问题的隐形放大器**：后端返回 403 时前端吞错，用户看到的是"功能不可用"而非"权限不足"，导致：
   - 用户以为是 Bug 而非权限问题，提错工单
   - 排查者从"搜索功能"入手，而非从"权限链路"入手，浪费时间
   - 测试环境用 admin 账号测不出问题，上线后非管理员账号才发现

2. **静默吞错违反"快速失败"原则**：错误应该尽早暴露，而不是静默处理后继续执行导致后续逻辑在错误状态下运行（空数组 → 下拉无候选 → 无法搜索）。

3. **`try/catch` 的 catch 块必须有明确处理**：至少记录日志、上报埋点或弹出错误提示，禁止空 catch 块。

### 操作规范

1. **禁止 `catch { /* silent */ }` 或 `catch {}` 空块**：catch 块必须有至少一项处理：
   - `console.error('[场景] xxx 失败', err)` 记录日志
   - `ElMessage.error('加载xxx失败：' + err.message)` 弹出提示
   - 降级处理 + 明确注释说明为什么降级（如 `// 403 时降级为空列表，权限由后端控制`）

2. **关键业务写操作（创建/更新/删除）禁止吞错**：必须向用户反馈失败，不能静默处理。

3. **数据加载类 catch 必须区分错误类型**：
   - 403/401：明确提示"权限不足"或降级为空列表 + 注释
   - 404：明确提示"资源不存在"
   - 500：明确提示"服务异常，请稍后重试"
   - 网络错误：明确提示"网络异常"

4. **Code Review 时必须检查 catch 块**：reviewer 看到 `catch {}` 或 `catch { /* silent */ }` 必须质疑，要求作者明确处理或注释说明降级原因。

### 验证命令

```bash
# 检查前端是否有空 catch 吞掉 API 错误
grep -rn "catch\s*{" src/views src/components | grep -v "catch.*err\|catch.*error\|catch.*e)" | head -20
# 期望输出：无空 catch 块（或 catch 块有明确注释说明降级原因）

# 检查 catch 块是否有 console.error 或 ElMessage
grep -rn "catch.*{" src/views src/components -A 3 | grep -B 1 "silent\|/\*.*\*/" | head -20
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-390-unified-picker.md` — 完整根因分析
- `docs/lessons/lessons-learned.md` §1 — 后端接口契约变更必须同步前端所有入口（同类教训）

---

## 26. 联动回填链路 4 层全链路验证 SOP（CO-390 思维链 Review 归纳）

### 问题背景

CO-390 修复 AccountFormDialog 绑定联系人后，需要 UserPicker 选中联系人后联动回填 phone/email。后端在 `UserSearchResult` 新增 phone/email 字段后，必须验证 4 层链路全部对齐，否则任意一层断链都会导致联动失败。

### 4 层链路验证 SOP

| 层级 | 验证点 | 验证方式 |
|------|--------|---------|
| **1. 后端 DTO** | record/DTO 包含目标字段 | Read 后端 record/DTO 文件，确认字段存在 + service 填充 |
| **2. API 层 normalize** | normalize 函数保留目标字段 | Read 前端 API module 的 normalize 函数，确认 `...user` 展开或显式映射目标字段 |
| **3. 组件层 @select 回传** | @select 事件回传完整对象 | Read 组件源码，确认 emit 时回传原始对象（含目标字段），而非仅回传 id |
| **4. 业务层联动** | 业务函数取目标字段联动 | Read 业务组件的事件处理函数，确认取 `user.目标字段` 并联动回填 |

### 验证示例（CO-390 phone/email 联动）

```
1. 后端 DTO:
   UserSearchResult.java record 新增 phone, email 字段 ✓
   UserSearchService.java 填充 u.getPhone(), u.getEmail() ✓

2. API 层 normalize:
   userNormalizers.js normalizeUserOption 用 `...user` 展开保留所有字段 ✓
   users.js usersApi.search 调 normalizeUserOption ✓

3. 组件层 @select 回传:
   UserPicker.vue handleChange 用 mergedOptions.value.find(...) 回传原始 user 对象 ✓
   （不是只回传 id，而是完整对象，含 phone/email）

4. 业务层联动:
   AccountFormDialog.vue onContactPersonSelected(user) 取 user.phone / user.email 联动回填 ✓
```

### 教训

1. **联动回填链路任何一层断链都会导致功能失败**：
   - 后端 DTO 没字段 → API 层拿不到 → 组件回传 undefined → 业务联动失败
   - API 层 normalize 丢字段 → 组件回传对象无字段 → 业务联动失败
   - 组件 @select 只回传 id → 业务拿不到 user 对象 → 联动失败
   - 业务函数不取字段 → 联动失败

2. **思维链 Review 必须验证 4 层全链路**：不能只看业务层代码，必须从后端 DTO 开始逐层验证，确认字段在每一层都被正确传递。

3. **normalize 函数用 `...user` 展开是最佳实践**：[userNormalizers.js](file:///Users/user/xiyu/worktrees/mimo/src/api/modules/userNormalizers.js) 用 `...user` 展开保留所有字段，后端新增字段时前端 API 层自动透传，无需修改 normalize 函数。如果 normalize 函数显式列出字段（如 `{id, name, phone}`），后端新增字段时必须同步更新 normalize 函数，容易遗漏。

### 操作规范

1. **后端 DTO 新增字段用于前端联动时，必须 4 层全链路验证**：
   - 后端 record/DTO 字段存在 + service 填充
   - 前端 API normalize 保留字段（`...user` 展开或显式映射）
   - 组件 @select 回传完整对象
   - 业务函数取字段联动

2. **思维链 Review 时必须画出 4 层链路图**：用表格列出每一层的验证点和状态，确认全绿。

3. **normalize 函数优先用 `...user` 展开保留所有字段**：避免显式列字段导致后端新增字段时遗漏同步。

### 相关文档

- `docs/lessons/root-cause-analysis-co-390-unified-picker.md` — 完整根因分析（含 4 层链路验证）
- `src/api/modules/userNormalizers.js` — normalizeUserOption `...user` 展开最佳实践
- `src/components/common/UserPicker.vue` — @select 回传完整对象
- `docs/lessons/vue-gotchas.md` §3 — UserPicker 统一控件规范

---

## 25. 迁移脚本之间不能互相覆盖（V1098 vs V1105 迁移漂移）

### 问题背景

CO-349 修复后遗留的"【待立项】"占位任务问题：

| 时间 | 迁移 | 操作 | 结果 |
|------|------|------|------|
| 2026-06-25 22:55 | V1098 | "【待立项】"TODO → CANCELLED | ✅ 清理了 62 个占位任务 |
| 2026-06-27 18:10 | V1105 | 所有 CANCELLED → TODO（三态收口） | ❌ 把占位任务又改回 TODO |

两个迁移脚本互相抵消，导致 62 个"【待立项】"任务复活，前端看不见但 `AllTasksCompletedPolicy` 计入，`submit-bid` 报"仍有 N 个任务未完成"。

### 根因

V1105（CO-361 三态模型收口）执行 `UPDATE tasks SET status = 'TODO' WHERE status = 'CANCELLED'` 时，未排除 V1098 刚处理的"【待立项】"占位任务。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 新迁移覆盖了旧迁移的处理结果 | 迁移脚本之间必须有依赖关系分析 | 执行迁移前检查是否有其他迁移涉及相同数据 |
| 占位任务用 CANCELLED 作终态，与三态模型冲突 | 废弃状态值不应与新引入的三态冲突 | 迁移脚本使用新状态值前，确认下游迁移不会改回旧值 |
| 前端过滤 vs 后端计数不一致 | 前端不展示的数据，后端也不应计入业务校验 | 业务校验层不应计入前端主动过滤的数据 |

### 操作规范

1. **新增迁移脚本涉及状态值变更时，必须检查历史迁移是否有冲突场景**：
   ```bash
   grep -l "tasks\|status" backend/src/main/resources/db/migration-mysql/V*.sql
   ```

2. **废弃状态值迁移前，确认下游迁移是否会覆盖**：
   - 如果下游迁移可能把所有非标准状态归一，需要在 WHERE 条件中排除
   - 或者改用直接删除（如 V1112）

3. **前端主动过滤的数据，后端业务校验层不应计入**：
   - "【待立项】"任务前端过滤不展示，后端 AllTasksCompletedPolicy 不应计入
   - 如果不能从源头删除，应在后端查询时过滤

### 根治方案

V1112 直接删除"【待立项】"TODO 占位任务，从源头解决问题。代码层 CO-349 已移除创建逻辑，存量数据清理后不会再复发。

### 相关文档

- `V1112__cleanup_legacy_pending_initiation_tasks.sql` — 根治迁移
- `V1098__cancel_legacy_pending_initiation_tasks.sql` — V1105 前的清理尝试
- `V1105__drop_in_progress_cancelled_status.sql` — 导致迁移漂移的脚本
- `TenderEvaluationService.java:274` — CO-349 移除占位任务创建逻辑

---

## 27. OkHttp3 传递依赖导致 RestTemplate GET 请求全面失败

### 问题背景

2026-06-30 dev-services.sh restart 后 backend `/actuator/health` 返回 DOWN，日志反复报 `IllegalArgumentException: method GET must not have a request body`。三处 sidecar 调用（SidecarHealthIndicator / MarkItDownSidecarExtractor / MarkItDownSidecarTextExtractor）全部受影响，frontend 因 backend DOWN 无法启动。

### 多轮修复弯路

1. **PR #1362（workaround）**：误判为 sidecar 拒绝带 body 的 GET，改用 JDK HttpClient 绕开。治标不治本。
2. **思维链 Review 识别 6 个问题**，追问根因。
3. **PR #1369（根因修复 sidecar）**：发现 `com.openai:openai-java-client-okhttp:4.32.0` 传递依赖引入 okhttp3，`RestTemplateBuilder` 自动检测后用 `OkHttp3ClientHttpRequestFactory`，OkHttp3 对 GET 严格要求 body 为 null。修复方式：显式指定 `SimpleClientHttpRequestFactory`。
4. **stash 发现遗漏点**：`OrganizationDirectoryHttpGateway` 也中招。
5. **PR #1373（根因修复 organization）**：抽取 `buildRestTemplate` 方法，同样显式指定 `SimpleClientHttpRequestFactory`。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 错误消息被误判为 sidecar 拒绝 | 错误消息要看完整调用栈，不能只看消息文本 | 排查时先看完整 stacktrace，确认抛异常的类属于哪一层 |
| 改用 JDK HttpClient 是 workaround | workaround 治标不治本，根因未消除，其他使用点仍会中招 | 修复后必须做 5 维度 Review，识别 workaround 并追问根因 |
| OkHttp3 通过传递依赖引入 | 传递依赖会改变框架行为，RestTemplateBuilder 自动检测不可靠 | 显式指定 `requestFactory`，不要依赖自动检测 |
| 修复一处后以为完事，stash 中才发现另一个使用点 | 同一根因可能影响多个使用点，必须全局排查 | 修复后用 `grep -rn "RestTemplateBuilder" backend/src/main` 列出所有使用点 |
| SidecarHealthIndicator 缺单元测试 | 健康检查是关键路径，必须有测试覆盖 | 关键 HealthIndicator 必须有 UP/DOWN/超时/5xx 至少 4 个测试 |

### 操作规范

1. 所有 `RestTemplateBuilder` 使用点必须显式指定 `requestFactory`，不依赖自动检测
2. 新增 HealthIndicator 必须配单元测试，覆盖 UP/DOWN/超时/5xx 至少 4 个场景
3. 关键日志拦截器必须有测试，验证对 GET/POST/PUT/DELETE 各种方法的行为
4. 修复 bug 后做 5 维度 Review，识别 workaround 并追问根因
5. 同一根因修复后全局排查，用 `grep` 列出所有同类使用点

### 验证命令

```bash
# 列出所有 RestTemplateBuilder 使用点，确认是否显式指定 requestFactory
grep -rn "RestTemplateBuilder" backend/src/main --include="*.java" | grep -v "requestFactory"

# 检查 OkHttp3 是否在 classpath
mvn dependency:tree -Dincludes=com.squareup.okhttp3 2>&1 | grep okhttp3

# 验证 sidecar 健康检查
curl -s http://127.0.0.1:18089/actuator/health | python3 -m json.tool
```

### 相关文档

- `docs/lessons/root-cause-analysis-okhttp3-get-body-resttemplate.md` — 完整根因分析
- `docs/lessons/spring-boot-actuator-gotchas.md` — 同类健康检查陷阱
- PR #1362（work workaround）→ #1369（根因修复 sidecar）→ #1373（根因修复 organization）

---

## 28. 权限 Bug 必须审视同一业务动作的所有 UI 入口 + 前后端对称修复（CO-400 五轮 + CO-415 归纳）

### 问题背景

CO-400 围绕"账户管理"权限经历了 **5 轮修复**，每一轮都解决了真实问题却总留尾巴，直到第 5 轮才找到真正的根因视角。CO-415 是 round5 Review 时发现的同系列对称 bug，进一步暴露了"只改前端、漏掉后端一刀切"的盲区。

| 轮次 | 修复视角 | 解决的问题 | 遗留的问题 |
|---|---|---|---|
| round1-3 | 列表页字段 / 编辑页密码回显 | 列表脱敏字段补齐 + getPassword 回显 | 操作按钮入口未审视 |
| round4 | getPassword 联系人豁免 | 投标专员作为绑定联系人可看密码 | 编辑/归还按钮未审视 + returnAccount 端点仍一刀切 |
| round5 | **详情页"编辑"按钮 v-if 守卫** | 详情页编辑按钮无条件渲染 | 同 footer 的"登记归还"按钮漏改 |
| CO-415 | **详情页"登记归还"按钮 + 后端 returnAccount 端点对称修复** | 前端守卫不对称 + 后端 @PreAuthorize 一刀切 + Service 层无校验 | 终于对齐 |

**反复返工的根因**：每轮修复都只看一个 UI 入口（列表页行操作）或一个后端端点（getPassword），没有从"同一业务动作的所有触发点 + 前后端对称"这个视角做全局审视。

### 三层根因（CO-415 完整链路，最值得沉淀）

**根因 1：前端守卫不对称（UX 层）**

同一业务动作（如"归还账户"）有两个 UI 入口，守卫不一致：
- 列表页 `AccountRowActions.vue:5`：`v-if="actions.return"` ✅ 走 `resolveAccountActions` 授权
- 详情页 `AccountDetailDialog.vue:58`：`v-if="data?.status === 'in_use'"` ❌ 仅判状态、不走 `actions.return`

非联系人投标专员在详情页能看到归还按钮，点击后撞后端 403。

**根因 2：后端 `@PreAuthorize` 一刀切拦截 bid-Team（真实拦截点，比前端更深）**

完整权限链路追踪（这是本次最关键的发现，必须沉淀）：
1. `RoleProfileCatalog.java:76` 的 `ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 列表包含 `bid-team`
2. `UserDetailsServiceImpl` 检测到 bid-Team 在该列表，设 `skipLegacyCompat=true`
3. 因此 Spring Security 的 `Authentication` 中**不发 `ROLE_MANAGER` authority**
4. `PlatformAccountController` 的 `/return` 与 `/return-with-password` 方法级 `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")` 直接 403，**包括绑定联系人**
5. 与前端 `resolveAccountActions` 给绑定联系人放行 `return:isInUse` 的语义冲突——前后端授权语义不一致

**根因 3：Service 层完全无权限校验（最危险，TDD Red 证实的漏洞）**

`PlatformAccountService.returnAccount` 收到 `currentUser` 参数但**完全未做权限校验**，唯一的防线是 Controller 的 `@PreAuthorize`，而它正好误拦联系人。

**TDD Red 阶段精确证实了这点**：写测试 `returnAccount_bidTeamNotContactPerson_throws` 期望抛 `IllegalStateException`，实际得到 **NPE**（非联系人一路执行到 `account.returnToPool()` 后 mapper 才崩）。这个 NPE 不是测试写错，而是**真实漏洞的证据**——若有人绕过 Controller（如内部调用），Service 层形同裸奔。

### 教训

1. **同一业务动作必须审视所有 UI 入口**：编辑/归还/删除等动作常有多个触发点（列表页行操作 + 详情页 footer 按钮 + 可能的右键菜单）。修复权限 Bug 前，先全局搜索该动作的所有触发点（`@click="$emit('edit')"`、`handleEdit`、`rowActionsFor`、`$emit('return')` 等），逐一核对权限守卫是否一致。只改一个入口 = 用户从另一个未修入口绕过权限撞 403。

2. **前端 `v-if` 守卫只是 UX 层，后端 `@PreAuthorize` 一刀切才是真实拦截点**：修前端不修后端 = 联系人看到按钮点了仍 403；修后端不修前端 = 联系人没按钮但 API 可达（不一致）。权限 Bug 必须**前后端对称审查**。

3. **`hasAnyRole('ADMIN','MANAGER')` 对 bid-Team 是陷阱**：bid-Team 在 `ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 中，`skipLegacyCompat=true` 导致不发 `ROLE_MANAGER`，所以 `hasAnyRole('ADMIN','MANAGER')` 会一刀切拦截**所有 bid-Team（包括绑定联系人）**。涉及 bid-Team 的端点应改用类级 `hasAuthority('resource')` + Service 层细粒度校验。

4. **Service 层收到 `currentUser` 就必须做权限校验**：不能把 Controller `@PreAuthorize` 当唯一防线。Controller 是"是否登录 + 是否有模块权限"的早过滤层，Service 层才是"是否对该具体资源有操作权限"的细粒度决策层。若 Service 层不做校验，Controller 一旦被绕过（内部调用、未来新增入口）就形同裸奔。

5. **已有验证过的范式应作为模板复用**：CO-400 round4 已为 `getPassword` 建立正确范式（Controller 去方法级 `@PreAuthorize` + Service 加联系人豁免 + 复用 `PlatformAccountContactMatcher`）。CO-415 的 `returnAccount` 应**对称复刻**此范式，而不是重新设计引入新风险。审查权限 Bug 时，用 `git log -S "@PreAuthorize" -- <Controller>` 找出同类端点，逐一对照 Service 层是否做了细粒度校验。

6. **TDD Red 阶段的"非预期异常"是漏洞证据，不是测试写错**：当 Red 测试期望抛业务异常却得到 NPE/其他异常时，往往说明被测代码根本没做该校验——这是真实漏洞的信号，应深挖而不是改测试期望去迎合现状。

### 操作规范（权限 Bug 修复清单）

```markdown
- [ ] 全局搜索该业务动作的所有 UI 入口（列表页/详情页/弹窗/右键菜单），逐一核对 v-if 守卫
- [ ] 后端对应端点的 @PreAuthorize 是否用 hasAnyRole('ADMIN','MANAGER') 一刀切？
      （bid-Team 在 ROLES_WITHOUT_LEGACY_ROLE_COMPAT，会被误拦）
- [ ] Service 层是否收到 currentUser 却未做权限校验？（Controller 是唯一防线 = 危险）
- [ ] 是否已有同类端点（如 getPassword）建立过正确范式？对称复刻，不要重新设计
- [ ] 前后端授权语义是否一致？（前端 resolveAccountActions 放行的角色，后端必须也放行）
- [ ] 测试是否覆盖：管理员 / bid-Team 联系人 / bid-Team 非联系人 / 无模块权限 四类调用方？
```

### 验证命令

```bash
# 1. 找出 Controller 中所有方法级 @PreAuthorize（可能一刀切的端点）
git log -S "@PreAuthorize" -- backend/src/main/java/com/xiyu/bid/platform/controller/PlatformAccountController.java

# 2. 确认 bid-Team 在 ROLES_WITHOUT_LEGACY_ROLE_COMPAT（会被 skipLegacyCompat 跳过）
grep -n "bid-team" backend/src/main/java/com/xiyu/bid/security/RoleProfileCatalog.java

# 3. 全局搜索某业务动作的所有 UI 入口（以"归还"为例）
grep -rn "\$emit('return')\|handleReturn\|登记归还" src/views/Resource/

# 4. TDD 验证 Service 层是否有校验（Red 期望 IllegalStateException 实际得 NPE = 漏洞）
cd backend && mvn -o test -Dtest='PlatformAccountServiceTest#returnAccount_bidTeamNotContactPerson_throws'
```

### 相关文档

- `implementation-notes.md` — CO-400 round5 + CO-415 完整决策记录（根因、范式复用、行预算棘轮迭代）
- §24 — Policy 权限矩阵对称设计（canUpload/canDelete 对称，同类思想）
- §23 — 全链路日志排查 SOP（本次 Step 4 用代码证据链替代乱猜）
- PR #1381（round5 + CO-415 对称修复，已合并）
- `backend/src/main/java/com/xiyu/bid/platform/service/PlatformAccountViewerPolicy.java` — canReturnAccount/checkCanReturnAccount 纯静态 Policy

## 29. 服务器字体缺失 + 多 Agent 并行修同一 bug 的协调教训（CO-438）

### 问题背景

Sentry 上报 `NullPointerException: Fontconfig head is null`，堆栈指向 POI `autoSizeColumn` → Java AWT 字体系统初始化失败。3 个 Agent 几乎同时提交了 PR，但策略各异，产生了重复劳动和危险改动。

| PR | Agent | 策略 | 问题 |
|---|---|---|---|
| 1430 | Claude | 删 helper，全改固定列宽 | ① 丢失 autoSize 能力 ② 代码重复 ③ 混入 CO-430 无关改动 |
| 1433 | Qoder | 删 helper，裸调 autoSizeColumn | **完全没有 try-catch**，比修之前更危险 |
| 1432 | Cursor | 分支名/commit 写 excel-autosize | **实际改的是 Vue 导入对话框**，文不对题 |

### 根因

**systemd 启动 Java 时未设 `-Djava.awt.headless=true`**。Java AWT 在无显示器 Linux 上不加 headless 会走 `X11FontManager`，fontconfig 加载失败后 `autoSizeColumn` 抛 NPE。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| systemd 缺 headless 参数 | 无显示器 Linux 服务器上 Java AWT 必须设 headless | systemd ExecStart 必须包含 `-Djava.awt.headless=true`；启动类加 `System.setProperty("java.awt.headless", "true")` 兜底 |
| 删防御代码 ≠ 修 bug | try-catch 是防御性编程，删除后比不修更糟 | 禁止删除已有的异常兜底代码，除非确认根因已消除且兜底不再需要 |
| 每列独立 try-catch 导致 Sentry 重复上报 | 首列失败后应跳过剩余 autoSize，避免重复触发失败 | autoSize 失败后设 flag，剩余列直接走 fallback |
| 多 Agent 同时修同一 bug | 3 个 PR 互相冲突，策略不一致 | 开工前跑 `who-touches.sh`，指定一个 Agent 统一修复 |
| 分支名/commit 与实际改动不符 | PR 1432 名为 excel-autosize 实改 Vue 对话框 | commit message 必须准确描述实际改动 |
| 一个 PR 混入无关改动 | CO-430 路径修复和 Excel autoSize 混在一起 | 一个 PR 只做一件事 |

### 防复发措施

1. **架构测试**：新增 `business_code_should_not_call_sheet_autoSizeColumn_directly` 规则，禁止业务代码直接调用 `Sheet.autoSizeColumn()`，必须走 `ExcelAutoSizeHelper`
2. **三层防御**：systemd headless + 启动类 System.setProperty + ExcelAutoSizeHelper 降级
3. **单元测试**：用 Mockito mock Sheet 模拟字体异常，验证首列失败后整批降级行为

### 相关文档

- `root-cause-analysis-co-438-fontconfig-head-null.md` — 完整根因分析
- `backend/src/main/java/com/xiyu/bid/common/util/ExcelAutoSizeHelper.java` — 统一入口 + 降级逻辑
- `backend/src/main/java/com/xiyu/bid/XiyuBidApplication.java` — 启动类 headless 兜底
- `backend/src/test/java/com/xiyu/bid/ArchitectureTest.java` — autoSizeColumn 禁止直接调用规则

## 26. 角色权限定义必须同时覆盖前端导航权限和后端 API 权限（CO-439）

### 问题背景

行政人员（`bid-administration`）切换到资质证书菜单页报 403"权限不足"。后端 `QualificationController` 的 GET 端点已正确使用 `qualification.view` 权限（行政人员已有），但用户请求被**前端路由守卫**拦截，根本未到达后端。

### 根因

角色权限定义（`RoleProfileCatalog`）只配了后端 API 权限（`qualification.view`），忘了配前端导航权限（`knowledge` 父权限 + `knowledge-qualification` 子路由权限）。前端有**两套独立的权限检查机制**：

| 机制 | 检查位置 | 作用 |
|------|---------|------|
| 前端路由守卫 | `router/index.js` → `hasAllPermissions(permissionKeys)` | 决定能否进入页面 |
| 后端 `@PreAuthorize` | `QualificationController` → `hasAuthority(...)` | 决定能否调用 API |

**两者使用不同的权限键**，新增角色权限时必须**同时覆盖**。

### 教训

1. **前端导航权限 ≠ 后端 API 权限**：`knowledge`/`knowledge-qualification`（前端导航）和 `qualification.view`/`qualification.manage`（后端 API）是两套独立键值
2. **authNormalizer 的父权限自动补全有盲区**：只有当子权限以 `knowledge-` 前缀开头时才会自动补全 `knowledge`。`qualification.view` 不匹配此规则
3. **新增角色权限的 Checklist**：
   - [ ] 前端侧边栏可见性（父权限键）
   - [ ] 前端路由守卫（子权限键，通常 2 个）
   - [ ] 后端 GET 端点（`@PreAuthorize` 读权限）
   - [ ] 后端写端点（`@PreAuthorize` 写权限，如需要）
   - [ ] Flyway 迁移同步数据库 `roles` 表
   - [ ] 单元测试断言同步更新

### 相关文件

- `RoleProfileCatalog.java:170` — 角色权限 seed 定义
- `router/index.js:162-166` — 路由 permissionKeys
- `sidebar-menu.js:47-49` — 侧边栏 permissionKeys
- `authNormalizer.js:22-42` — 父权限自动补全逻辑
- `V1124__fix_co_439_admin_staff_navigation_permissions.sql` — 修复迁移

## 30. REQUIRES_NEW + try-catch 反模式导致 UnexpectedRollbackException（CO-440）

### 问题背景

Sentry 上报 `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`，触发接口 `POST /api/ca-certificates/{id}/borrow`。用户提交 CA 借用申请时，业务数据已保存但最终返回 500 错误。

### 根因

`CaNotificationDispatcher.onBorrowSubmitted()` 使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 开独立事务，方法内部用 try-catch 吞掉 `notificationService.createNotification()` 抛出的 RuntimeException，意图实现 "best-effort" 语义（通知失败不影响主业务）。

**但这个模式是错误的**——Spring 事务拦截器的执行顺序导致了问题：

```
调用链（自顶向下）：
┌─────────────────────────────────────────────────────┐
│ CaBorrowService.borrow()                            │
│   @Transactional(REQUIRED)  — 事务 A               │
│                                                      │
│   ┌──────────────────────────────────────────────┐  │
│   │ CaNotificationDispatcher.onBorrowSubmitted() │  │
│   │   @Transactional(REQUIRES_NEW)  — 事务 B    │  │
│   │                                              │  │
│   │   ┌──────────────────────────────────────┐   │  │
│   │   │ createNotification()                 │   │  │
│   │   │   @Transactional(REQUIRED)           │   │  │
│   │   │   → 加入事务 B                        │   │  │
│   │   └──────────────────────────────────────┘   │  │
│   └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

**异常发生时的执行顺序**：

1. `createNotification()` 内部抛出 RuntimeException
2. **`createNotification` 的事务拦截器先捕获异常** → 标记事务 B 为 `rollback-only` → 重抛异常
3. 异常向上传播到 `onBorrowSubmitted()` 的 try-catch → 被捕获，记录日志
4. `onBorrowSubmitted()` 方法**正常返回**
5. **`onBorrowSubmitted` 的事务拦截器尝试提交事务 B**
6. 发现事务 B 已被标记为 `rollback-only` → 抛出 `UnexpectedRollbackException`

**核心认知误区**：认为 try-catch 在 catch 块里就能阻止事务回滚。实际上内层 `@Transactional` 方法的事务拦截器**先于**外层 catch 执行，等 catch 到异常时，事务已经被标记为 rollback-only 了。

### 教训

1. **「REQUIRES_NEW + try-catch」是反模式**：不能靠在外层方法加 try-catch 来实现 "best-effort 通知失败不影响主事务" 的语义，因为内层事务拦截器已经先把事务标记为回滚了。

2. **正确修复方式**：在 catch 块中显式调用 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`，告诉 Spring "这个事务就是要回滚的"。Spring 看到是 rollback-only 就会正常执行回滚，而不是尝试提交失败。

   ```java
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public void onSomethingHappened(...) {
       try {
           notificationService.createNotification(...);
       } catch (RuntimeException ex) {
           log.warn("Notification failed: {}", ex.getMessage());
           // 必须显式标记回滚，否则 Spring 尝试提交时抛 UnexpectedRollbackException
           TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
       }
   }
   ```

3. **同类模式必须全仓排查**：所有使用 `@Transactional(REQUIRES_NEW) + 内部 try-catch 吞 RuntimeException` 模式的类都有此问题，不能只修报 Sentry 的那一个。

   本次排查出 3 个同类问题类：
   - `CaNotificationDispatcher`（4 个通知方法）
   - `TenderEvaluationNotificationService`（`createNotificationSafely`）
   - `TenderPendingAssignmentNotifier`（`createNotificationSafely`）

### 防复发检查清单

新增 "best-effort 通知" 类代码时，检查：

- [ ] `@Transactional(REQUIRES_NEW)` 加在 public 方法上（Spring AOP 代理才能拦截）
- [ ] catch 块中必须调用 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`
- [ ] 同类内部调用不会绕过代理（必须是外部 bean 调用 public 方法）
- [ ] 全仓搜索 `@Transactional.*REQUIRES_NEW` 排查是否有遗漏

### 相关文件

- `backend/src/main/java/com/xiyu/bid/resources/notification/CaNotificationDispatcher.java` — CA 通知派发器（修复后）
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderEvaluationNotificationService.java` — 标讯评估通知（修复后）
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderPendingAssignmentNotifier.java` — 待分配通知（修复后）
- §23 — 全链路日志排查 SOP（本次排查使用）

---

## 31. 审批接口契约不统一 + JS 默认参数陷阱导致 `Required request body is missing`（CO-459）

### 问题背景

CO-459 实现「CA 信息管理 - 我的申请 / 我的审批」功能后，在生产环境（`172.16.38.78`）验收时，投标管理员审批 CA 借用申请返回 400：

```
请求体格式错误: Required request body is missing: public ResponseEntity<CaBorrowApplicationDTO>
CaCertificateController.approve(Long, CaApprovalRequest, UserDetails)
```

服务器日志铁证（traceId `68e838cb89714d4e9f518f774d81effc`，2026-07-02 09:17:41.943，400 + 9ms 返回）证明请求到达后端但反序列化失败。

### 多重根因（流程性，非孤立 bug）

#### 根因 1：项目内审批接口存在四种契约风格，无统一规范

| 模块 | Controller 签名 | 前端调用 |
|---|---|---|
| 项目立项 (InitiationController) | `@Valid @RequestBody InitiationApprovalRequest req` | `approveInitiation(id, {...})` |
| 标书审核 (DraftingController) | `@RequestBody(required=false) Map<String,String>` | `approveBid(id, { comment: '' })` |
| 结项审核 (ClosureController) | `@Valid @RequestBody ClosureReviewRequest req`（reject）/ 无 body（approve） | `approveClosure(id)` / `rejectClosure(id, {...})` |
| CA 借用（CO-459 新写）⚠️ | `@Valid @RequestBody CaApprovalRequest req` + `@NotBlank` | `approveApplication(id, '')` ⚠️ |

CO-459 写新代码时**没有参照已有审批接口契约风格**，独立设计了"更严格"的 DTO（`@Valid + @RequestBody + @NotBlank`），但前端没同步按新契约传参。

#### 根因 2：JavaScript 默认参数陷阱——`data = {}` 的假安全感

```javascript
// ca.js:225
async approve(applicationId, data = {}) {
  return httpClient.post(`${BASE}/borrow-applications/${applicationId}/approve`, data)
}

// CAManagement.vue:688
await caStore.approveApplication(row.id, '')  // 传空字符串
```

`data = {}` 只在参数为 `undefined` 时生效，传 `''`（空字符串）**不会触发默认值**。结果 `httpClient.post(url, '')` 发送空字符串作为 body，后端 `@RequestBody` 期望 JSON 对象，反序列化失败。

这个陷阱在 code review 时肉眼很难发现——`data = {}` 看起来已经做了防御。

#### 根因 3：单测覆盖的是 store 层，不是端到端契约

CO-459 PR 验证清单：`npm run build` + `mvn test -Dtest=ArchitectureTest` + line-budget 全通过。但**没有**：
- ❌ 后端 Controller 的 `@WebMvcTest`（验证 `@RequestBody` 反序列化）
- ❌ 前端调用 store 的集成测试（验证传参格式）
- ❌ E2E 测试（真实点击审批按钮→后端落库）

每一层都"看起来对"，但**契约的接缝处没人测**。

#### 根因 4（元根因）：没有"契约单一源"

项目里审批接口有四种写法，**没有统一的契约规范文档**。开发者写第五个审批接口（CA 借用）时，面临四种参考样板，选哪种全凭个人偏好。选了严格契约但前端没同步升级——**契约脱节**。如果契约规范统一，新接口照葫芦画瓢即可，不会脱节。

### 排查弯路（自我反思）

| 轮次 | 动作 | 错误 | 用户纠偏 |
|---|---|---|---|
| 1 | 没看日志，直接改代码 | 盲目猜测 | "你有去服务器上看看日志，找出失败的根因吗？不要盲目地判断" |
| 2 | 看本地日志找不到记录 | 找错环境 | "是在服务器上 你搞错了" |
| 3 | 只回答"前端传字符串后端期望对象" | 只回答技术现象，没分析"为什么会写出这种代码" | "核心关键是 为什么会有这个bug 你分析了吗" |

正确做法应该是：**先确认事故发生在哪个环境（本地/测试/生产），再去对应环境的日志里找铁证；代码分析只能作为辅助验证，不能作为根因判定依据**。**技术根因只是现象，流程根因才是 bug 的真正起源**。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 审批接口四种契约风格 | 新写接口时没有统一规范可参照 | 制定全项目审批接口契约规范，新增审批接口必须照规范走 |
| `data = {}` 默认参数假防御 | JS 默认参数只对 `undefined` 生效，对 `''`/`null` 不生效 | API 模块禁止用默认参数兜底契约；后端要求对象，前端必须显式传对象 |
| 单测覆盖层不覆盖接缝 | 每层都"看起来对"但契约接缝处没人测 | 审批类 Controller 必须有 `@WebMvcTest` 覆盖空 body / 空字符串 / 缺字段三种场景 |
| 只回答技术根因没回答流程根因 | 技术根因只是现象（前端传错字符串），流程根因才是 bug 起源（为什么写出这种代码） | 复盘 bug 时必须问"为什么会写出这种代码"，不只问"哪行代码错了" |

### 操作规范（防复发）

1. **统一审批接口契约**：全项目审批类 Controller 必须用 `@Valid @RequestBody XxxApprovalRequest` DTO，统一字段 `comment`，不混用 `Map<String,String>` 或 `required=false`。
2. **前端 API 模块禁止默认参数兜底契约**：如果后端 `@RequestBody` 是必需的，前端必须显式传对象，不允许 `data = {}` 这种假防御。
3. **审批类 Controller 必须有 `@WebMvcTest`**：覆盖三种反序列化场景——空 body、空字符串、缺字段。
4. **审批操作必须有 E2E 烟雾测试**：真实点击→请求→后端 200 落库。
5. **Code Review 审批接口 PR 时必填**："参照了哪个已有接口的契约？前后端契约是否一致？"
6. **复盘 bug 必须问流程根因**：不只问"哪行代码错了"，要问"为什么会写出这种代码"。

### 防复发检查清单

新增审批类接口时，检查：

- [ ] Controller 签名符合全项目统一契约规范（`@Valid @RequestBody XxxApprovalRequest`）
- [ ] 前端 API 模块**显式传对象**，不依赖默认参数兜底
- [ ] 后端 Controller 有 `@WebMvcTest` 覆盖空 body / 空字符串 / 缺字段
- [ ] 审批操作有 E2E 烟雾测试
- [ ] PR 描述中标注"参照了哪个已有接口的契约"
- [ ] 复盘时回答了"为什么会写出这种代码"（不只是"哪行错了"）

### 验证命令

```bash
# 1. 排查审批类接口契约不统一（后端）
grep -rn "@RequestBody.*Map<String" backend/src/main/java --include="*Controller.java" | grep -i "approv\|reject"
# 应该返回空——审批类不应该用 Map 接收 body

# 2. 排查前端 API 模块的默认参数假防御
grep -rn "approve.*data = {}\|reject.*data = {}" src/api/modules/
# 应该返回空——审批类前端 API 不应该用默认参数

# 3. 排查审批类 Controller 是否有 WebMvcTest
find backend/src/test -name "*Controller*Test.java" | xargs grep -l "approve\|reject" 2>/dev/null
# 应该覆盖所有审批类 Controller

# 4. 服务器日志排查审批错误（生产）
ssh jetty@172.16.38.78 'grep -E "请求体不可读|Required request body" /var/log/xiyu-bid/application.json.log | tail -10'
```

### 相关文件

- `src/views/Resource/CAManagement.vue` — CA 审批前端入口（bug 修复后）
- `src/api/modules/ca.js` — CA API 模块（含 `data = {}` 假防御）
- `src/stores/ca.js` — CA Pinia Store
- `backend/src/main/java/com/xiyu/bid/resources/controller/CaCertificateController.java` — CA 审批 Controller
- `backend/src/main/java/com/xiyu/bid/resources/dto/CaApprovalRequest.java` — CA 审批 DTO
- `backend/src/main/java/com/xiyu/bid/project/controller/ProjectDraftingController.java` — 标书审核 Controller（参照样本，宽松契约）
- `backend/src/main/java/com/xiyu/bid/project/controller/ProjectInitiationController.java` — 立项审核 Controller（参照样本，严格契约）
- `backend/src/main/java/com/xiyu/bid/project/controller/ProjectClosureController.java` — 结项审核 Controller（参照样本，混合契约）
- PR #1516 — 本次 bug 修复
- PR #1509 — CO-459 原始实现（引入 bug 的 PR）
- §1 — 后端接口契约变更必须同步前端所有入口（同源教训）
- §23 — 全链路日志排查 SOP（本次排查使用）
