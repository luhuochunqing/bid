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

## 7. sync-env.sh stash pop 失败导致修改丢失，用 git fsck 找回

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


