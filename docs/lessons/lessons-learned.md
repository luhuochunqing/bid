# 通用工程教训与复盘

> 本文件记录跨模块、可复用的工程教训与流程改进，按 session 追加章节。

---

## 1. OSS 同步用户禁止写入本地默认密码

### 问题背景

2026-07-10 生产环境发现所有 OSS 同步用户可用测试密码 `123456` 登录。根因：commit `4a01054be`（CO-284）为 OSS 同步员工写入 `DEFAULT_PASSWORD_HASH`（`123456` 的 BCrypt 编码），且 `AuthService.loginOssUser()` 在 OSS 认证失败时回退到本地密码验证。生产日志显示当日已有 18 次通过该 fallback 路径成功登录。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 为 SSO/OSS 用户预设本地密码 | SSO/OSS 用户的密码真相源必须是外部认证系统，本地数据库不应存储可用密码 | 禁止为外部身份源用户生成默认本地密码；无本地密码时应写入锁定哈希 |
| 本地密码回退机制 | 回退路径绕过外部认证，形成后门 | 外部身份源认证失败必须直接返回 401，禁止本地密码回退 |
| 测试密码流入生产 | `123456` 等测试凭据不得出现在生产代码常量中 | 敏感常量（密码哈希、密钥、token）必须走配置或环境变量，且代码审查时重点检查 |

### 操作规范

1. `OrganizationUserSyncWriter` 等外部用户同步入口：新用户无本地密码时写入 `LOCKED_PASSWORD_HASH`，确保 `passwordEncoder.matches` 永远失败。
2. `AuthService` 等认证入口：OSS 用户认证仅委托 `OssDelegationService`，失败直接抛 `BadCredentialsException`，不尝试本地密码。
3. 生产问题排查时，除检查代码外，必须扫描数据库中是否存在统一默认哈希：

```bash
# 查找可能使用默认测试密码的 OSS 用户（哈希值以修复时为准）
SELECT username, external_org_source_app, password
FROM users
WHERE external_org_source_app IS NOT NULL
  AND external_org_source_app != ''
  AND password = '$2a$10$FwCOuxKv3WA8f2uwiUE23umE0ooMOPDOoOs2JTK49zN8i8PYLxK4y';
```

### 相关文档

- `specs/003-remove-staff-unify-oss-enabled/contracts/login-behavior.md` — OSS 登录契约
- `docs/lessons/root-cause-analysis-bcrypt-invalid-hash.md` — 历史 BCrypt 无效哈希根因
- 修复迁移：`backend/src/main/resources/db/migration-mysql/V1164__lock_oss_user_local_passwords.sql`

---

## 2. 后端接口契约变更必须同步前端所有入口

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

### 根因分析

**直接原因**：部署期间 `systemctl restart` 触发服务重启，重启期间 Nginx 无法连接后端导致 502。

**深层原因**：

1. **jar 被替换后 ShutdownHook 失效**：部署脚本先替换 jar 文件，再执行 `systemctl restart`。Spring Boot 的 ShutdownHook 在执行时需要加载类（Tomcat/Redis/Kafka 等），但此时 jar 已被替换，classloader 引用的类已变化，导致 `NoClassDefFoundError`，ShutdownHook 卡住。

2. **systemd 超时 SIGKILL**：`TimeoutStopSec` 默认 90 秒，ShutdownHook 卡住后 systemd 发送 SIGKILL 强制终止，可能导致正在处理的请求被中断、数据库连接未正常关闭、缓存数据丢失。

3. **并发部署无锁机制**：多个 agent/人可以同时执行部署脚本，没有部署锁或互斥机制。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 并发部署导致 502 | 部署期间不能并发部署 | 部署前确认没有其他人在部署，或引入部署锁 |
| jar 替换后 ShutdownHook 失效 | 先停服务再替换 jar，不能先替换再 restart | 部署顺序：stop → 替换 jar → start |
| ShutdownHook 卡住 | NoClassDefFoundError 导致 ShutdownHook 无法正常执行 | 避免在 ShutdownHook 中加载新类 |
| systemd SIGKILL | 强制终止可能导致数据丢失 | 配置合理的 `TimeoutStopSec`，监控 ShutdownHook 执行时间 |
| 502 持续较久 | 服务重启期间 Nginx 无健康检查兜底 | Nginx 配置 `proxy_next_upstream` 或维护页面 |

### 部署顺序 SOP（防复发核心）

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
    echo "Service is UP"; break
  fi
  sleep 5
done

# 4. 验证 readiness 也恢复
curl -s http://127.0.0.1:8080/actuator/health/readiness
```

### 部署协作规范

1. **部署前确认无人正在部署**：检查 `systemctl status` 和最近部署日志，确认服务稳定运行后再开始部署。
2. **部署顺序：stop → 替换 → start**：不能先替换 jar 再 `restart`，避免 ShutdownHook 因 jar 变化而失效。
3. **部署后等待健康检查恢复**：不能部署完就离开，必须等待 `health` + `readiness` 全部 UP。
4. **部署后通知团队**：在协作群通知"已部署版本 X，服务已恢复"，避免其他人误判 502 为故障。
5. **502 排查第一步**：检查 `systemctl status` 和 `journalctl`，确认是否有人正在部署。

### 相关文档

- `scripts/release/remote-deploy.sh` — 远程部署脚本
- `scripts/release/package-release.sh` — 打包脚本
- `/etc/systemd/system/xiyu-bid-backend.service` — systemd 服务配置

---

## 6. 部署后配置未生效的排查方法论

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 部署后密码未生效，不知从何排查 | 需要系统化的部署验证清单 | 每次部署后按「代码→配置→数据→运行时」四层验证 |
| 数据库 UPDATE 含 `$` 被 shell 截断 | 命令行执行 SQL 要注意特殊字符转义 | 含特殊字符的 SQL 必须使用文件方式执行 |
| 硬编码的 BCrypt 哈希未验证有效性 | "看起来像"不等于"真的是" | 任何密码哈希必须通过 `matches()` 验证后才能入库 |

### 操作规范（部署后验证四层模型）

```
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

### 相关文档

- `docs/lessons/root-cause-analysis-bcrypt-invalid-hash.md` — 完整根因分析
- `docs/lessons/shell-gotchas.md` — Shell 转义陷阱

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

### 经验教训（四原则）

| 问题 | 教训 | 规范 |
|------|------|------|
| 打包后只检查 jar 大小 | jar 大小相近时无法区分新旧版本 | **打包后必须验证 jar 内容**：用 `javap -v` 或 `unzip -p ... \| strings` 检查关键 class 文件的常量池 |
| Maven 缓存旧 class | `mvn package` 不一定触发重新编译 | **`mvn clean` 后重新打包**：确保使用最新编译结果，不要依赖增量编译 |
| SSH 终端中文乱码 | `javap` 通过 SSH 显示中文常量为 `???` | **用 `xxd` 或字节比较验证**：不依赖终端中文显示，用 `xxd \| grep` 或 `diff <(xxd) <(xxd)` 比较字节 |
| 重新打包整个 jar 耗时长 | 全量打包 + 上传 + 重启耗时大 | **用 `jar uf` 局部更新**：只更新修改的 class 文件，无需重新打包整个 jar |

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

### 根因

**直接原因**：`CurrentUserResolver` 使用了 `@RequestScope` 注解，在 HTTP 请求线程外无法实例化。

**触发链路**：
1. 组织事件 SDK 的 `StartCallback` 监听 `ApplicationReadyEvent`
2. `StartCallback.onApplicationEvent()` 调用 `CacheBeanComponent.initCacheBean()`
3. `initCacheBean()` 内部调用 `applicationContext.getBeansOfType()` 扫描所有 bean
4. Spring 尝试实例化 `@RequestScope` 的 `CurrentUserResolver`，但此时没有 request 上下文
5. 抛出 `ScopeNotActiveException`，应用启动失败

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| `@RequestScope` Bean 在非 HTTP 线程中被扫描 | 第三方 SDK 可能在启动时通过 `getBeansOfType()` 扫描所有 bean | **生产环境启用 SDK 时必须测试完整启动流程**，不能只在本地 dev 环境验证 |
| `CacheBeanComponent.initCacheBean()` 不区分 bean scope | SDK 内部实现无法控制，必须从自身代码防御 | **避免使用 `@RequestScope`**，改用单例 + 直接查询或 ThreadLocal |
| 本地 dev 环境 `XIYU_ORG_EVENT_SDK_ENABLED=false` | SDK 功能被关闭时不会触发此问题 | **部署前必须确认服务器环境变量与本地测试环境一致**，特别是功能开关 |
| 只看代码无法发现启动时 scope 冲突 | 代码审查不覆盖"启动时 bean 扫描顺序" | **新增 `@RequestScope`/`@SessionScope` Bean 时，必须在 CI 中增加生产 profile 启动测试** |

### 修复方案

将 `CurrentUserResolver` 从 `@RequestScope` 改为普通单例 `@Component`，每次调用直接查询数据库（无缓存，避免 ThreadLocal 泄漏风险）：

```java
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

**为什么不使用 ThreadLocal 缓存**：Tomcat 线程池会复用线程，如果不在请求结束时清理 ThreadLocal，下一个请求可能拿到上一个用户的缓存数据，造成安全隐患。用户查询是走索引的简单查询，性能影响可忽略。

### 防复发检查清单

- [ ] 新增 Bean 时检查是否使用了 `@RequestScope` / `@SessionScope` 等非 singleton scope
- [ ] 如果必须使用非 singleton scope，评估是否与 SDK 的 `getBeansOfType()` 扫描冲突
- [ ] 本地测试时启用生产环境的功能开关（如 `XIYU_ORG_EVENT_SDK_ENABLED=true`）
- [ ] 部署失败时第一时间检查 `journalctl` 中的 `ScopeNotActiveException` 关键字

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

## 21. @EventListener(ApplicationReadyEvent) 阻塞主线程导致 readiness 延迟恢复 UP

### 根因

**直接原因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class)` 监听 `ApplicationReadyEvent`，在主线程同步执行 SDK 初始化（`register()` + `initCacheBean()` + `KafkaProcessor.start()`）。Spring Boot 的 `ApplicationAvailabilityBean` 也通过 `@EventListener` 接收 `ApplicationReadyEvent` 来切换 `ReadinessState` 从 `REFUSING_TRAFFIC` 到 `ACCEPTING_TRAFFIC`。两者都在主线程同步执行，存在时序竞争。

**关键矛盾点**：`@EventListener` 是同步的，即使 `OrganizationEventSdkKafkaStarter` 标注了 `@Order(Ordered.LOWEST_PRECEDENCE)`，它仍然在主线程执行。如果 `register()` / `initCacheBean()` / `KafkaProcessor.start()` 中任一步骤阻塞（如网络超时、Kafka broker 不可达），主线程被占用，`AvailabilityChangeEvent` 发布延迟 → `ReadinessState` 切换延迟 → readiness 持续 503。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| `@EventListener(ApplicationReadyEvent)` 在主线程同步执行 | **`@EventListener` 默认同步**，长时间任务会阻塞主线程 | 启动期耗时操作（SDK 初始化、网络调用）必须用 `@Async` 或独立线程池 |
| readiness 持续 OUT_OF_SERVICE 但 liveness UP | **readiness 和 liveness 是独立状态机**，readiness 由 `ApplicationReadyEvent` 触发切换 | 排查 readiness 问题时，检查 `ApplicationReadyEvent` 是否被延迟发布 |
| Kafka SDK 初始化阻塞较久 | **第三方 SDK 的网络调用不可控**，必须假设会超时 | 包装第三方 SDK 启动逻辑时，加超时 + 异步执行 |
| 临时修改生产配置排查问题 | **排查完必须清理临时配置** | 修改 `/etc/xiyu-bid/backend.env` 后记录到部署报告，部署完恢复 |
| `@Order(Ordered.LOWEST_PRECEDENCE)` 不能解决阻塞 | **`@Order` 只决定顺序，不改变同步性** | 需要异步用 `@Async`，不是 `@Order` |

### 正确做法

```java
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

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 外部诊断报告称根因为"V1223 三态收口"，但 V1223 不存在（迁移最大 V1105） | 任何"别人给的根因"都是假设，必须回到代码与机器真相复核 | 拿到诊断报告后，第一步是用 `grep`/文件列表验证报告中引用的迁移版本号、文件名是否真实存在 |
| `baseline-on-migrate: true` + `baseline-version: 1050` 让 `V101` 静默跳过，无报错无告警 | "迁移文件存在" ≠ "已执行"，低于 baseline 的脚本只是历史档案 | 凡是依赖某迁移脚本灌入种子的功能，必须在 PR 中验证该脚本版本号 > baseline，或确认有 ApplicationRunner 兜底 |
| `@Profile("e2e")` 把唯一 seed 来源锁死，dev profile 无数据 | profile 限定 + 唯一数据源 = 隐形空表 | 一个数据源被 `@Profile` 限定时，必须问"其他 profile 从哪获取这份数据"，答案为"没有"即潜在空表 |
| 已保存交付物同时被 `el-upload` file-list 和 `.deliverable-list` 渲染 | UI 容器的数据源必须互斥，一个数据只能有一个"渲染责任人" | 已保存数据走展示容器（可下载链接），待上传数据走上传容器（el-upload），不可混用 file-list |

### 操作规范

1. **拿到外部诊断报告先复核**：报告中引用的迁移版本号、文件名、行号，必须用 `grep`/`ls` 自己验证一遍再动手修。
2. **Flyway baseline 排查清单**：依赖某迁移脚本种子的功能，启动后若数据为空，先查 `flyway_schema_history` 表确认该版本是否真的执行过，而不是假设"文件在就跑过"。
3. **@Profile 数据源审查**：新增 `@Profile` 限定的 seed/初始化逻辑时，必须在 PR 描述中列出"其他 profile 如何获取这份数据"，缺失即技术债登记。
4. **UI 数据源互斥原则**：同一份数据只能由一个容器负责渲染。已保存数据与待上传数据必须走不同容器，禁止共用 `file-list`。

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

## 27. 迁移脚本之间不能互相覆盖（V1098 vs V1105 迁移漂移）

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

## 28. OkHttp3 传递依赖导致 RestTemplate GET 请求全面失败

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

## 29. 权限 Bug 必须审视同一业务动作的所有 UI 入口 + 前后端对称修复（CO-400 五轮 + CO-415 归纳）

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

## 30. 服务器字体缺失 + 多 Agent 并行修同一 bug 的协调教训（CO-438）

### 根因

**systemd 启动 Java 时未设 `-Djava.awt.headless=true`**。Java AWT 在无显示器 Linux 上不加 headless 会走 `X11FontManager`，fontconfig 加载失败后 POI `autoSizeColumn` 抛 `NullPointerException: Fontconfig head is null`。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| systemd 缺 headless 参数 | 无显示器 Linux 服务器上 Java AWT 必须设 headless | systemd ExecStart 必须包含 `-Djava.awt.headless=true`；启动类加 `System.setProperty("java.awt.headless", "true")` 兜底 |
| 删防御代码 ≠ 修 bug | try-catch 是防御性编程，删除后比不修更糟 | 禁止删除已有的异常兜底代码，除非确认根因已消除且兜底不再需要 |
| 每列独立 try-catch 导致 Sentry 重复上报 | 首列失败后应跳过剩余 autoSize，避免重复触发失败 | autoSize 失败后设 flag，剩余列直接走 fallback |
| 多 Agent 同时修同一 bug | 多个 PR 互相冲突，策略不一致 | 开工前跑 `who-touches.sh`，指定一个 Agent 统一修复 |
| 分支名/commit 与实际改动不符 | commit message 文不对题 | commit message 必须准确描述实际改动 |
| 一个 PR 混入无关改动 | 多个无关 bug 混在一起 | 一个 PR 只做一件事 |

### 防复发措施

1. **架构测试**：新增 `business_code_should_not_call_sheet_autoSizeColumn_directly` 规则，禁止业务代码直接调用 `Sheet.autoSizeColumn()`，必须走 `ExcelAutoSizeHelper`
2. **三层防御**：systemd headless + 启动类 System.setProperty + ExcelAutoSizeHelper 降级
3. **单元测试**：用 Mockito mock Sheet 模拟字体异常，验证首列失败后整批降级行为

### 相关文档

- `root-cause-analysis-co-438-fontconfig-head-null.md` — 完整根因分析
- `backend/src/main/java/com/xiyu/bid/common/util/ExcelAutoSizeHelper.java` — 统一入口 + 降级逻辑
- `backend/src/main/java/com/xiyu/bid/XiyuBidApplication.java` — 启动类 headless 兜底
- `backend/src/test/java/com/xiyu/bid/ArchitectureTest.java` — autoSizeColumn 禁止直接调用规则

## 31. 角色权限定义必须同时覆盖前端导航权限和后端 API 权限（CO-439）

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

## 32. REQUIRES_NEW + try-catch 反模式导致 UnexpectedRollbackException（CO-440）

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

## 33. 审批接口契约不统一 + JS 默认参数陷阱导致 `Required request body is missing`（CO-459）

### 问题背景

CO-459 实现「CA 信息管理 - 我的申请 / 我的审批」功能后，在生产环境验收时，投标管理员审批 CA 借用申请返回 400：

```
请求体格式错误: Required request body is missing: public ResponseEntity<CaBorrowApplicationDTO>
CaCertificateController.approve(Long, CaApprovalRequest, UserDetails)
```

服务器日志铁证（traceId `68e838cb89714d4e9f518f774d81effc`，2026-07-02 09:17:41.943，400 + 9ms 返回）证明请求到达后端但反序列化失败。

### 根因（流程性，非孤立 bug）

1. **项目内审批接口存在四种契约风格，无统一规范**：立项用 `@Valid @RequestBody XxxRequest`、标书审核用 `@RequestBody(required=false) Map<String,String>`、结项审核 reject 有 body / approve 无 body、CA 借用新写时 `@Valid + @RequestBody + @NotBlank`。CO-459 写新代码时**没有参照已有审批接口契约风格**，独立设计了"更严格"的 DTO，但前端没同步按新契约传参。

2. **JavaScript 默认参数陷阱**：`data = {}` 只在参数为 `undefined` 时生效，传 `''`（空字符串）**不会触发默认值**。结果 `httpClient.post(url, '')` 发送空字符串作为 body，后端 `@RequestBody` 期望 JSON 对象，反序列化失败。这个陷阱在 code review 时肉眼很难发现——`data = {}` 看起来已经做了防御。

3. **单测覆盖的是 store 层，不是端到端契约**：每一层都"看起来对"，但**契约的接缝处没人测**。

4. **元根因：没有"契约单一源"**：项目里审批接口有四种写法，**没有统一的契约规范文档**。开发者写第五个审批接口（CA 借用）时，面临四种参考样板，选哪种全凭个人偏好。选了严格契约但前端没同步升级——**契约脱节**。

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

---

## 34. hasAnyRole 双轨制：用 ArchUnit 总数断言守卫技术债迁移（ Constitution VI 落地）

> 日期: 2026-07-02
> 来源: Spec Kit 024 消除 @PreAuthorize hasAnyRole 双轨制技术债
> 沉淀者: zcode agent

### 问题背景

后端 201 处 `@PreAuthorize` 使用 `hasAnyRole`/`hasRole` 角色枚举式白名单（方法级 187 + 类级 14；方法级含 12 处 `static final String` 常量引用，Java 编译期内联后 ArchUnit 字节码扫描统一捕获），与 `RoleProfileCatalog` 的细粒度权限键形成双轨制。

根因：`eb58f2817`（2026-06-16）切断 `bid-otherDept`/`bid-administration`/`bid-Team` 的 legacy `ROLE_STAFF`/`ROLE_MANAGER` 兼容（堵越权，正确），但未同步迁移依赖该 authority 的白名单 → 系统分裂为"新模型给角色赋权 / 旧模型拒绝承认"的矛盾状态 → CO-362→CO-466 共 20+ 个反复返工的 403 PR。最近症状：`bid-otherDept` 用户(09118) 2026-07-02 访问 `GET /api/task-extended-fields` 被 403。

### 教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 外部审核（gemini）发现文档数据偏差（177 vs 实际 176 注解 + 编译期内联 12 处常量 = ArchUnit 实测 187） | 起草者也要对自己的数据断言做 grep 复核，不能只信初版统计 | 文档数据断言必须用精确 `grep -rEn` 正则，不能用未加 `-E` 的 BRE |
| 规则 1（`@ArchTest ArchRule` 字段形式）会报告所有存量违规，阻塞 CI | 过渡期守卫不能直接用硬失败 ArchRule | 用"总数断言 + assumeTrue 跳过"双轨：规则 1（`void` 方法 + AssertJ 断言实际数 == EXPECTED）锁定存量，规则 2（ArchRule）在 EXPECTED 归零后才独立生效 |
| `@Disabled` 注解不适用于 `static final ArchRule` 字段 | JUnit 5 的 `@Disabled` 只能加在方法/类上 | 要 disable ArchRule，改成 `void method(JavaClasses)` 形式，或用 `Assumptions.assumeTrue` 在方法内跳过 |
| `static final String ADMIN_MANAGER_EXPR = "hasAnyRole(...)"` 常量会被编译期内联 | ArchUnit 读字节码，常量引用和字面量写法都被同等捕获 | 守卫基线数必须以 ArchUnit 实测为准，不能只用 grep 字面量统计 |
| Controller 级豁免清单无法发现"Controller 内部分方法迁移"（gemini 改进建议） | 豁免粒度要用"使用点整数断言"而非"Controller 名单" | 总数断言 `actual == EXPECTED` 比名单更精确，且强制开发者迁移后递减 EXPECTED |

### ArchUnit 双轨守卫范式（可复用）

```java
// 规则 1（主守卫，过渡期）：实际违规数 == EXPECTED 常量
private static final int EXPECTED_LEGACY_USE_COUNT = 201;  // 方法级187+类级14，迁移递减

@ArchTest
public static final void legacy_count_must_match_baseline(JavaClasses classes) {
    int actual = countViolations(classes);
    Assertions.assertThat(actual)
        .as("违规总数须与 EXPECTED 一致，不一致说明偷偷新增或忘改常量")
        .isEqualTo(EXPECTED_LEGACY_USE_COUNT);
}

// 规则 2（硬失败门禁，最终态）：扫描每个注解，含违规即报
@ArchTest
public static final void should_not_use_violation(JavaClasses classes) {
    int actual = countViolations(classes);
    if (actual == EXPECTED_LEGACY_USE_COUNT) {
        Assumptions.assumeTrue(true, "过渡期存量由规则 1 宽容");  // 跳过
        return;
    }
    methods().that().areAnnotatedWith(PreAuthorize.class)
        .should(NOT_USE_VIOLATION).check(classes);  // 最终态硬失败
}
```

### 操作规范

1. **过渡期守卫**：新增类似技术债迁移时，用规则 1（总数断言）锁定存量基线，禁止新增
2. **迁移流程**：每消除一处违规，递减 `EXPECTED_LEGACY_USE_COUNT`，规则 1 强制对齐
3. **最终态**：EXPECTED 归零后，删除规则 1 + 删除规则 2 的 `assumeTrue` 跳过逻辑，规则 2 升级为永久硬失败门禁
4. **负向验证**：守卫上线时必须做负向验证（临时加违规注解 → 规则 1 失败 → 恢复 → 全绿），证明守卫有效

### 验证命令

```bash
# 守卫当前状态（规则 1 锁定 187，规则 2 跳过）
cd backend && mvn test -Dtest='ArchitectureTest#legacy_hasanyrole_count_must_match_baseline+preauthorize_should_not_use_role_enumeration'

# 守卫负向验证（临时加违规 → 应失败）
# 改某 Controller 注解为 hasAnyRole → 跑规则 1 → expected:187 but was:188

# 迁移进度指标（ArchUnit 实测，含编译期内联的常量）
cd backend && mvn test -Dtest='ArchitectureTest#legacy_hasanyrole_count_must_match_baseline' 2>&1 | grep "but was"
```

### 相关文档

- `docs/architecture/preauthorize-unification-design.md` — 完整设计 RFC
- `specs/024-preauthorize-unification/` — Spec Kit 产物（spec/plan/tasks/research）
- `specs/024-preauthorize-unification/review-response.md` — gemini 审核回应（4 数据偏差复核）
- `.specify/memory/constitution.md` §VI — Authorization Unification 原则
- §23 — 全链路日志排查 SOP（本次根因定位使用）
- §29 — CO-400/CO-415 hasAnyRole 陷阱（同类教训）

---

## 35. Spring Data JPA 派生查询方法传 null 不会变成"无过滤条件"（PR !1563）

### 问题背景

`https://winbid-test.ehsy.com/knowledge/brand-auth` 批量导出 Excel 文件内容为空，只有表头无数据行。列表查询正常有数据，仅导出空。

按 `§23 全链路日志排查 SOP` 定位：

- **Layer 1（Sentry）不适用**：此 bug 不触发任何异常 — `WHERE status = NULL` 是合法 SQL，MySQL 不报错，HTTP 200 OK 正常返回。属于 SOP 定义的"Layer 2 适用：业务逻辑错误、Sentry 未覆盖场景"。
- **Layer 2（代码证据链溯源）**：通过代码证据链直接定位根因，无需 traceId。
- **Layer 3（git log 追溯）**：`git log -S "findByStatus(null)"` 显示问题代码从 `c6c326341 initial snapshot` 就存在 → **从初始快照引入的历史 bug，非回归**。

### 根因

[BrandAuthExportService.exportAll()](file:///backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/BrandAuthExportService.java) 调用 `repository.findByStatus(null)`，**期望"传 null 查全部"**，但这是 Spring Data JPA **派生查询方法**（derived query method），传 null 会生成 `WHERE status = ?` 绑 null 参数，**不会**自动跳过过滤条件。MySQL 中 `status = NULL` **永远返回 false**（NULL 三值逻辑）→ 结果集为空 → Excel 只有表头无数据行。

### 完整证据链

1. [ManufacturerAuthorizationJpaRepository.java:18](file:///backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/infrastructure/persistence/repository/ManufacturerAuthorizationJpaRepository.java#L18) 定义派生方法：
   ```java
   List<ManufacturerAuthorizationEntity> findByStatus(AuthStatus status);
   ```
2. [BrandAuthExportService.java:50](file:///backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/BrandAuthExportService.java#L50) 调用 `repository.findByStatus(null)` 期望"查全部"
3. [ManufacturerAuthorizationEntity.java:118-120](file:///backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/infrastructure/persistence/entity/ManufacturerAuthorizationEntity.java#L118-L120) status 列 `nullable = false` + `@PrePersist` 兜底设 `ACTIVE` → 数据库里 status **永远非 NULL** → `WHERE status = NULL` 必返回空集
4. **对照证据**：[ListManufacturerAuthAppService.java:111](file:///backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/ListManufacturerAuthAppService.java#L111) 列表查询走 `jpaRepository.findAll(spec, pr)`（动态 Specification，过滤条件按需拼接）→ 列表有数据，导出空 → 正好解释现象

### 教训

1. **Spring Data JPA 派生查询方法传 null 不会变成"无过滤条件"**：派生方法（`findByXxx`）对 null 参数的处理是直接生成 `WHERE xxx = ?` 绑 null，**不会**像 MyBatis 的 `<if test=` 那样自动跳过条件。MySQL 中 `= NULL` 永远返回 false（NULL 三值逻辑），结果集为空。

2. **"查全部"必须用 `findAll()`，不能用 `findByXxx(null)`**：这是 Spring Data JPA 的标准坑。
   - 查全部：`jpaRepository.findAll()`
   - 可选条件查询：用 `Specification` 动态拼接（如本项目 `ListManufacturerAuthAppService` 的做法），或用 `@Query("... where (:status is null or status = :status)")`

3. **Code Review 时看到 `findByXxx(null)` 必须质疑**：这是反模式信号。派生查询方法的参数应标注 `@NonNull` 或在 Javadoc 明确"不接受 null"。

4. **对照判别法**：当出现"列表有数据，导出/报表空"时，优先检查导出查询是否走了与列表不同的查询路径，特别是 `findByXxx(null)` 这类派生方法调用。

5. **导出与列表查询应共用 Specification**：本次修复采用方案 B — 让导出复用列表查询的 `Specification`，确保过滤语义一致。这样既修复了 bug，又让"导出当前筛选结果"成为原生能力（更符合用户预期）。

### 验证命令

```bash
# 检查项目中是否有 findByXxx(null) 的误用
grep -rn "findBy[A-Z][a-zA-Z]*(null)" backend/src/main/java --include="*.java"
# 期望输出：无匹配，或匹配项是明确接受 null 的 @Query 方法

# 检查派生查询方法是否被 null 调用
grep -rn "repository\.findBy.*null" backend/src/main/java --include="*.java"
```

### 修复方案（方案 B：导出与列表共用 Specification）

- `ListManufacturerAuthAppService`：抽取 `buildSpec(filter)` + 新增 `listAllForExport(filter)`
- `BrandAuthExportService`：移除 `repository` 依赖（根因点），改注入 `listService`；`exportAll()` → `exportByFilter(filter)`
- `ManufacturerAuthorizationController /export`：接收与 `/list` 一致的过滤参数
- 前端 `doExport`：参数与后端对齐

### 相关文档

- PR !1563 — 本次修复
- §23 — 全链路日志排查 SOP（本次排查使用）
- §1 — 后端接口契约变更必须同步前端所有入口（同类：导出与列表参数对齐）
- `backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/ListManufacturerAuthAppService.java` — Specification 动态拼接的正确范例
- `backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/BrandAuthExportService.java` — 修复后的导出服务

## 36. 后端枚举归一化必须同步前端所有展示位 + 静态检查防复发（PR !1571 回归）

### 问题背景

2026-07-03 用户反馈：项目列表和项目详情页的「客户类型」字段值展示为英文（如 `CENTRAL_SOE`、`GOVERNMENT`），刚部署后出现。

按 `§23 全链路日志排查 SOP` 定位：

- **Layer 1（Sentry）不适用**：不是异常崩溃，是显示文本错误，Sentry 不捕获。
- **Layer 2（结构化日志/TraceId）不适用**：没有报错日志，HTTP 200 OK 正常返回。
- **Layer 3（git log + cherry-pick 追溯）✅ 适用**：典型回归问题，PR 修复 A bug 时引入 B bug。

### 根因

**触发 PR**：!1571 `fix(project): 修复投标项目列表筛选客户类型(央企)筛不出数据的问题`（commit `10037e445`，2026-07-02 第 37 次部署上线）

PR !1571 为修复「筛选央企筛不出数据」问题，把后端 `customerType` 返回值统一归一化为 CustomerType 枚举名（`CENTRAL_SOE`/`GOVERNMENT`/...），同时把前端筛选项 `value` 从 `GOVERNMENT_INSTITUTION`/`PRIVATE_ENTERPRISE`/`FOREIGN_HK_MACAO_TW` 改为 `GOVERNMENT`/`PRIVATE`/`FOREIGN`（label 仍是中文）。

**遗漏点**：PR !1571 只对齐了筛选项的 value，**遗漏了「展示位」的翻译层**：

- `src/views/Project/List.vue:96` 列表列直接渲染 `{{ row.customerType }}`
- `src/components/project/ProjectBasicInfoCard.vue:16` 详情页直接渲染 `{{ project?.customerType }}`
- `src/views/Analytics/components/CustomerTypePanel.vue:40,53,81` Analytics 看板三处直接渲染（PR !1632 顺手修复）

归一化前数据是中文（如「央企」），直接显示正常；归一化后变英文枚举名（`CENTRAL_SOE`），直接展示就是英文。

### 经验教训

1. **后端枚举字段归一化是高风险变更**：归一化会改变所有下游消费方的字段值语义，必须审视整个「字段消费矩阵」——筛选项 value、展示位、导出 Excel、API 返回值、E2E 断言等所有入口，不能只改一个入口。
2. **展示位必须有翻译层**：枚举名 → 中文 label 的翻译必须经过统一 formatter 函数（如 `customerTypeLabel`），不能在 template 直接 `{{ row.xxx }}` 渲染。
3. **缺乏静态检查是根因**：PR !1571 code-review 时人工遗漏了展示位，若有静态检查脚本扫描「直接渲染枚举字段」模式，可在 commit 阶段拦截。

### 防复发机制（PR !1632 落地）

新增静态检查脚本 `scripts/check-vue-enum-direct-render.mjs`，集成到 `.githooks/pre-commit`：

- **检测模式**：`<template>` 中 `{{ row.customerType }}` / `{{ project.customerType }}` 等直接字段访问，未经过 formatter 函数
- **字段清单**：`customerType`（后续可扩展 `priority`/`stage`/`source` 等，扩展前需先处理存量）
- **豁免机制**：`<!-- SAFE: <具体豁免理由> -->` 上方注释，用于调试页/原始数据查看等合理场景
- **阻断级别**：⛔ 强制（exit 1）

### 操作规范（建议固化到 code-review skill）

1. **后端枚举归一化 PR 必须扫描前端所有消费位**：用 `grep -r "fieldName" src/` 列出所有引用，逐一确认是否需要翻译层
2. **新增 formatter 函数时，同步扩展 `check-vue-enum-direct-render.mjs` 的 `ENUM_FIELDS` 清单**
3. **Tender 表的 customerType 是外部抓取的原始中文字符串（未归一化），与 Project 模块的归一化枚举名不同源**：TenderTable.vue:62 已用 SAFE 注释豁免

### 验证命令

```bash
# 全量扫描（不依赖 git staged）
node -e "
import('fs').then(fs => {
  const ENUM_FIELDS = { customerType: 'customerTypeLabel' };
  // ... 见 scripts/check-vue-enum-direct-render.mjs
})
"

# staged 文件检查（pre-commit 自动执行）
node scripts/check-vue-enum-direct-render.mjs
```

### 相关文档

- PR !1571 — 触发回归的 PR
- PR !1632 — 本次修复 + 防复发脚本
- §23 — 全链路日志排查 SOP（本次排查使用 Layer 3 git 追溯）
- §1 — 后端接口契约变更必须同步前端所有入口（同类：归一化需同步所有展示位）
- §29 — 权限 Bug 必须审视同一业务动作的所有 UI 入口（同类：审视整个字段矩阵）
- `scripts/check-vue-enum-direct-render.mjs` — 防复发静态检查脚本
- `src/views/Project/utils/projectListFormatters.js` — customerTypeLabel formatter 函数

## 37. 筛选语义必须与展示列对齐 + 「null 永真 fallback」是隐形 bug 放大器（PR !1642）

### 问题背景

用户在投标项目列表按投标负责人筛选"陈梦瑶"，张莉娜的项目也显示出来。用户反馈"PR1574 已经改了但是没生效"，要求看服务器数据库。

排查后发现：**PR1574 实际上已部署生效**，"没生效"是错觉。真正的根因是 PR1574 修复后筛选真正工作，反而暴露了之前被掩盖的 OR 匹配语义设计问题。

### 根因链路（双层 bug 叠加）

**第一层（PR1574 修复前 — 筛选根本不工作，但伪装成正常）**：

```javascript
// UserPicker.vue（修复前）
:value-key="valueField"  // valueField 默认 'id'

// 但 selectOptions 生成的 option 格式是 { value, label }
// option 对象中没有 id 字段 → 选中后值为 undefined
```

```javascript
// useProjectFilter.js matchId 函数
function matchId(filterVal, ...fieldVals) {
  if (filterVal == null || filterVal === '') return true  // ← undefined == null 为 true
  // ...
}
// → 筛选值 undefined → 永远返回 true → 等于不筛选 → 所有项目都显示
```

**第二层（PR1574 修复后 — 筛选生效，暴露 OR 语义问题）**：

```javascript
// useProjectFilter.js:68（修复前）
if (!matchId(f.biddingLeaderId, p.biddingLeaderId, p.secondaryBiddingLeaderId)) return false
//                                                                    ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
// matchId 是 fieldVals.some(...) → 主负责人 OR 副负责人任一匹配即命中
```

而展示列 `biddingLeaderName` 只显示主负责人姓名（来自 `project_initiation_details.bidding_leader_name`，只存主负责人，没有副负责人字段）。

**结果**：筛"陈梦瑶"时，主=张莉娜/副=陈梦瑶的项目（project 136）被命中（副匹配），但列表显示"张莉娜"，用户误以为筛错了。

### 数据库证据（生产 winbid）

| project_id | 展示列 `bidding_leader_name` | 主投标 ID | 副投标 ID | 筛"陈梦瑶"是否命中 | 用户看到 |
|---|---|---|---|---|---|
| 114 | 陈梦瑶 | 7246 陈梦瑶 | NULL | ✓ 主匹配 | 陈梦瑶 ✓ |
| 136 | **张莉娜** | 7396 **张莉娜** | 7246 **陈梦瑶** | ✓ **副匹配** | **张莉娜** ✗ |
| 146 | 陈梦瑶 | 7246 陈梦瑶 | 7396 张莉娜 | ✓ 主匹配 | 陈梦瑶 ✓ |

### 教训

1. **「筛选值==null 时永真」是隐形的 bug 放大器**：`if (filterVal == null) return true` 是合理的"空值不过滤"设计，但当上游组件因配置错误返回 `undefined` 时，`undefined == null` 为 true → 永远返回 true → 等于不筛选。**这个 fallback 把"筛选不工作"伪装成"筛选正常但所有项目都显示"，掩盖了真正的 bug**。设计 fallback 时要考虑上游传入 `undefined` 的场景，必要时用 `filterVal === undefined` 严格判断并报警。

2. **修复一个 bug 可能暴露另一个隐藏 bug**：PR1574 修复 UserPicker value-key 后，筛选真正生效，反而暴露了 OR 匹配语义问题。排查时**不能因为"刚修过"就跳过验证**，必须确认修复是否真正生效，以及修复后是否暴露新的设计问题。用户反馈"修了没生效"时，第一步是验证修复是否真的部署，而非假设没部署。

3. **筛选语义必须与展示列对齐**：筛选用「主 OR 副」匹配，但展示列只显示主负责人姓名 → 用户看到"筛 A 命中 B"的错觉。检查清单：
   - 筛选匹配的字段范围 vs 展示列显示的字段范围是否对齐？
   - 如果筛选匹配主+副，展示列是否也显示主+副？
   - 如果展示列只显示主，筛选是否也只匹配主？

4. **展示用姓名、筛选用 ID 的双数据源设计需要强同步**：
   - 展示用 `project_initiation_details.bidding_leader_name`（VARCHAR，只存主负责人）
   - 筛选用 `project_lead_assignment.primary_lead_user_id` + `secondary_lead_user_id`（BIGINT，主+副）
   - 两表无外键约束、无强同步机制。转派/投标负责人分配时如果只改 ID 不回写姓名，就会出现"筛 A 命中但显示 B"的问题。

5. **前端内存筛选 vs 后端筛选的契约必须一致**：前端 `useProjectFilter.js` 做内存筛选（不传参给后端），但后端 `ProjectController.java` 也有同样的筛选逻辑（dead code，前端不传参）。**虽然后端代码不执行，但契约必须保持一致**，否则未来改成后端筛选会踩坑。

### 操作规范

1. **设计筛选 fallback 时考虑 `undefined` 场景**：
   ```javascript
   // 危险：undefined == null 为 true，把 bug 伪装成正常
   if (filterVal == null) return true

   // 更安全：区分 null（明确不筛选）和 undefined（上游错误）
   if (filterVal === null || filterVal === '') return true
   if (filterVal === undefined) {
     console.warn('[useProjectFilter] filterVal is undefined, possible upstream bug')
     return true // 或 return false 视业务而定
   }
   ```

2. **修复 bug 后必须验证「修复是否暴露新问题」**：
   - 验证修复是否真的部署（grep 服务器产物，而非假设）
   - 验证修复后功能是否符合用户预期（而非只验证"不报错"）
   - 验证修复后是否暴露之前被掩盖的设计问题

3. **筛选匹配范围必须与展示列对齐**：
   - 筛选匹配主+副 → 展示列必须显示主+副
   - 展示列只显示主 → 筛选只匹配主
   - 在 PR review 时用表格列出「筛选匹配字段 vs 展示字段」对照

4. **展示用姓名 + 筛选用 ID 的双数据源必须有同步机制**：
   - 转派/分配时同步回写姓名列
   - 或展示列也通过 ID 实时反查姓名（而非读存储的姓名字段）

### 验证命令

```bash
# 1. 确认 PR1574 是否真的部署（服务器前端 chunk）
ssh -i ~/.ssh/xiyu_cursor_deploy jetty@172.16.38.78 \
  'grep -oE "value-key[^,}]{0,50}" /srv/www/xiyu-bid/assets/UserPicker-Co7JMLd0.js'
# 期望：value-key":"value"

# 2. 数据库验证姓名/ID 是否不同步
mysql -h winbid-01.test.rds.ehsy.com -u ea_bid -p"***" winbid -e "
SELECT d.project_id, d.bidding_leader_name AS 展示姓名,
       a.primary_lead_user_id AS 主ID, u2.full_name AS 主姓名,
       a.secondary_lead_user_id AS 副ID, u3.full_name AS 副姓名
FROM project_initiation_details d
LEFT JOIN project_lead_assignment a ON a.project_id = d.project_id
LEFT JOIN users u2 ON u2.id = a.primary_lead_user_id
LEFT JOIN users u3 ON u3.id = a.secondary_lead_user_id
WHERE a.primary_lead_user_id IN (7246,7396)
   OR a.secondary_lead_user_id IN (7246,7396);"

# 3. 前端回归测试
npx vitest run src/views/Project/composables/useProjectFilter.spec.js
```

### 相关文档

- `docs/lessons/root-cause-analysis-bidding-leader-filter-or-semantics.md` — 完整根因分析
- §23 — 全链路日志排查 SOP（本次排查使用）
- §25 — 前端禁止 `catch { /* silent */ }` 吞掉 API 错误（同类：fallback 把 bug 伪装成正常）
- PR !1574 — UserPicker value-key 修复（修复前掩盖了本次 bug）
- PR !1642 — 本次修复（投标负责人筛选只匹配主负责人）
- `src/views/Project/composables/useProjectFilter.js` — 前端筛选逻辑（修复后）
- `backend/src/main/java/com/xiyu/bid/project/service/ProjectQueryService.java` — enrich 逻辑（姓名与 ID 来源分叉点）

## 38. Collectors.toMap 无 merge function 三层失效 + 35 处全仓治理（PR !1640 + Spec Kit 027）

**事故时间**: 2026-07-03
**影响范围**: 标讯中心整个模块不可用（列表页 + 详情页均报"加载标讯列表失败"）
**根因类别**: 防御性编程缺失 + 装饰性操作未降级 + 异常 handler 诊断缺失

### 事故经过

测试系统 `tenderId=937` 关联 2 个 Project（managerId=585 和 7246），这是业务允许的二次招标场景（`ProjectClosureService.rebidProject` 会基于已结项项目创建新项目，保留同一 tenderId）。

`TenderQueryService.fetchManagerNames` 使用 `Collectors.toMap(Project::getTenderId, Project::getManagerId)` **无 merge function**，遇到重复 key 时抛 `IllegalStateException: Duplicate key 937 (attempted merging values 585 and 7246)`。

异常传播链：`toMap` → `enrichAssignmentInfoBatch`（无降级）→ `searchTendersPaged`（无 try-catch）→ `GlobalExceptionHandler.handleIllegalStateException`（只 `log.warn` 一行，不打印堆栈/不上报 Sentry）→ 前端弹"加载标讯列表失败"。

**三层失效**：
1. **数据层**: `toMap` 无 merge function，fail-fast 抛异常（Java 标准库默认行为）
2. **服务层**: enrichment 是装饰性操作（补充 manager name 显示），但失败时未降级，导致主列表功能崩溃
3. **异常层**: handler 只 `log.warn` 一行，不打印堆栈、不上报 Sentry，导致 Sentry Dashboard 看不到，后端日志无堆栈，定位困难

### 排查方法

按 §23 全链路日志排查 SOP：
1. **Layer 1 异常日志**: 后端日志只有 `Duplicate key 937 (attempted merging values 585 and 7246)`，无堆栈
2. **Layer 3 git 追溯**: grep `Collectors.toMap` 找到 `TenderQueryService.fetchManagerNames`，确认无 merge function
3. **错误特征匹配**: `937` = `Project::getTenderId`，`585/7246` = `Project::getManagerId`
4. **全仓扫描**: 用 subagent 扫描全仓 62 处 `toMap` 调用，发现 31 处无 merge function（隐患）

### 修复方案（三层防御体系）

**L1 数据层**: 修复全仓 35 处 `toMap` 2 参数版本，添加 `(a, b) -> a` merge function
- 取第一条与 `findByTenderId().findFirst()` 语义一致
- 35 处分布：ProjectQueryService (4)、TenderQueryService (6)、DocumentSectionTreeService (2)、JpaWorkflowFormAdminStore (2) 等 20 个文件

**L2 服务层**: 装饰性 enrichment 加 try-catch 降级
- `TenderQueryService.enrichAssignmentInfoBatch` 外层加 `try { ... } catch (RuntimeException e) { log.warn(...); }`
- 降级后 dtos 保持原样（基础数据完整，装饰性字段为空），不影响主列表返回
- 注意：只对装饰性 enrichment 降级，不对核心方法降级（如 `DocumentSectionTreeService.getSectionTree` 是核心方法，失败应抛异常）

**L3 异常层**: 5xx handler 对齐 SOP §23
- `handleIllegalStateException` + `handleOptimisticLockingFailureException` 修复
- `log.warn` → `log.error`（打印堆栈）+ `getRequestPayload`（打印 Payload）+ `Sentry.captureException`（上报）

### 防复发机制

1. **ArchUnit 守卫**: `ArchitectureTest.RULE 18 toMapMustHaveMergeFunction`
   - 扫描 `Collectors.toMap` 2 参数版本调用，命中即失败
   - 豁免清单 `scripts/tomap-exemptions.json`（现已清空，35 处全部修复）

2. **pre-push gate**: `scripts/check-tomap-no-merge-function.mjs`
   - Node.js 脚本，pre-push 阶段拦截新增 2 参数 toMap
   - 已接入 `scripts/pre-push-gate.sh` 第 9.6 节

3. **Constitution v2.0.0 Principle VII**: Defensive Collection & Graceful Degradation
   - 新增 Core Principle VII（NON-NEGOTIABLE），三条规则：
     - `toMap` MUST 提供 merge function（key 非主键唯一约束时）
     - 装饰性 enrichment MUST 降级
     - 5xx handler MUST 打印堆栈 + Payload + Sentry

### 教训归纳

1. **`Collectors.toMap` 无 merge function 是定时炸弹**: Java 标准库 fail-fast 设计，遇到重复 key 直接抛异常。**任何** key 非唯一约束的 toMap 调用都可能触发。新代码 MUST 用 3 参数版本。

2. **装饰性操作不得影响主功能**: enrichment（name resolution、display field 补充）是装饰性的，失败时 MUST 降级。判断标准：方法名含 `enrich`/`fetchXxxNames`/`fetchXxxMap` 且返回值用于补充显示字段（非业务决策）。

3. **异常 handler 必须满足诊断标准**: 5xx handler 只 `log.warn` 一行是灾难——Sentry 看不到、日志无堆栈、定位困难。MUST `log.error`（堆栈）+ Payload + Sentry。

4. **fail-safe 优于 fail-fast**: 对于用户体验而言，"返回部分数据"优于"整个模块崩溃"。fail-fast 适用于编译期和启动期，运行期面对边界数据应 fail-safe。

5. **ArchUnit 守卫是技术债治理的终极武器**: 31 处隐患手工修复后，用 ArchUnit 守卫防止新增。与 §34 hasAnyRole 双轨制治理（ArchUnit 总数断言）同模式。

### 流程性教训：紧急修复通道缺失

本次事故除了技术层面三层失效外，**流程层面也暴露了重大缺口**——没有正式的 P0 hotfix 通道，导致修复耗时远超必要的止血窗口。

| 问题 | 教训 | 改进 |
|------|------|------|
| 完整 14 道门禁 + Spec Kit 流程在 P0 场景耗时过长 | 紧急修复必须有快速通道，不能一刀切 | 新增 `hotfix/*` 分支 + `PRE_PUSH_GATE=0` 合规绕过 |
| `PRE_PUSH_GATE=0` 是"绕过机制"而非"通道"，无分支命名规范、无事后补测要求 | 绕过不等于无序，必须有边界和事后闭环 | 明确 P0 判定标准 + 7 工作日补作业清单 |
| 没有 P0/P1/P2 严重程度分级 | 无分级导致所有 bug 走同一流程，紧急的不急、不急的拖慢 | 在 RELIABILITY.md 新增 P0/P1/P2 判定表 |
| 回滚 SLA 30 分钟已有（ROLLBACK.md），但修复 SLA 缺失 | 修复和回滚是两条止血路径，应同等重视 | 新增 30 分钟 hotfix SLA 流程 |

**关键洞察**：本次修复走了完整 Spec Kit（`specs/027-tomap-defensive-collection/`）+ 14 道门禁 + PR review + auto-merge，虽然质量高（三层防御体系 + ArchUnit 守卫 + Constitution Principle VII），但如果是生产事故，这个时长不可接受。

**已落地改进**（2026-07-04）：
- RELIABILITY.md 新增 §紧急修复通道（P0 Hotfix）章节
- 定义 P0/P1/P2 判定标准
- 30 分钟 SLA 流程：止血决策 → hotfix 分支 → 紧急合入 → 紧急发布
- 合规边界：ArchUnit + Flyway 不可跳，其他门禁可绕过
- 事后补作业清单（7 工作日内）

**适用边界**：紧急通道只解决"修得快"的问题，不解决"修得好"的问题。完整防御体系（ArchUnit 守卫、装饰性降级、Sentry 可观测）才是治本。两者配合：紧急通道止血 → 7 天内补完完整防御。

### 关键文件

- `backend/src/main/java/com/xiyu/bid/tender/service/TenderQueryService.java` — 核心修复点 + enrichment 降级
- `backend/src/test/java/com/xiyu/bid/ArchitectureTest.java` — RULE 18 toMap 守卫
- `scripts/check-tomap-no-merge-function.mjs` — pre-push gate 脚本
- `scripts/tomap-exemptions.json` — 豁免清单（已清空，35 处全部修复）
- `.specify/memory/constitution.md` — Constitution v2.0.0 Principle VII
- `specs/027-tomap-defensive-collection/` — Spec Kit 完整文档

### 相关 SOP

- §23 — 全链路日志排查 SOP（本次排查使用 Layer 1 + Layer 3）
- §34 — hasAnyRole 双轨制 ArchUnit 总数断言守卫（同类：ArchUnit 治理模式）
- §22 — 外部诊断根因必须复核（同类：全仓扫描发现 31 处隐患）

---

## 39. Flyway 迁移目录混淆：db/migration/ vs db/migration-mysql/ 双轨制守卫缺失（CO-483/484 P0 事故）

**事故时间**: 2026-07-04（第 40 次生产部署）
**影响范围**: 标书审核多人化功能上线后，`/api/projects/{id}/stage` 接口 500，前端"系统繁忙"，生产环境 1 小时内 P0 故障
**根因类别**: 迁移目录双轨制 + pre-commit hook 仅在主工作区生效 + 版本号撞历史基线

### 事故经过

CO-483/484 PR !1637（标书审核多人化 + 驳回后审核人清空）在 kimi worktree 开发，建表迁移 `V123__add_bid_review_assignment.sql` **误放在 `db/migration/`**（历史目录，Flyway 9.22.3 不读取此目录）。

第 40 次部署后生产环境现象：
- `BidReviewAssignmentEntity` 标了 `@Table(name = "bid_review_assignment")`，但表从未被创建
- `/api/projects/{id}/stage` 接口查询 `bid_review_assignment` 报 `SQLSyntaxErrorException: Table 'xiyu_bid_main.bid_review_assignment' doesn't exist` → 500
- 前端 `ProjectStageTimeline` 组件加载失败，弹"系统繁忙"

**根因三层**：
1. **目录双轨制**: `db/migration-mysql/` 是活跃目录（Flyway 读取），`db/migration/` 是历史目录（Flyway 9.22.3 配置已不读取）。新开发者/新 worktree 容易混淆。
2. **pre-commit hook 仅在主工作区生效**: `.githooks/pre-commit` 第 42 行调用 `check-flyway-migration-dir.sh` 守卫，但其他 worktree（kimi/codex/claude/cursor/gemini/mimo/qoder/zcode）的 `.git/hooks/pre-commit` 都是 MISSING，没有机会拦截。
3. **版本号撞历史基线**: `V123` 已被 `db/migration/V123__tender_reminder_settings.sql` 占用。即便 V123 放在正确目录，Flyway 也会因为版本号已应用而跳过执行。

### 排查方法

按 §23 全链路日志排查 SOP：
1. **Layer 1 异常日志**: 后端日志 `Table 'xiyu_bid_main.bid_review_assignment' doesn't exist`
2. **Layer 2 业务接口**: `curl /api/projects/84/stage` 返回 500（不是 403）
3. **Layer 3 git 追溯**: `git log --all --oneline -- backend/src/main/resources/db/migration/V123*` 找到 PR !1637 的 commit
4. **Layer 4 配置验证**: `grep -r "migration-mysql" backend/src/main/resources/` 确认 Flyway 配置 `spring.flyway.locations=classpath:db/migration-mysql`
5. **关键发现**: `db/migration/V123__add_bid_review_assignment.sql` 存在但 Flyway 不读此目录

### 修复方案

**P0 热修复（已部署）**:
- 新增 `V1133__add_bid_review_assignment_table_hotfix.sql` 放在活跃目录 `migration-mysql/`
- 内容幂等：`CREATE TABLE IF NOT EXISTS` + `INSERT ... WHERE NOT EXISTS`（迁移历史 91 行单审核人记录）
- 删除误放的 `db/migration/V123__add_bid_review_assignment.sql`
- 配套 rollback `U1133__add_bid_review_assignment_table_hotfix.sql`

### 防复发机制（三层防御纵深）

**L1 push 时拦截（pre-push-gate.sh §3.7）**:
- `scripts/pre-push-gate.sh` 新增 §3.7 "Flyway 迁移目录守卫"
- 扫描 commit 范围 `$GATE_BASE..HEAD` 内被新增/修改/重命名的 V*.sql / B*.sql 是否误放在 `db/migration/`
- 通过 `scripts/git` 包装器在所有 worktree 都生效（不依赖 `install-githooks.sh`）
- 逃生阀：`FLYWAY_ALLOW_LEGACY_DIR=1`（仅限已记录豁免场景）

**L2 CI 时拦截（EntityTableMigrationCoverageTest）**:
- `backend/src/test/java/com/xiyu/bid/support/EntityTableMigrationCoverageTest.java`
- 扫描所有 `@Table(name = "xxx")` 实体，验证 `migration-mysql/` 或 `migration/` 中存在 `CREATE TABLE xxx` 迁移
- ArchUnit + ClassFileImporter 反射加载实体类，提取 `@Table` 注解的 `name` 属性
- 豁免清单 `TABLE_MIGRATION_EXEMPTIONS`：`users`（B73 之前已存在）、`brand_authorization_deprecated`（废弃实体）、`flyway_schema_history`
- 关键正则修复：`CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?\`?(?:\w+\.)?(\w+)\`?`（避免 `[\`\\w]*` 贪婪匹配导致 group(1) 只剩末尾字符）

**L3 手动审计（check-flyway-migration-dir.sh）**:
- `scripts/check-flyway-migration-dir.sh`（已存在，被 .githooks/pre-commit 调用）
- 仅在主工作区生效，作为 L1/L2 的补充

### 防复发评估

| 场景 | L1 pre-push | L2 ArchUnit | L3 manual | 是否拦截 |
|------|------------|-------------|-----------|----------|
| kimi worktree 把 V123 放 db/migration/ | ✅（commit 范围扫描） | ✅（@Table 实体扫描） | ❌（hook 未装） | ✅ L1+L2 双拦 |
| 新增 @Table 实体但忘记写建表迁移 | ❌（不扫实体） | ✅（实体必须有迁移） | ❌ | ✅ L2 拦 |
| 历史遗留 V111-V122 在 db/migration/ | ❌（不在 commit 范围） | ❌（已删除该测试） | ✅ | ⚠️ 仅 L3，作为 tech-debt 处理 |
| 版本号撞历史基线 | ❌ | ❌ | ❌ | ❌ 不在本次防御范围（由 `check-flyway-versions.sh` 单独负责） |

**结论**：L1 + L2 形成防御纵深，覆盖 99% 的迁移目录混淆场景。版本号冲突由 `check-flyway-versions.sh` 在 pre-push §3 单独守卫。历史遗留 V111-V122 清理作为独立 tech-debt。

### 教训归纳

1. **pre-commit hook 不是万能的**: 仅在主工作区生效，其他 worktree 默认 MISSING。任何依赖 pre-commit 的守卫都必须有 pre-push 或 CI 层的备份。

2. **目录双轨制是隐形陷阱**: `db/migration/` 和 `db/migration-mysql/` 名字相似，新开发者/新 worktree 容易混淆。Flyway 9.22.3 静默跳过不读取的目录，没有 warning。最佳实践是删除历史目录，或在 README 中显著标注。

3. **ArchUnit 守卫应覆盖"实体-迁移"对应关系**: 之前的守卫只检查"迁移文件本身"（版本号、回滚脚本），没有检查"实体是否有对应迁移"。`@Table(name = "xxx")` 是 JPA 实体的强契约，应该有对应的 `CREATE TABLE xxx` 迁移。

4. **正则 bug 会让守卫形同虚设**: `[\`\\w]*` 贪婪匹配导致 group(1) 只剩末尾字符（如 "templates" 被捕获为 "s"），60+ 个实体表全部"找不到" CREATE TABLE 迁移。修复后正则 `\`?(?:\w+\.)?(\w+)\`?` 正确捕获表名。新守卫必须用真实数据验证正则。

5. **版本号撞历史基线是叠加事故**: 即使 V123 放对目录，也会因为版本号已应用而被跳过。`scripts/check-flyway-versions.sh` 在 pre-push §3 单独守卫版本号冲突，但只检查 `migration-mysql/` 目录，不检查 `migration/`。

6. **多 worktree 协作必须假设其他 worktree 没装 hook**: 任何"依赖本地 hook 拦截"的守卫都失效。`scripts/git` 包装器是唯一在所有 worktree 都生效的拦截点。

### 关键文件

- `scripts/pre-push-gate.sh` — §3.7 Flyway 迁移目录守卫（commit 范围扫描）
- `backend/src/test/java/com/xiyu/bid/support/EntityTableMigrationCoverageTest.java` — @Table 实体迁移覆盖守卫
- `scripts/check-flyway-migration-dir.sh` — 主工作区 pre-commit 守卫（已存在）
- `.githooks/pre-commit` — 调用 check-flyway-migration-dir.sh（仅主工作区）
- `backend/src/main/resources/db/migration-mysql/V1133__add_bid_review_assignment_table_hotfix.sql` — P0 热修复迁移
- `backend/src/main/resources/db/rollback/migration-mysql/U1133__add_bid_review_assignment_table_hotfix.sql` — 回滚脚本
- `docs/release/deploy-report-2026-07-04-40th.md` — 第 40 次部署报告（事故记录）
- 第 41 次热修复部署（V1133 应用，报告待补）

### 相关 SOP

- §23 — 全链路日志排查 SOP（本次排查使用 Layer 1-4）
- §38 — Collectors.toMap 三层失效（同类：三层防御纵深模式）
- §18 — 部署前必须验证 jar 中 Flyway 迁移脚本无重复版本（同类：Flyway 守卫）

---

## 40. 修 bug 时删除代码必须审视隐式前后端字段契约（CO-498 修 CO-443 引入导航断层）

**事故时间**: 2026-07-04
**影响范围**: 复盘提交后项目负责人无法进入结项阶段，整个结项审核流程死锁
**根因类别**: 修 A bug 删除代码时，未审视此代码对前后端字段契约的隐式副作用

### 事故经过

CO-498：项目 157 复盘阶段（RETROSPECTIVE）提交后，导航时间线上的"结项"tab 显示「待进入」且不可点击，项目负责人无法进入结项阶段提交结项申请。

**根因三层**：

1. **5d1b36b53 修 CO-443 时删除了"复盘直达 CLOSED"**: 为了修 `ClosureService.preview` 的 `alreadyClosed` 误判 bug，删除了 `ProjectRetrospectiveService.submit()` 中 `RETROSPECTIVE→CLOSED` 的二次推进。该修改**本身正确**。

2. **未审视 `ProjectStageController.get()` 的隐式契约**: `accessibleStages` 字段的计算逻辑一直依赖"复盘提交后 stage=CLOSED"这个隐式假设——因为 CLOSED 是 stage 推进的产物，不需要单独解锁。删除二次推进后，stage 停在 RETROSPECTIVE，CLOSED **永远不进 accessibleStages**。

3. **前端 `ProjectStageTimeline.isUnlocked()` 完全信任后端字段**: tab 是否可点 100% 取决于 `accessibleStages.includes(stage.code)`，没有任何兜底逻辑。后端字段断层直接导致前端 tab 锁死。

### 排查方法

按 §23 全链路日志排查 SOP，判定为"Layer 2 适用：业务逻辑错误、Sentry 未覆盖场景"（无 5xx 异常，HTTP 200 OK）：

1. **Layer 1 Sentry**: 生产 `sentryEnabled=false`，不可用；本 bug 也不触发异常
2. **Layer 2 DB+TraceId**: 项目 157 `stage=RETROSPECTIVE`、`project_closure` 不存在；复盘提交后无 RETROSPECTIVE→CLOSED 推进日志（与代码设计吻合）
3. **Layer 3 git 追溯**: 定位 `5d1b36b53` 删除二次推进的 commit，读懂 commit message 中"修 CO-443"的动机

### 关键认知

**Service 行为变更 ≠ 视图层契约自动同步**。三层链条是隐式契约：

```
Service 行为（stage 是否推进）
  ↓ 隐式契约
Controller 字段计算（accessibleStages 是否含 CLOSED）
  ↓ 隐式契约
前端视图判定（isUnlocked(stage) 是否返回 true）
```

任何一层行为变更，必须审视另外两层是否依赖此行为。本案例中 `5d1b36b53` 只审视了 Service 层（修 CO-443）和 Controller 部分字段（`current` 计算），漏看了 `accessibleStages` 字段对 stage 推进的依赖。

### 防复发机制

**L1 排查清单（修改 service 行为时必跑）**：

```markdown
- [ ] 此 service 方法的所有调用方在哪里？grep 出全部 caller
- [ ] 此 service 方法的行为变更会影响哪些 controller 返回字段？
- [ ] 这些字段在前端有哪些视图层判定依赖？（grep `accessibleStages` / `currentStage` / `terminal` 等）
- [ ] 改完之后，受影响字段的"前后端契约测试"是否仍然通过？
- [ ] 是否有"依赖此 service 副作用"的其他代码路径（如阶段推进、状态变更）？
```

**L2 测试守卫**：

新增测试必须覆盖"前后端字段契约"的边界，不只是 service 自身行为：

```java
// ❌ 不够：只测 service 行为
@Test void retrospectiveSubmit_doesNotAdvanceToClosed() { ... }

// ✅ 推荐：同时测 controller 字段契约
@Test void co498_retrospectiveWithoutClosure_unlocksClosedTab() {
    // 验证 accessibleStages 含 CLOSED（前端 tab 解锁的契约）
}

@Test void co498_retrospectiveWithClosureDraft_doesNotUnlockClosedTwice() {
    // 验证 CO-443 假 CLOSED 与 CO-498 解锁不双重计数
}
```

**L3 顽固 bug 全链路推演（Code Review 必跑）**：

修复"修 A 引入 B"类顽固 bug 时，必须用 sequential-thinking 推演用户全链路：

```
GET /api → 后端字段计算 → 前端字段消费 → 用户交互 → 下一个 API → ... → 业务终态
```

本案例 CO-498 Code Review 推演了 8 步（从 `GET /stage` 到 `canSubmitClosure=true`），确认无第二层根因阻塞。详见 `docs/lessons/root-cause-analysis-co-498.md` "Code Review" 章节。

### 教训归纳

1. **删除代码比新增代码风险更高**: 新增代码的副作用通常在调用方可控范围内，删除代码则会"静默切断"所有依赖此代码的隐式契约。`5d1b36b53` 删除 5 行代码引出一个 P1 bug。删除代码前必须 grep 所有依赖。

2. **前后端字段是隐式契约，不是"自由数据"**: 后端 controller 返回的每个字段（特别是 `accessibleStages`、`currentStage`、`terminal`、`canXxx` 等布尔/列表字段）都是前端视图判定的依据。改 service 行为时，必须审视这些字段是否需要同步调整计算逻辑。

3. **"修 bug 引入 bug"的镜像模式**: CO-443 与 CO-498 互为镜像——
   - CO-443 的问题：closure 已提交时 stage 还在 RETROSPECTIVE，导致显示"待进入"而非"进行中"
   - CO-498 的问题：closure 未提交时 CLOSED tab 锁死，用户进不去提交 closure 的入口
   - 两个 bug 的修复方向相反（一个让 current 显示 CLOSED，一个让 accessible 解锁 CLOSED），但都改同一个 controller 方法。**修其中一个时必须同时审视另一个**。

4. **Sentry 不可用时 SOP Layer 2 是兜底**: 本 bug 不触发任何异常（HTTP 200 OK），即使 Sentry 启用也不会上报。Layer 2（DB+TraceId+git 追溯）是这类"业务逻辑错误"的唯一可行排查路径。

5. **"看代码注释理解历史决策"是关键排查手段**: `ProjectStageController.java:68-69` 的 CO-443 修正注释（"复盘提交已把 stage 推到 CLOSED"）与 `ProjectRetrospectiveService.java:99-103` 的 `5d1b36b53` 注释（"复盘提交后停在 RETROSPECTIVE"）直接矛盾。两段矛盾的注释是定位"修 A 引入 B"的关键信号——必有一段注释描述的世界已不再存在。

### 防复发评估

| 场景 | L1 排查清单 | L2 测试守卫 | L3 全链路推演 | 是否拦截 |
|------|------------|-------------|---------------|----------|
| 修 service 时删除 stage 推进代码 | ✅（"是否有依赖此副作用的路径"） | ✅（accessibleStages 契约测试） | ✅（Code Review 推演） | ✅ 三层防御 |
| 新增字段但前端未消费 | ❌ | ❌ | ❌ | ❌ 不在本次范围（前端单测/契约测试负责） |
| 字段语义变更（如 `current` 含义改变） | ⚠️（清单可补充） | ⚠️（需配套契约测试） | ✅ | ⚠️ 部分 |

**结论**：本节提供的 L1-L3 防御针对"修 service 引入 controller/视图层断层"类 bug。前端字段消费的覆盖由前端单测/E2E 负责（参考 §26 联动回填链路 4 层全链路验证 SOP）。

### 关键文件

- `backend/src/main/java/com/xiyu/bid/project/controller/ProjectStageController.java` — CO-498 修复点（accessibleStages 计算补全）
- `backend/src/main/java/com/xiyu/bid/project/service/ProjectRetrospectiveService.java` — `5d1b36b53` 删除直达 CLOSED 的位置（行为本身正确，未改）
- `backend/src/test/java/com/xiyu/bid/project/controller/ProjectStageControllerTest.java` — 4 个 CO-498 防复发测试
- `src/components/project/stage/ProjectStageTimeline.vue` — 前端 `isUnlocked()` 判定（信任后端 accessibleStages）
- `docs/lessons/root-cause-analysis-co-498.md` — 完整根因分析（含 8 步全链路推演）
- `.specify/specs/024-co498-retrospective-closure-tab-unlock/` — Spec Kit 完整规格（spec/plan/tasks/implementation-notes）

### 相关 SOP

- §23 — 全链路日志排查 SOP（本次排查使用 Layer 1-3）
- §29 — 权限 Bug 必须审视同一业务动作的所有 UI 入口（同类：修 A bug 漏看 B 链路）
- §26 — 联动回填链路 4 层全链路验证 SOP（同类：前后端字段联动验证）

---

## 41. CO-469 七轮修复全记录：多轮返工的系统性根因与防复发（2026-07-02 ~ 07-04）

### 七轮修复时间线

| 轮次 | 日期 | 用户反馈 | 根因 | 修复 | PR |
|------|------|---------|------|------|-----|
| 1 | 07-02 | 导入导出卡在 0%，无报错 | 异步任务异常被静默吞掉 | 初始排查 | — |
| 2 | 07-02 | 部分场景仍死循环 | 前端状态机仅处理 2/5 后端状态（PARTIAL_SUCCESS/UNKNOWN/NOT_FOUND 未覆盖） | 状态机覆盖全部 5 状态 | !1548 |
| 3 | 07-03 | 导出卡在 70% | `catch (IOException)` 接不住 NPE/IllegalStateException → 异步线程静默终止 | `catch (IOException \| RuntimeException)` | !1575 |
| 4 | 07-03 | 导出报 NPE | `Collectors.toMap` null key（employeeNumber 为 null） | 全仓治理 35 处 toMap | !1640 |
| 5 | 07-04 | `DataIntegrityViolationException: personnel_id cannot be null` | 批量操作日志写入 `personnel_id=NULL`，表约束 NOT NULL | V1134 迁移允许 NULL | !1672 |
| 6 | 07-04 | 导出 0 条记录，zip 无法解压 | 前端 `new Blob([res])` 包裹了 axios response 对象；下载按钮无守卫 | `res.data` + `v-if` 守卫 | !1679 |
| 7 | 07-04 | 仍 0 条记录，无下载按钮 | 后端 `ExportProgress.recordCount` ≠ 前端 `info.totalCount`；且 !1672 遗漏此修复 | `recordCount` → `totalCount` | !1684 |

### 为什么改了七轮

**表层原因**：每一轮都是"用户报一个问题 → 修一个问题 → 部署 → 暴露下一个问题"的串行模式。

**深层原因**：

1. **症状修复而非根因修复**：前 4 轮每一轮只修了异常栈最顶层的报错，没有追问"为什么异步任务异常会静默吞掉？为什么状态机只覆盖了 2 种状态？"——如果 Round 2 就审视整个异常处理链路（catch 类型 + 状态机 + toMap + 数据库约束），一次可以修完 4 个问题。

2. **前后端分离修复的时序错位**：Round 6（前端 `res.data`）和 Round 7（后端 `recordCount`→`totalCount`）本应是一体的——同一个字段名对齐问题，前后端应该同一次 PR 修复。但 !1679 只修了前端，后端修复被遗漏在本地未提交。

3. **"收尾"时机不当**：!1672 合并后，`recordCount`→`totalCount` 的修改是本地 uncommitted 状态。此时用户说"收尾"，切换到锚点分支，改动留在工作区被遗忘。**规则：收尾前必须 `git status` 确认无未提交改动。**

4. **测试覆盖不足**：没有针对"导出进度 JSON 字段名与前端 composable 字段名一致性"的集成测试。如果有一个测试验证 `ExportProgress` 的 JSON 序列化结果能被 `usePersonnelBatchTask` 正确解析，Round 7 在 CI 阶段就会被拦截。

### 系统性教训

| 教训 | 具体表现 | 规范 |
|------|---------|------|
| 多轮返工是"症状修复"的信号 | 7 轮每轮修一个异常栈顶层 | 第一轮修复后，追问"同类问题还有哪些？"做全仓扫描 |
| 前后端字段契约必须同 PR 对齐 | Round 6 修前端、Round 7 修后端，中间隔了部署 | 涉及前后端字段名变更的，必须同一 PR 同时修改 |
| 收尾前必须检查未提交改动 | `recordCount`→`totalCount` 修改留在本地被遗忘 | 收尾前执行 `git status --short`，确认工作区干净 |
| 异步任务的异常处理必须覆盖所有 RuntimeException | 4 轮都在修异步异常处理 | `@Async` 方法必须 `catch (Exception)` 兜底，不可只 catch 受检异常 |
| 前端 composable 字段名必须与后端 record 字段名一致 | `totalCount` vs `recordCount` | 字段命名以 DTO/Record 为唯一真相源，前端 composable 直接引用后端字段名（或通过 TypeScript 类型约束） |

### 为什么 7 轮都找不到根因——三层叠加失效

不是单一原因，是日志系统、排查方法论、验证闭环三个层面同时失效。

**层面 1：日志/可观测性系统缺陷**

| 缺陷 | 具体表现 | 影响 |
|------|---------|------|
| 生产 Sentry 关闭 | `sentryEnabled=false` | 异步任务异常不会上报，日志里看不到 |
| `@Async` 异常被 catch 吞掉 | `catch (IOException)` 接不住 NPE → 线程静默终止 | 日志里没有任何错误记录 |
| 无结构化业务日志 | 导出流程没有"查到 N 条、写入 M 条、生成 ZIP"等关键节点日志 | 无法从日志还原执行链路 |
| 进度接口返回 200 OK | 字段名不匹配不触发异常 | 日志系统天然抓不到"业务逻辑错误" |

但日志不是根本借口。Round 7 的字段名不匹配即使日志再完善也抓不到——后端没报错、前端没报错，只是 `undefined → 0`。

**层面 2：排查方法论根本问题（核心原因）**

每一轮只修"用户报告的那一个点"，没有做全链路推演：

| 轮次 | 修了什么 | **没**做什么 |
|------|---------|------------|
| 1-4 | 异常栈顶层的报错 | 没追问"为什么异步异常会被吞？状态机为什么只覆盖 2 种？同类还有几个？" |
| 5 | 数据库约束 | 修完没验证"导出全链路是否真的通了" |
| 6 | 前端 `res.data` | **没验证后端 JSON 实际返回什么字段名** |
| 7 | 字段名 | 根因找对了，但是用户"还是 0 条"被动触发，不是主动验证 |

根本能力缺陷：
- 过度依赖"异常栈"作为排查入口，但 Round 6/7 的 bug **根本不触发异常**
- `lessons-learned.md §17` 早就写了"Bug 修复前必须先验证实际行为"，但每一轮都没遵守
- "用户报什么修什么"的串行模式，没有主动做全链路验证

**层面 3：验证闭环缺失**

每一轮修复后只跑改动的模块单测（`ExportPersonnelAppServiceTest` 8 个绿），但：
- 没有端到端测试：API 调用 → 异步导出 → 进度轮询 → 下载 → 解压
- 没有前后端契约测试：后端 `ExportProgress` 序列化的 JSON 能被前端 `usePersonnelBatchTask` 正确解析
- 没有让用户在测试环境实际跑一遍

**如果 Round 5 修完后做一次端到端验证，Round 6/7 在同一次验证里就会暴露。**

### 防复发机制——四层补强

**L1：端到端集成测试（最关键）**

```java
@Test
void co469_exportFullFlow_endToEnd() {
    // 1. 触发导出
    // 2. 轮询进度 → 验证 JSON 字段名（totalCount 不是 recordCount）
    // 3. 下载 ZIP → 验证能解压
    // 4. 解压后验证人员数据条数 = 进度返回的 totalCount
}
```

这一层如果有了，Round 6/7 在 CI 阶段就会被拦截。

**L2：前后端契约测试**

后端 record 的 JSON 序列化结果 → 前端 composable 能正确解析。可以用 schema 校验或字段名快照。

**L3：异步任务可观测性补强**

- `@Async` 方法的异常**必须**通过 `AsyncUncaughtExceptionHandler` 上报 Sentry
- 生产环境 Sentry 必须开启（至少对异步任务）
- 关键业务节点写结构化日志（查询条数、写入条数、生成文件路径）

**L4：排查方法论强化（§23 SOP 补充）**

> **修完一个 bug 后，必须做"全链路推演"：从用户操作入口到最终业务终态，每一步都问"这步会成功吗？返回什么？前端能消费吗？"——不能只验证你修的那一个点。**

具体落地：修复完成后，在 PR 描述里写"全链路推演记录"（从用户点击到最终结果，每步的输入/输出/消费方），而不是只写"修了 X，测试通过"。

**L5：前端 composable 字段名一致性检查**（CI 守卫）

- 后端 `ExportProgress` / `ImportProgressInfo` 等 record 的字段名变更时，CI 自动扫描前端 `usePersonnelBatchTask.js` 中引用的字段名是否匹配
- 实现方式：`scripts/check-frontend-field-names.sh` 扫描 record 定义 → 提取字段名 → 在前端 JS 文件中 grep 确认存在

**L6：收尾前干净工作区检查**（已存在，需强化）

- `agent-finish-task.sh` 已有 `git status --porcelain` 检查
- 强化：当检测到未提交改动时，打印改动文件列表并要求用户显式确认（`--force` 或先提交）

**L7：异步任务异常处理全仓审计**（CI 守卫）

- 扫描所有 `@Async` 方法，检查 catch 块是否覆盖 `RuntimeException`
- 实现方式：`scripts/check-async-exception-handling.sh`

### 诚实评估：能否保证不复发

**不能。** L1-L7 中：
- L1/L2（端到端 + 契约测试）：**尚未实现**，是最有效的防线，但需要开发投入
- L3（可观测性）：**尚未实现**，依赖生产 Sentry 开启
- L4（方法论）：**人为纪律**，无强制力，依赖每次修复时自觉执行
- L5/L7（CI 守卫）：**尚未实现**，需要脚本开发
- L6（收尾检查）：**已存在但需强化**

**当前真实状态**：只有 L4（人为纪律）和 L6（弱守卫）生效。要真正降低复发概率，L1（端到端测试）是最高优先级——它能在 CI 阶段拦截字段名不匹配、下载失败、进度错误等整类问题，不依赖人的自觉。

### 关键文件

- `backend/.../ExportPersonnelAppService.java` — 导出服务（Round 3/5/7 修改）
- `backend/.../ImportPersonnelAppService.java` — 导入服务（Round 5 修改）
- `src/views/.../usePersonnelBatchTask.js` — 前端批量任务状态机（Round 2/6/7 修改）
- `src/api/modules/personnelBatchApi.js` — 前端 API 层（Round 6 修改）
- `backend/.../V1134__personnel_operation_log_allow_null_personnel_id.sql` — 迁移脚本（Round 5）
- `backend/.../PersonnelZipExporter.java` — ZIP 导出器（Round 3/4 涉及）

### 相关 SOP

- §23 — 全链路日志排查 SOP（Round 7 使用 Layer 3 git 追溯定位根因）
- §38 — Collectors.toMap 三层失效（Round 4 同类问题）
- §17 — Bug 修复前必须先验证实际行为（Round 1 如果先验证全链路就不会只修表层）

---

## 42. CO-469 第八轮：MySQL JSON 字段 + `List.toString()` 反序列化失败导致 `failImportTask` 二次异常被吞（2026-07-06）

### 事故背景

用户反馈"批量导入人员又出问题了 一直卡主 无法导入"，距第七轮修复（PR !1684）仅 2 天。明确是复发：前 7 轮 PR 都已合入，理论上应彻底解决。

### 根因三层链

| 层 | 现象 | 根因 |
|----|------|------|
| L1（最表层） | 任务状态永久停在 `PROCESSING/5%`，前端轮询一直显示"正在解析Excel文件..." | `failImportTask` 没有更新任务状态 |
| L2（中间层） | `failImportTask` 的 `importTaskRepository.save(updated)` 抛 `DataIntegrityViolationException`，被 `SimpleAsyncUncaughtExceptionHandler` 吞掉 | `error_details` 字段写入非法 JSON |
| L3（最深根因） | MySQL `cast(? as json)` 失败 | `serializeErrorDetails` 用 `List.toString()` 输出 `[ImportErrorDetail[sheetName=...]]`，**不是合法 JSON**（缺 `{}` 和 `""`） |

### 后端日志铁证

```
DataIntegrityViolationException: Invalid JSON text: "Invalid value" in position 0
SQL: insert into personnel_import_task ... error_details=cast(? as json) ...
Caused by: java.sql.SQLException: Invalid JSON text: "Invalid value" in position 0
```

### 复发 trigger 分析（**不是** CO-505 引入，**不是** 第七轮引入）

- 初始 commit `c6c326341` 的 `serializeErrorDetails` 就用了 `List.toString()`，代码注释自写"简化实现，后续可换成 Jackson"
- CO-469 前 7 轮只修了**正常路径**（`errorDetails` 为空时返回 `"[]"` 合法 JSON）
- CO-505（日期格式统一）的 `CommonDateParser.parseAdaptive` 失败返回 null，**不抛异常**，不会触发 `failImportTask`
- **真正的 trigger**：用户上传某个 xlsx，在 `excelImporter.importFromStream` 阶段抛 `RuntimeException`，进入 `catch (IOException | RuntimeException | Error)` 调 `failImportTask`，触发 `save()` 写入非法 JSON → `DataIntegrityViolationException` 被吞 → 任务永卡 5%

### 为什么 7 轮都没发现

| 原因 | 具体表现 |
|------|---------|
| 正常路径掩盖 bug | 当 xlsx 解析成功（无异常）时，`errorDetails` 为空，`serializeErrorDetails` 返回 `"[]"` 合法 JSON，bug 不暴露 |
| 测试覆盖盲区 | 前 7 轮的测试只覆盖"导入成功"和"导入有 validation errors（不抛异常）"路径，**没有覆盖"importFromStream 抛 RuntimeException → failImportTask → save 抛异常"链路** |
| SimpleAsyncUncaughtExceptionHandler 默认吞异常 | Spring Boot 默认 `AsyncUncaughtExceptionHandler` 仅 `log.error`，但日志层级 WARN + 生产 Sentry 关闭 → 异常完全不可见 |
| 日志字段名误导 | `DataIntegrityViolationException` 的 message 是"Invalid JSON text"，**不是** "List.toString() is not JSON"，需深入 SQL 才能看出是 `cast(? as json)` 失败 |
| `List.toString()` 是 JDK 原生方法 | 不抛任何警告，IDE 也不会提示，肉眼扫代码容易跳过 |

### 修复方案：三层防御

**L1：用 Jackson 替代 `List.toString()`**（根因修复）

```java
private static final ObjectMapper ERROR_DETAIL_MAPPER = new ObjectMapper();

private String serializeErrorDetails(List<ImportErrorDetail> details) {
    if (details == null || details.isEmpty()) return "[]";
    try {
        return ERROR_DETAIL_MAPPER.writeValueAsString(details);
    } catch (JsonProcessingException e) {
        log.warn("序列化 ImportErrorDetail 失败，降级为空数组: {}", e.getMessage());
        return "[]";
    }
}
```

**L2：`failImportTask` 加防御性 try/catch + 降级**（防二次异常吞掉）

```java
private void failImportTask(Long taskId, String errorMessage) {
    PersonnelImportTask task = null;
    try {
        task = importTaskRepository.findById(taskId).orElse(null);
    } catch (RuntimeException findException) {
        log.warn("failImportTask findById 失败: taskId={}", taskId, findException);
    }
    if (task == null) {
        safeClearProgress(taskId);
        return;
    }

    List<ImportErrorDetail> errors = List.of(new ImportErrorDetail(
            "系统", null, null, null, errorMessage
    ));

    PersonnelImportTask updated = new PersonnelImportTask(/* ... */);

    try {
        importTaskRepository.save(updated);
    } catch (RuntimeException saveException) {
        log.error("failImportTask save 失败，降级到 updateStatus: taskId={}", taskId, saveException);
        try {
            importTaskRepository.updateStatus(taskId, ImportTaskStatus.FAILED.name());
        } catch (RuntimeException fallbackException) {
            log.error("failImportTask updateStatus 兜底也失败: taskId={}", taskId, fallbackException);
        }
    }
    safeClearProgress(taskId);
}
```

**L3：历史脏数据兼容**（`deserializeErrorDetails` 加 try/catch 返回空列表）

```java
private List<ImportErrorDetail> deserializeErrorDetails(String json) {
    if (json == null || json.isBlank() || "[]".equals(json)) {
        return List.of();
    }
    try {
        ImportErrorDetail[] array = ERROR_DETAIL_MAPPER.readValue(json, ImportErrorDetail[].class);
        return List.of(array);
    } catch (JsonProcessingException e) {
        log.warn("反序列化 ImportErrorDetail 失败，返回空列表: {}", e.getMessage());
        return List.of();
    }
}
```

### 防复发机制（CI 守卫）

**测试守卫：JSON 合法性断言**（核心）

```java
@Test
void serializeErrorDetails_特殊字符_必须输出合法JSON_等价于MySQL校验() {
    // given
    List<ImportErrorDetail> details = List.of(new ImportErrorDetail(
            "人员", null, null, null, "包含\"引号\"和{大括号}"
    ));
    // when
    String json = adapter.serializeErrorDetails(details);
    // then
    // 等价于 MySQL cast(? as json) 校验
    JsonSchemaValidator validator = new JsonSchemaValidator();
    assertThatNoException().isThrownBy(() -> validator.readTree(json));
}
```

如果未来有人把 `serializeErrorDetails` 改回 `List.toString()`，这个测试会立即在 CI 阶段失败。

### 系统性教训

| 教训 | 具体表现 | 规范 |
|------|---------|------|
| **JDK 原生方法不等于业务正确** | `List.toString()` 是 JDK 方法，但输出格式不是 JSON | 写入 MySQL JSON 字段前必须用 Jackson/Gson 等专业库，禁止 `toString()` |
| **MySQL JSON 字段是契约，不是字符串** | `columnDefinition = "JSON"` + `@JdbcTypeCode(SqlTypes.JSON)` 在 SQL 层做 `cast(? as json)` 校验 | 任何写入 JSON 字段的数据必须经过 JSON 序列化器，禁止直接 `String` 拼接或 `toString()` |
| **`failImportTask` 自身必须容错** | save 抛异常被 SimpleAsyncUncaughtExceptionHandler 吞掉，状态永卡 PROCESSING | **失败处理函数不能抛异常**——任何 save 失败必须有降级路径（如 `updateStatus(FAILED)`） |
| **`@Async` 方法的所有下游调用都必须容错** | failImportTask 是 executeImportAsync 的下游，自身异常会层层吞掉 | `@Async` 方法的所有下游方法（包括 catch 块中的 failXxx）必须自带 try/catch + 降级 |
| **测试必须覆盖异常路径，不只是正常路径** | 前 7 轮测试只覆盖正常路径，bug 在异常路径潜伏 7 轮 | 凡是 `catch` 块中调用的方法（failImportTask/handleError 等），必须有专门的测试覆盖该方法自身抛异常的场景 |
| **代码注释"后续可换成 Jackson"是隐形 bug** | 初始 commit 自写"简化实现，后续可换成 Jackson" | 禁止写"后续可换成 X"的注释——要么立即正确实现，要么写 TODO + 在 issue tracker 登记 |

### 验证命令

```bash
# 验证 serializeErrorDetails 输出合法 JSON（等价于 MySQL cast(? as json) 校验）
cd backend && mvn test -Dtest=PersonnelImportTaskRepositoryAdapterTest

# 验证 failImportTask 降级路径
cd backend && mvn test -Dtest=ImportPersonnelAppServiceTest

# 全仓扫描 List.toString() 写入 JSON 字段的隐患
grep -rn "columnDefinition.*JSON" backend/src/main/java | awk -F: '{print $1}' | sort -u | \
  while read f; do
    grep -n "toString()" "$f" 2>/dev/null
  done
```

### 关键文件

- `backend/.../PersonnelImportTaskRepositoryAdapter.java` — 序列化/反序列化（L1 + L3 修复）
- `backend/.../ImportPersonnelAppService.java` — `failImportTask` try/catch 降级（L2 修复）
- `backend/.../PersonnelImportTaskEntity.java` — `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "JSON"` 实体定义
- `backend/.../ImportErrorDetail.java` — record，默认 `toString()` 不是 JSON
- `backend/.../PersonnelImportTaskRepositoryAdapterTest.java` — JSON 合法性断言（防复发守卫）
- `backend/.../ImportPersonnelAppServiceTest.java` — `failImportTask` 降级路径测试

### 相关 PR

- PR !1736 — CO-469 第八轮修复（commit `da755ce28`）

### 相关 SOP

- §41 — CO-469 七轮修复全记录（前 7 轮根因总结，本次复发是 §41 §"诚实评估：能否保证不复发"已预言的"L1/L2/L3/L5/L7 尚未实现"导致）
- §17 — Bug 修复前必须先验证实际行为（本次先查后端日志铁证，避免推测式修复）
- §23 — 全链路日志排查 SOP（使用 Layer 3 git 追溯定位初始 commit c6c326341）

### 诚实评估：本次能否保证不复发

**仍不能 100% 保证。** 三层防御已落地：
- L1（Jackson 序列化）：**已实现**，根因消除
- L2（failImportTask try/catch 降级）：**已实现**，二次异常不再被吞
- L3（历史脏数据兼容）：**已实现**，反序列化失败不抛异常
- **测试守卫**（JSON 合法性断言）：**已实现**，CI 阶段拦截 `List.toString()` 回归

**仍存在的复发风险**：
1. 其他模块（如 brand-auth / project 等）若有类似 `MySQL JSON 字段 + toString()` 写入，本 PR 不覆盖——需全仓审计
2. `failImportTask` 自身降级到 `updateStatus` 也失败时，只能 log.error，状态仍卡 PROCESSING（极端情况）
3. 没有端到端测试覆盖"上传异常 xlsx → 触发 failImportTask → 验证状态最终落库为 FAILED"

**优先级建议**：
- P1：全仓审计所有 `columnDefinition = "JSON"` 的 entity，检查写入路径是否用了 `toString()`
- P2：为 brand-auth / project 模块的导入服务补类似 L1/L2/L3 防御
- P3：补端到端测试覆盖"异常 xlsx 触发 failImportTask"完整链路

### P2 补充：根因 1（MultipartFile 异步临时文件失效）—— 2026-07-06 当日晚些

> 本节是 §42 P1（根因 2）的补充。P1 修完后用户反馈「现在的失败提示改成：导入失败了」
> ——这恰好证明根因 1 仍在活跃：P1 让 failImportTask 能正确兜住，所以状态变成 FAILED
> （不再卡住），但**导入功能依然完全不可用**，因为所有正常 Excel 都会因临时文件被清理而失败。

#### P1 漏掉的日志铁证（与 §42 的日志只隔 13 毫秒）

backend.log 2026-07-06 06:25:12 同一秒内的两条 stacktrace：

```
06:25:12.287  ← P1 漏看了这条
[personnel-imp-exp-1] ERROR c.x.b.p.a.s.ImportPersonnelAppService - 导入任务执行失败: taskId=1
java.nio.file.NoSuchFileException: /private/var/folders/.../upload_9f6c0414_..._00000000.tmp

06:25:12.300  ← §42 P1 只看了这条
ERROR o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Invalid JSON text...
```

#### 真根因 1：`@Async` + `MultipartFile` 反模式

Spring MVC 的 `MultipartFile` 基于 Servlet 容器（Tomcat）磁盘临时文件。
Controller 返回 202 后，**Tomcat 立即清理临时文件**。`@Async` 方法执行时
（几十毫秒后）临时文件已不存在 → `file.getInputStream()` 抛 `NoSuchFileException`。

#### 修复（PR !1755）

同步阶段（HTTP 请求仍存活）`file.getBytes()` 读到 `byte[]`，传给 `@Async` 方法。
`byte[]` 是不可变纯 JDK 对象，不依赖 request 生命周期。

```java
// Controller 同步阶段
byte[] fileBytes = file.getBytes();   // ← 在这里读完，避开临时文件清理
importAppService.executeImportAsync(taskId, fileBytes, originalFilename, userId);

// @Async 方法
public void executeImportAsync(Long taskId, byte[] fileBytes, ...) {
    excelImporter.importFromStream(new ByteArrayInputStream(fileBytes));
}
```

#### 新增系统性教训（P2 补充）

| 教训 | 规范 |
|------|------|
| **改 bug 前 grep `ERROR` 日志必须逐条过** | §42 P1 只看了 `Invalid JSON text` 一条就停，漏掉紧挨着的 `NoSuchFileException`。两条 stacktrace 在日志里只隔 13 毫秒。**正确做法**：`grep ERROR` 后逐条审视，特别关注同一秒/同一 traceId 的多条错误，它们往往是同一事故的多米诺 |
| **`@Async` 方法禁止接收绑定 request 生命周期的对象** | `MultipartFile`、`HttpServletRequest`、`InputStream` 等依赖 HTTP request 的对象，在 request 结束后会被容器清理/失效。Spring 官方文档明确警告此反模式。**正确做法**：在同步阶段提取不可变数据（`byte[]`、`String`、值对象）传入异步方法 |

#### 防复发守卫（P2 补充）

P2 在 `ImportPersonnelAppServiceTest` 保留了 P1 的 2 个降级测试，并把 5 个测试的
入参从 `MultipartFile` 改为 `byte[]`。若未来有人把签名改回 `MultipartFile`，
这 5 个测试会立即编译失败，强制开发者审视异步生命周期契约。

#### 诚实评估更新（覆盖 §42 原评估）

P1 + P2 合起来后，第八轮才算完整：
- L1（Jackson 序列化）：**已实现**（P1，§42 已述）
- L2（failImportTask try/catch 降级）：**已实现**（P1，§42 已述）
- L3（历史脏数据兼容）：**已实现**（P1，§42 已述）
- **L4（MultipartFile → byte[]）：已实现**（P2，本节）← P1 漏掉，P2 补上

**剩余复发风险**（更新）：
1. 其他模块若有 `@Async + MultipartFile` 模式，本 PR 不覆盖——需全仓审计
2. §42 原列的「极端情况 failImportTask 完全失败」风险依然存在
3. 端到端测试仍缺（与 §42 一致）

#### 相关 PR

- PR !1755 — CO-469 第八轮 P2 根因 1 修复（commit `8034283e2`）
- PR !1736 — CO-469 第八轮 P1 根因 2 修复（commit `da755ce28`，§42）

#### 相关 SOP

- §42 — CO-469 第八轮 P1（根因 2，本节是其补充）
- §41 — CO-469 七轮修复全记录
- §23 — 全链路日志排查 SOP（本节强调「grep ERROR 后逐条过」，是对 §23 Layer 3 的强化）

## 43. Excel 日期单元格反复出 bug：分散实现 → 统一基础设施 + 架构门禁（CO-505 第 N 轮）

### 问题背景

用户反馈"第 2 行: 有效期至格式错误"，距 CO-505 批量导入日期格式统一兼容的 PR 合入后不久。表面看是 CA 证书导入又出 bug，深入排查发现是**全仓 7 个 Excel 读取路径各写各的日期处理逻辑**，修了一个漏了另一个。

### 根因：为什么会反复修反复出

| 层 | 现象 | 根因 |
|----|------|------|
| L1（表层） | CA 证书导入报"有效期至格式错误" | `ImportQualificationAppService` 直接用 `DataFormatter.formatCellValue()`，日期单元格输出格式取决于 Excel number format |
| L2（中层） | 修了 CA 证书，资质证书又出同样问题 | 7 个 Excel 读取器分散实现，5 种不同的日期处理写法 |
| L3（根因） | 没有统一入口 + 没有架构门禁 | 基础设施层缺少 `ExcelCellFormatter` 工具类，ArchUnit 没有规则阻止新增分散实现 |

### 全仓 Excel 读取路径盘点（修复前）

| 模块 | 类 | 日期单元格处理 | Bug? |
|------|----|--------------|------|
| 仓库导入 | `WarehouseImportExcelReader` | 自己实现 formatCell → yyyy-MM-dd | ✅ 正确 |
| 通用单 Sheet | `SingleSheetExcelReader` | 刚修复（之前直接 DataFormatter） | ❌→✅ |
| 标讯导入 | `TenderExcelCellReader` | 自己实现 formatNumeric → 含时间 | ⚠️ 可工作但输出含 T |
| 人员证书 | `PersonnelExcelImporter` | 自己实现 getCellStringValue → yyyy-MM-dd | ✅ 正确 |
| 业绩导入 | `PerformanceRowImporter` | 自己 switch cell type + getDateCellValue() | ⚠️ 用了过时 API |
| 资质证书 | `ImportQualificationAppService` | 直接 DataFormatter.formatCellValue() | ❌ Bug |
| 文本提取 | `ScoreDraftDocumentTextExtractor` | 直接 DataFormatter.formatCellValue() | ⚠️ 影响小但不统一 |

### 根治方案（三层防御）

**L1：统一基础设施类**
- 新建 `ExcelCellFormatter`（`infrastructure.excel` 包），提供 `formatCell(Cell, DataFormatter)` 和 `formatCell(Cell, DataFormatter, FormulaEvaluator)` 两个重载
- 日期单元格统一输出 `yyyy-MM-dd` ISO 格式
- 文本/数字/公式单元格走 DataFormatter 原路径

**L2：全仓迁移到统一入口**
- 迁移 4 个使用 DataFormatter 的读取器：`WarehouseImportExcelReader`、`SingleSheetExcelReader`、`ImportQualificationAppService`、`ScoreDraftDocumentTextExtractor`
- 另外 3 个不使用 DataFormatter 的读取器（`TenderExcelCellReader`/`PersonnelExcelImporter`/`PerformanceRowImporter`）暂不迁移（各有特殊逻辑，且日期处理本身正确）

**L3：架构测试门禁**
- 在 `ArchitectureTest` 新增规则 `excel_date_cell_must_use_excel_cell_formatter`
- 规则：`infrastructure.excel` 包外的任何类，**禁止**直接调用 `DataFormatter.formatCellValue(Cell)` 或 `formatCellValue(Cell, FormulaEvaluator)`
- 新增 Excel 读取代码必须通过 `ExcelCellFormatter`，自动继承日期单元格统一处理

### 经验教训

1. **"修一个 bug"≠"解决问题"**：同一个模式在多个地方复现，说明是架构问题不是单点问题。修完当前报错的模块后，必须全仓 grep 相同模式，一次性根治。
2. **防复发 = 统一入口 + 架构门禁**：只靠 code review 防不住，必须有 ArchUnit 测试在 CI 里自动拦。本项目已有 `business_code_should_not_call_sheet_autoSizeColumn_directly` 的先例，照着抄就行。
3. **基础设施层要主动沉淀**：`WarehouseImportExcelReader` 和 `SingleSheetExcelReader` 的 `formatCell` 方法逻辑完全一样，各写一遍本身就是技术债信号。发现重复时应该往上抽，而不是"能跑就行"。

### 操作规范

- 新增 Excel 读取代码时，**永远通过 `ExcelCellFormatter.formatCell()` 读取单元格字符串**，不要直接调用 `DataFormatter.formatCellValue()`
- 如确需直接调用（极特殊场景），必须在 `infrastructure.excel` 包内实现，并在 PR 中说明理由
- 架构测试 `ArchitectureTest` 会自动拦截违规调用，CI 不通过

### 相关文件

- `backend/src/main/java/com/xiyu/bid/infrastructure/excel/ExcelCellFormatter.java`（统一入口）
- `backend/src/test/java/com/xiyu/bid/infrastructure/excel/ExcelCellFormatterTest.java`（单元测试）
- `backend/src/test/java/com/xiyu/bid/ArchitectureTest.java`（架构门禁：`excel_date_cell_must_use_excel_cell_formatter`）


---

## 44. 通知派发接收人必须按资源可见性过滤：广播范围 × 资源权限 × targetUrl 三者联动（spec 030 / 06131 案例）

> **场景**：用户 06131（bid-Team 投标专员）收到大量任务审核通知，点击通知跳转报'没有权限'。
> **排查方法**：按 §23 全链路日志排查 SOP，跳过 Layer 1（AccessDeniedException 不上报 Sentry），直接走 Layer 2 + Layer 3。
> **修复过程**：完整走 Spec Kit 门禁（spec 030，specify/plan/tasks/analyze），分支 `agent/zcode/fix-task-review-notify-403`。

### 问题背景

2026-07-06 用户 06131 反馈'收到很多条通知，但点击跳转都报错提示没有权限'。这是一个影响所有 `bid-Team`/`bid-projectLeader` 角色的系统性 Bug——只要被广播到任何自己无权访问的项目任务审核通知，都会复现。

### 全链路证据链（按 §23 SOP Layer 2 + Layer 3）

**Layer 2 现场抓取**（服务器日志 `/var/log/xiyu-bid/application.json.log`）：

```
17:55:57 WARN GlobalExceptionHandler: 权限不足 - URI: /api/projects/172, User: 06131, Message: 权限不足，无法访问该项目  traceId=34858d95640b41aea0b0cd2c4cc53d2d
17:56:53 WARN GlobalExceptionHandler: 权限不足 - URI: /api/projects/171, User: 06131, ...                                            traceId=6303175138734199953f2a750e11a0fb
17:57:06 WARN GlobalExceptionHandler: 权限不足 - URI: /api/projects/162, User: 06131, ...                                            traceId=dbe0f91fc1d44b0484970e033d8cbc00
```

**Layer 2 DB 真相**（直查 `winbid` 库）：

```
notification.payload_json = {"targetUrl":"/project/172/drafting","taskId":"2967"}
user_notification.user_id = 1471 (06131 王晓莉, role=MANAGER, role_id=6 → bid-Team)

# 06131 在 162/171/172/173 项目的可访问来源全部不命中：
tasks WHERE assignee_id=1471 AND project_id IN (160..173) → 空集
```

**Layer 3 git 追溯**：commit `c8446b0ea`（2026-07-03 'feat: 任务审核通知'）原设计即如此——广播式接收人从未做过项目可见性过滤，**非回归，是原设计缺陷**。

### 三层根因表

| 层级 | 表象 | 真相 |
|------|------|------|
| L1（表层） | 06131 点击通知跳转 403 | 后端 `ProjectAccessScopeService.assertCurrentUserCanAccessProject` 抛 `AccessDeniedException`，前端 `client.js:188` 全局 403 拦截器弹红色 toast |
| L2（中层） | 06131 不该收到这些项目通知 | `TaskReviewNotificationService.notifyTaskReviewSubmitted` 用 `findEnabledByRoleProfileCodes(TASK_MUTATION_ALLOWED_ROLES)` 全球广播给所有投标专员/负责人，未过滤接收人对项目的访问权 |
| L3（根因） | 接收人策略与资源访问权脱节 + targetUrl 硬编码 | 通知派发的'接收人范围'、'资源访问权'、'跳转 URL'三者各自独立设计，没有约束关系：接收人范围按角色全局反查（含 self 受限角色），targetUrl 又硬编码 `/project/{id}/drafting`，导致广播到无权用户时必 403 |

### 关键设计教训：广播范围 × 资源权限 × targetUrl 三者必须联动

通知派发的三个维度：

1. **接收人范围**：通过 `findEnabledByRoleProfileCodes(roleCodes)` 按角色反查。如果 `roleCodes` 含 `bid-Team`/`bid-otherDept`/`bid-administration` 等 `dataScope=self` 的受限角色，反查结果会包含全球所有该角色用户。
2. **资源访问权**：通过 `ProjectAccessScopeService.getAllowedProjectIds(user)` 计算用户对项目的可访问集。`dataScope=self` 角色的可访问集只含自己参与的项目，远小于广播范围。
3. **跳转 URL**：通知 `payload_json.targetUrl`，常被硬编码为 `/project/{id}/...`。

**三者必须联动**：如果'接收人范围'含受限角色，且'targetUrl'跳转到的资源有访问权校验，**派发前必须对接收人做资源可见性过滤**。否则广播范围 ⊃ 可访问集，差集中的用户会收到通知但跳转 403。

### 全仓审视结论（详见 tech-debt-tracker.md）

全仓 11 处 `findEnabledByRoleProfileCodes` 调用点，**只有 `TASK_MUTATION_ALLOWED_ROLES` 一个常量把 `bid-Team`（self 受限角色）纳入了广播范围**——这是 06131 案例的根本原因。其他 10 处调用要么全是 `dataScope=all` 全局角色（admin/bidAdmin/bid-TeamLeader），要么是资源当事人（applicant/custodian），要么 targetUrl 跳全局可访问页，**全部豁免**。

### 操作规范（新增通知派发器时必跑）

新增/修改通知派发逻辑时，必须按以下清单自检：

- [ ] **接收人范围审查**：`roleCodes` 是否含 `bid-Team`/`bid-otherDept`/`bid-administration` 等 `dataScope=self` 受限角色？
- [ ] **如含 self 角色，必须做项目可见性过滤**：用 `NotificationRecipientFilter.filterRecipients(candidates, uid -> projectAccessScopeService.canAccessProject(uid, projectId))`，详见 `backend/src/main/java/com/xiyu/bid/notification/core/NotificationRecipientFilter.java`。
- [ ] **targetUrl 审查**：跳转目标是否有访问权校验？如有（如 `/project/{id}`），必须确保接收人能通过校验；如不能，要么过滤掉该接收人，要么降级 targetUrl。
- [ ] **降级策略**：过滤逻辑异常时是否降级为原广播？应该降级（通知送达优先于精准，符合 Constitution VII §2 装饰性操作降级精神）。
- [ ] **空接收人安全跳过**：过滤后列表为空时，打 INFO 日志安全跳过，不抛异常。
- [ ] **前端兜底**：`src/api/client.js` 全局 403 拦截器是否对'项目详情类 403'友好化？已实现（spec 030 commit `e63ef8043` + Review 修正 `4f847f6c2`）：仅匹配 `GET /api/projects/{id}` 主请求（用正则 `/^\/api\/projects\/\d+(?:\?|$)/` 严格排除 `/documents`、`/ai-cards`、`/tender-breakdown` 等子路径），403 改黄色 warning + 2.5s 后跳 `/inbox`。

### 设计 Review 教训（spec 030 PR Review 阶段沉淀）

本次 PR 在合入前做了系统性设计 Review，识别并修复了 2 个 HIGH 问题。两条过程教训通用化如下，适用于**所有**类似改动场景，不仅限通知派发器：

#### 教训 A：改动全局拦截器/中间件时，必须在 plan 阶段做"影响面分析"

**反例（本次 H1 弯路）**：spec FR-004 只写"targetUrl 降级到接收人可访问的安全路径"，没规定实现方式。实施时直接改了 `src/api/client.js` 全局 403 拦截器，写了正则 `/^\/api\/projects\/\d+(?:\/|$|\?)/`。Review 时才发现这个正则误伤所有 `GET /api/projects/{id}/*` 子路径——`/documents`、`/ai-cards`、`/tender-breakdown`、`/score-drafts` 等非通知跳转场景的 403 也会被错误"友好化 + 强制跳通知中心"，打断用户当前操作。

**根因**：开发时只关注"通知跳转场景能不能命中"，没反向思考"非通知场景会不会被误伤"。

**操作规范**（涉及全局拦截器/中间件/WebSocket 拦截/事件总线等改动时必跑）：

- [ ] **正向命中验证**：本次场景的所有请求路径能否被精准命中？（如本次通知跳转 → `GET /api/projects/{id}`）
- [ ] **反向误伤分析**：用 `grep -rn '<拦截点特征>' src/` 搜索所有命中点，逐一判定"是否真的属于本次场景"。例如改 axios 拦截器时，必须 grep 所有命中 URL 模式的 API 调用。
- [ ] **正则覆盖度验证**：拦截器用正则匹配 URL 时，必须列出至少 5 个边界用例（应命中 + 不应命中各半），在 plan/research 文档显式验证。仅靠"看一眼能跑通"不够。
- [ ] **精准标记优于全局模式匹配**：如果业务允许，优先用 `config` 标记（如 `httpClient.get(url, { treatAsNotificationRedirect: true })`）让调用方显式声明场景，而不是用 URL 正则倒推场景。后者天然有误伤风险。

#### 教训 B：新增"权限判定"类方法时，必须先 grep 既有同类方法的判定源

**反例（本次 H2 弯路）**：spec 030 新增 `ProjectAccessScopeService.canAccessProject(userId, projectId)` 时，直接写了 `EffectiveRoleResolver.resolveRoleCode(user) = "admin"` 判定 admin。Review 时才发现同文件内既有 `assertCurrentUserCanAccessProject` 用的是 `hasAdminAccess(authentication)`——基于 Spring Security authorities（`ROLE_ADMIN`）。两者判定源不同（role_code 字段 vs authority），OSS 同步脏数据场景下可能分歧——**正是 06131 案例的脏数据形态**（`users.role=MANAGER` 但 `role_id=6 → bid-Team`）。后果：通知过滤时被剔除的用户，实际登录访问却能通过权限闸门，过滤结果与实际访问判定不一致。

**根因**：开发时只读了自己的需求（"需要判定 admin"），没看同文件内已有的同类方法是怎么判定的。

**操作规范**（新增权限/角色/可见性判定方法时必跑）：

- [ ] **同文件 grep**：在目标类内 `grep -n 'public.*can\|public.*isAllowed\|public.*hasAccess'`，列出所有同类公开方法。
- [ ] **判定源对齐**：新增方法的判定源（authority vs role_code vs DB 字段）**必须**与既有同类方法一致。如确实需要用不同判定源（如本次 `ROLE_EXTERNAL_API` 仅 authority 可识别），必须在私有方法层抽取共享逻辑，公开方法各自处理边界差异。
- [ ] **对齐测试**：写一个"同一用户对同一资源，两个方法判定结果必须一致"的测试用例（参考 `ProjectAccessScopeServiceTest.canAccessProject_shouldAlignWith_assertCurrentUserCanAccessProject`）。OSS 同步脏数据场景下尤其重要。
- [ ] **EffectiveRoleResolver 是 OSS 同步后的权威源**：新代码优先用它而不是 Spring Security authority——OSS 同步可能落后于登录态变化，导致 authority/role_code 分歧。

### 修复方案三层防御（spec 030 实施）

**L1 后端核心修复**（commit `8527766c0`）：
- 新增纯函数 `NotificationRecipientFilter`（无状态、Predicate 注入判定，Constitution I FP-Java Pure Core）
- 新增 `ProjectAccessScopeService.canAccessProject(userId, projectId)` 轻量方法（admin/dataScope=all 短路）
- `TaskReviewNotificationService.notifyTaskReviewSubmitted` 派发前过滤，异常降级保留原广播

**L2 前端兜底**（commit `e63ef8043` 初版 + `4f847f6c2` Review H1 修正）：
- `src/api/client.js` 全局 403 拦截器精准识别 `GET /api/projects/{id}` 主请求
- 红色 `ElMessage.error` → 黄色 `ElMessage.warning` + '已为您返回通知中心'
- 2.5s 后自动 `router.push('/inbox')`
- Review 后修正：正则严格排除子路径（避免 `/documents`、`/ai-cards` 等被误伤）

**L3 教训沉淀**：
- tech-debt-tracker.md 登记 11 处审视清单（commit `008ff2679`）
- 本节 §44 沉淀设计教训 + 操作规范检查清单（含 Review 阶段补充的"全局拦截器影响面分析"和"权限判定方法判定源对齐"两条通用教训）
- 独立 RCA 文件 `docs/lessons/root-cause-analysis-spec030-task-review-notify-403.md` 归档完整证据链
- Review 阶段 H2 重构：`ProjectAccessScopeService` 抽取 `canAccessProjectInternal(User, Long)` 共享私有方法（commit `ad94e3650`），确保 `canAccessProject` 与 `assertCurrentUserCanAccessProject` 判定口径完全一致

### SOP 取舍说明（给后续 Agent）

| §23 Layer | 是否适用 | 原因 |
|---|---|---|
| Layer 1 Sentry | ❌ 不适用 | `AccessDeniedException` 属 `NON_CRITICAL_EXCEPTIONS`，不上报 Sentry（§23 明确说明）|
| Layer 2 日志+TraceId | ✅ 采用 | 业务/权限校验问题主场，GlobalExceptionHandler 现场日志 + DB payload 直查定位根因 |
| Layer 3 git 追溯 | ✅ 辅助 | 判定是回归还是原设计缺陷 → 结论：原设计缺陷（c8446b0ea，非回归）|

### 相关文件

- `backend/src/main/java/com/xiyu/bid/notification/core/NotificationRecipientFilter.java`（新增 Pure Core 纯函数）
- `backend/src/test/java/com/xiyu/bid/notification/core/NotificationRecipientFilterTest.java`（10 个单测）
- `backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java`（新增 `canAccessProject` 方法）
- `backend/src/main/java/com/xiyu/bid/project/notification/TaskReviewNotificationService.java`（核心修复点）
- `src/api/client.js`（前端 403 友好降级）
- `docs/exec-plans/tech-debt-tracker.md`（11 处审视清单）
- `docs/lessons/root-cause-analysis-spec030-task-review-notify-403.md`（完整 RCA 归档）
- `specs/030-fix-task-review-notify-403/`（spec/plan/tasks/contracts/data-model/quickstart）

## 45. Java 枚举与数据库 ENUM 不同步导致静默失败：alert_rules.type 事件（CO-523）

### 问题背景

2026-07-07 发现四个模块（资质证书/业绩管理/品牌授权/CA信息管理）的到期提醒均未生效。
用户报告"到期提醒没有收到"，但代码中 `CaExpiryScanService`、`PerformanceExpiryScanTask` 等服务逻辑完整、定时任务配置正确、单元测试通过。

### 根因分析

**代码先行，数据库未同步**：

- Java 枚举 `AlertRule.AlertType` 包含 9 个值：`DEADLINE, BUDGET, RISK, DOCUMENT, QUALIFICATION_EXPIRY, DEPOSIT_RETURN, PERFORMANCE_EXPIRY, CA_EXPIRY, CA_BORROW_OVERDUE`
- 数据库 `alert_rules.type` 列定义为 `enum('DEADLINE','BUDGET','RISK','DOCUMENT','QUALIFICATION_EXPIRY','DEPOSIT_RETURN')` — 只有 6 个值
- 后 3 个枚举值（`PERFORMANCE_EXPIRY, CA_EXPIRY, CA_BORROW_OVERDUE`）在代码新增时**没有同步更新数据库 ENUM 定义**

**失败路径**：
1. 定时任务触发 `CaExpiryScanService.scanCertificateExpiry()`
2. 服务调用 `ensureRule(AlertType.CA_EXPIRY, ...)` → 尝试 INSERT alert_rules 记录
3. MySQL 报错 `Data truncated for column 'type' at row 1`（枚举值不存在，被截断为空）
4. 异常被 `@Transactional` 回滚，告警未生成
5. **定时任务静默失败** — 日志中只有 ERROR 行，无重试、无告警、无用户可见反馈

**为什么没有更早发现**：
- 单元测试使用 H2 内存数据库，H2 对 ENUM 类型的校验比 MySQL 宽松
- 定时任务异常被吞掉（只 log.error，不抛出），服务层不报错
- 到期提醒是"不发通知"型故障，用户感知滞后

### 经验教训

1. **Java 枚举与数据库 ENUM 必须同步**：新增 Java 枚举值时，必须同步创建 Flyway 迁移脚本更新数据库 ENUM 定义
2. **H2 测试不等于 MySQL 行为**：H2 对 ENUM 校验宽松，MySQL 严格；涉及 ENUM 类型的变更必须在 MySQL 环境验证
3. **定时任务不能吞异常**：定时任务中的异常必须至少 log.error + 记录到监控（Sentry），不能只 log 后丢弃
4. **"不发通知"型故障需要主动检测**：到期提醒类功能失灵不会产生错误日志（因为失败的是"生成告警"本身），需要独立的"告警生成计数"监控

### 操作规范（建议固化到 CLAUDE.md / RULES.md）

1. **新增 Java 枚举值时检查清单**：
   - [ ] 该枚举是否映射到数据库列？
   - [ ] 如果是 ENUM 列，是否已创建 Flyway 迁移脚本？
   - [ ] 迁移脚本是否包含回滚脚本？
   - [ ] 回滚脚本是否有 `-- Input:` source header？

2. **定时任务异常处理标准**：
   ```java
   @Scheduled(cron = "...")
   public void executeScan() {
       try {
           int count = scanService.scan();
           log.info("扫描完成，生成 {} 条告警", count);
       } catch (Exception e) {
           log.error("定时扫描失败", e);
           // 必须上报 Sentry，不能只 log
           // sentryClient.captureException(e);
       }
   }
   ```

3. **ENUM 类型变更验证命令**：
   ```bash
   # 检查 Java 枚举值
   grep -A 20 "enum AlertType" backend/src/main/java/com/xiyu/bid/alerts/entity/AlertRule.java

   # 检查数据库 ENUM 定义
   docker exec xiyu-bid-local-mysql mysql -u xiyu_user -pXiyuDB!2026 -D xiyu_bid_main \
     -e "SHOW COLUMNS FROM alert_rules LIKE 'type';"

   # 运行回滚脚本覆盖测试
   cd backend && mvn test -Dtest=FlywayRollbackScriptCoverageTest
   ```

### 防复发方案

**建议新增 ArchUnit 测试**：断言 Java 枚举值数量 ≤ 数据库 ENUM 值数量（通过解析 B73 基线 + 迁移脚本中的 ALTER TABLE 语句）。

短期替代方案：在 `EntityTableMigrationCoverageTest` 中增加一条断言：所有 `@Enumerated` 字段对应的数据库列必须在其 CREATE TABLE 或最近一次 ALTER TABLE 中包含所有枚举值。

### 相关文件

- [AlertRule.java](file:///Users/user/xiyu/worktrees/qoder/backend/src/main/java/com/xiyu/bid/alerts/entity/AlertRule.java)（Java 枚举定义）
- [V1145__add_alert_rule_types.sql](file:///Users/user/xiyu/worktrees/qoder/backend/src/main/resources/db/migration-mysql/V1145__add_alert_rule_types.sql)（修复迁移）
- [U1145__add_alert_rule_types.sql](file:///Users/user/xiyu/worktrees/qoder/backend/src/main/resources/db/rollback/migration-mysql/U1145__add_alert_rule_types.sql)（回滚脚本）
- [CaExpiryScanService.java](file:///Users/user/xiyu/worktrees/qoder/backend/src/main/java/com/xiyu/bid/resources/service/CaExpiryScanService.java)（受影响服务）
- PR: https://gitee.com/allinai888/bid/pulls/1799

## 46. Nginx proxy_read_timeout 60s 默认值与后端实际耗时错配导致"request timeout 但数据已入库"（spec 031 / 2026-07-07）

### 问题背景

用户在标讯管理页批量导入 180 行 Excel 时，前端报 `request timeout`，但数据库检查发现数据已成功入库。Sentry 报警中显示 `userId":"anonymous","roleCode":"anonymous"`，MDC 用户上下文未填充。

按 §23 全链路日志排查 SOP 定位：

**Layer 1（Sentry）**：无 5xx 异常上报。
**Layer 2（业务日志）**：后端日志显示导入任务从 17:23:45 开始，17:25:28 完成，耗时 **103.5 秒**。所有标讯行成功 INSERT。
**Layer 3（git 追溯）**：`TenderController.importTenders` 是同步阻塞实现，前端 axios timeout=120s，Nginx `proxy_read_timeout 60s`（默认值）。
**Layer 4（配置）**：`/etc/nginx/conf.d/xiyu-bid.conf` 的 `location /api/` 块未显式配置 `proxy_read_timeout`，落到 Nginx 默认 60s。

### 根因分析

**双层超时错配 + 后端事务无中断信号**：

```
前端 axios timeout=120s
    ↓
Nginx proxy_read_timeout=60s  ← 第 60 秒返回 504 给前端
    ↓
后端 Spring 事务（仍在运行，无中断信号）  ← 第 103.5 秒 COMMIT 完成
    ↓
数据已入库，但用户看到 timeout
```

**MDC anonymous 副因**：`TraceFilter` 在 `filterChain.doFilter()` 之前调用 `putUserContext()`，但此时 `SecurityContextHolder` 还没被 `JwtAuthenticationFilter` 填充，导致 `putUserContext()` 走 anonymous 兜底分支。`JwtAuthenticationFilter` 后续虽然填充了 SecurityContext，但 **没有回写 MDC**，导致整个请求生命周期 MDC 都是 anonymous。

### 修复方案（spec 031 三 User Story）

**US1 异步化（P1）**：
- 新增 `tender_import_task` 表持久化任务状态（5 状态机：PENDING/PROCESSING/COMPLETED/PARTIAL_SUCCESS/FAILED）
- `TenderImportAppService.triggerImport()` 同步阶段：校验文件 + 创建 task + 读取 `byte[]` + 触发 `@Async executeImportAsync()`，3s 内返回 202 + taskId
- `@Async("tenderImportExecutor")` 异步阶段：Excel 解析 + 循环 createTender + 进度更新 + 三层降级失败标记（save → updateStatus → clearRedis）
- `TenderImportProgressService` 进度查询：Redis 优先 + DB fallback，`Optional<StringRedisTemplate>` 注入降级
- `TenderImportTaskRecoveryRunner`：启动时扫描 PROCESSING + updated_at < now-30min 的卡死任务，标记 FAILED
- 前端 `BulkImportDialog.vue` 改造：进度条 + 2s 轮询 + 终态结果展示

**US3 MDC 修复（P3，先做）**：
- `JwtAuthenticationFilter` 在 `setAuthentication()` 之后立即 `MDC.put(userId)` + `MDC.put(roleCode)`（通过 `EffectiveRoleResolver.resolveRoleCode(user)`，遵循 CO-373）
- `TraceFilter.putUserContext()` 仅作未认证请求兜底，已认证请求的 MDC 由 `JwtAuthenticationFilter` 刷新
- 新增 `MdcTaskDecorator`：`TaskDecorator` 实现，复制主线程 MDC（traceId/userId/roleCode）到 @Async 线程，执行完后 `MDC.clear()` 避免线程池复用串味
- `AsyncConfig` 所有 4 个 executor + 新增 `tenderImportExecutor` 都挂载 `MdcTaskDecorator`

**US2 性能优化（P2，未在 MVP 中实施）**：
- `CachedCrmLookupService` 批次内缓存 CRM 查询结果（`Map<String, Optional<CompanySearchResult>>` + `computeIfAbsent`）
- Hibernate `jdbc.batch_size: 50` + `order_inserts: true`
- MySQL URL 加 `rewriteBatchedStatements=true`

**Phase 6 Nginx 兜底**：`docs/release/nginx-tender-import-timeout.md` 记录 `proxy_read_timeout 180s` 配置 patch，由用户亲自部署。

### @Async 关键技术坑

1. **@Async 自调用失效**：Spring @Async 通过 AOP 代理实现，同类内方法互调不触发代理。采用 `@Lazy @Autowired` 注入自身代理（`self`）解决。
2. **@Async 参数必须为 `byte[]`**：`MultipartFile` 基于 Servlet 容器磁盘临时文件，HTTP 请求结束后 Tomcat 立即清理临时文件。如果在异步线程内才访问 `file.getBytes()`，会读到空数据或抛 IOException。
3. **异常捕获范围 `RuntimeException | Error`**：不 catch IOException 因为 `parseExcel` 内部已包装为 `IllegalArgumentException`；`Error` 级别必须 catch 避免异步任务静默终止。
4. **三层降级失败标记**：异步任务异常时 `failTaskWithThreeLayerFallback`（save → updateStatus → clearRedis），任意一层失败就退到下一层，确保任务状态最终被标记为 FAILED 而非永久卡在 PROCESSING。

### 经验教训

1. **Nginx 默认 60s `proxy_read_timeout` 是隐藏陷阱**：所有需要超过 60s 的同步接口都必须显式配置 Nginx 超时，或改为异步任务。
2. **"request timeout 但数据已入库"是同步阻塞接口 + 上游超时错配的典型症状**：看到这个症状应立即检查 Nginx/网关层超时配置，而非怀疑后端事务回滚。
3. **MDC 填充时机必须在 `JwtAuthenticationFilter.setAuthentication()` 之后**：在 `TraceFilter`（更早的 filter）中填充会读到 anonymous，因为 SecurityContext 还没被填充。
4. **@Async 跨线程必须显式传递 MDC**：Spring `TaskExecutor` 不会自动复制 MDC，必须通过 `TaskDecorator` 在 `decorate(Runnable)` 中复制主线程 MDC 到异步线程，并在 `finally` 中 `MDC.clear()`。
5. **`@WebMvcTest` 切片不实例化非 `@Controller`/`@ControllerAdvice` 的 bean**：TraceFilter 改造为依赖 `EffectiveRoleResolver` 后，所有 19 个 `@WebMvcTest` 都需要补 `@MockBean EffectiveRoleResolver` 才能加载 ApplicationContext。这是 Phase 3 回归修复的主要工作量。
6. **line-budget 300 行限制会触发拆分**：`TenderImportService` 从同步改造为异步后增至 329 行，触发 `check:line-budgets` 失败。拆分出 `TenderExcelParser`（248 行）承载 Excel 解析逻辑，原类降至 144 行，通过 `@Deprecated static final` re-export 常量保持向后兼容。

### 操作规范

1. **新增同步耗时接口前**：先评估 P95 耗时，若可能超过 60s，必须改为异步任务（@Async + DB 持久化 + 进度查询），不能依赖前端 timeout 拉长。
2. **MDC 填充位置**：已认证请求的 MDC 必须在 `JwtAuthenticationFilter.setAuthentication()` 之后立即填充；`TraceFilter` 仅作未认证请求兜底。
3. **新增 @Async 方法**：必须确认 `AsyncConfig` 中对应的 executor 挂载了 `MdcTaskDecorator`，否则异步线程日志会是 anonymous。
4. **新增 @WebMvcTest**：如果测试加载的 Controller 依赖 `TraceFilter`，必须 `@MockBean EffectiveRoleResolver` + `@MockBean CurrentUserResolver`。
5. **Nginx 配置变更**：所有 `location /api/` 块必须显式配置 `proxy_read_timeout`，不依赖默认 60s。

### 验证命令

```bash
# 后端：本次改动相关测试
cd backend && mvn test -Dtest='TenderImport*,TraceFilter*,MdcTaskDecorator*,AsyncConfig*,JwtAuthenticationFilter*'
# 期望：64 tests, 0 failures, 0 errors

# 前端：BulkImportDialog 单元测试
npx vitest run src/views/Bidding/list/components/BulkImportDialog.spec.js
# 期望：17 tests passed

# 架构测试
cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
# 期望：41 tests passed

# E2E（需开发环境）
npm run test:e2e -- --grep "tender-import-async"
```

### 相关文件

- [TenderImportAppService.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/tender/service/TenderImportAppService.java)（@Async 编排层）
- [TenderExcelParser.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/tender/service/TenderExcelParser.java)（line-budget 治理拆分）
- [MdcTaskDecorator.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/config/MdcTaskDecorator.java)
- [JwtAuthenticationFilter.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/auth/JwtAuthenticationFilter.java)（MDC 填充位置）
- [TraceFilter.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/config/TraceFilter.java)（anonymous 兜底）
- [AsyncConfig.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/config/AsyncConfig.java)
- [BulkImportDialog.vue](file:///Users/user/xiyu/worktrees/trae/src/views/Bidding/list/components/BulkImportDialog.vue)（前端进度条 + 轮询）
- [nginx-tender-import-timeout.md](file:///Users/user/xiyu/worktrees/trae/docs/release/nginx-tender-import-timeout.md)（Nginx 兜底配置）
- spec: `specs/031-tender-import-async-perf/`
- §23 — 全链路日志排查 SOP（本次排查使用 Layer 1-4）

### 关联案例：spec 039 仓库导出 @Async 自调用同陷阱不同解法（2026-07-17 / PR !2110, !2111）

spec 039 仓库模块实现"全量合订本导出"时再次踩中本节（§46）的 @Async 自调用陷阱——**同类内方法互调不触发 Spring AOP 代理，@Async 失效**。两次踩坑的解法不同：

| 维度 | spec 031（本节 §46） | spec 039（关联案例） |
|---|---|---|
| 解法 | `@Lazy @Autowired` 注入自身代理（`self.executeImportAsync()`） | 提取 `@Async` 方法到独立 Bean |
| 优点 | 改动小，不增加类 | 职责分离，避免 self-injection 反模式 |
| 缺点 | self-injection 是反模式，初学者难理解 | 新增一个类 |
| 适用 | 已有 service 不便拆分 | 新功能或可拆分的场景 |

**结论**：spec 039 优先选择"提取独立 Bean"避免 self-injection 反模式，与本节 §46 的解法形成对比。后续 ArchUnit 规则建议禁止 `@Async` 方法被同类调用（参见 §64 设计评审第 1 条）。

- 关联 PR：!2110、!2111（spec 039 仓库导出 @Async 修复）
- 关联本节：§46 spec 031 标讯批量导入 @Async 自调用失效
- 关联 §64 设计评审 10 个通用问题第 1 条（@Async 与 @Transactional 事务边界）

---

## 47. OSS 用户权限扩散：双轨制下的"内部映射"是权限越权的根源（spec 032 / 2026-07-08）

### 问题背景

OSS 同步用户（如 03063 韩辉、06234 郑蓉蓉）配置为"跨部门协作人员"/"投标管理部高级投标经理"，但登录后能看到系统所有菜单（包括系统设置、资源管理、知识库等管理员专属菜单）。

**用户的核心反馈**："用户从 OSS 返回的菜单权限应该完全依赖于 OSS 的权限，你不应该在我们系统内部去做一些映射。"

### 根因：双轨制下的 4 个权限扩散点

系统存在**双轨制权限体系**：
- **本地账户**：DB roleCode → RoleProfileCatalog seed → menuPermissions（含 "all" 短路键）
- **OSS 用户**：OSS 返回菜单 codes → 缓存 → menuPermissions（应严格按 OSS 返回值）

问题出在 OSS 用户经过 `UserDetailsServiceImpl` 和 `DataScopeConfigService` 时，被**内部映射逻辑**污染了 4 个交叉点：

| # | 位置 | 扩散行为 |
|---|------|---------|
| 1 | `UserDetailsServiceImpl.java#L126` | OSS 用户 roleCode=admin 时触发 `menuPermissions.contains("all")` 扩散，展开所有 seed 权限 |
| 2 | `UserDetailsServiceImpl.java#L156` | OSS admin 用户补发 `system.admin` / `warehouse.manage` 系统级权限键 |
| 3 | `DataScopeConfigService.java#L138` | OSS 缓存菜单直接加入 authorities 时未过滤 "all" |
| 4 | `DataScopeConfigService.java#L148` | OSS 用户合并 catalog seed 时未过滤 "all" |

### 经验教训

1. **OSS 用户的权限必须严格等于 OSS 返回值**：系统内部不应做任何"映射"、"扩散"、"补发"。OSS 返回什么菜单，用户就只能看到什么菜单。
2. **"all" 是内部 admin 专属权限键**：OSS 用户永远不应持有 "all"，因为 OSS 返回的是具体菜单 codes（如 1001/1002），不含 "all"。任何让 OSS 用户获得 "all" 的路径都是 bug。
3. **双轨制交叉点是最危险的**：本地账户的"扩散"逻辑（admin → all → 所有 seed 权限）对本地 admin 是预期行为，但对 OSS admin 用户是越权。交叉点必须有 `isOssUser` 守卫。
4. **前端必须防御性兜底**：即使后端漏过 "all"，前端 `hasPermission` 也不应对 OSS 用户短路放行。纵深防御。
5. **normalizer 是字段透传的关键关卡**：`normalizeUser` 显式枚举字段，新增 `isOssUser` 字段时必须同步更新 normalizer，否则后端返回的字段会被静默丢弃。

### 修复方案（三层防御，spec 032）

**第一层：后端 UserDetailsServiceImpl 守卫**
- OSS 用户 menuPermissions 过滤 "all" 后再加入 authorities
- OSS 用户不触发 admin 扩散（`!isOssUser && (contains("all") || roleCode=admin)`）
- OSS 用户不补发 `system.admin` / `warehouse.manage`

**第二层：后端 DataScopeConfigService 守卫**
- OSS 缓存菜单过滤 "all"（`ossPermissions.stream().filter(p -> !"all".equals(p))`）
- OSS 用户合并 catalog seed 时过滤 "all"

**第三层：前端 hasPermission 守卫**
- `if (perms.includes('all') && !state.currentUser?.isOssUser) return true`
- OSS 用户即使携带 "all" 也不短路，按精确匹配鉴权
- `AuthResponse` 新增 `isOssUser` 字段，`normalizeUser` 透传该字段

### 测试证据

```
后端：5 个新测试 + 回归测试全绿
- ossCachedAdminRoleShouldNotExpandAllSeedPermissions ✅
- ossAdminUserShouldNotHaveSystemAdminPermission ✅
- localAdminUserShouldHaveAllAndSystemAdminPermissionRegression ✅（回归保护）
- getRoleMenuPermissions_OssAdminUserShouldNotContainAll ✅
- getRoleMenuPermissions_LocalAdminUserShouldContainAllRegression ✅（回归保护）

前端：25 个测试全绿
- OSS 用户 menuPermissions 含 all 时不应短路放行 ✅
- 本地 admin 用户 menuPermissions 含 all 时应短路放行（回归）✅
- normalizeUser 保留 isOssUser 字段 ✅
```

关键日志确认：
- `local_admin_regression isOssUser=false roleCode=admin` authorities 含 `all, system.admin` ✅
- `oss_admin_all isOssUser=true roleCode=admin` authorities 仅 `[ROLE_MANAGER, admin, ROLE_ADMIN]` ✅
- `06234 isOssUser=true roleCode=admin` authorities `[ROLE_MANAGER, admin, ROLE_ADMIN, bidding]` ✅

### 操作规范（防复发）

1. **OSS 用户权限相关改动必须加 `isOssUser` 守卫**：任何涉及 admin 扩散、catalog seed 合并、系统权限补发的代码路径，都必须检查 `isOssUser`。
2. **新增 AuthResponse 字段时必须同步更新 `normalizeUser`**：前端 normalizer 显式枚举字段，新增字段不更新 normalizer 会被静默丢弃。
3. **OSS 用户权限测试必须包含"反向断言"**：不只测"应该有什么"，还要测"不应该有什么"（如 `doesNotContain("all")`、`doesNotContain("system.admin")`）。
4. **本地 admin 回归测试是必选项**：每次修改 OSS 用户权限逻辑，必须同时跑本地 admin 回归测试，确保不破坏本地 admin 的预期扩散行为。

### 验证命令

```bash
# 后端：权限扩散相关测试
cd backend && mvn test -Dtest='UserDetailsServiceImplTest,DataScopeConfigServiceTest' -Djacoco.skip=true

# 前端：hasPermission + normalizer 测试
npx vitest run src/stores/__tests__/user.spec.js src/api/authNormalizer.spec.js

# 架构测试
cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

### 相关文件

- [UserDetailsServiceImpl.java](file:///Users/user/xiyu/worktrees/claude/backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java)（三处 isOssUser 守卫）
- [DataScopeConfigService.java](file:///Users/user/xiyu/worktrees/claude/backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java)（OSS 菜单过滤 "all"）
- [AuthResponse.java](file:///Users/user/xiyu/worktrees/claude/backend/src/main/java/com/xiyu/bid/dto/AuthResponse.java)（新增 isOssUser 字段）
- [user.js](file:///Users/user/xiyu/worktrees/claude/src/stores/user.js)（hasPermission 前端守卫）
- [authNormalizer.js](file:///Users/user/xiyu/worktrees/claude/src/api/authNormalizer.js)（isOssUser 字段透传）
- spec: `specs/032-fix-oss-permission-diffusion/`
- §29 — hasAnyRole 双轨制陷阱（同源教训）
- §44 — EffectiveRoleResolver 权限来源统一（前置治理）

---

## 48. 止血补丁与技术债清偿必须分 PR，避免一次性还清导致长时间阻塞

### 问题背景

2026-07-08，spec 032 的 OSS 菜单权限修复从 16:43 完成 1:N 映射核心改动，到 20:13 才提交 PR，历时约 3.5 小时。核心代码改动（`application.yml` 列表化 + `OssMenuPermissionMapper` 1:N 映射）本可在 1 小时内合入，但后续被设计评审发现的 5 类技术债拖成了长时间重构。

这些债务包括：

1. `OssMenuPermissionMapper` 未声明为 Spring Bean，多处重复实例化
2. `application.yml` 仍用逗号分隔字符串，可读性和可维护性差
3. OSS 用户权限来源不唯一，仍混入本地 `RoleProfileCatalog` seed
4. 缺少架构测试覆盖所有 OSS 菜单码映射
5. `UserDetailsServiceImpl` / `DataScopeConfigService` / `RoleProfileCatalog` 中存在重复的 admin 权限过滤逻辑

### 时间线

| 阶段 | 耗时 | 内容 |
|------|------|------|
| 核心修复 | ~1h | 1:N 映射、YAML 列表化、前端 `all` 短路修复 |
| 设计评审后债务清偿 | ~3h | 提取 Bean、统一过滤、拆分类、迁移包、补测试、逐个修复架构测试失败 |
| 提交 PR | ~20min | pre-push gate、force-with-lease、创建 PR |

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 设计评审后发现债务，用户要求“本次全部修好” | 止血补丁和债务清偿应明确拆分，避免线上 bug 被重构阻塞 | P0/P1 线上问题先合最小修复；技术债单独开 PR 治理 |
| 5 类债务同时清偿，测试失败串行暴露 | 重构范围越大，返工链越长 | 单笔 PR 聚焦一个债务类型；大范围重构先跑受影响测试清单 |
| `sync-env.sh` rebase 后分支历史改写，push 需 force-with-lease | agent 分支早操后 push 失败是常态 | rebase 后若此前已 push，直接使用 `--force-with-lease` |
| `BidCaseSliceArchitectureTest` 既有失败干扰收尾判断 | 无关失败会消耗收尾信心 | 收尾前明确列出“本次无关的既有失败” |

### 操作规范（建议固化到 CLAUDE.md / RULES.md）

1. **线上问题必须拆为止血 PR + 债务清偿 PR**：
   - 止血 PR：只修复症状，保证最小改动当天可上线
   - 债务清偿 PR：承载设计评审后的重构、测试补齐、架构治理
2. **开始大范围重构前，先一次性跑受影响测试**：
   ```bash
   cd backend && mvn test -Dtest='ArchitectureTest,ResponsibilityArchitectureTest,OssMenuPermissionMappingCoverageTest,UserDetailsServiceImplTest,DataScopeConfigServiceTest'
   ```
3. **agent 分支 rebase 后若已 push 过，默认使用 `--force-with-lease`**：无需每次询问，但要在 PR 描述中注明。
4. **PR 描述中必须列出“既有无关失败”**：避免 reviewer 在无关红点上浪费时间。

### 验证命令

```bash
# 快速确认本次改动是否通过核心门禁
cd /Users/user/xiyu/worktrees/claude
npm run build
bash scripts/pre-push-gate.sh --skip-tests --skip-e2e-check
cd backend && mvn test -Dtest='ResponsibilityArchitectureTest,OssMenuPermissionMappingCoverageTest,UserDetailsServiceImplTest,DataScopeConfigServiceTest'
```

### 相关文件

- PR !1892 — `fix(permission): OSS 菜单权限映射 1:N 多值映射与权限扩散修复`
- `docs/lessons/lessons-learned.md` §47 — OSS 用户权限扩散根因
- `docs/lessons/oss-integration-lessons.md` — OSS 菜单码 1:N 映射集成经验
- `docs/lessons/decisions.md` ## 4 — admin 专属权限过滤统一化决策

## 49. OSS 权限键必须全量盘点补全：单点修复是债务积累，不是完结（CO-560 / 2026-07-09）

### 问题背景

2026-07-09，测试服务器 172.16.38.78 报 OSS 用户提交复盘报告 403。按 §23 全链路日志 SOP 排查：

- 10 次 `POST /api/projects/{id}/retrospective` 全部 status=403
- traceId 溯源：user=10208 isOssUser=true，authorities 列表无 `retrospective.submit`
- 代码证据链：`ProjectRetrospectiveController#submit` 标注 `@PreAuthorize("hasAuthority('retrospective.submit')")`，但 `application.yml` 中 1003（投标项目）菜单码只映射到 `["project"]`，缺 `retrospective.submit`

**用户感知与真相的差异**：用户以为"报错 201"是错误，实际 201 是 URL 中的项目 ID，HTTP 状态码是 403。

### 系统盘点：单点修复不够

第一次修复只补了 `retrospective.submit` 一个键就提交。用户立即追问："其他的权限会不会也有这样的问题？帮我系统地盘点一下，这次务必改掉所有的问题。"

脚本对比 `RoleProfileCatalog.menuPermissions`（45 个权限键）与 `application.yml` 的 `menu-code-to-permission-key-mappings`，找出 **30 个缺失权限键**。后端 + 前端并行扫描确认每个缺失键的实际影响：

| 缺失类型 | 数量 | 影响 |
|---|---|---|
| 工作台 widget 可见性（`dashboard:view_*` + `dashboard.quickStart`） | 15 | OSS 用户工作台 widget 缺失 |
| 标讯操作（`bidding.manage/create/delete/sync` + `tender.view`） | 5 | OSS 用户标讯列表按钮不显示 |
| 投标项目操作（`project.create/view` + `evaluation.update` + `result.register` + `retrospective.review` + `closure.request/review` + `lead.assign`） | 8 | OSS 用户工作台"创建项目"按钮缺失；未来加 @PreAuthorize 会 403 |
| 资质证书（`certificate.manage`） | 1 | OSS 用户资质页面 isAdmin 判定失败 |
| 告警规则（`settings-alerts`） | 1 | OSS 用户告警规则路由 403（路由 meta 要求 `['settings', 'settings-alerts']` 全部命中） |
| `analytics` 命名不一致 | 1 | OSS 用户工作台汇总数据不显示（catalog 用 `analytics`，OSS 此前只有 `analytics-dashboard`） |
| 任务看板（`task.assign/review` + `task.view.own/handle.own`） | 4 | OSS 用户告警待办加载失败；未来加 @PreAuthorize 会 403 |

### 关键根因

**spec 032 修复 OSS 权限扩散后，OSS 用户 authorities 严格等于 OSS 菜单码映射出的内部权限键**。`RoleProfileCatalog` 中角色声明的 `menuPermissions` 只对本地用户走 catalog fallback 生效，OSS 用户不走此路径。如果 catalog 中声明的权限键在 OSS 映射表中没有来源：

1. **前端 `hasPermission(xxx)` 对 OSS 用户返回 false** → UI 元素缺失（按钮/卡片不显示）
2. **未来给这些键加 `@PreAuthorize(hasAuthority('xxx'))` 会重演 403 事故**

**`tender.view` 就是前车之鉴**：此前因 OSS 缺映射被迫回退为 `hasAnyRole`（`TenderController.java:135-139` 注释），这正是单点修补而非系统盘点的代价。

### 修复方案

**修复原则**：单点修复是债务积累，必须全量盘点。

**操作**：按 OSS 菜单码业务语义归类，把 30 个缺失权限键挂到合适的父菜单下：

| OSS 菜单码 | 追加权限键 |
|---|---|
| 1001 工作台 | `dashboard.quickStart` + 14 个 `dashboard:view_*` |
| 1002 标讯中心 | `bidding.manage/create/delete/sync` + `tender.view` |
| 1003 投标项目 | `project.create/view` + `evaluation.update` + `result.register` + `retrospective.review` + `closure.request/review` + `lead.assign` |
| 1005 资源管理 | `deposit.return.fill` |
| 100402 资质库 | `certificate.manage` |
| 1007 数据分析 | `analytics`（与 `analytics-dashboard` 共存，命名不一致需双映射） |
| 1010 系统设置 | `settings-alerts` |
| 1011 任务看板 | `task.assign/review` + `task.view.own` + `task.handle.own` |

**`analytics` 命名不一致处理**：catalog 用 `analytics`，OSS 此前只有 `analytics-dashboard`，两者是不同字符串。保留 `analytics-dashboard` 用于路由，新增 `analytics` 用于业务逻辑。

### 防复发守卫（双重保险）

**守卫 1**（已在第一次修复中建立）：`PreAuthorizeAuthorityOssMappingCoverageTest#allHasAuthorityKeys_mustHaveOssMappingSource`
- 扫描所有 `@PreAuthorize(hasAuthority('xxx'))` 用法
- 校验权限键在 OSS 映射表中有来源

**守卫 2**（本次系统盘点新增）：`PreAuthorizeAuthorityOssMappingCoverageTest#allCatalogPermissionKeys_mustHaveOssMappingSource`
- 从 `RoleProfileCatalog.seedDefinitions()` 反向扫描所有角色 `menuPermissions`
- 校验每个权限键（除 admin 专属 `"all"`）都在 OSS 映射表中有来源
- 未来给 catalog 新增权限键时，如果忘了在 OSS 映射表加来源，架构测试会立即失败

### 时间线

| 阶段 | 内容 |
|---|---|
| 全链路日志 SOP 排查 | 10 次 POST /retrospective 全部 403 → traceId 溯源 → 代码证据链 |
| 第一次修复 | 补 `retrospective.submit` + 守卫 1 → PR !1948 第一次 push |
| 系统盘点 | 用户追问"其他权限会不会也有" → 脚本对比 + 后端/前端并行扫描 |
| 全量补全 | 30 个权限键一次性补齐 + 守卫 2 → PR !1948 第二次 push |
| 反向验证 | 临时移除 `tender.view` → 守卫精确报告 → BUILD FAILURE → 恢复后通过 |

### 验证命令

```bash
cd /Users/user/xiyu/worktrees/cursor/backend
# 双重守卫测试
mvn test -Dtest='PreAuthorizeAuthorityOssMappingCoverageTest,OssMenuPermissionMappingCoverageTest'
# 完整测试套
mvn test -Dtest='PreAuthorizeAuthorityOssMappingCoverageTest,OssMenuPermissionMappingCoverageTest,UserDetailsServiceImplTest,ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest,RoleProfileCatalogTest,RoleProfileCatalogTenderLifecycleTest'
# 反向验证（临时移除一个映射 → 应失败 → 恢复 → 通过）
```

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 单点修复 `retrospective.submit` 后就准备收尾 | OSS 权限键缺失是系统性问题，不是单点问题 | 修复 OSS 权限 403 时，必须全量盘点 catalog 与 OSS 映射的差集 |
| `tender.view` 已因 OSS 缺映射被迫回退为 `hasAnyRole` | 单点修补会积累成历史债 | 修复时遇到"回退为 hasAnyRole"注释，必须同时补全映射，避免同类问题延续 |
| `analytics` 与 `analytics-dashboard` 命名不一致 | 权限键字符串不一致会导致前端 hasPermission 失败 | catalog 与 OSS 映射的权限键必须字符串严格一致；命名不一致时双映射共存 |
| 守卫 1 只覆盖已使用的 `@PreAuthorize` 用法 | catalog 中声明但未在 @PreAuthorize 使用的权限键是定时炸弹 | 必须从 catalog 反向扫描（守卫 2），覆盖所有声明的权限键 |

### 操作规范（建议固化到 CLAUDE.md / SECURITY.md）

1. **修复 OSS 权限 403 时，必须全量盘点 catalog 与 OSS 映射的差集**：
   ```bash
   # 脚本对比 RoleProfileCatalog.menuPermissions 与 application.yml 的 menu-code-to-permission-key-mappings
   # 找出 catalog 中有但 OSS 映射中没有的权限键
   ```
   单点修复不允许直接提交 PR，必须同时给出全量盘点的差集分析。

2. **遇到"已回退为 hasAnyRole"注释，必须同时补全映射**：
   - 注释中提到的 `@PreAuthorize(hasAuthority('xxx'))` 因 OSS 缺映射回退为 `hasAnyRole`
   - 必须同时补全 OSS 映射，避免同类问题延续
   - 补全后可在后续 PR 恢复为 `hasAuthority`

3. **权限键命名不一致时双映射共存**：
   - catalog 与 OSS 映射的权限键字符串不一致时（如 `analytics` vs `analytics-dashboard`）
   - 不要强行归一化（会破坏路由或前端代码）
   - 双映射共存，每个键保留各自的语义

4. **架构守卫必须双向覆盖**：
   - 正向：扫描 `@PreAuthorize(hasAuthority('xxx'))` 用法，校验权限键在 OSS 映射中有来源
   - 反向：扫描 `RoleProfileCatalog.menuPermissions`，校验每个权限键在 OSS 映射中有来源
   - 两个方向缺一不可，否则会漏掉"声明但未使用"的定时炸弹

### 相关文件

- PR !1948 — `fix(permission): CO-560 系统盘点补全所有 catalog 业务操作权限键 OSS 映射`
- `docs/lessons/lessons-learned.md` §47 — OSS 用户权限扩散根因（spec 032）
- `docs/lessons/lessons-learned.md` §48 — 止血补丁与技术债清偿必须分 PR（CO-551）
- `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java` — 角色 menuPermissions 真相来源
- `backend/src/main/resources/application.yml` — OSS 菜单码 → 内部权限键映射表
- `backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java:135-139` — `tender.view` 历史回退注释
- `backend/src/test/java/com/xiyu/bid/architecture/PreAuthorizeAuthorityOssMappingCoverageTest.java` — 双重守卫
---

## 50. SPRING_CONFIG_IMPORT 外部配置覆盖导致代码修复无效（2026-07-09 第 65/66 次部署事故）

### 问题背景

2026-07-09 第 65 次部署后，OSS 用户 06234（郑蓉蓉）无法登录，报错 `ROLE_NOT_AUTHORIZED: 无有效 OSS 角色，不允许登录`。回滚到第 64 次后恢复。开发者修复代码（PR !1949 合并白名单用户 catalog 菜单权限）后第 66 次部署，**代码修复无效**，06234 仍然无法登录。

### 事故时间线

| 时间 | 操作 | 判断 |
|------|------|------|
| 17:37 第 65 次部署 | PR !1945 引入 person-to-role 优先解析 | 06234 登录失败 |
| 17:46 回滚到第 64 次 | 回滚后 06234 恢复登录 | 误判根因为代码 bug |
| 18:15 第 66 次部署 | PR !1949 修复菜单合并逻辑 | 06234 仍然登录失败 |
| 18:21 排查日志 | 发现 `role resolved from person-to-role-mappings: 06234 -> bid-SystemAdmin` | 配置不是 /bidAdmin |
| 18:22 检查 jar 内 application.yml | 06234 -> /bidAdmin ✅ | jar 内配置正确 |
| 18:25 发现根因 | `/etc/xiyu-bid/application-org-mappings.yml` 中 06234 -> bid-SystemAdmin | **外部配置覆盖了 jar 内配置** |
| 18:26 修复外部配置 | sed 改 bid-SystemAdmin 为 /bidAdmin + 重启 | 06234 恢复登录 ✅ |

### 根因

服务器通过 `SPRING_CONFIG_IMPORT=optional:file:/etc/xiyu-bid/application-org-mappings.yml` 导入了一个外部配置文件。Spring Boot 的 `SPRING_CONFIG_IMPORT` 导入的外部配置优先级**高于** jar 内 `application.yml`。

外部配置文件 `/etc/xiyu-bid/application-org-mappings.yml` 中 06234 的 role-code 是 `bid-SystemAdmin`（旧的错误值），而 jar 内 `application.yml` 中是 `/bidAdmin`（修复后的正确值）。外部配置覆盖了 jar 内配置，导致代码修复（PR !1949）完全无效。

### 故障链

```
PR !1945 引入 person-to-role 优先解析
    ↓
OssRoleResolver.resolveRoleCodeFromJobList() 从 person-to-role-mappings 解析得到 bid-SystemAdmin
    ↓
LoginRoleWhitelist.isAllowed(bid-SystemAdmin) → false（不在 RoleProfileCatalog 7 个标准角色中）
    ↓
OssLoginFlowService: "role not allowed for user=06234" → 清除权限缓存
    ↓
AuthService.login(): "no valid OSS role" → 403 登录失败
    ↓
UserDetailsServiceImpl: "fail-closed, no DB fallback" → 401 后续请求
```

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 代码修复后部署，问题仍然存在 | 服务器外部配置文件可以覆盖 jar 内配置，代码修复不能改变外部配置 | 部署时必须检查 `SPRING_CONFIG_IMPORT` 引入的外部配置是否与 jar 内配置一致 |
| 排查时只看代码和 jar 内配置 | 外部配置文件是不可见的配置漂移源 | 排查"配置看起来正确但行为异常"时，必须检查 `SPRING_CONFIG_IMPORT` |
| 误判根因为代码 bug 后回滚 | 回滚前没有确认根因，错误回滚了正确修复 | 回滚前用"五个为什么"追问根因，确认被回滚的修复与根因无关 |
| 外部配置文件中的 role-code 是旧值 | 外部配置文件没有随代码修复一起更新 | 修复涉及配置变更时，必须同步更新服务器外部配置文件 |

### 操作规范（已固化到部署流程）

1. **部署预检必须检查外部配置覆盖**：在 `LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` §6 服务器预检中新增 §6.2，检查 `SPRING_CONFIG_IMPORT` 引入的外部配置文件中的 person-to-role-mappings 与 jar 内配置是否一致。
2. **修复涉及配置变更时，必须同步更新服务器外部配置文件**：不能只改代码和 jar 内配置，还要检查 `/etc/xiyu-bid/application-org-mappings.yml` 是否需要同步更新。
3. **排查"配置看起来正确但行为异常"时，必须检查 `SPRING_CONFIG_IMPORT`**：这是不可见的配置漂移源，jar 内配置正确不代表运行时配置正确。

### 验证方法

```bash
# 检查 SPRING_CONFIG_IMPORT 引入的外部配置文件
ssh jetty@172.16.38.78 'grep "SPRING_CONFIG_IMPORT" /etc/xiyu-bid/backend.env'
# 期望输出：SPRING_CONFIG_IMPORT=optional:file:/etc/xiyu-bid/application-org-mappings.yml

# 检查外部配置文件中的 person-to-role-mappings
ssh jetty@172.16.38.78 'grep -A 2 "person-identifier: \"06234\"" /etc/xiyu-bid/application-org-mappings.yml'
# 期望输出：role-code: /bidAdmin（不是 bid-SystemAdmin）

# 对比 jar 内配置
unzip -p app.jar BOOT-INF/classes/application.yml | grep -A 2 "person-identifier: \"06234\""
# 期望输出：role-code: /bidAdmin
```

### 相关文件

- `/etc/xiyu-bid/application-org-mappings.yml` — 服务器外部配置文件（通过 SPRING_CONFIG_IMPORT 导入）
- `/etc/xiyu-bid/backend.env` — 包含 `SPRING_CONFIG_IMPORT` 环境变量
- `backend/src/main/resources/application.yml` — jar 内配置（被外部配置覆盖）
- `backend/src/main/java/com/xiyu/bid/crm/application/OssRoleResolver.java` — 角色解析逻辑
- `backend/src/main/java/com/xiyu/bid/security/domain/LoginRoleWhitelist.java` — 角色白名单校验
- `docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` §6.2 — 部署预检检查点（已固化）

---

## 51. 首次生产部署复盘：测试环境通过 ≠ 生产环境通过（2026-07-09 首次生产部署）

### 问题背景

2026-07-09 执行西域数智化投标管理平台首次生产环境部署。部署前已完成 35 次测试环境部署、225 条 Flyway 迁移验证、完整的生产环境档案和部署 SOP。但首次部署仍遇到 5 个问题，导致从开始到业务可用耗时约 5 小时。

### 五个根因

| # | 问题 | 根因分类 | 测试环境能发现吗 |
|---|------|---------|----------------|
| 1 | V1092 collation 冲突 | 隐性配置差异（测试 utf8mb4_unicode_ci vs 生产 utf8mb4_0900_ai_ci） | ❌ |
| 2 | skipUnmappedUsers 配置声明但代码未使用 | 配置-代码契约断裂 | ⚠️ 集成测试可以 |
| 3 | 同步"假成功"掩盖问题（8572 条全标记 SUCCESS，实际只入库 168 条） | 统计口径不严谨 | ⚠️ 代码审查可以 |
| 4 | 端口认知混乱（application-prod.yml 默认 8080，实际 SERVER_PORT=18080） | 文档中 Nginx 对外端口与后端内部端口混用 | ✅ |
| 5 | 本地 HTTP_PROXY 干扰 curl（第 19/23/N 次复发） | 本地代理 | ✅ 已知但反复复发 |

### 核心教训

1. **测试环境通过 ≠ 生产环境通过** — collation、字符集、时区、SQL mode 这些隐性配置差异，只有在生产环境才会暴露
2. **配置项声明 ≠ 配置生效** — `skipUnmappedUsers` 在 Properties 类中声明但代码未使用，单元测试只测了声明层没测行为层
3. **"跳过"不等于"成功"** — 同步框架把 `upsert()` 返回 `Optional.empty()`（跳过）也标记为 `successItem`，导致 8572 条"假成功"掩盖了 8404 条跳过
4. **Nginx 对外端口 ≠ 后端内部端口** — `application-prod.yml` 默认 `8080` 是 fallback 值从未实际使用，两个环境后端内部端口都是 `18080`（通过 `SERVER_PORT=18080` 注入），Nginx 对外端口 `80/8080` 反代到后端 `18080`
5. **本地代理是排障隐形杀手** — macOS `HTTP_PROXY=127.0.0.1:7897` 导致 `curl http://172.16.10.149:18080` 走代理隧道超时，排障时必须 `curl --noproxy '*'` 或直接 ssh 到服务器内部执行

### 端口对照表（测试和生产一致）

| 层 | 端口 | 说明 |
|----|------|------|
| Nginx 对外 | 80 / 8080 | 浏览器和外部 curl 访问入口 |
| 后端内部 | 18080 | `SERVER_PORT=18080`，通过 `/etc/xiyu-bid/backend.env` 注入 |
| application-prod.yml 默认 | 8080 | 仅是 fallback 默认值，**从未实际使用** |
| 本地开发环境（主工作区 trae） | 18089 | 仅本地开发用，与生产无关 |

### 改进措施

- V1092 collation 教训：临时表必须显式指定 `COLLATE` 与关联表对齐
- skipUnmappedUsers 修复：1 行代码加 `properties.isSkipUnmappedUsers()` 条件 + 环境变量 `XIYU_ORG_SYNC_SKIP_UNMAPPED_USERS=false`
- 端口对照表已更新到 `LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` §1 和 `PROD_ENVIRONMENT_PROFILE.md` §1.2
- 所有 curl 排障命令统一加 `--noproxy '*'`

### 相关文件

- `docs/release/deploy-report-2026-07-09-1st-prod.md` — 首次生产部署报告
- `docs/release/postmortem-2026-07-09-1st-prod.md` — 复盘文档
- `docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` §1 — 端口记录（已更新）
- `docs/release/PROD_ENVIRONMENT_PROFILE.md` §1.2 — 网络架构（端口已正确）
- `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java` — skipUnmappedUsers 修复

## 52. OSS 角色解析忽略 roleCode 字段：roleName 映射不全导致登录被拒（2026-07-10 / 用户 04569 / PR !1977）

### 问题背景

用户 **04569 沈樱娇** 在生产环境通过 **用户名+密码** 和 **OSS 单点登录** 两种方式都无法登录系统。生产 Release ID `e88dbd207`（2026-07-09 部署）。

按本文件 §23 全链路日志排查 SOP Layer 2（结构化日志 + TraceId）定位根因。

### 根因（生产日志证据链）

traceId=`e182d631c4e844e89b63c3749c9073ab`，用户 04569：

1. `OssDelegationService` — `OSS login succeeded for user: 04569` ✓ OSS 认证成功
2. `OssLoginFlowService` — `OSS user info retrieved for user=04569` ✓ getUserInfo 成功
3. `CrmHttpClient` 打印 getUserInfo body，roleList 包含 `{"roleName":"投标-行政专员","roleCode":"bid-administration","status":"1","del":false}`
4. `OssRoleResolver` — `cannot resolve internal role from OSS jobList for jobNumber=04569` ✗ **角色解析失败**
5. `OssLoginFlowService` — `role not allowed for user=04569, roleCode=null, clearing cache` ✗ **缓存被清空**
6. `AuthService` — `Login denied for user=04569: no valid OSS role` ✗ **登录被拒绝**

### 5 Whys 分析

| 层级 | 问题 | 答案 |
|---|---|---|
| 1 | 为什么登录失败？ | OssRoleResolver 无法解析角色，登录被拒 |
| 2 | 为什么无法解析角色？ | sysRoleList 遍历时只用 `sysRole.getRoleName()` |
| 3 | 为什么 roleName 匹配不到？ | 04569 的 roleName "投标-行政专员" 不在 `OSS_ROLE_NAME_TO_INTERNAL` 映射表（表中只有"行政人员"） |
| 4 | 为什么不查 roleCode？ | 代码完全忽略 `sysRole.getRoleCode()` 字段 |
| 5 | 为什么注释误导后续开发？ | `JobRoleLookupResolver` 注释错误声称「sysRoleList 只包含 roleName，不包含 roleCode」，实际 `CrmJobListResponse.SysRole` 明确定义了 roleCode 字段 |

### 根因

`OssRoleResolver.resolveRoleCodeFromJobList` 遍历 sysRoleList 时**只用 `sysRole.getRoleName()`，完全忽略 `sysRole.getRoleCode()`**。04569 的 roleCode `bid-administration` = `RoleProfileCatalog.ADMIN_STAFF_CODE` 是已注册的白名单角色，但从未被检查。

### 补充发现

- **PR !1972**（commit `a73181b0c`，2026-07-10 合并）修复了 OSS 密码登录失败时误抛 `RoleNotAuthorizedException`，但生产 Release `e88dbd207` 是 2026-07-09 部署的，早于此修复
- 本 Bug 的根因（OssRoleResolver 忽略 roleCode）覆盖 SSO + 密码两条路径，与 PR !1972 的 fallback 修复**独立**
- 即便部署 PR !1972 后，04569 通过 SSO 登录仍会失败，根因是 OssRoleResolver 本身的解析逻辑漏洞

### 修复方案（双保险）

#### 1. 主修复：sysRoleList 遍历优先检查 roleCode

`OssRoleResolver.resolveRoleCodeFromJobList`:

- 优先检查 `sysRole.getRoleCode()`（通过 `JobRoleLookupResolver.mapOssRoleCodeToInternal` 判断是否为 bid 系统已知角色码）
- 再检查 `sysRole.getRoleName()` 作为 fallback（中文角色名通过映射表匹配）

#### 2. Fallback：从 getUserInfo 返回的 employeeInfo.roleList 解析

新增 `OssRoleResolver.resolveRoleCodeFromEmployeeInfo(JsonNode employeeInfo, String username)`:

- jobList 解析失败时自动 fallback
- 从 getUserInfo 返回的 roleList 解析 bid-* 角色码
- 同样按 `status=1 && del=false` 过滤

`OssLoginFlowService.cacheOssPermissions` 在 `resolveRoleCodeFromJobList` 返回 null/空时调用此 fallback。

#### 3. 注释修正

`JobRoleLookupResolver` 原注释错误声称「sysRoleList 只包含 roleName，不包含 roleCode」，更新为「sysRoleList 同时包含 roleName 和 roleCode，roleCode 优先解析」。

### 测试证据

`OssRoleResolverTest` 新增 8 个根因行为测试：

1. `resolveRoleCodeFromJobList_usesRoleCodeWhenRoleNameNotInMapping` — **根因复现**：roleCode=bid-administration 但 roleName 不在映射表时通过 roleCode 解析成功
2. `resolveRoleCodeFromJobList_fallsBackToRoleNameWhenRoleCodeNull` — roleCode 为 null 时 fallback 到 roleName
3. `resolveRoleCodeFromJobList_ignoresOssSystemRoleCodes` — OSS 系统角色码（SE/PE）不误匹配
4. `resolveRoleCodeFromEmployeeInfo_resolvesBidRoleCode` — 从 employeeInfo roleList 解析 bid-administration
5. `resolveRoleCodeFromEmployeeInfo_returnsNullForNonBidRoles` — 只有 OSS 系统角色码时返回 null
6. `resolveRoleCodeFromEmployeeInfo_skipsInactiveRoles` — 跳过 status=0 和 del=true
7. `resolveRoleCodeFromEmployeeInfo_returnsNullForNullInput` — null 输入返回 null
8. `fullScenario_04569_jobListFailsEmployeeInfoFallbackSucceeds` — **04569 完整场景**：jobList 失败 → employeeInfo fallback 成功

### 核心教训

1. **OSS 返回字段中 roleCode 比 roleName 更可靠** — roleCode 是机器可识别的内部角色码（bid-* 前缀），roleName 是中文展示名（会随业务变化）。优先解析 roleCode 可避免中文映射表不全的陷阱。
2. **过时注释会误导后续开发** — `JobRoleLookupResolver` 注释错误声称「sysRoleList 只包含 roleName」，导致后续开发不会想到检查 roleCode。**注释必须与字段定义同步**。
3. **SSO + 密码两条路径可能共享同一根因** — 修 PR !1972（密码路径）时没注意到 SSO 路径的同一根因（OssRoleResolver 忽略 roleCode）。**修 bug 时必须审视同一业务动作的所有路径**（参见 §29）。
4. **生产 Release 落后于 main 是常态** — Release `e88dbd207`（2026-07-09）落后 main `a73181b0c`（2026-07-10）的 PR !1972 修复。**生产 Bug 排查时必须确认当前 Release ID 与 main 的差异**，不能假设最新修复已生效。
5. **根因行为测试必须独立于被改动的函数** — `OssRoleResolverTest` 不 mock `OssRoleResolver` 本身，只 mock 依赖，确保 Bug 行为被根治而非掩盖。

### 改进措施

- 修复后所有 `bid-*` 前缀的 OSS roleCode 都能直接映射为内部角色码，不再依赖中文 roleName 映射表
- `resolveRoleCodeFromEmployeeInfo` 提供双保险：jobList 解析失败时从 getUserInfo roleList 解析
- `JobRoleLookupResolver` 注释已修正，避免误导后续开发
- 根因行为测试覆盖 04569 完整场景，防止回归

### 相关文件

- `backend/src/main/java/com/xiyu/bid/crm/application/OssRoleResolver.java` — 主修复
- `backend/src/main/java/com/xiyu/bid/crm/application/OssLoginFlowService.java` — fallback 集成
- `backend/src/main/java/com/xiyu/bid/integration/organization/domain/policy/JobRoleLookupResolver.java` — 注释修正
- `backend/src/test/java/com/xiyu/bid/crm/application/OssRoleResolverTest.java` — 8 个根因行为测试
- PR !1977 — 本修复 PR
- PR !1972 — 前置修复（OSS 密码登录失败时误抛 RoleNotAuthorizedException，与本 Bug 独立）
---

## 53. OSS 与本地用户共用权限代码路径是 10+ 轮反复踩坑的根因（2026-07-10 根因猎手分析）

### 问题背景

系统存在两套人员权限体系：
1. **登录鉴权体系**：登录时调用 OSS 鉴权，获取用户的密码、角色、菜单树，实时加载到 `OssPermissionCache`（Redis + 内存双写，TTL 25h）。
2. **选人业务体系**：从组织架构事件库同步人员信息到 DB `role_profile` 表，用于选人接口返回候选人及其角色。

设计上两套体系各自独立（见 `DbRoleSnapshotResolver.java` 类注释），但代码实现层共用 `UserDetailsServiceImpl` / `DataScopeConfigService` / `User.getRoleCode()`，导致 OSS 用户走到为本地用户写的代码路径时反复踩坑。跨 CO-361 → CO-373 → spec 032 → CO-551 → bid-Team 菜单泄漏 → 标讯 403 等 10+ 轮修复未根治。

### 历史踩坑时间线

| 时间 | Issue/Spec | 现象 | 修复 | 是否根治 |
|---|---|---|---|---|
| 06-27 | CO-361 | 项目负责人 403 / 投标负责人只看自己 / 执行人看不到自己 | #1245 改 `DataScopeConfigService.getRoleCode` | 局部 |
| 06-28 | CO-373 | 27 处直调 `User.getRoleCode()` 引爆同类问题 | #1259 引入 `EffectiveRoleResolver` + `@Deprecated` + pre-push 拦截 | 系统性但未根治 |
| 07-04 | bid-Team 菜单泄漏 | bid-Team 看到 ai-center/operation-logs | #1661 删除 `RoleProfileCatalog` 中 bid-Team 的菜单权限 | 局部 |
| 07-08 | spec 032 / CO-551 | OSS 用户 03063/06234 看到所有菜单 | 4 个扩散点加 `isOssUser` 守卫 + 前端 `hasPermission` 守卫 | 三层防御但根因仍在 |
| 07-09 | 标讯 403 | OSS 用户 audit-logs 接口 403 | #1921 回退到 `hasAnyRole` | 单点修补 |
| 07-09 | CO-551 矛盾 | spec 说"OSS 不应持有 system.admin"，代码却允许 | #1916 改 spec 与代码对齐 | 文档对齐，未根治代码 |

**5 个 PR、跨度 13 天、每次"修一次好一阵子"**——典型"补交叉感染点不治根因"模式。

### 零号病人

零号病人不是某一行代码，是一个**架构决策**：

> 决定让 OSS 同步用户与本地用户共用同一套 `UserDetailsService` / `DataScopeConfigService` / `User` 实体代码路径，靠"分支判断 + 字段标识 + 后续修补"来区分两种身份。

### 必然性证明

```
A. OSS 用户走 loginOssUser() → 写入 OssPermissionCache（隔离的、干净的）
   ↓
B. 但 buildAuthResponse() / Service 层权限校验 → 走为本地用户写的 DataScopeConfigService.getRoleCode()
   ↓
C. DataScopeConfigService 内部遇到 "OSS 用户 roleCode=admin" 分支 → 触发 admin 扩散逻辑（本应只对本地 admin 生效）
   ↓
D. 扩散出 "all" + system.admin + warehouse.manage → OSS 用户看到所有菜单（spec 032 现象）

并行链路 1：
E. 选人接口直调 user.getRoleCode() → OSS 用户 role_id=NULL → fallback "manager"
   ↓
F. "manager" 被当成管理员 → 越权 OR 被错误过滤 → CO-361/CO-373 五轮反复

并行链路 2：
G. 前端 hasPermission 对含 "all" 的权限短路放行（本为本地 admin 设计）
   ↓
H. OSS 用户被扩散出 "all" → 前端短路 → spec 032 第三层扩散

并行链路 3：
I. RoleProfileCatalog.bid-Team 的 menuPermissions 包含 ai-center/operation-logs（本地内存目录）
   ↓
J. UserDetailsServiceImpl 合并 catalog seed → OSS bid-Team 用户拿到本地菜单权限
   ↓
K. 但 OSS 端未配置这些菜单 → 前端有权限、后端无对应 OSS 菜单 → 菜单泄漏
```

**数学上的必然**：每当新增一个"按角色判断"的业务分支，都会同时影响 OSS 用户和本地用户，但二者数据源不同，必然产生新的不一致场景。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 声明分离 vs 代码分离 | `DbRoleSnapshotResolver` 类注释说"统一读 DB role_profile 快照"，但 `UserSearchService` / `AssignmentCandidatePolicy` 直调 `user.getRoleCode()` 绕过它 | 设计声明必须有 ArchUnit 硬约束兜底，不能只靠注释 |
| 共用代码路径靠分支判断区分身份 | OSS 用户走本地用户的 admin 扩散逻辑 → 越权 | 两套身份体系必须物理隔离代码路径，不能靠 `if (isOssUser)` 守卫 |
| fallback "manager" 是症状放大器 | OSS 用户 role_id=NULL 时 fallback 返回 "manager" → CO-361 五次反复 | `User.getRoleCode()` 的 fallback 应抛异常（fail-closed），禁止返回任意值 |
| 补交叉感染点不治根因 | 10+ 轮修复每次"修一次好一阵子，下一个场景又炸" | 根因是架构决策，不是某一行代码；必须走 Spec Kit 流程门禁做根治方案 |

### 操作规范（建议固化到 CLAUDE.md / RULES.md）

1. **新增"按角色判断"业务分支时的检查清单**：
   - [ ] 该分支是否会同时影响 OSS 用户和本地用户？
   - [ ] OSS 用户走到该分支时数据源是什么（OSS cache / DB role_profile）？
   - [ ] 是否需要前置 `isOssUser` 守卫？
   - [ ] 是否需要新增"权限不扩散"测试用例？

2. **Spec Kit 门禁**：新增角色或权限相关改动必须走 `specs/` 下的 Spec Kit 流程，禁止单点 PR 修补。

3. **ArchUnit 硬约束**：OSS 用户相关类不得依赖 `RoleProfileCatalog` / `DataScopeConfigService` 的本地分支（方案 A），或 `UserDetailsServiceImpl` 中 `roleCode.equals("admin")` 分支必须前置 `!isOssUser` 守卫（方案 B）。

4. **`User.getRoleCode()` fallback 禁止返回任意值**：方案 A 提出删除 `"manager"` fallback 改抛 `IllegalStateException`（fail-closed），强制调用方走 `EffectiveRoleResolver` 或 `DbRoleSnapshotResolver`。

### 修复方向（三选一，详见 specs/033）

- **方案 A**：真正的代码路径分离（推荐根治）— OSS 用户走独立的 `OssUserDetailsService` + ArchUnit 强制隔离
- **方案 B**：强约束门禁（最小代价）— 扩展 `check-rolecode-direct-calls.mjs` + ArchUnit 守卫 admin 分支
- **方案 C**：消除 "all" 短路 + admin 扩散（中间态）— 删除扩散逻辑，本地 admin 走显式 seed

### 相关文件

- `docs/lessons/root-cause-analysis-oss-local-permission-dual-track.md` — 完整根因分析
- `specs/033-oss-local-permission-path-separation/spec.md` — 根治 Spec Kit
- `specs/032-fix-oss-permission-diffusion/` — 第一层止血 Spec Kit
- `.wiki/pages/lessons-learned/CO-361-five-rounds-no-fix.md` — CO-361 五次反复修复的完整教训
- `.wiki/pages/architecture/effective-role-resolution.md` — 角色码解析的工程规范

---

## 54. 父权限缺失导致 403：模块级 @PreAuthorize 与 OSS 叶子菜单的语义鸿沟（2026-07-10 / 账户管理 & CA 信息管理 / PR !1989）

### 问题背景

`PlatformAccountController` 和 `CaCertificateController` 使用类级 `@PreAuthorize("hasAuthority('resource')")` 作为模块入口兜底。业务上要求 `bid-projectLeader`（投标项目负责人/销售）也能看到账户管理和 CA 信息管理页面。

- `RoleProfileCatalog` 中本地 `bid-projectLeader` 的 `menuPermissions` 包含 `resource`、`resource-account`、`resource-ca`。
- 但 **OSS 端对该角色只下发子菜单** `100504` / `100505`，映射为 `resource-account` / `resource-ca`，**没有下发父菜单 `1005 → resource`**。

结果：用户 5052 登录后 authorities 里只有 `resource-account` / `resource-ca`，请求 `/api/platform/accounts` 和 `/api/ca-certificates` 时在 Controller 层就被 403 拦截。

### 修复历程

| 轮次 | 改动 | 结果 |
|---|---|---|
| 第 1 轮 | 在 `PlatformAccountViewerPolicy` / `CaCertificate` Service 层放开 `bid-projectLeader` 可见性 | 仍 403，因为没到 Service 就被 `@PreAuthorize` 拦截 |
| 第 2 轮 | 在 `UserDetailsServiceImpl` 兜底：持有任意 `resource-*` 子权限时自动补 `resource` 父权限 | ✅ 修复 |

关键失误：第 2 轮代码修对后，**部署的 jar 里并不包含该修复**。线上 `deployed-release.json` 显示运行的是 `460ccb5d7`（构建于 14:12 CST），而修复提交 `c3fce0f88` 在 16:34 CST 才推送到远端，导致“代码改了但线上还是 403”。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 只修 Service 层数据权限，没修 Controller 层入口权限 | 模块级 `@PreAuthorize` 是入口门禁，必须在放行业务前先放行入口 | 权限调整要同时检查三层：菜单导航（前端）、Controller 入口（@PreAuthorize）、Service/Policy 数据权限 |
| OSS 只发子菜单，后端要求父权限 | 模块级父权限与 OSS 叶子菜单之间存在语义鸿沟，不能假设父权限一定存在 | 当 Controller 使用父权限 `X` 且存在子权限 `X-Y` 时，必须在 `UserDetailsServiceImpl` 中兜底推导 |
| 修复后未验证线上 jar 是否包含修复 | 代码合入 ≠ 线上生效 | 部署后必须检查 `deployed-release.json` / jar 内容 / 真实用户 authorities 日志 |
| 同一页面反复 403 | 第 2 次修同一个 bug 时必须停下来做根因分析，不能继续打补丁 | 反复修复时优先按 engineering-discipline 第四章 SOP 执行 |

### 操作规范（建议固化到 CLAUDE.md / RULES.md）

1. **模块级 `@PreAuthorize` 使用父权限时，必须检查 OSS 映射是否下发父菜单**：
   - 若 OSS 只下发子菜单，必须在 `UserDetailsServiceImpl` 中显式兜底。
   - 兜底模式：`if (authorities.stream().anyMatch(p -> p != null && p.startsWith("X-"))) authorities.add("X");`

2. **权限改动三层验收**：
   - 前端：sidebar / 按钮是否显示（`hasPermission`）
   - 后端入口：`@PreAuthorize` 是否放行
   - 后端数据：Service / Policy 是否返回正确数据范围

3. **部署后必须验证真实用户 authorities**：
   - 检查日志 `UserDetails authorities built: user=xxx authorities=[...]` 是否包含目标权限。
   - 用受影响角色账号实测 API，确认返回 200 且数据范围正确。

4. **第 2 次修同一个 bug 时强制根因分析**：
   - 读取 `docs/lessons/lessons-learned.md` 同类问题。
   - 用“5 个为什么”追问，至少定位到配置/代码/部署三个层面中的一个。

### 验证命令

```bash
# 1. 检查 @PreAuthorize 使用的父权限是否有子权限兜底
node scripts/check-parent-permission-fallback.mjs

# 2. 检查线上 jar 是否包含修复
ssh jetty@172.16.38.78 '
  unzip -p /opt/xiyu-bid/shared/backend/app.jar \
    BOOT-INF/classes/com/xiyu/bid/auth/UserDetailsServiceImpl.class \
    | strings | grep -c "resource-"
'

# 3. 检查真实用户 authorities 是否包含 resource
rg 'UserDetails authorities built.*user=5052' /var/log/xiyu-bid/application.json.log

# 4. 验证受影响角色可访问
GET /api/platform/accounts
GET /api/ca-certificates?size=500
```

### 防复发措施

- **pre-push 拦截脚本**：`scripts/check-parent-permission-fallback.mjs` 已接入 `scripts/pre-push-gate.sh` 9.8 节。
  - 扫描 `@PreAuthorize(hasAuthority('X'))`。
  - 若 `RoleProfileCatalog` 中存在 `X-Y` 子权限，则要求 `UserDetailsServiceImpl` 必须有 `X` 的兜底推导。
  - 缺少兜底时阻断 push。

### 相关文档

- `docs/lessons/root-cause-analysis-resource-parent-permission-403.md` — 完整根因分析
- `backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java`
- `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`
- `backend/src/test/java/com/xiyu/bid/auth/UserDetailsServiceImplTest.java`
- `scripts/check-parent-permission-fallback.mjs`

---

## 55. Webhook 入队必须解析可用 OSS username，空 username 禁止静默死信（CO-576 / 2026-07-10/12）

> **注意**：本条目原始编号 CO-571 为错误关联（CO-571 是「评标中阶段选项顺序调整」纯前端 UI，由 PR !2045 处理）。
> 2026-07-12 合并前已修正为 CO-576（webhook 空 operator 死信修复）。

### 问题背景

2026-07-09/10 的连续 CRM 鉴权收紧提交（`5ed8d7dba` 删除全局 03595 happy path、`8b356d766` 删除虚构系统账号、`af8f3a32a` 合并 user/system 入口）把 CRM webhook 的 token 获取路径改成"必须由真人操作者触发"。

`WebhookCrmTokenResolver.resolveToken(username)` 在 `operatorUsername` 为空时直接抛 `TokenUnavailableException`。

但原始入队逻辑只用 `event.operatorId` 反查 username：API Key 场景下 event operator 常是 admin（有 username 无 OSS token），导致 `webhook_delivery_tasks.operator_username` 虽非空但换不到 OSS token；更糟的是 `operatorId` 为 null 时直接写入 `operator_username = null`，异步投递时 `resolveToken(null)` 抛异常 → 重试 3 次 → **DEAD_LETTER**，CRM 永远收不到回调。

### 触发链路（修复前）

1. 用户/AI 评分分析将标讯置为 `EVALUATED`，或用户弃标 `ABANDONED`（webhook 触发态）
2. `WebhookEventListener` 用 `OperatorUsernameResolver.resolve(event.operatorId())` 反查 username
3. API Key 场景 operator 是 admin（无 OSS）或 operatorId 为 null → `operator_username` 为空/null
4. `WebhookDeliveryJobService` 调度时，`WebhookCrmTokenResolver.resolveToken(null/空)` 抛 `TokenUnavailableException`
5. 重试 3 次失败 → 任务进入 **DEAD_LETTER**
6. **CRM 永远收不到 bidInfoSync 回调**

### 修复要点（Phase B，2026-07-12）

**策略**：入队用「能换 OSS token 的人」（对齐 #1641），解析顺序为 `tender.creatorId → tender.projectManagerId → event.operatorId`。说明：API Key 场景 event 常是 admin（有 username 无 OSS），故 creator/PM 优先于 event。operatorName 仅展示，不参与 token。

- [OperatorUsernameResolver.resolveDeliveryUsername](file:///Users/user/xiyu/worktrees/gemini/backend/src/main/java/com/xiyu/bid/webhook/application/OperatorUsernameResolver.java) 新增方法：按 `creatorId → projectManagerId → eventOperatorId` 顺序解析可用 OSS username，内部仍复用 `resolve(id)`
- [WebhookEventListener](file:///Users/user/xiyu/worktrees/gemini/backend/src/main/java/com/xiyu/bid/webhook/application/WebhookEventListener.java) 改用 `resolveDeliveryUsername`；username 空 → 不 save PENDING，写 error 日志（tenderId/status/三 ID）
- [ProjectResultConfirmedWebhookListener](file:///Users/user/xiyu/worktrees/gemini/backend/src/main/java/com/xiyu/bid/webhook/application/ProjectResultConfirmedWebhookListener.java) 对称改造
- Phase A 止血（PR !2031，已合）：`TenderCommandService.updateStatus` 传 operatorId、`ScoreAnalysisService` 注入 `CurrentUserResolver`、9 个 `TenderStatusChangedEvent.of` 调用点全用完整 factory

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 收紧鉴权前未盘点"无用户上下文"的所有事件源 | 设计意图与代码现状不一致 | 删除兜底路径前必须 grep 全部 `TenderStatusChangedEvent.of(` / `publishEvent` 调用点 |
| 提交 43dc6d2b0 明确警告"未来删除 03595 路径前需引入系统账号方案"，但后续提交未遵守 | 历史 commit 中的 TODO 警告是上游保护信号 | 删除/收紧前置依赖时，必须在同次或紧随 PR 中兑现前置条件 |
| 入队只看 event.operatorId，API Key 场景 operator 是 admin 无 OSS token | event operator 不等于"能换 token 的人" | 入队阶段应按业务字段（creator/PM）优先解析，event 作为末位 fallback |
| 空 username 仍入队 PENDING → 异步投递必然失败 → 死信 | 静默死信比不入队更糟 | 入队前校验 username 非空，空则不入队 + error 日志（含 tenderId/status/三 ID） |

### 验证命令

```bash
# Phase B 验收
mvn test -Dtest=OperatorUsernameResolverTest,WebhookEventListenerTest,ProjectResultConfirmedWebhookListenerTest,ArchitectureTest -DfailIfNoTests=false

# 检查所有 TenderStatusChangedEvent 发布点是否传 operatorId + operatorName
git grep -B1 -A4 "TenderStatusChangedEvent.of(" origin/main \
  -- "backend/src/main/java/**/*.java" | grep -B1 "operatorId"

# 查询历史 pending 任务
SELECT id, tender_id, operator_username, status, last_error
FROM webhook_delivery_tasks
WHERE operator_username IS NULL AND created_at > NOW() - INTERVAL 7 DAY
ORDER BY created_at DESC LIMIT 20;
```

### 防复发措施

- **入队阶段**：`OperatorUsernameResolver.resolveDeliveryUsername(tender, eventOperatorId)` 按 creatorId → PM → event 顺序解析；空 username 不入队 + error 日志（含 tenderId/status/三 ID）
- **Phase C（已完成，2026-07-12）**：删除 `TenderStatusChangedEvent` 5 参/6 参 factory + 删除 `TenderCommandService.updateStatus(Long, Status)` 两参重载，让编译期阻止回归；`ScoreAnalysisService` 无当前用户时用 `tender.creatorId` 作为 operatorId 兜底（通过 `TenderCommandService.resolveCreatorId` 应用层方法），仅当 currentUser + creatorId 双空时才跳过状态变更并 warn
- 建议补 backfill 迁移：将历史 `operator_username IS NULL` 的 pending 任务标记为 DEAD_LETTER 或补 system 账号

### 相关文档

- `backend/src/main/java/com/xiyu/bid/webhook/application/OperatorUsernameResolver.java`
- `backend/src/main/java/com/xiyu/bid/webhook/application/WebhookEventListener.java`
- `backend/src/main/java/com/xiyu/bid/webhook/application/ProjectResultConfirmedWebhookListener.java`
- `backend/src/main/java/com/xiyu/bid/webhook/application/WebhookDeliveryJobService.java`
- `backend/src/main/java/com/xiyu/bid/crm/application/WebhookCrmTokenResolver.java`
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandService.java`
- `backend/src/main/java/com/xiyu/bid/scoreanalysis/service/ScoreAnalysisService.java`
- `backend/src/main/java/com/xiyu/bid/batch/service/BatchTenderStatusAppService.java`
- `backend/src/main/java/com/xiyu/bid/webhook/domain/TenderStatusChangedEvent.java`

### 案例 56：项目结果确认回调未送达 CRM（2026-07-12）

**现象**：生产环境项目 ID=16（tenderId=43）中标结果 WON 登记后，CRM 商机 `CC2026071255` 状态仍是跟进中。webhook 投递 3 次重试全失败，进入死信队列。

**Layer 2 证据链**：
- 入队日志：`operatorUsername=admin`（admin 无 OSS token）
- 投递日志：`Cannot get CRM token: user OSS token missing, username=admin`
- 数据库：`webhook_delivery_tasks.status=DEAD_LETTER, attempt_count=3`，`webhook_delivery_dlq.reason_code=TRANSIENT_DEPENDENCY_EXHAUSTED`
- tender 43：`creator_id=1`（admin），`project_manager_id=110`（OSS 用户王占俊），`source_type=CRM_OPPORTUNITY`

**Layer 3 git 追溯**：
- commit `1f99ed2a0` "fix: 优先用项目负责人 username 获取 CRM token（admin 无 OSS token）" 在 `OperatorUsernameResolver` 新增了 `resolveForCrmLookup` 方法（PM 优先）
- 但只改了 `TenderIntegrationCommandService` 的 3 处调用（CRM 推送创建关联），**漏改了**两个 webhook listener
- 直接触发点：`ProjectResultConfirmedWebhookListener.java:93` 仍用 `resolveDeliveryUsername`（creator 优先）

**5 Whys**：
1. CRM 没收到回调 → webhook 投递 3 次失败进死信
2. 投递失败 → `CrmAuthService.getValidTokenForUser(admin)` 抛 `TokenUnavailableException`
3. 用 admin 投递 → 入队时 `operatorUsername=admin`
4. 入队选 admin → `resolveDeliveryUsername` 优先取 creatorId，tender 43 的 `creator_id=1`
5. creator 是 admin → tender 43 由 CRM 推送经 API Key 路径创建，创建者默认是 admin

**根因分类**：§1 追症状不追根因（commit `1f99ed2a0` 只修了主路径，未做全仓库调用点排查）+ §7 未在真实环境验证（修复未覆盖 E2E 链路验证）。

**修复**：两个 webhook listener 改用 `resolveForCrmLookup`（PR !2047 / CO-571 Phase B 补齐）。

**防复发**：
- `OperatorUsernameResolver` javadoc 顶部追加"使用指引"小节，明确两个方法的语义差异和适用场景
- 凡是 username 进入 `CrmAuthService.getValidTokenForUser(username)` 链路的调用点，必须用 `resolveForCrmLookup`
- 后续任何"新增 CRM token 换取调用点"的 PR，必须 grep `resolveDeliveryUsername` 确认是否需要改为 `resolveForCrmLookup`

---

## 56. OBS 直传三层保护：构建变量缺失导致 isObsEnabled=false（2026-07-12/13 / PR !2059）

> 来源：2026-07-12 测试环境第 82 次部署 + 2026-07-13 测试环境第 84 次部署事故
> PR !2059 修复并引入三层防护机制

### 问题背景

前端 OBS 直传功能依赖构建时环境变量 `VITE_OBS_ENABLED=true`。但部署脚本未固化该变量，导致：

1. **首次遗漏**：测试环境第 82 次部署时前端构建未传入 `VITE_OBS_ENABLED=true`，产物中 `isObsEnabled=false`（`dist/assets/Detail-*.js` 中值为 `!1`）
2. **覆盖事故**：测试环境用正确版本（Release `66c245218`）部署后，后续一次不完整部署（Release `f0366414b`）覆盖了正确前端产物，`isObsEnabled` 回退为 `false`
3. **生产同步问题**：生产环境因手动命令绕过脚本同样存在该问题

### 根因：构建变量未固化 + 部署覆盖无校验

| 断点 | 表现 | 根因 |
|---|---|---|
| 构建脚本未固化变量 | `npm run build` 不传 `VITE_OBS_ENABLED` | 脚本未将业务关键构建变量硬编码 |
| 产物无验证 | `isObsEnabled=false` 的产物被部署 | 缺少构建后校验步骤 |
| 部署覆盖无感知 | 后续部署覆盖正确产物 | 无部署后 OBS 状态检查 |

### 修复：三层防护机制（PR !2059）

1. **脚本固化层**：部署脚本中硬编码 `VITE_OBS_ENABLED=true`
   - 推荐命令：`VITE_OBS_ENABLED=true bash scripts/release/package-release.sh`
   - 手动构建：`VITE_OBS_ENABLED=true npm run build`

2. **构建期校验层**：构建后校验 `dist/assets/Detail-*.js` 中 `isObsEnabled` 值
   - 正确值：`!0` 或 `true`
   - 错误值：`!1` 或 `false`
   - 校验 `.upload()` 调用数：启用时 2 处，禁用时 0 处

3. **部署后验证层**：部署后检查前端产物
   - 验证 `/srv/www/xiyu-bid/assets/Detail-*.js` 含 `.upload()` 调用
   - 验证 `obs-direct:` 前缀存在
   - 验证 `obsEnabled=true`

### 最大教训：业务关键构建变量不能依赖人工记忆

| 验证手段 | 结论 | 是否可靠 |
|---|---|---|
| 前端构建无报错 | "成功" | ❌ 不可靠，变量未传也不报错 |
| 后端健康检查 UP | "正常" | ❌ 不可靠，与前端 OBS 无关 |
| **产物中 isObsEnabled 值** | **false → 未启用；true → 已启用** | **✅ 唯一可靠** |
| **.upload() 调用数** | **0 → 禁用；2 → 启用** | **✅ 端到端可靠** |

**铁律**：业务关键构建变量（如 `VITE_OBS_ENABLED`）必须固化在部署脚本中，不能依赖人工传入。构建产物必须有机验步骤，部署后必须有功能验证。后续不完整部署可能覆盖正确产物，部署后验证是最后一道防线。

### 通用规则

1. **VITE_ 前缀变量在构建时注入**：Vite 构建变量是编译期常量，运行时无法修改。漏传 = 产物中值为 `false`/`undefined`
2. **部署脚本必须固化业务关键变量**：不能依赖运维人员记忆或手动传入
3. **构建产物校验**：对关键功能（OBS 直传、AI 分析等）的产物做 grep 校验，确认功能开关已启用
4. **部署后功能验证**：部署后验证前端产物的功能开关状态，防止后续部署覆盖

### 相关文档

- `scripts/release/package-release.sh` — 构建脚本（已固化 `VITE_OBS_ENABLED=true`）
- `scripts/release/deploy-prod.sh` — 生产部署脚本（含 OBS 校验）
- `docs/release/deploy-report-2026-07-12-82nd-test.md` — 第 82 次测试部署报告（首次修复）
- `docs/release/deploy-report-2026-07-13-85th-test.md` — 第 85 次测试部署报告（三层防护验证）

---

## 57. 前端 N+1 GET 请求触发 429 限流：列表页并发详情加载（2026-07-10 / Account.vue + bar.js / PR !1997 + !2000）

> 来源：2026-07-10 生产环境 Account-jlC7xUrD.js 大量 429 错误
> PR !1997 修复 Account.vue，PR !2000 修复 bar.js

### 问题背景

前端列表页加载时对每行数据并发调用 GET /api/platform/accounts/{id} 获取详情，形成 N+1 请求模式。当列表行数较多时（如 20+ 行），瞬时并发请求超过后端 100 次/60 秒的限流阈值，导致大量 429 错误。

两处独立但同类型的 bug：

| 页面 | 文件 | 请求模式 | 限流接口 |
|---|---|---|---|
| 平台账号管理 | `src/views/Resource/Account.vue` | 列表每行并发 GET /api/platform/accounts/{id} | 100 次/60 秒 |
| BAR 站点台账 | `src/stores/bar.js` | getSites + getSiteById 并发 N+1 | 同限流阈值 |

### 根因：列表 N+1 GET 请求无限流控制

```javascript
// 错误模式：Promise.all + map 并发请求所有详情
const details = await Promise.all(
  list.map(item => api.getDetail(item.id))  // N 行 = N 个并发请求
)
```

当 N > 限流阈值时，瞬时并发触发 429。后端限流配置为 100 次/60 秒，前端 20 行列表 × 多个接口 = 轻松超限。

### 修复：分批并发加载

```javascript
// 正确模式：分批并发，控制并发数
const DETAIL_CONCURRENCY = 5;  // Account.vue
const SITE_DETAIL_BATCH_SIZE = 2;  // bar.js
const BORROW_RECORD_BATCH_SIZE = 5;  // bar.js

async function loadDetailsInBatches(items, concurrency) {
  const results = [];
  for (let i = 0; i < items.length; i += concurrency) {
    const batch = items.slice(i, i + concurrency);
    const batchResults = await Promise.all(
      batch.map(item => api.getDetail(item.id))
    );
    results.push(...batchResults);
  }
  return results;
}
```

### 最大教训：列表页 N+1 GET 请求必须控制并发

| 验证手段 | 结论 | 是否可靠 |
|---|---|---|
| 列表正常加载 | "成功" | ❌ 不可靠，少量数据时不触发限流 |
| 单个详情请求 | "正常" | ❌ 不可靠，单请求不触发限流 |
| **大量数据列表加载** | **429 错误 → 超限流阈值** | **✅ 唯一可靠** |

**铁律**：前端列表页禁止用 `Promise.all + map` 对每行并发 GET 请求详情。必须使用分批并发加载（`DETAIL_CONCURRENCY=5` 或更低），控制瞬时并发数在限流阈值内。

### 通用规则

1. **后端 GET 接口有限流阈值**：当前系统 GET 接口限流为 100 次/60 秒，前端并发请求必须控制在此阈值内
2. **列表 N+1 是高风险模式**：列表页每行调 GET 详情接口时，必须用分批并发，不能用 `Promise.all + map`
3. **分批并发数建议**：`DETAIL_CONCURRENCY=5`（保守）或 `BATCH_SIZE=2-5`（根据接口耗时调整）
4. **系统性排查**：修复一处后必须扫描 `src/` 下所有 `Promise.all` + `map` 组合的 N+1 请求模式，防止其他页面存在同类 bug
5. **POST 请求不受此限流影响**：POST 请求通常不触发 GET 限流，但仍建议控制并发

### 排查方法

```bash
# 扫描前端所有 Promise.all + map 组合的 N+1 请求模式
grep -rn "Promise\.all.*\.map" src/ --include="*.vue" --include="*.js"
# 重点关注 .map 内含 api.get / api.fetch / getDetail 等 GET 请求的调用
```

### 相关文档

- `src/views/Resource/Account.vue` — `loadDetailsInBatches` 函数（DETAIL_CONCURRENCY=5）
- `src/stores/bar.js` — `runInBatches` 函数（SITE_DETAIL_BATCH_SIZE=2, BORROW_RECORD_BATCH_SIZE=5）
- `src/stores/bar.spec.js` — 分批并发单元测试
- PR !1997: Account.vue N+1 修复
- PR !2000: bar.js N+1 修复

---

## 58. 业绩模块部署陷阱：tar 解包路径 + MySQL 字符集 + 数据库备份（2026-07-11 / 测试 + 生产环境）

> 来源：2026-07-11 业绩模块初始化数据导入测试和生产环境
> 涉及环境：测试 172.16.38.78 + 生产 172.16.10.149

### 问题背景

业绩模块初始化数据导入脚本存在三个独立陷阱，导致首次部署时附件 404 + 中文乱码：

| 陷阱 | 表现 | 根因 |
|---|---|---|
| tar 解包路径多一层 | 附件 404 错误 | tar 包解压后多出 `performance/` 目录，附件实际路径与 ATTACHMENT_ROOT 不匹配 |
| MySQL 字符集 latin1 | 中文乱码 | MySQL 客户端默认 latin1 字符集，导入中文数据时乱码 |
| 无数据备份 | 回滚困难 | 部署脚本未包含数据库备份步骤 |

### 根因分析

1. **tar 解包路径**：`tar -xf performance.tar -C $ATTACHMENT_ROOT` 解压后实际路径为 `$ATTACHMENT_ROOT/performance/xxx`，而系统期望路径为 `$ATTACHMENT_ROOT/xxx`。应解压到 ATTACHMENT_ROOT 的父目录或使用 `--strip-components=1`

2. **MySQL 字符集**：MySQL 客户端默认 `character_set=latin1`，导入 UTF-8 编码的 SQL 文件时中文全部乱码。必须显式指定 `--default-character-set=utf8mb4`

3. **数据备份缺失**：业绩模块导入涉及大量数据（766 条业绩记录 + 3870 个附件），无备份时回滚需手动清理

### 修复方案

```bash
# 1. tar 解包到 ATTACHMENT_ROOT 父目录（或用 --strip-components=1）
tar -xf performance.tar -C $(dirname $ATTACHMENT_ROOT)

# 2. MySQL 导入显式指定 utf8mb4 字符集
mysql --default-character-set=utf8mb4 -u $DB_USER -p$DB_PASSWORD $DB_NAME < performance.sql

# 3. 导入前自动备份
mysqldump --default-character-set=utf8mb4 -u $DB_USER -p$DB_PASSWORD $DB_NAME \
  > backup-before-performance-$(date +%Y%m%d%H%M%S).sql.gz

# 4. 生产环境防护：要求 YES 确认
read -p "确认导入生产环境业绩数据？(YES to continue): " confirm
if [ "$confirm" != "YES" ]; then
  echo "取消导入"
  exit 1
fi
```

### 最大教训：数据导入脚本必须处理路径、字符集、备份三件事

| 验证手段 | 结论 | 是否可靠 |
|---|---|---|
| 导入脚本无报错 | "成功" | ❌ 不可靠，路径错也不报错 |
| MySQL 导入完成 | "成功" | ❌ 不可靠，乱码也不报错 |
| **附件可访问** | **404 → 路径错；200 → 正确** | **✅ 唯一可靠** |
| **中文显示正常** | **乱码 → 字符集错；正常 → 正确** | **✅ 端到端可靠** |

**铁律**：数据导入脚本必须显式处理 tar 解包路径（`--strip-components` 或解到父目录）、MySQL 字符集（`--default-character-set=utf8mb4`）、数据备份（导入前自动 dump）三件事。生产环境导入必须有 YES 确认防护。

### 通用规则

1. **tar 解包路径**：解压前先检查 tar 包内部结构，确认是否有额外目录层级。使用 `tar -tf` 预览内容
2. **MySQL 字符集**：所有 MySQL 命令显式指定 `--default-character-set=utf8mb4`，不依赖客户端默认值
3. **数据备份**：导入前自动 `mysqldump` 备份，命名含时间戳
4. **生产环境防护**：数据导入脚本必须要求 `YES`（全大写）确认，防止误操作
5. **导入后验证**：验证附件可访问（HTTP 200）+ 中文显示正常 + 记录数符合预期

### 相关部署结果

- 测试环境（172.16.38.78）：首次导入中文乱码，用 utf8mb4 重新导入后修复
- 生产环境（172.16.10.149）：766 条业绩记录 + 3870 个附件，健康检查和附件访问均正常

---

## 59. 生产环境测试数据清理：99 表清理 + 知识库数据保留（2026-07-13 / 上线前数据准备）

> 来源：2026-07-13 生产环境正式上线前测试数据清理
> 涉及环境：生产 172.16.10.149（winbid.ehsy.com）

### 问题背景

生产环境正式上线前，需清理测试期间产生的测试数据（标讯、项目、通知、审计日志等），同时保留知识库正式数据（案例库、资质证书、人员证书、仓库信息、业绩管理、品牌授权）和系统配置。

### 清理范围

| 类别 | 表数量 | 说明 |
|---|---|---|
| 标讯相关 | 20 表 | 51 条 tenders + 评估表 + 附件等 |
| 项目相关 | 64 表 | 20 个 projects + 项目档案 + 文档等 |
| 通知相关 | 7 表 | 367 条业务通知 |
| 其他 | 7 表 | 419 条审计日志 + 文件队列等 |
| **合计删除** | **98 表** | — |
| 仅更新不删除 | 2 表 | bar_certificates、business_qualifications（清测试数据保留正式数据） |

### 保留范围

| 类别 | 说明 |
|---|---|
| 知识库数据 | 案例库、资质证书、人员证书、仓库信息、业绩管理、品牌授权 |
| 系统配置 | tender_source_configs（标讯源配置） |
| OSS 用户 | 全部保留，不删除 |
| 系统表 | Flyway 迁移记录、角色配置等 |

### 最大教训：生产数据清理必须区分测试数据与正式数据

| 验证手段 | 结论 | 是否可靠 |
|---|---|---|
| 表名包含 "test" | "测试数据" | ❌ 不可靠，测试数据可能在正式表中 |
| 数据创建时间 | "早期数据" | ❌ 不可靠，正式数据也可能早期创建 |
| **业务确认** | **用户明确指定哪些是测试数据** | **✅ 唯一可靠** |
| **保留清单核对** | **逐表核对保留清单** | **✅ 端到端可靠** |

**铁律**：生产环境数据清理必须由用户明确指定清理范围和保留范围，不能靠表名或时间猜测。清理前必须生成完整的删除表清单和保留表清单，供用户逐项核对。清理脚本必须有 dry-run 模式预览影响范围。

### 通用规则

1. **用户确认优先**：哪些数据是测试数据、哪些是正式数据，必须由用户明确指定
2. **清理清单文档化**：生成 `cleanup-table-list-YYYY-MM-DD.md` 文档，列出所有删除/更新/保留的表
3. **保留清单同样重要**：必须列出所有保留的表，方便逐项核对
4. **仅更新不删除的表**：部分表（如 bar_certificates）需清测试数据但保留正式数据，不能整表删除
5. **SQL 脚本由运维执行**：清理 SQL 脚本交给运维执行，开发人员不直接操作生产数据库
6. **清理后验证**：清理后验证知识库数据完整、系统功能正常

### 相关文档

- `docs/release/cleanup-table-list-2026-07-13.md` — 清理表清单（98 表删除 + 2 表更新 + 保留清单）
- 清理 SQL 脚本（由运维执行）

---

## 60. CRM 商机关联失败三层根因：sourceId 语义错误 + crm_sales_no 全表 NULL + generateToken 不校验 Authorization（spec 037 / 2026-07-16 / tender 56 案例）

### 现象

生产环境 tender 56（external_id=`CRM:7`）推送后未关联 CRM 商机，`crm_opportunity_id` 字段为 NULL。PM 王旭州（username=04503）从未登录过本系统。

### 三层根因

| 层 | 根因 | 影响 |
|---|---|---|
| **L1 代码语义错误** | `CrmTenderLinkService.linkByChanceIdIfPresent` 把 `sourceId`（标讯 ID / bidId）当作 `chanceId`（商机主键）调 detail 接口 | CRM 标讯 ID=7 对应商机 id=6, code=CC2026071568，但代码用 7 调 detail 接口查不到商机 |
| **L2 字段未填充** | `users.crm_sales_no` 全表 NULL（OSS 同步时未填充） | CrmAuthService 无法用 salesNo 换 CRM JWT，退化为抛 TokenUnavailableException |
| **L3 认证强依赖** | `generateToken` 实测不校验 Authorization header，但代码仍要求 OSS token | PM 从未登录 → 无 OSS token → 无法换 CRM JWT → 即使 L1/L2 修复也无法关联 |

### 修复方案（spec 037）

1. **L1 修复**：`CrmTenderLinkService` 新增 `linkByBidIdIfPresent`，用 `findProjectLeaderByBidId`（page-list 按 bidId 查）替代 `findProjectLeaderByChanceId`（detail 按 chanceId 查）。旧方法名 `linkByChanceIdIfPresent` 标 `@Deprecated` 委托新方法，保持 caller 兼容。
2. **L2 修复**：`OrganizationUserSyncWriter.upsert` 新增 `user.setCrmSalesNo(plan.username())`，OSS 同步时把工号写入 `crm_sales_no`。
3. **L3 修复**：`CrmAuthService.applyCrmToken` 改用 `httpClient.postJson`（无 Authorization），去掉 OSS token 依赖。`fetchAndCacheUserToken` 直接用 `nickName + salesNo` 换 CRM JWT。

### 关键教训

1. **external_id 语义必须明确**：`{sourceSystem}:{sourceId}` 中的 sourceId 是什么 ID（标讯 ID / 商机 ID / 其他），必须在代码注释和文档中明确。本次 bug 根因是代码假设 sourceId 是 chanceId，实际是 bidId。
2. **CRM 三种 token 体系必须分清**：
   - OSS access_token（用户登录 OSS 获得）
   - CRM JWT（用 OSS token + nickName + salesNo 换取，**实测 generateToken 不校验 Authorization**）
   - 本系统 JWT
3. **接口实测优于代码分析**：本次通过生产环境 curl 实测确认 generateToken 不校验 Authorization，这是代码分析无法发现的。但生产环境实测需谨慎，避免重复测试。
4. **字段填充责任必须在数据入口**：`crm_sales_no` 应在 OSS 同步时填充，而不是等用户登录时再填充（用户可能从不登录）。
5. **@Deprecated 委托模式保持兼容**：修改方法语义时，保留旧方法名委托新方法，避免破坏 caller。

### 回退路径

若客户方修复 generateToken 不校验 Authorization 的"漏洞"：
- `CrmAuthService.applyCrmToken` 方法体改回 `httpClient.postWithAuth(baseUrl, path, ossToken, body)` 即可恢复三步认证
- `OssUserTokenCache` 仍保留注入，回退无需改动构造函数

### 相关文档

- `specs/037-crm-link-compensation/spec.md` — 需求规格
- `specs/037-crm-link-compensation/plan.md` — 实现计划
- `specs/037-crm-link-compensation/research.md` — T004 实测结果 + 方案决策
- `specs/037-crm-link-compensation/tasks.md` — 24 个任务清单
- tender 56 治标修复：SQL UPDATE 设置 `crm_opportunity_id` = 'CC2026071568'（上一会话已完成）

---

## 61. 自定义表单 schema 迁移必须 merge 不能覆盖，否则用户自定义丢失（2026-07-16 / form_definition_registry）

> 来源：2026-07-16 用户反馈"系统升级后自定义表单会不会被还原成默认表单"
> 涉及模块：动态表单引擎（form_definition_registry 表 + WorkflowFormTemplateQueryService）
> 关联 PR：!2089（标讯录入自定义表单）、!2095（revert + 守护测试）

### 问题背景

用户在 `/settings/workflow-forms` 配置页自定义了表单字段（显隐/必填/排序/标签）。系统升级后用户自定义可能丢失，还原成默认表单。

### 根因：schema 迁移脚本采用"覆盖式"而非"合并式"

动态表单 schema 存储在 `form_definition_registry.schema_json` 字段。现有 4 个 Flyway 迁移脚本直接 UPDATE 全表，**无条件覆盖**用户自定义：

| 迁移脚本 | 作用 | 覆盖方式 | 是否检查用户自定义 |
|---|---|---|---|
| `V1007` | 全量替换 schema（version 1→2） | `UPDATE ... SET schema_json='...'` | ❌ 不检查 |
| `V1120` | 改 tenderInfo.maxLength（5000→20000） | `UPDATE ... SET schema_json='...'` | ❌ 不检查 |
| `V1166` | 全量替换 schema（version 2→3，字段 key 改名） | `UPDATE ... SET schema_json='...'` | ❌ 不检查 |
| `V1167` | 全量替换 schema（version 3→4，加 enabled/pastedText/attachments） | `UPDATE ... SET schema_json='...'` | ❌ 不检查 |

**关键问题**：所有迁移都是 `UPDATE form_definition_registry SET schema_json='...' WHERE scope='tender.entry' AND org_id IS NULL` —— 无条件覆盖，不检查用户是否自定义过。

### 影响范围

| 场景 | 是否覆盖 | 影响 |
|---|---|---|
| 后端 Java 代码升级（不改 schema） | ❌ 不覆盖 | 无影响 |
| 前端 Vue 代码升级（不改 schema） | ❌ 不覆盖 | 无影响 |
| 新增 Flyway 迁移改 schema | ✅ **覆盖** | 用户自定义的显隐/必填/排序丢失 |
| 部署时重建 DB（生产不会做） | ✅ **覆盖** | 全部丢 |

### 现有保护机制（部分有效）

1. **前端 DEFAULT_FIELDS fallback**（`TenderBasicInfoTab.vue:151`）— 只在 schema 加载失败时用，不解决覆盖问题
2. **version 字段** — 但现有迁移不检查 version，直接覆盖
3. **回滚脚本**（U1166/U1167）— 但回滚也是覆盖，且只回滚到上一个系统版本，不回滚到用户自定义

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| schema 迁移用 UPDATE 全表覆盖 | 用户自定义 schema 是业务数据，不是系统配置；覆盖式迁移会丢业务数据 | schema 迁移必须 merge，不能覆盖 |
| 迁移脚本不检查用户自定义状态 | 无法区分"系统基线"和"用户自定义" | 迁移前必须检查 `custom_version > system_version` 判断是否有用户自定义 |
| 缺少 schema 双版本机制 | 单一 version 字段无法区分系统基线版本和用户自定义版本 | 应引入 `system_version` + `custom_version` 双版本机制 |

### 操作规范

1. **schema 迁移脚本必须做 merge**：
   - 读取当前 schema_json
   - 检查是否已有用户自定义（version > 系统基线 version）
   - 如果有用户自定义，只追加系统新增字段，不动现有字段的 enabled/required/label
   - 如果没有用户自定义，直接写入系统基线

2. **双版本机制（建议长期改造）**：
   ```sql
   ALTER TABLE form_definition_registry
     ADD COLUMN system_version INT NOT NULL DEFAULT 1 COMMENT '系统基线版本',
     ADD COLUMN custom_version INT NOT NULL DEFAULT 0 COMMENT '用户自定义版本（0=未自定义）';
   ```

3. **迁移脚本规范**（加入 `create-migration` skill 门禁）：
   - 凡是 `UPDATE form_definition_registry SET schema_json` 的迁移，必须先读当前 version
   - 必须在 PR 描述中说明"是否会影响用户自定义 schema"
   - 必须提供回滚脚本（回滚到用户自定义，而非回滚到上一个系统基线）

4. **配置页保存时打标记**：
   - `WorkflowFormAdminService.saveDraft()` 时设置 `custom_version = custom_version + 1`
   - 迁移脚本检查 `custom_version > 0` 决定是否 merge

### 验证命令

```bash
# 检查是否有覆盖式 schema 迁移
grep -rn "UPDATE form_definition_registry" backend/src/main/resources/db/migration-mysql/
# 期望：所有迁移都应该检查 custom_version 或做 merge

# 检查 schema 加载逻辑是否从 DB 读取（而非硬编码）
grep -rn "templateStore.findActiveByCode\|getActiveSchema" backend/src/main/java/
# 期望：从 DB 读取，不经 Flyway 重新初始化
```

### 短期改进（本 PR 不做，登记技术债）

1. 在 `scripts/check-migration-safety.sh`（如不存在则新建）加检查：凡 `UPDATE form_definition_registry SET schema_json` 的迁移必须人工 review
2. 在 `create-migration` skill 的模板里加警告："自定义表单 schema 迁移必须 merge 不能覆盖"

### 长期改进（建议独立 spec）

引入 schema 双版本机制：
- `system_version`：系统基线版本（迁移时 +1）
- `custom_version`：用户自定义版本（用户改时 +1，0=未自定义）
- 升级时比较两个版本，做 merge 而非覆盖
- merge 策略：
  - 系统新增字段 → 追加
  - 系统删除字段 → 标记 deprecated，不直接删（保留用户自定义）
  - 系统修改字段类型 → 提示用户冲突，需手动确认
  - 用户自定义的 enabled/required/label/sort → 保留

### 相关文档

- `backend/src/main/resources/db/migration-mysql/V1007__update_tender_entry_schema_blueprint.sql` — 覆盖式迁移先例 1
- `backend/src/main/resources/db/migration-mysql/V1166__align_tender_entry_schema_with_fallback.sql` — 覆盖式迁移先例 2
- `backend/src/main/resources/db/migration-mysql/V1167__add_enabled_field_to_tender_entry_schema.sql` — 覆盖式迁移先例 3
- `backend/src/main/java/com/xiyu/bid/workflowform/application/service/WorkflowFormTemplateQueryService.java` — schema 加载入口（从 DB 读取）
- `src/views/Bidding/list/components/TenderBasicInfoTab.vue:151` — 前端 DEFAULT_FIELDS fallback
- PR !2089 — 标讯录入自定义表单改造
- PR !2095 — locked 语义 revert + 守护测试（同 session 关联）

---

## 62. 错误消息不应包含不存在的功能引导；前端透传后端 msg 时文案修改只需后端做（2026-07-12 / PR !2043）

> 来源：2026-07-12 用户反馈 CRM 商机占用提示中包含不存在的"解除原关联"功能引导
> 涉及模块：TenderCrmOccupancyChecker（CRM 商机占用校验）
> 关联 PR：!2043（commit `9f0ecf598` + merge `c3f2fe8ca`）

### 问题背景

用户在标讯详情页尝试关联一个已被他标占用的 CRM 商机时，系统返回 409 并显示：

> 该 CRM 商机已被标讯 ID=xxx 关联，**请先解除原关联**

但系统当前**并不存在**"解除关联"功能。该文案引导用户去执行一个无法完成的操作，造成误导和用户 frustration。

### 根因：错误消息编写时假设了未实现的功能

`TenderCrmOccupancyChecker.java:46` 的原始文案：

```java
throw new BusinessException(409,
        "该 CRM 商机已被标讯 ID=" + occupied.getId() + " 关联，请先解除原关联");
```

文案编写者**预设了未来会实现"解除关联"功能**，但该功能从未落地。错误消息成为了一张"空头支票"，长期误导用户。

### 修复内容（PR !2043）

1. **后端文案修改**（`TenderCrmOccupancyChecker.java:46`）：
   ```java
   // 修改前
   "该 CRM 商机已被标讯 ID=" + occupied.getId() + " 关联，请先解除原关联"
   // 修改后
   "该 CRM 商机已被标讯 ID=" + occupied.getId() + " 关联"
   ```
   仅陈述占用事实，不引导用户执行不存在的操作。

2. **测试文案同步更新**：3 处测试代码（`TenderIntegrationCommandServiceCrmDuplicateTest`、`TenderCommandServiceLinkCrmOpportunityDedupTest`）中的预期文案同步修改。`hasMessageContaining` 子串断言依然成立。

3. **前端无需改动**：前端 catch 块透传后端 `msg` 字段，文案修改在后端完成即可，前端自动展示新文案。

4. **保留并发冲突等真实引导**：并发冲突的"请刷新后重试"对应浏览器原生能力，属于真实引导，不应一刀切删除。

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 错误消息引导用户执行不存在的功能 | 错误消息是用户与系统交互的"最后一公里"，引导必须可执行 | 错误消息中提到的任何操作必须对应实际存在的功能；未实现的功能不得出现在文案中 |
| 文案编写者预设未来功能 | "先写文案，后补功能"是反向耦合，文案成了未实现功能的广告 | 文案只能描述当前能力；功能上线后再补对应引导文案，而非相反 |
| 前端透传后端 msg 时改文案需前后端同步 | 实际上只需后端改 | 前端透传后端 `msg` 时，文案修改只需后端做，前端无需改动（降低同步成本） |

### 操作规范

1. **错误消息审计**：编写错误消息时，必须验证其中提到的功能/操作是否实际存在。可执行命令：
   ```bash
   # 在后端抛 BusinessException 的地方搜索"请先"、"请前往"、"请使用"等引导词
   grep -rn "BusinessException.*请先\|BusinessException.*请前往\|BusinessException.*请使用" backend/src/main/java/
   # 逐条验证对应功能是否存在
   ```

2. **文案与功能上线顺序**：功能先上线，文案后补。禁止"文案先行，功能后补"——一旦功能延期或废弃，文案就成了虚假引导。

3. **前端透传后端 msg 的场景识别**：当前端 catch 块直接 `ElMessage.error(err.response.data.msg)` 透传后端消息时，文案修改只需后端做。识别命令：
   ```bash
   # 查找前端透传后端 msg 的 catch 块
   grep -rn "err\.response\.data\.msg\|error\.response\.data\.msg" src/
   ```

4. **保留并发冲突等真实引导**：并发冲突的"请刷新后重试"对应浏览器原生刷新能力，属于真实引导，不应一刀切删除。

### 防复发措施

- **错误消息编写规范**：在 `engineering-discipline.md` 中追加"错误消息引导必须可执行"原则（由 wiki:ingest 同步到 `.wiki/pages/engineering-discipline.md`）
- **代码审查检查项**：PR review 时检查新增/修改的 `BusinessException` 消息是否包含不可执行的引导
- **测试文案同步**：修改 `BusinessException` 消息时，必须同步修改测试中的预期文案，避免 `hasMessageContaining` 断言失效

### 相关文档

- [TenderCrmOccupancyChecker.java](../../backend/src/main/java/com/xiyu/bid/tender/service/TenderCrmOccupancyChecker.java) — CRM 商机占用校验（纯函数）
- [TenderIntegrationCommandServiceCrmDuplicateTest.java](../../backend/src/test/java/com/xiyu/bid/integration/external/TenderIntegrationCommandServiceCrmDuplicateTest.java) — 测试文案同步
- [TenderCommandServiceLinkCrmOpportunityDedupTest.java](../../backend/src/test/java/com/xiyu/bid/tender/service/TenderCommandServiceLinkCrmOpportunityDedupTest.java) — 测试文案同步
- [crm-integration-lessons.md §8](./crm-integration-lessons.md) — TenderCrmOccupancyChecker 已作为纯数字 id 防复发监控防御
- [deploy-report-2026-07-12-83rd-test.md](../release/deploy-report-2026-07-12-83rd-test.md) — PR !2043 部署记录
- PR !2043 — 本决策落地（commit `9f0ecf598` + merge `c3f2fe8ca`）

---

## 63. Bug 回归反思：违反 SOP 3 条纪律 + 新增 2 条 SOP（2026-07-18 / Files.readAllBytes OOM 5 轮修复）

> 来源：2026-07-18 用户反思仓库导出下载 OOM bug 5 轮修复（!2112~!2124）均为补丁式修复
> 涉及模块：仓库导出下载链路（生成阶段 + 下载阶段）
> 关联 PR：!2112 / !2113 / !2116 / !2120 / !2124 / !2126
> 关联根因分析：[root-cause-analysis-warehouse-export-download-oom.md](./root-cause-analysis-warehouse-export-download-oom.md)

### 问题背景

仓库导出下载 bug 经历 5 次 PR 修复（!2112 至 !2124），均为补丁式修复而非一次性根治，最终通过 `journalctl` 服务器日志定位到 `Files.readAllBytes()` 导致的 OOM。

### 违反 SOP 的 3 条关键纪律

| # | SOP 条款 | 违反点 | 应有动作 |
|---|---|---|---|
| 1 | 真实环境验证（查看服务器日志） | 5 轮 PR 都未查看 `journalctl` 服务器日志，仅靠本地推测 | 第 1 轮修复后必须 SSH 到服务器查日志验证 |
| 2 | 第 2 次修同一个 bug 必须停下来做根因分析 | 第 2 次 PR !2113 仅添加 WARN 诊断日志，未做根因分析 | 第 2 次修复时必须停下来，按 §23 全链路日志排查 SOP 定位零号病人 |
| 3 | 一次性根治（处理周边技术债） | 生成阶段已流式（PR !2120）但下载阶段未流式，已知技术债未在同一 PR 收口 | 同一资源链路上的已知技术债必须在同一 PR 中收口 |

### 回归原因：PR !2116 未评估数据量变化

PR !2116 修复 PDF 照片被静默跳过时，引入了 PDF→图片→Word 嵌入逻辑。这个改动导致 Word 文档体积从 KB 级膨胀到百 MB 级。但 PR !2116 描述中**未提及这个数据量变化**对上下游下载链路的影响，导致下游 `Files.readAllBytes()` 在百 MB 数据量下触发 OOM。

### 新增 2 条 SOP

#### SOP-1：PR 审查必须评估数据量变化

PR 引入新的资源消费（如 PDF→图片→Word 嵌入、批量导入、流式→全量加载）时，必须在 PR 描述中评估：
- 数据量从 X 级变化到 Y 级（如 KB → MB、MB → GB）
- 对上下游链路的影响（如内存占用、超时时间、网络流量）
- 是否触发已知的资源消费瓶颈（如 `Files.readAllBytes`、`byte[]` 全量加载）

PR 模板新增检查项：「本 PR 是否引入数据量变化？如是，评估对上下游链路的影响」。

#### SOP-2：新增重资源操作时检查周边技术债

修改文件读写、流式输出、批量处理等链路时，必须检查同一资源链路上的其他环节是否存在已知技术债：
- 生成阶段已流式 → 检查下载阶段是否流式
- 上游已用 `StreamingResponseBody` → 检查下游是否用 `byte[]` 加载
- 同一文件路径有多个读写点 → 检查所有读写点是否一致

技术债检查清单登记到 `docs/exec-plans/tech-debt-tracker.md`，新增资源操作时必须核对清单。

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 5 轮补丁式修复 | 第 2 次修同一个 bug 必须停下来做根因分析，禁止继续打补丁 | 补丁式修复累计 2 次后强制停下来 |
| 未查看服务器日志 | 真实环境验证必须查看 `journalctl` 服务器日志，不能仅靠本地推测 | 第 1 轮修复后必须 SSH 验证 |
| 未评估数据量变化 | PR 引入新的资源消费时必须评估对上下游链路的影响 | PR 模板新增「数据量变化」检查项 |
| 未处理周边技术债 | 同一资源链路上的已知技术债必须在同一 PR 中收口 | 新增资源操作时核对技术债清单 |

### 相关文档

- [root-cause-analysis-warehouse-export-download-oom.md](./root-cause-analysis-warehouse-export-download-oom.md) — 完整根因分析
- `docs/lessons/lessons-learned.md` §23 — 全链路日志排查 SOP
- `docs/lessons/lessons-learned.md` §19 — 简单 bug 多轮修不对：先定位"空值从哪来"

---

## 64. 设计评审 10 个通用问题：事务边界/状态机/Error/参数列表/public 暴露/setter 污染/Stream/文件复制/内存/纯函数错位（2026-07-17 / PR !2110 设计评审）

> 来源：2026-07-17 用户对 PR !2110（仓库导出 @Async 自调用修复）做系统性设计评审，识别 10 个通用问题
> 涉及模块：仓库导出（WarehouseExportAppService / WarehouseLedgerExportAppService）
> 关联 PR：!2110 / !2111

### 10 个通用问题清单

| # | 问题 | 根因 | 修复方案 |
|---|---|---|---|
| 1 | @Async 与 @Transactional 事务边界竞态条件 | @Async 方法在事务提交前执行，查询不到数据 | 提取 StateService 独立事务，@TransactionalEvent 监听事务提交后执行 |
| 2 | 状态机方法（markProcessing/fail/complete）跨类复制粘贴 | 多个 Executor 类各自实现状态变更 | 提取到 WarehouseExportTaskStateService 统一管理 |
| 3 | 异步任务执行未捕获 Error | 只 catch Exception，Error（如 OOM）导致任务永久卡死 | catch (Error e) 并标记任务失败 |
| 4 | 长参数列表（5+ 个参数） | 方法签名参数过多，可读性差 | 包装成 ExportCompletion record |
| 5 | 过度暴露 public 方法 | 内部辅助方法设为 public，被外部误用 | 改为 private 或移到 StateService |
| 6 | 测试专用 setter 污染生产代码 | 为测试方便给生产类加 setter | 用 ReflectionTestUtils 替代 |
| 7 | 低效 Stream 使用（多次遍历） | 同一集合多次 stream() | 一次遍历收集到 Map |
| 8 | 不必要的文件复制 | Files.copy 临时文件再删除 | 用 Files.move 原子移动 |
| 9 | 内存密集型文件读取 | Files.readAllBytes 加载整个文件到 byte[] | 用 InputStream + buffer 流式读取 |
| 10 | 纯函数错位（应用服务中包含业务决策） | 纯核心逻辑写在应用服务层 | 迁移到 core/policy 包 |

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| @Async 与 @Transactional 事务边界 | @Async 方法不在原事务上下文中，必须用 @TransactionalEvent 或独立事务 | 异步方法必须提取到独立 Bean + 独立事务 |
| 状态机方法复制粘贴 | 状态变更逻辑必须集中到 StateService，禁止跨类复制 | 任务状态机方法必须提取到独立 StateService |
| 未捕获 Error | 异步任务必须 catch (Error e)，避免 OOM 等导致任务卡死 | 异步方法 catch 块必须包含 Error |
| 长参数列表 | 5+ 个参数必须包装成 record/DTO | 方法参数超过 4 个必须包装 |
| 测试 setter 污染 | 测试不得修改生产代码可见性 | 用 ReflectionTestUtils 替代测试 setter |
| 静默跳过/continue | 所有跳过逻辑必须打 WARN 日志 | continue/return 跳过必须有日志 |

### 操作规范

1. **@Async 方法必须提取到独立 Spring Bean**：禁止同类内部直接调用（self-invocation），否则代理失效
2. **任务状态机方法必须提取到独立 StateService**：markProcessing/fail/complete 等状态变更集中管理
3. **异步任务必须 catch (Error e)**：避免 OOM/StackOverflow 导致任务永久卡死
4. **方法参数超过 4 个必须包装成 record**：长参数列表降低可读性
5. **public 方法必须审视必要性**：内部辅助方法设为 private，测试用 ReflectionTestUtils
6. **新增文件读写链路必须检查周边技术债**：同一资源链路上的其他环节是否存在已知技术债

### 相关文档

- `backend/src/main/java/com/xiyu/bid/.../WarehouseExportTaskStateService.java` — 状态机方法集中管理
- `backend/src/main/java/com/xiyu/bid/.../ExportCompletion.java` — 参数包装 record
- [docs/lessons/root-cause-analysis-warehouse-export-download-oom.md](./root-cause-analysis-warehouse-export-download-oom.md) — 静默跳过/内存问题详细根因
- `docs/lessons/lessons-learned.md` §63 — Bug 回归反思 SOP

---

## 65. PR #2115 设计评审 4 个问题：workbench 端点分散/ProjectService 超 300 行/内存分页/projectId=null（2026-07-17）

> 来源：2026-07-17 用户对 PR #2115 做系统性设计评审
> 涉及模块：workbench（待办工作台）/ project（项目）
> 关联 PR：!2115（commit 22fa764e4）

### 4 个问题清单

| # | 问题 | 根因 | 修复方案 |
|---|---|---|---|
| 1 | workbench 端点分散在 dashboard 和 project 包 | 历史演进中 workbench 端点散落多处 | 端点迁移到 WorkbenchTodoController |
| 2 | ProjectService 超过 300 行限制 | 单一 Service 承担过多职责 | 拆分 ProjectService |
| 3 | DashboardResourcePendingService 内存分页 | 一次性加载所有数据到内存再分页 | 改为数据库分页（Pageable） |
| 4 | projectId=null 显示问题 | 数据库中存在 projectId=NULL 的记录 | 过滤 null projectId |

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 端点分散 | 同一业务域的端点必须集中在同一 Controller，避免散落多个包 | 新增端点前审视现有 Controller 归属 |
| Service 超限 | 任何类不得超过 300 行，超限前必须拆分 | 拆分时按职责分（查询/命令/状态机/外部集成） |
| 内存分页 | 任何分页查询必须走数据库 Pageable，禁止内存分页 | 内存分页在数据量大时性能灾难 |
| null 显示 | 数据库 NULL 字段必须在 UI 层显式处理 | 前端用 v-if 或 ?? '' 处理 null |

### 操作规范

1. **同一业务域端点必须集中**：workbench 端点统一在 WorkbenchTodoController，禁止散落
2. **Service 类不得超过 300 行**：超限前必须按职责拆分（FP-Java 11 条）
3. **分页查询必须走数据库 Pageable**：禁止 `findAll()` + 内存 `subList()`
4. **数据库 NULL 字段必须 UI 层处理**：前端用 `v-if`/`?? ''`/`?? '-'` 显式处理

### 相关文档

- `backend/src/main/java/com/xiyu/bid/.../WorkbenchTodoController.java` — workbench 端点集中
- `backend/src/main/java/com/xiyu/bid/.../DashboardResourcePendingService.java` — 数据库分页改造
- [docs/lessons/lessons-learned.md](./lessons-learned.md) §64 — 设计评审 10 个通用问题

---

## 66. 删除 bootstrap 锚点分支会导致 worktree 跟踪失败（agent/*-init 不可删）（2026-07-17）

> 来源：2026-07-17 用户清理远端 stale 分支时删除了 `agent/trae-init`，导致 trae worktree 跟踪失败
> 涉及模块：多 Agent worktree 协作

### 问题背景

用户清理远端 stale 分支时，删除了 `agent/trae-init`（bootstrap 锚点分支）。这导致：
- 本地 trae worktree 的 tracking 分支失效
- 下次 `sync-env.sh` 的 ff-only 同步失败
- trae worktree 无法正常 `git fetch` / `git rebase`

### 根因：bootstrap 锚点分支的语义

`agent/*-init` 分支是各 worktree 的 bootstrap 锚点分支：
- 通过 `agent-worktree-guard.sh` 做身份识别（`worktree_name` 匹配）
- 安装全局钩子、初始环境配置
- `sync-env.sh` 对 init 分支自动执行 ff-only 同步（保持与 main 一致）

删除 `agent/*-init` 分支会让 worktree 失去锚点，所有自动化脚本失效。

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| 误删 bootstrap 锚点分支 | `agent/*-init` 是 worktree 的身份标识和同步锚点 | 禁止删除 `agent/*-init` 远端分支 |
| stale 分支清理未区分类型 | 清理脚本未区分任务分支（可删）和 bootstrap 分支（不可删） | 清理前必须区分分支类型 |

### 操作规范

1. **`agent/*-init` 分支禁止删除**：这是 bootstrap 锚点分支，删除会导致 worktree 失效
2. **stale 分支清理前必须区分类型**：
   - 任务分支（`agent/<agent>/<task>`）：PR 合入后可删
   - bootstrap 分支（`agent/<agent>-init`）：禁止删除
   - 长期任务分支：检查是否有未合 PR，无则可删
3. **删除前用 `git branch -vv` 确认分支跟踪关系**：避免误删有 worktree 跟踪的分支
4. **worktree 失效后恢复**：
   - 重建锚点分支：`git branch agent/<agent>-init origin/main`
   - 推送远端：`git push origin agent/<agent>-init`
   - 在 worktree 内 `git checkout agent/<agent>-init` 重新跟踪
5. **未来建议**：清理脚本加入 `agent/*-init` 保护名单，禁止自动删除

### 相关文档

- `scripts/agent-finish-task.sh` — 任务分支收尾（不删 init 分支）
- `scripts/sync-env.sh` — init 分支 ff-only 同步逻辑
- `scripts/agent-worktree-guard.sh` — worktree 身份识别

---

## 67. Excel 导出 sentinel 反模式 + 枚举 `displayName()` 陷阱（PR !2084 / 2026-07-15 / CO-583 关联）

> 来源：2026-07-15 业绩管理 Excel 导出两个 bug 修复 + P0/P2 架构治理
> 涉及模块：`PerformanceExcelExporter` / `ContractStatusPolicy` / 5 个枚举（`ContractStatus` / `CustomerType` / `PerformanceStatus` / `PerformanceTypeEnum` / `SettlementMethod`）
> 关联 PR：!2082（CO-583 分组聚合）、!2084（bug 修复 + 架构治理）

### 问题背景

业绩管理 Excel 导出存在两个用户可见 bug：

| Bug | 表现 | 根因 |
|---|---|---|
| 状态列显示英文 | `ONGOING` / `EXPIRED` 而非"履约中"/"已到期" | `ContractStatus.name()` 返回枚举名（英文），应使用 `displayName()`（中文） |
| 到期天数列显示 9223372036854780000 | 19 位整数，Excel 显示为 `9.22337E+18` | `ContractStatusPolicy` 用 `Long.MAX_VALUE` 作为"无到期日"的 sentinel 值 |

### 根因分析

**Bug 1: `name()` vs `displayName()` 语义混淆**

5 个枚举类各自维护 `displayName` 字段，但 `PerformanceExcelExporter` 直接调用 `ContractStatus.name()`（Java 枚举内置方法，返回枚举常量名）。这种调用混淆了"枚举常量名"和"业务展示名"两个语义层。

**Bug 2: `Long.MAX_VALUE` sentinel 反模式**

`ContractStatusPolicy.calculateStatus` 在 `expiryDate == null` 时返回 `Long.MAX_VALUE` 作为"无到期日"的标记。这是典型的 sentinel 反模式：

1. **类型语义污染**：`Long` 类型本应表示"天数"，但 `Long.MAX_VALUE` 是一个魔法值，语义为"无穷大"
2. **下游处理陷阱**：Excel 导出直接写入 `Long.MAX_VALUE`，Excel 把它显示为科学计数法 `9.22337E+18`
3. **计算溢出风险**：任何对 sentinel 值的算术运算（如 `sentinel - today`）都可能导致溢出
4. **可读性差**：调试时看到 `9223372036854780000` 难以理解其含义

### 修复方案

**Bug 1 修复**：5 个枚举统一改造为构造注入 `displayName` 字段：

```java
// 修复前
public enum ContractStatus {
    ONGOING,
    EXPIRED;

    public String displayName() {
        return this == ONGOING ? "履约中" : "已到期";
    }
}

// 修复后（构造注入）
public enum ContractStatus {
    ONGOING("履约中"),
    EXPIRED("已到期");

    private final String displayName;

    ContractStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

同时删除 `PerformanceEnumLabels` 的 forward mapping 函数（`getContractStatusLabel(ContractStatus)` 等），统一通过 `enum.displayName()` 获取。

**Bug 2 修复**：sentinel 值改为 `null`，Excel 导出处理 null：

```java
// ContractStatusPolicy
public Long calculateExpiryDays(LocalDate today, LocalDate expiryDate) {
    if (expiryDate == null) {
        return null;  // 无到期日，返回 null 而非 Long.MAX_VALUE
    }
    return ChronoUnit.DAYS.between(today, expiryDate);
}

// PerformanceExcelExporter
Long expiryDays = policy.calculateExpiryDays(today, contract.getExpiryDate());
String cellValue = expiryDays == null ? "" : String.valueOf(expiryDays);
```

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| `name()` vs `displayName()` 混淆 | Java 枚举的 `name()` 是技术标识，不应直接用于业务展示 | 枚举展示一律走 `displayName()`，禁止在导出/UI 层调用 `name()` |
| `Long.MAX_VALUE` sentinel | sentinel 值是隐式契约，下游容易误用 | 用 `Optional<Long>` 或 `null` 表达"无值"，禁止用 `Long.MAX_VALUE` / `Long.MIN_VALUE` / `-1` 等 sentinel |
| 枚举 if-else 分支多 | 每加一个枚举常量就要改 if-else | 枚举字段统一用构造注入，禁止在方法内用 switch/if-else 返回常量值 |
| forward mapping 散落 | `EnumLabels.getXxxLabel()` 类方法重复枚举内部的 displayName 逻辑 | 删除 forward mapping，统一通过 `enum.displayName()` |

### 操作规范

1. **新增枚举**：必须用构造注入 `displayName` 字段，禁止 if-else / switch 返回常量值
2. **Excel 导出枚举值**：一律调用 `displayName()`，禁止 `name()` 或 `toString()`
3. **sentinel 值禁令**：禁止用 `Long.MAX_VALUE` / `Long.MIN_VALUE` / `-1` / `Integer.MAX_VALUE` 等魔法值表达"无值"语义，改用 `null` 或 `Optional`
4. **Excel 单元格 null 处理**：导出层必须显式处理 `null`，写入空字符串或留空，禁止依赖 POI 默认行为
5. **回归测试**：null 场景必须覆盖（`ContractStatusPolicyTest` 13 tests + `PerformanceExcelExporterTest` 1 test）

### 验证命令

```bash
# 检查枚举是否用 name() 直接导出
grep -rn "\.name()" backend/src/main/java/com/xiyu/bid/**/PerformanceExcelExporter.java
# 期望：无 .name() 调用

# 检查 Long.MAX_VALUE sentinel
grep -rn "Long.MAX_VALUE\|Long.MIN_VALUE" backend/src/main/java/com/xiyu/bid/performance/
# 期望：无 sentinel 用法

# 跑回归测试
cd backend && mvn test -Dtest='ContractStatusPolicyTest,PerformanceExcelExporterTest'
# 期望：14 tests, 0 failures
```

### 相关文档

- `backend/src/main/java/com/xiyu/bid/performance/domain/ContractStatusPolicy.java` — sentinel 修复
- `backend/src/main/java/com/xiyu/bid/performance/infrastructure/PerformanceExcelExporter.java` — Excel 导出修复
- `backend/src/main/java/com/xiyu/bid/performance/domain/PerformanceEnumLabels.java` — forward mapping 删除
- PR !2082 — CO-583 业绩管理列表分组与总截止日期聚合
- PR !2084 — bug 修复 + P0/P2 架构治理

---

## 68. PR revert 必须保留正确修复 + 守护测试防重蹈覆辙（PR !2091 → !2095 / 2026-07-16）

> 来源：2026-07-16 PR !2091 引入 locked 字段可编辑回归，PR !2095 revert 后补 5 个守护测试
> 涉及模块：`project` 立项表单 PENDING_REVIEW 状态
> 关联 PR：!2091（引入回归）、!2095（revert + 守护测试）、!2089（同 session 关联的标讯录入自定义表单）

### 问题背景

PR !2091 试图让 PENDING_REVIEW 状态下立项表单字段可编辑，但修改 `locked` 语义时引入回归：region cascader 自动关闭逻辑被破坏。同时该 PR 还包含 region cascader 修复（独立正确改动）。

PR !2095 进行 revert，但只 revert locked 修改部分，**保留 region cascader 修复**，并补 5 个 locked 语义守护测试。

### 时间线

| 时间 | 操作 | 判断 |
|---|---|---|
| PR !2089 | 标讯录入自定义表单改造（同 session） | ✅ 独立正确 |
| PR !2091 | feat(project): PENDING_REVIEW 状态下立项表单字段可编辑 | ⚠️ 混合两个改动 |
| 回归发现 | region cascader 自动关闭失效 | ❌ locked 改动破坏 cascader |
| PR !2095 | revert !2091 的 locked 修改，保留 region cascader 修复 | ✅ 选择性 revert |
| 守护测试 | 补 5 个 locked 语义守护测试 | ✅ 防重蹈覆辙 |

### 根因分析

**根因 1: PR 混合多个不相关改动**

PR !2091 同时包含：
- locked 语义修改（业务变更）
- region cascader 修复（独立 bug fix）

两个改动没有业务关联，应分两个 PR。混合后 revert 时被迫做选择性 revert，增加复杂度。

**根因 2: locked 语义修改未审视所有依赖**

`locked` 字段在立项表单、cascader、审批模式等多处使用，修改时只考虑了 PENDING_REVIEW 状态，未审视对 cascader 自动关闭行为的副作用。

**根因 3: 缺少 locked 语义守护测试**

修改 `locked` 语义时，没有对应的守护测试来捕获回归。`locked` 是表单状态机的核心字段，缺少测试意味着任何改动都可能引入隐性回归。

### 修复方案

**步骤 1: 选择性 revert**

PR !2095 只 revert locked 修改部分，保留 region cascader 修复：

```bash
git revert <commit-hash>  # 整体 revert
git checkout <previous-commit> -- path/to/cascader/fix  # 恢复 cascader 修复
git commit -m "revert(project): 撤销 PR !2091 的 locked 修改，保留 region cascader 修复"
```

**步骤 2: 补 5 个 locked 语义守护测试**

```java
@Test
void locked_should_be_true_when_in_approval_mode() { ... }

@Test
void locked_should_be_false_when_in_edit_mode() { ... }

@Test
void locked_should_not_affect_region_cascader_auto_close() { ... }

@Test
void locked_should_not_block_form_submission_when_pending_review() { ... }

@Test
void locked_semantics_should_be_independent_of_region_cascader() { ... }
```

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| PR 混合多个不相关改动 | revert 时被迫做选择性操作，增加复杂度 | 一个 PR 只做一件事，禁止混合业务变更 + 独立 bug fix |
| 修改 `locked` 等核心状态字段未审视所有依赖 | 隐性回归（cascader 自动关闭失效） | 修改状态机字段前必须 Grep 所有调用点，逐一审视副作用 |
| 缺少守护测试 | 回归无法在 CI 阶段捕获 | 状态机核心字段必须有守护测试，每次改动都跑 |
| 选择性 revert 操作复杂 | 容易漏 revert 或多 revert | 选择性 revert 后必须 diff 确认保留的改动仍在 |

### 操作规范

1. **PR 原子性**：一个 PR 只做一件事，禁止混合业务变更 + 独立 bug fix + 重构
2. **修改核心状态字段前**：`Grep` 所有调用点（如 `locked` / `reviewStatus` / `projectStatus`），逐一审视副作用
3. **守护测试**：状态机核心字段必须有守护测试，覆盖所有状态组合
4. **选择性 revert**：revert 后必须 `git diff` 确认保留的改动仍在，跑测试验证
5. **revert commit message**：必须写明"撤销了什么"+"保留了什么"+"为什么"

### 验证命令

```bash
# 检查 locked 字段调用点
grep -rn "\.locked\b\|getLocked\b\|isLocked\b" backend/src/main/java/com/xiyu/bid/project/ src/views/Project/
# 期望：所有调用点都已审视

# 跑守护测试
cd backend && mvn test -Dtest='ProjectLockedSemanticsTest'
# 期望：5 tests, 0 failures

# 验证 cascader 修复仍在
git diff origin/main HEAD -- src/views/**/RegionCascader.vue
# 期望：cascader 修复改动仍在
```

### 相关文档

- `backend/src/test/java/com/xiyu/bid/project/ProjectLockedSemanticsTest.java` — 5 个守护测试
- PR !2091 — 引入回归（已 revert）
- PR !2095 — 选择性 revert + 守护测试
- §4 — 回滚 PR 前必须确认根因（revert 相关教训）

---

## 69. MySQL JDBC URL 必须显式配置 `zeroDateTimeBehavior=convertToNull`（PR !2087 / !2088 / 2026-07-15）

> 来源：2026-07-15 生产环境表单加载失败，JDBC URL 缺少 `zeroDateTimeBehavior=convertToNull`
> 涉及模块：`application-prod.yml` / `application.yml`
> 关联 PR：!2087（生产修复）、!2088（同步 application-prod.yml）

### 问题背景

生产环境部署后，用户访问某些表单页时加载失败，后端日志报错：

```
SQLException: Cannot convert value '0000-00-00 00:00:00' from column N to TIMESTAMP
```

根因是数据库中存在 `0000-00-00 00:00:00` 这样的 zero date 值，MySQL JDBC 驱动默认拒绝转换。

### 根因分析

**MySQL zero date 历史**：

- MySQL 5.x 允许 `0000-00-00 00:00:00` 作为合法日期值（用于表示"无日期"）
- MySQL 8.0 默认 `sql_mode` 包含 `NO_ZERO_DATE`，禁止插入 zero date
- 但历史数据中可能仍存在 zero date（来自 MySQL 5.x 时代）
- MySQL JDBC 驱动（`mysql-connector-j`）默认 `zeroDateTimeBehavior=EXCEPTION`，遇到 zero date 抛异常

**项目配置缺口**：

- `application.yml`（dev）未显式配置 `zeroDateTimeBehavior`
- `application-prod.yml`（prod）也未显式配置
- 项目 V1077 迁移虽然清理了 zero date 记录，但无法保证所有表都清理干净
- 生产环境 `docker-compose.yml` 中 MySQL `sql_mode` 已去掉 `NO_ZERO_DATE,NO_ZERO_IN_DATE`，但 JDBC 驱动仍按 `EXCEPTION` 处理

### 修复方案

在 JDBC URL 中显式加 `zeroDateTimeBehavior=convertToNull`：

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/xiyu_bid_main?useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=Asia/Shanghai&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true

# application-prod.yml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=Asia/Shanghai&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true
```

**`convertToNull` 的语义**：遇到 zero date 时，JDBC 驱动不抛异常，而是转换为 `null`，由应用层处理。

### 经验教训

| 问题 | 教训 | 规范 |
|---|---|---|
| JDBC URL 未显式配置 `zeroDateTimeBehavior` | 依赖驱动默认值（EXCEPTION），遇到 zero date 直接抛异常 | 所有环境（dev/test/prod）的 JDBC URL 必须显式加 `zeroDateTimeBehavior=convertToNull` |
| V1077 清理 zero date 不彻底 | 历史数据可能仍有 zero date | 迁移脚本清理 + JDBC 配置兜底，两者都要 |
| dev/test 与 prod 配置不一致 | dev 通过，prod 失败 | 所有环境配置必须同步，禁止 dev/prod 配置漂移 |

### 操作规范

1. **JDBC URL 必备参数**：
   - `zeroDateTimeBehavior=convertToNull` — zero date 转 null
   - `useUnicode=true&characterEncoding=utf8mb4` — 字符集
   - `serverTimezone=Asia/Shanghai` — 时区
   - `allowPublicKeyRetrieval=true` — MySQL 8.0 公钥检索
2. **dev/prod 配置同步**：修改 JDBC URL 时必须同步所有环境（dev/test/prod）
3. **应用层处理 null 日期**：实体类字段用 `LocalDate` / `LocalDateTime`（可为 null），DTO 显式处理 null
4. **V1077 类型的清理迁移**：清理 zero date 后仍需保留 JDBC 配置兜底，防止新数据再次产生 zero date

### 验证命令

```bash
# 检查所有环境的 JDBC URL
grep -rn "zeroDateTimeBehavior" backend/src/main/resources/
# 期望：application.yml + application-prod.yml + application-test.yml 都有

# 检查是否还有 zero date 数据
mysql -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='xiyu_bid_main' AND data_type IN ('date','datetime','timestamp')"
# 然后逐表检查 zero date

# 跑表单加载相关测试
cd backend && mvn test -Dtest='*FormLoad*,*WorkflowForm*'
```

### 相关文档

- `backend/src/main/resources/application.yml` — dev 配置
- `backend/src/main/resources/application-prod.yml` — prod 配置
- `backend/src/main/resources/db/migration-mysql/V1077__cleanup_zero_dates.sql` — zero date 清理迁移
- `docker-compose.yml` — MySQL `sql_mode` 配置（去掉 `NO_ZERO_DATE,NO_ZERO_IN_DATE`）
- PR !2087 — 生产 JDBC URL 修复
- PR !2088 — application-prod.yml 同步

---

## 70. `remote-deploy.sh` 覆盖 `deployed-release.json` 前未备份上一版本 ID，导致前端旧 assets 保留逻辑断链（2026-07-17 / 第 93 次测试部署）

> 来源：2026-07-17 第 93 次测试部署时识别的风险
> 涉及模块：`scripts/release/remote-deploy.sh`（`write_deployed_release_record()` + 前端资源激活逻辑）
> 关联文件：`docs/release/deploy-report-2026-07-17-93rd-test.md`

### 问题背景

第 93 次测试部署中，前端资源激活后，`/srv/www/xiyu-bid/assets/` 目录下**没有保留上一版本的 assets 文件**。旧版本（`0a79f0d68-api8080`）的 index.html 引用的 Vite hash 命名 assets（如 `Detail-abc123.js`）在新版本部署后丢失，若有用户浏览器仍缓存旧 index.html，访问会 404。

部署报告记录的临时处置：
> 前端资源保留 | ✅ 从 0a79f0d68-api8080 手动 cp -rn assets（脚本自动取 PREV 失败，已手动指定）

### 根因：`write_deployed_release_record()` 直接覆盖，未先备份旧记录

`remote-deploy.sh` 的 `write_deployed_release_record()` 函数直接 `cat > "$DEPLOYED_RELEASE_RECORD"` 覆盖文件：

```bash
# ❌ 当前实现：直接覆盖，不保留旧记录
cat > "$DEPLOYED_RELEASE_RECORD" <<EOF
{
  "releaseId": "$RELEASE_ID",       # ← 新版本 ID 直接覆盖旧版本 ID
  "activatedAt": "$activated_at",
  "releaseDir": "$RELEASE_DIR",
  ...
}
EOF
```

同时，前端资源激活逻辑直接 `rm -rf` 整个公共目录：

```bash
# ❌ 当前实现：删除整个目录，旧 assets 全部丢失
rm -rf "$PENDING_FRONTEND_DIR"
mkdir -p "$PENDING_FRONTEND_DIR"
cp -R "$RELEASE_DIR/frontend/." "$PENDING_FRONTEND_DIR/"   # 只复制新版本 assets
rm -rf "$FRONTEND_PUBLIC_DIR"                                # ← 删除整个公共目录（含旧 assets）
mv "$PENDING_FRONTEND_DIR" "$FRONTEND_PUBLIC_DIR"
```

**PREV 变量失效链路**：
1. 部署前，`deployed-release.json` 中 `releaseId` = 旧版本（如 `0a79f0d68-api8080`）
2. 部署中，`write_deployed_release_record()` 把 `releaseId` 覆盖为新版本（如 `a33c1339d-api8080`）
3. 后续保留旧 assets 逻辑若尝试从 `deployed-release.json` 读取 `PREV` 版本 ID，取到的是**新版本 ID**，不是上一版本
4. `cp -rn "$APP_ROOT/releases/$PREV/frontend/assets/." "$PENDING_FRONTEND_DIR/assets/"` 实际是从新版本复制到新版本，等于 no-op
5. 旧版本 assets 全部丢失

### 危害

1. **用户访问 404**：浏览器缓存旧 index.html 时，引用的旧 assets 文件已被删除，访问报 404。
2. **SPA 回滚困难**：若需要回滚到上一版本前端，assets 文件已丢失，必须从 release 归档目录重新 cp。
3. **依赖人工补救**：每次部署都需要人工 `cp -rn` 保留旧 assets，容易遗漏。

### 修复方案（建议，未实施）

**方案 A：在覆盖前先备份上一版本 ID（推荐）**

```bash
# ✅ 修复：覆盖前先备份上一版本 ID
PREV_RELEASE_ID=""
if [[ -f "$DEPLOYED_RELEASE_RECORD" ]]; then
  PREV_RELEASE_ID=$(grep -oP '"releaseId":\s*"\K[^"]+' "$DEPLOYED_RELEASE_RECORD" 2>/dev/null || true)
fi

if [[ -n "$PREV_RELEASE_ID" ]]; then
  printf '==> Preserving previous assets from %s\n' "$PREV_RELEASE_ID"
  cp -rn "$APP_ROOT/releases/$PREV_RELEASE_ID/frontend/assets/." "$PENDING_FRONTEND_DIR/assets/" 2>/dev/null || true
fi

write_deployed_release_record
```

**方案 B：`deployed-release.json` 增加 `previousReleaseId` 字段**

```json
{
  "releaseId": "a33c1339d-api8080",
  "previousReleaseId": "0a79f0d68-api8080",
  "activatedAt": "...",
  ...
}
```

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| `cat >` 直接覆盖部署记录 | 部署记录是「上一版本 ID」的唯一来源，覆盖前必须先备份 | 覆盖 `deployed-release.json` 前必须先读取旧 `releaseId` 并保存为 `PREV_RELEASE_ID` |
| `rm -rf "$FRONTEND_PUBLIC_DIR"` 删除整个目录 | Vite hash 命名的 assets 在版本切换后仍需保留（供旧 index.html 引用） | 前端资源激活必须用 `cp -rn`（no-clobber）保留旧 assets，而非 `rm -rf` 后 `mv` |
| PREV 变量取值失效无告警 | 脚本静默取到当前版本而非上一版本，保留逻辑变成 no-op | 保留 assets 后必须验证数量应大于当前版本 assets 数量 |
| 依赖人工补救 | 每次部署都需要手动 `cp -rn`，容易遗漏 | 自动化脚本必须内置旧 assets 保留逻辑，禁止依赖人工 |

### 相关文档

- [remote-deploy.sh](../../scripts/release/remote-deploy.sh) — `write_deployed_release_record()` + 前端激活逻辑
- [deploy-test.sh](../../scripts/release/deploy-test.sh) — 注释提到 `cp -rn` 保留旧 assets 逻辑本应存在
- [deploy-report-2026-07-17-93rd-test.md](../release/deploy-report-2026-07-17-93rd-test.md) — 手动补救过程记录

---

## 71. Kafka SDK readiness 延迟导致 `remote-deploy.sh` 健康检查 120 次重试假失败（2026-07-17 / 第 93 次测试部署）

> 来源：2026-07-17 第 93 次测试部署观察到的现象
> 涉及模块：`OrganizationEventSdkKafkaStarter` + `scripts/release/remote-deploy.sh` 健康检查逻辑
> 关联文件：`docs/release/deploy-report-2026-07-17-93rd-test.md`

### 问题背景

第 93 次测试部署中，`remote-deploy.sh` 执行健康检查时连续失败 120 次重试（约 4 分钟），脚本退出码非零。但**服务实际已正常运行**，约 4 分钟后 `/actuator/health` 返回 200，后端 readiness 状态从 `OUT_OF_SERVICE` 自动恢复为 `UP`。

历史出现次数：第 8/9/10/13/15/93 次部署时出现，**累计 6 次以上**，是反复出现的已知行为。

### 根因：`OrganizationEventSdkKafkaStarter.onApplicationEvent()` 阻塞主线程

`OrganizationEventSdkKafkaStarter` 监听 `ApplicationReadyEvent`，在主线程同步执行：
1. `register()` — 向配置中心注册消费者
2. `initCacheBean()` — 初始化 SDK 缓存 Bean
3. `KafkaProcessor.start()` — 启动 Kafka 消费者

任一步骤阻塞（如 Kafka broker 不可达、配置中心超时）都会导致主线程被占用，`AvailabilityChangeEvent` 发布延迟，`ReadinessState` 切换延迟，readiness 持续返回 503。

### `remote-deploy.sh` 健康检查时序

- 默认 120 次重试 × 2 秒间隔 = **240 秒 = 4 分钟**
- Kafka readiness 延迟实际约 4-5 分钟
- 120 次重试刚好处于临界值，部分场景下会假失败

### 危害

1. **假阳性部署失败**：服务实际已启动，但脚本判定为失败，退出码非零
2. **误导运维**：运维人员可能误判为部署失败，执行不必要的回滚
3. **CI 假红**：CI 流水线执行 `remote-deploy.sh` 时无故失败
4. **重复排查**：每次出现都需要人工 ssh 确认 `/actuator/health` 状态

### 修复方案（建议，未实施）

**方案 A：延长健康检查重试次数（短期缓解）**

```bash
# ✅ 修复：延长到 180 次（6 分钟）
HEALTH_CHECK_MAX_ATTEMPTS="${HEALTH_CHECK_MAX_ATTEMPTS:-180}"
```

**方案 B：`OrganizationEventSdkKafkaStarter.onApplicationEvent()` 改为异步（根治）**

```java
@EventListener(ApplicationReadyEvent.class)
@Async  // ← 新增：异步执行，不阻塞主线程
public void onApplicationEvent(ApplicationReadyEvent event) {
    register();
    initCacheBean();
    KafkaProcessor.start();
}
```

**方案 C：健康检查 URL 区分 liveness 与 readiness** — 部署脚本只检查 liveness，readiness 失败不阻断部署

### 教训

| 问题 | 教训 | 规范 |
|---|---|---|
| `@EventListener(ApplicationReadyEvent)` 阻塞主线程 | 同步事件监听器会延迟 `ReadinessState` 切换 | 耗时操作必须 `@Async` 异步执行 |
| 健康检查重试次数临界值设计 | 120 次 × 2 秒 = 4 分钟，刚好等于 Kafka 延迟，临界值不稳定 | 重试次数应**大于**已知最大延迟时间的 1.5 倍 |
| 已知行为重复 6 次未根治 | 每次都靠「约 4 分钟自恢复」缓解 | 已知行为重复 3 次以上必须进入技术债台账 |

### 相关文档

- [remote-deploy.sh](../../scripts/release/remote-deploy.sh) — `HEALTH_CHECK_MAX_ATTEMPTS` 默认值
- [deploy-report-2026-07-17-93rd-test.md](../release/deploy-report-2026-07-17-93rd-test.md) — Kafka 延迟现象记录
- `OrganizationEventSdkKafkaStarter.java` — 根治方案目标类

## 72. 分支基线过期导致 PR diff 静默回退/删除他人文件（PR !2138 / !2141 / 2026-07-19）

> 来源：2026-07-19 审查 PR !2138（CO-590）与 !2141（CO-591）时连续发现同类问题
> 涉及模块：多 Agent 分支协作 / Gitee PR 合并

### 问题背景

同一 agent（trae2）连续两个 PR 的 diff 中混入与本任务无关的变更：

- **PR !2138（CO-590 合同信息模块）**：9 个 `.wiki/pages/*.md` 的 `health_checked: 2026-07-19` 被改回 `2026-07-10`，静默回退了其他 agent 的 wiki 元数据更新，并随合并落入 main。
- **PR !2141（CO-591 列表四列）**：diff 显示整文件删除 `docs/release/deploy-report-2026-07-19-12th-prod.md`（275 行，由 `fc6518c66` 在该分支切出后合入 main）。分支上并无显式删除提交——纯粹是基线过期导致的 diff 假象，一旦被 squash/rebase 式合入就会真删。

### 根因

1. **分支从旧 main 切出后未 rebase**：`git merge-base` 落后于 `origin/main` 多个提交，Gitee PR 展示的是 `base...head` 双边 diff，基线之后 main 上新增/修改的文件在 diff 中表现为"删除/回退"。
2. **Agent 提交了 stale 工作区文件**：!2138 的 wiki 文件在分支上有真实提交（工作区残留旧版本被一并 commit），三方合并也救不回来。
3. **合并前无人核对完整文件清单**：`git diff origin/main --stat` 一眼可见异常文件，但两次都未检查。

### 危害

- 静默回退他人已合入的文档/元数据变更，无冲突提示、无 CI 拦截，事后靠 review 肉眼发现。
- squash 合入模式下，基线之后新增的文件会被真实删除。

### 防御规范

| 问题 | 教训 | 规范 |
|---|---|---|
| 分支基线过期 | 双边 diff 会把 main 的新增文件显示成"被删除" | **推送/合并前必做** `git fetch origin && git rebase origin/main`，并核对 `git diff origin/main --stat` 只含本任务文件 |
| stale 工作区文件被提交 | 旧版本文件 commit 进分支后，merge 也会回退 main | commit 前 `git status` + 抽查 diff，发现与本任务无关的文件改动一律不 stage |
| Review 漏看 | 两个 PR 都是人工审查才发现 | PR 审查第一步必看完整 `--stat`，出现无关文件直接打回 |

### 处置记录

- !2138：已合并后发现，wiki 元数据回退随合入落入 main（影响仅为 `health_checked` 日期，危害低）。
- !2141：合并前由 kimi 执行 rebase + 补单测后 `--force-with-lease` 推送（`1c9ab2799`），diff 收敛到本任务 11 个文件后正常合并。

### 相关文档

- [AGENTS.md](../../AGENTS.md) — "早操SOP"（`git fetch origin && git rebase origin/main`）
- 第 66 节 — 删除 bootstrap 锚点分支的同类多 Agent 协作事故

## 73. Review PR 必须看 commit vs parent 的实际 diff，Gitee 显示的 PR diff 会因 base 过期产生"虚假 diff"（PR !2146 / 2026-07-20）

> 来源：2026-07-20 review PR !2146（CO-591 列宽调整）时误判"夹带改动 + 违反 AGENTS.md 底线 #3"
> 涉及模块：Code Review 流程 / Gitee PR diff 计算 / Git rebase 修复

### 问题背景

审查 PR !2146 时，Gitee `get_diff_files` API 返回的 diff 中除了标题声明的 `src/views/Project/List.vue` 外，还包含 `scripts/agent-start-task.sh` 改动（删除了 main 上 5 天前刚加的"不可删除 worktree"保护注释，新增了 `git worktree remove --force` 死代码块）。

我据此发布了 CRITICAL review，判断为"夹带改动 + 违反底线 #3 + 撤销 main 修复"，并直接代作者执行了"恢复 agent-start-task.sh + amend commit + force push"的修复操作。

### 根因

本地 git 验证后发现原始判断完全错误：

```bash
git diff 08457b9..c41c3f897 --stat
# 只输出：src/views/Project/List.vue | 4 ++--
# 1 file changed, 2 insertions(+), 2 deletions(-)
```

原始 commit `c41c3f897` vs 其 parent `08457b9` 的 diff **只有 List.vue 一个文件**，并没有改 `agent-start-task.sh`。

**Gitee 显示 agent-start-task.sh 改动的原因**：
- PR 分支基于 `08457b9`（旧 main，包含死代码块）
- PR 创建后，main 通过 `!2145` 合并了修复 commit `e6820d0e6`（删除死代码，加保护注释）
- PR 分支没 rebase，相对最新 main "缺少"了修复
- Gitee PR diff 用 `merge-base` 计算，把"PR 没跟上 main 的修复"显示成"PR 撤销了 main 的修复"
- **这是 PR base 过期问题，不是夹带改动**

### 我做错了什么

1. **误判为夹带改动**：没看 `git diff <commit>~1 <commit>` 就直接相信 Gitee 显示的 PR diff
2. **第一次修复方法错误**：用 `git checkout origin/main -- scripts/agent-start-task.sh` + `git commit --amend`，反而让 PR 分支相对 parent 多了 agent-start-task.sh 改动（因为把 main 的修复"塞"进了 PR commit）
3. **推送后才发现错误**：用 `compare_branches_tags` 验证发现 PR 仍显示 agent-start-task.sh diff，才意识到问题

### 正确的识别方法

判断 PR 是否夹带改动，应看 **commit vs parent** 的 diff，而不是 Gitee 显示的 PR diff：

```bash
# 拿到 PR head 的 commit SHA
PR_HEAD_SHA=c41c3f897   # 从 Gitee API 获取

# 看 commit vs parent 的实际改动（这是作者真正做的改动）
git diff ${PR_HEAD_SHA}~1..${PR_HEAD_SHA} --stat

# 看 PR 分支 vs 最新 main 的 diff（Gitee 显示的，可能含 base 过期导致的虚假 diff）
git diff origin/main..${PR_HEAD_SHA} --stat

# 两者对比：如果前者干净、后者有无关文件，说明是 base 过期；如果前者也有无关文件，才是夹带改动
```

### 正确的修复方法

修复 PR base 过期应优先 `git rebase`，不要手动改文件：

```bash
# 错误方法（我第一次做的）：
git checkout origin/main -- scripts/agent-start-task.sh
git commit --amend --no-edit
# 后果：把 main 的修复塞进了 PR commit，PR diff 仍显示 agent-start-task.sh 改动

# 正确方法：
git reset --hard ${PR_HEAD_SHA}    # 回到原始 commit
git rebase origin/main              # rebase 到最新 main
# rebase 会自动让 PR 分支与最新 main 对齐，不会引入多余改动
git push --force-with-lease origin <branch>
```

### 危害

- **对作者的伤害**：误判为"夹带改动 + 违反底线"，给作者造成不必要的困扰和声誉影响
- **对 PR 的伤害**：第一次修复反而让 PR 多了无关改动，需要二次修复
- **对 review 信誉的伤害**：CRITICAL 评价错判，影响后续 review 的可信度

### 防御规范

| 问题 | 教训 | 规范 |
|---|---|---|
| Gitee PR diff 显示无关文件改动 | merge-base 计算 会把"PR 没跟上 main"显示成"PR 撤销了 main" | **review 时必须看 `git diff <commit>~1 <commit> --stat`**，不能只看 Gitee 显示的 PR diff |
| 误判为夹带改动 | 没有验证 commit vs parent 就下结论 | 判断夹带改动前必须本地 fetch PR 分支并跑 `git diff <commit>~1 <commit>` |
| 修复方法错误 | 用 `git checkout + amend` 把 main 修复塞进 PR commit | 修复 PR base 过期应优先 `git rebase origin/main`，不要手动改文件 |
| force push 前未验证 | 推送后才发现 PR 仍显示无关改动 | **force push 前必须本地验证 `git diff origin/main..HEAD --stat`** 只含本任务文件 |

### 处置记录

- 在 PR !2146 发布了 2 条评论：
  - #50565543 — 原始（错误的）CRITICAL Code Review
  - #50565588 — 纠正 + 修复说明 + 教训沉淀
- PR head 从 `c41c3f897` → `b57d3a441`（错误修复）→ `82b2c0da3`（rebase 正确修复）
- 最终 PR diff 只剩 `src/views/Project/List.vue` 一处改动（+2/-2），14 道门禁全绿

### 相关文档

- [AGENTS.md](../../AGENTS.md) — "早操SOP"（`git fetch origin && git rebase origin/main`）
- 第 72 节 — 分支基线过期导致 PR diff 静默回退/删除他人文件（作者方视角）
- 第 66 节 — 删除 bootstrap 锚点分支的同类多 Agent 协作事故

## 74. OBS Bucket CORS 白名单漏配测试环境前端域名，302 重定向到 OBS 后 preflight 失败（2026-07-20 / winbid-test 下载 .docx 失败）

> 来源：2026-07-20 测试环境 `https://winbid-test.ehsy.com` 下载项目文档报 CORS 错误
> 涉及模块：OBS 直传下载 / 302 重定向跨域 / 华为云 OBS 控制台 CORS 配置
> 根因层级：第 5 层（部署清单缺失约束，导致 OBS 控制台配置漂移）

### 问题背景

测试环境用户在 `https://winbid-test.ehsy.com` 项目文档页点击下载，浏览器控制台报：

```
Access to XMLHttpRequest at 'https://widbid-obs.ehsy.com/bids/2026/07/.../xxx.docx?AWSAccessKeyId=...&Expires=...&Signature=...'
(redirected from 'https://winbid-test.ehsy.com/api/projects/216/documents/960/download')
from origin 'https://winbid-test.ehsy.com' has been blocked by CORS policy:
Response to preflight request doesn't pass access control check:
No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

### 5 Whys 根因分析

| 层级 | 问题 | 答案 |
|---|---|---|
| 1 | 为什么 CORS 失败？ | 浏览器从 `winbid-test.ehsy.com` 调后端 `/download` → 后端返回 302 重定向到 `widbid-obs.ehsy.com` → 浏览器对 OBS 域名的 preflight 请求未返回 `Access-Control-Allow-Origin` |
| 2 | 为什么 OBS 没返回 CORS 头？ | OBS 桶 CORS 白名单未包含 `https://winbid-test.ehsy.com`。设计文档 §8.3 示例只列了 `https://bid.xiyu.com` |
| 3 | 为什么测试环境前端域名没加？ | 部署测试环境时只配了 `XIYU_OBS_DOWNLOAD_CUSTOM_DOMAIN=widbid-obs.ehsy.com`（OBS 自定义域名），但漏了在华为云 OBS 控制台同步更新桶 CORS |
| 4 | 为什么会漏？ | OBS CORS 配置在华为云控制台（不在代码仓库），无部署清单约束，且设计文档示例只给了一个生产域名 |
| 5 | **根因** | **OBS 桶 CORS 配置缺失测试环境前端域名 + 缺少"环境新增前端域名 → 同步更新 OBS CORS"的部署约束** |

### 关键证据

- 后端 302 重定向逻辑：[ProjectDocumentController.java:54-78](../../backend/src/main/java/com/xiyu/bid/projectworkflow/controller/ProjectDocumentController.java#L54-L78)（OBS 直传文件返回 302 + `Location` 头）
- OBS 自定义域名（CNAME 模式）：[HuaweiObsDownloadUrlGateway.java:43-67](../../backend/src/main/java/com/xiyu/bid/file/infrastructure/obs/HuaweiObsDownloadUrlGateway.java#L43-L67)
- 前端用 axios 下载（触发 preflight）：[src/utils/download.js:44-77](../../src/utils/download.js#L44-L77)
- 后端 Spring CORS 已包含 `winbid-test.ehsy.com`：[SecurityConfig.java:80](../../backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java#L80)（但对 OBS 域名无效，OBS CORS 是独立配置）
- 设计文档原 CORS 示例只含 `bid.xiyu.com`：[docs/plans/2026-07-07-huawei-obs-large-file-upload-design.md §8.3](../plans/2026-07-07-huawei-obs-large-file-upload-design.md#L637)

### 危害

- 测试环境用户无法下载 OBS 直传上传的文件（投标文档、附件等），影响日常使用
- 同样的根因可能在生产环境复现：若未来新增前端域名（如 `winbid.ehsy.com` 已加入但未来再增加其他域名），仍会踩同样的坑
- 错误表象是"前端 axios 报 CORS"，容易让人误以为是后端 SecurityConfig 漏配，浪费时间在错误方向排查

### 防御规范

| 问题 | 教训 | 规范 |
|---|---|---|
| OBS 桶 CORS 漏配前端域名 | OBS CORS 是 Bucket 级别配置，与后端 SecurityConfig CORS 无关 | **新增前端域名时必须同步更新 OBS 桶 CORS**，参见设计文档 §8.3 完整清单 |
| 设计文档示例只给一个域名 | 容易让人误以为只需配生产域名 | 设计文档示例已更新为包含生产 + 测试 + 本地所有域名（PR !2152） |
| OBS CORS 配置在控制台不在仓库 | 容易遗漏且无 review | 部署清单已加入"OBS CORS 配置"检查项（设计文档 §9.3 第 3 条），新增环境部署时必须逐项核对 |
| 误判为后端 CORS 问题 | 后端 SecurityConfig 已配 `winbid-test.ehsy.com`，但 OBS 域名是独立 CORS | 排查 CORS 错误时先看请求实际重定向到哪个域名，再查该域名的 CORS 配置（OBS 控制台 vs 后端 SecurityConfig） |
| ExposeHeader 漏配 Content-Disposition | 即使 CORS 通过，前端也无法读取文件名（CO-285 教训重演） | OBS CORS `ExposeHeader` 必须包含 `Content-Disposition`、`Content-Length`、`ETag`、`x-obs-request-id` |

### 修复方案（双管齐下）

1. **运维操作（华为云 OBS 控制台）**：给 widbid-obs 桶添加 CORS 规则，`AllowedOrigin` 包含 `https://winbid-test.ehsy.com`、`https://winbid.ehsy.com`、`https://bid.xiyu.com` 等所有环境前端域名
2. **代码/文档侧（本 PR）**：
   - 补全设计文档 §8.3 CORS 示例（加入所有环境前端域名 + `Content-Disposition`/`Content-Length` expose header）
   - 设计文档 §9.3 第 3 条改为详细部署清单，明确"绑定自定义域名后也必须配 CORS"
   - 在本节追加踩坑案例

### 处置记录

- 设计文档 §8.3、§9.3 已更新（PR !2152）
- OBS 控制台 CORS 配置：待运维同步添加 `winbid-test.ehsy.com` 到 widbid-obs 桶
- 验证方法：配置完成后，在 `https://winbid-test.ehsy.com` 项目文档页下载 `.docx` 文件，浏览器 Network 面板观察 `widbid-obs.ehsy.com` 请求的 Response Headers 应包含 `Access-Control-Allow-Origin: https://winbid-test.ehsy.com`

### 相关文档

- [docs/plans/2026-07-07-huawei-obs-large-file-upload-design.md §8.3](../plans/2026-07-07-huawei-obs-large-file-upload-design.md) — OBS CORS 完整配置示例
- [docs/plans/2026-07-07-huawei-obs-large-file-upload-design.md §9.3](../plans/2026-07-07-huawei-obs-large-file-upload-design.md) — 华为云侧准备清单
- 第 56 节 — OBS 直传三层保护：构建变量缺失导致 isObsEnabled=false
- CO-285 第 32 节 — 附件下载文件名 + CORS 教训（`<a>` 标签 vs fetch+blob）

---

## 75. CO-333 重名跳过 id 绑定 + 显示层缺工号：v3.10 + PR !2154 双层根因治理（2026-07-20 / CC2026072071）

### 事故背景

**CC2026072071**：用户王亮（user_id=75, 工号 05972, role=bid-projectLeader）在 CRM 侧维护的标讯（tender_id:59）推送后，在项目详情页/列表/保证金看板中"项目负责人"字段只显示"王亮"（纯姓名），不带工号。用户偏好："系统中选人/显示人的地方都要带工号"。

### 5 Whys 根因分析

```
Why 1: 为什么显示纯姓名？→ 前端直接渲染 project.projectLeaderName
Why 2: projectLeaderName 哪来的？→ 后端 ProjectListEnrichmentSupport 用 tender.project_manager_name 兜底填充
Why 3: 为什么 tender.project_manager_name 是纯姓名？→ CRM 推送过来就是纯姓名（无工号字段）
Why 4: 为什么后端不通过 project_manager_id 反查 users.employee_number 拼接？→ DTO 没有工号字段，enrichment 没有反查逻辑
Why 5: 为什么 project_manager_id 能正确绑定？→ 之前 CO-333 重名跳过绑定，v3.10 工号优先策略后才正确绑定
```

### 双层根因

**数据层根因（CO-333）**：CRM 推送纯姓名，`ProjectManagerIdResolver.resolveByFullName` 发现重名（user 75 + user 1816 都叫"王亮"）→ 按规则跳过 id 绑定 → `tender.project_manager_id=NULL`

**显示层根因（本 PR !2154 修复）**：后端 DTO `projectLeaderName` 直接透传 CRM 纯姓名，未通过 `projectLeaderId` 反查 `users.employee_number` 拼接工号

### 双层治理方案

**数据层治理（v3.10 / PR !2153）**：
- `TenderPushRequest` / `TenderUpdateRequest` 新增 `projectManagerEmployeeId` 字段
- `ProjectManagerIdResolver` 新增 `resolveByEmployeeNumber` 方法 + `applyTo` 公共入口
- 四级解析策略：工号优先 → 姓名作校验 → username 回落 → fullName 兜底
- 工号命中后姓名不符仅 warn 不阻断（与产品方案一致）
- 停用用户跳过 id 绑定（避免绑定离职员工）

**显示层治理（本 PR !2154）**：
- `ProjectDTO` / `MarginDTO` 新增 `projectLeaderEmployeeNumber` 字段
- `ProjectQueryService` 批量预加载 userMap 覆盖三条 projectLeaderId 来源（pid.ownerUserId / tender.projectManagerId / project.managerId 兜底）
- `ProjectListEnrichmentSupport` 新增纯核心 `populateLeaderEmployeeNumber(dto, userMap)` 方法
- `MarginQuerySupport` / `MarginDerivedTableColumns` 补 `project_leader_emp_no` 列（C_PROJ_LEAD_EMP_NO=16）
- 前端新增 `src/utils/userDisplay.js` 纯函数 `formatUserWithNameAndNumber`
- `ProjectBasicInfoCard.vue` / `List.vue` / `MarginManagement.vue` 三处接入"姓名 (工号)"显示

### 协同关系

| 维度 | v3.10 (!2153) | 显示层修复 (!2154) |
|------|---------------|---------------------|
| 层级 | 数据接入层 | 显示层 |
| 解决根因 | CRM 推送时正确绑定 `project_manager_id` | 后端 DTO 补工号字段 + 前端展示"姓名 (工号)" |
| 互补性 | 数据层根因 | 显示层根因 |
| 冲突 | 无 | 无 |

**完整解决路径**：v3.10 确保 `project_manager_id` 正确绑定 → 本 PR 通过该 id 反查 `users.employee_number` → 前端拼接显示"姓名 (工号)"

### 受影响字段（grep 全局收敛）

后端 5 处类似兜底逻辑同步治理：
- `ProjectListEnrichmentSupport.java:79`（projectLeaderName ← tender.projectManagerName）
- `ProjectTenderPopulator.java:38`（同上）
- `ProjectQueryService.java:197`（projectLeaderName ← pid.projectLeaderName）
- `MarginQuerySupport.java:167`（margin 表 project_leader_name）
- `ProjectTransferService.java:135`（转移时设置 projectLeaderName）

### 已知技术债

1. **历史数据订正**：v3.10 之前推送的 `project_manager_id=NULL` 数据需要订正脚本（spec 037 Phase 5 T025 追加任务）
2. **投标负责人/辅助人员工号显示**：后端 ProjectDTO 暂未支持 `biddingLeaderEmployeeNumber` / `secondaryBiddingLeaderEmployeeNumber` 字段，已在 `ProjectBasicInfoCard.vue L40` 加 TODO 注释标记

### 教训

1. **追症状不追根因**：排查 1 小时未找到"王亮看不到项目"根因（自愈后无法复现），但发现"项目负责人字段不带工号"是独立的显示层根因，最终通过双层治理完整解决
2. **跨 PR 协同**：v3.10（数据层）+ 本 PR（显示层）是互补关系，必须在 PR 描述中明确标注协同关系，避免 reviewer 误判为"重复修复"
3. **批量预加载避免 N+1**：enrichment 阶段一次性 `userRepository.findAllById(userIds)` 查 5 类 user ID（lead/manager/reviewer/tender/pid），避免 N+1 查询
4. **派生表列对齐**：`MarginDerivedTableColumns` 的 `DERIVED_SELECT_FEES` / `DERIVED_SELECT_INIT` 两分支必须同步新增列，否则 UNION ALL 列数不匹配
5. **向后兼容设计**：新增 DTO 字段（可选，无默认值约束）对旧前端/旧后端/Excel 导出均透明，前后端无顺序依赖

### 相关文档

- [specs/037-crm-link-compensation/](../../specs/037-crm-link-compensation/) — CRM 商机关联补偿规范（Phase 5 T025 历史数据订正任务）
- PR !2153 — v3.10 标讯接口新增项目负责人工号字段（数据层根因治理）
- PR !2154 — CC2026072071 项目负责人显示补工号 (姓名 (工号)) 格式（显示层根因治理）
- 第 65 节 — PR #1162 webhook 回调缺工号（类似的"姓名 (工号)"格式问题）

---

## 76. 清除 @Deprecated 编译警告时禁止改字段名，只能加 @SuppressWarnings（PR !564 回归 / 2026-07-20）

> 来源：2026-07-20 测试环境 https://winbid-test.ehsy.com/ 项目 ID=218 "客户营收"字段值丢失
> 涉及模块：ProjectQueryService / InitiationStage / 字段映射 / 单位语义

### 问题背景

测试环境反馈两个症状：

1. 项目详情页"客户营收（亿）"输入框值丢失（显示 0）
2. 项目列表"客户营收（亿）"列实际展示的是"电商MRO+办公流水金额（万）"字段值

### 根因

PR !564（commit `d1994a3fa`，2026-06-14，标题"fix: 清除 2401 源文件编译警告"）在清除 `@Deprecated` 警告时**把字段名也一起换掉了**：

```diff
-                if (dto.getRevenue() == null
-                        && det.getAnnualRevenue() != null) {
-                    dto.setRevenue(det.getAnnualRevenue());
+                if (dto.getRevenue() == null
+                        && det.getAnnualEcommerceAmount() != null) {
+                    dto.setRevenue(det.getAnnualEcommerceAmount());
```

commit message 写的是"ProjectQueryService 的 getAnnualRevenue() 调用点同样标注"——本意是加 `@SuppressWarnings("deprecation")`，但实际把 `getAnnualRevenue()`（客户营收，@Deprecated）替换成了 `getAnnualEcommerceAmount()`（电商MRO+办公流水金额，完全不同的字段）。

后果：
- 立项表 `annual_ecommerce_amount` 通常有值（MRO 流水是必填）
- `dto.revenue != null`，导致 247-250 行的 fallback `eval.basic.customerRevenue` **永远不会执行**
- 列表 [List.vue:104](../../src/views/Project/List.vue#L104) 渲染 `row.revenue` → 显示 MRO 值

详情页丢失问题（症状 1）是 pre-existing 的前后端字段名不一致：
- 后端 [InitiationViewDto](../../backend/src/main/java/com/xiyu/bid/project/dto/InitiationViewDto.java) 只有 `annualRevenue`（@Deprecated），无 `customerRevenue`
- 前端 [InitiationStage.vue:46](../../src/views/Project/stages/InitiationStage.vue#L46) 表单绑定 `form.customerRevenue`
- [useInitiationStageActions.js](../../src/views/Project/stages/useInitiationStageActions.js) `load()` 直接 `Object.assign(form, data)`，无字段映射

### 危害

- 测试环境项目列表客户营收列数据失真（显示 MRO 流水金额）
- 详情页客户营收输入框永远显示 0，用户编辑后保存路径正常（保存侧有映射），但加载侧丢失
- 持续时间：2026-06-14 上线至 2026-07-20 修复，约 36 天

### 防御规范

**清除 @Deprecated 警告时禁止改字段名，只能加 @SuppressWarnings("deprecation")。**

具体规则：
1. `@Deprecated` 字段调用点的警告清除，**唯一允许的操作**是在方法/语句上加 `@SuppressWarnings("deprecation")` 注解
2. **禁止**把 `getXxx()` 调用替换成同实体的其他 getter（即使新 getter 类型相同）
3. **禁止**借"清除警告"之名重命名字段或调整调用点语义
4. PR 标题/commit message 必须明确区分"加注解"与"改字段名"——前者是机械操作，后者是语义变更
5. `@Deprecated` 字段是否真正废弃需要看 migration 路径，不能只看注解就动手替换
6. 修改字段映射（尤其涉及 BigDecimal 金额字段）时，必须同时补回归测试覆盖三条路径：
   - 旧字段有值 → dto 正确填充
   - 新字段有值（fallback 路径）→ dto 正确填充
   - 语义相近但不同的字段有值 → dto 不被污染

### 处置记录

- 定位罪魁 PR：`git log --all --oneline -S 'det.getAnnualEcommerceAmount()' -- backend/.../ProjectQueryService.java` → `d1994a3fa` → 反查 PR 号 `!564`
- 修复 commit（待提交）：
  - 后端：[ProjectQueryService.java](../../backend/src/main/java/com/xiyu/bid/project/service/ProjectQueryService.java) 改回 `getAnnualRevenue()` + `@SuppressWarnings("deprecation")`
  - 后端：[ProjectQueryServiceTest.java](../../backend/src/test/java/com/xiyu/bid/project/service/ProjectQueryServiceTest.java) 新增 3 条回归测试（9/9 通过）
  - 前端：[useInitiationStageActions.js](../../src/views/Project/stages/useInitiationStageActions.js) `load()` 补 `annualRevenue → customerRevenue` 映射
  - 前端：[useInitiationStageActions.spec.js](../../src/views/Project/stages/useInitiationStageActions.spec.js) 新增 3 条加载测试（8/8 通过）
  - 文档：[ProjectDTO.java](../../backend/src/main/java/com/xiyu/bid/project/dto/ProjectDTO.java)、[TenderEvaluationBasic.java](../../backend/src/main/java/com/xiyu/bid/tender/entity/TenderEvaluationBasic.java)、[EvaluationBasicDTO.java](../../backend/src/main/java/com/xiyu/bid/tender/dto/EvaluationBasicDTO.java) 注释单位统一为"亿"
  - 迁移：V1172 修改 `customer_revenue` 列 COMMENT 为"客户营收（亿）"，消除代码-数据库-前端三层不一致

### 相关文档

- [ARCHITECTURE.md](../../ARCHITECTURE.md) — FP-Java 字段映射规范
- [RELIABILITY.md](../../RELIABILITY.md) — 14 道门禁（本 PR 因 ProjectQueryServiceTest 未覆盖 revenue 字段赋值，CI 未拦住）
- 第 73 节 — Review PR 必须看 commit vs parent 的实际 diff（本次定位 d1994a3fa 时也用了 `git log -S` 字符串追踪）

## 77. 写迁移 SQL 前必须 DESC 确认列名，MySQL 保留字 `size` 不能作为列引用（V1173 / 2026-07-20 / 第 103 次测试部署阻断）

> 来源：2026-07-20 第 103 次测试环境部署首次尝试失败，V1173 迁移 SQL 报 `Unknown column 'pd.size'`
> 涉及模块：Flyway 迁移 / `project_documents` 表 / `archive_file.file_size` 回填
> 影响等级：高（backend crash-loop，测试环境服务不可用约 30 分钟）

### 事故背景

第 102 次测试环境部署成功后，合入后续 PR 时 V1173 迁移失败导致 backend 反复重启：

```
ERROR 1054 (42S22): Unknown column 'pd.size' in 'where clause'
```

V1173 SQL 试图从 `project_documents` 表的 `size` 列读取文件大小（VARCHAR 如 "1.5MB"），解析为字节数后回填到 `archive_file.file_size`。但 `project_documents` 表实际列名是 `file_size`，`size` 是 MySQL 保留字且表中无此列。

### 根因

V1173 SQL 撰写时**没有先 DESC 表结构确认列名**，凭直觉用了 `size`：

```sql
-- 错误写法（V1173 原版）
UPDATE archive_file af
INNER JOIN project_documents pd ON pd.file_url = af.file_path
SET af.file_size = ... CASE WHEN pd.size REGEXP ...
WHERE af.file_size = 0 AND pd.size IS NOT NULL;
```

两个问题叠加：
1. **列名猜错**：实际列名是 `file_size`（VARCHAR），不是 `size`
2. **MySQL 保留字陷阱**：`size` 是 MySQL 8.0 保留字（https://dev.mysql.com/doc/refman/8.0/en/keywords.html），即使表里有此列也需要反引号 `` `size` ``，否则 SQL 解析器会把它当成关键字

### 危害

- 第 103 次测试部署首次尝试失败，release `1052e4fdb` 已上传但 Flyway 应用 V1173 时报错
- backend 服务 crash-loop（Flyway 启动校验失败 → systemd Restart=on-failure → 反复重启）
- 测试环境服务不可用约 30 分钟（从首次失败到修复后重新部署完成）
- 需要人工介入：手动 stop 服务、删除 flyway_schema_history 失败记录、重新打包部署

### 防御规范

**写迁移 SQL 引用任何列名前，必须先 `DESC <table>` 或 `SHOW COLUMNS FROM <table>` 确认列名存在且拼写正确。**

具体规则：
1. **禁止凭直觉猜列名**：尤其是不常见的列名（如 `file_size` 而非 `size`、`file_url` 而非 `url`）
2. **MySQL 保留字清单**：写 SQL 前对照 https://dev.mysql.com/doc/refman/8.0/en/keywords.html，常见陷阱字：`size`、`desc`、`asc`、`order`、`group`、`key`、`index`、`type`、`status`、`name`、`level`、`range`、`row`、`column`、`condition`、`database`、`table`、`user`、`password`、`access`、`account`、`order`
3. **VARCHAR 字段解析必须 SELECT 验证**：对 VARCHAR 字段存放带单位字符串（如 "1.5MB"）的场景，使用 CASE WHEN + REGEXP 解析时**必须先在测试环境 SELECT 验证**逻辑正确，再写迁移 SQL
4. **迁移 SQL 评审要点**：reviewer 必须检查 SQL 中引用的所有列名是否真实存在（可让提交者在 PR 描述中贴 `DESC` 输出）
5. **不可变迁移门禁逃生舱**：已推送到 origin/main 的迁移文件如需修复，使用 `FLYWAY_ALLOW_IMMUTABLE_EDIT=1` 紧急例外通道（`scripts/check-flyway-immutable.sh` 文档化）

### 处置记录

- 定位根因：`ssh jetty@172.16.38.78 'source backend.env && mysql ... -e "DESC project_documents;"'` → 确认列名是 `file_size` 不是 `size`
- 验证修复逻辑：在服务器上跑 SELECT 测试 CASE WHEN 解析（`1MB` → 1048576, `340MB` → 356515840）
- 修复 commit：`c8fb291b5` on `agent/trae/fix-v1173-migration`（所有 `pd.size` → `pd.file_size`）
- PR !2165：squash merge → `07b34a932`
- 服务器清理：`DELETE FROM flyway_schema_history WHERE version='1173' AND success=0;`
- 重新打包部署：release `07b34a932`，V1173 success=1, 20:19:05 应用成功
- 完整恢复时间：约 30 分钟（从首次失败到重新部署完成）

### 相关文档

- [CLAUDE.md](../../CLAUDE.md) §数据库迁移规范 — 版本号、回滚脚本、不可变门禁
- [RELIABILITY.md](../../RELIABILITY.md) — Flyway 不可变迁移门禁（`scripts/check-flyway-immutable.sh`）
- [第 103 次测试环境部署报告](../release/deploy-report-2026-07-20-103rd-test.md) — 完整恢复时间线
- MySQL 8.0 关键字清单：https://dev.mysql.com/doc/refman/8.0/en/keywords.html

## 78. PR !2178 基于错误根因分析的修复方案必败——投标系统管理员 403 bug 的真正 3 条故障链（2026-07-21 / 覃超颖 bidding/60 报 403 / PR !2178 被驳回）

> 来源：2026-07-21 用户覃超颖（OSS username=09118，userId=112，角色=投标系统管理员 bid-SystemAdmin）访问 https://winbid.ehsy.com/bidding/60 报 "权限不足 无法访问该资源！还有 403"
> 涉及模块：OSS 权限缓存 / UserDetailsServiceImpl / TenderController @PreAuthorize / EffectiveRoleResolver / DataScopeConfigService / TenderProjectAccessGuard / ProjectAccessScopeService
> 影响等级：高（用户无法访问标讯详情页，影响投标推进）
> PR !2178（agent/codex/fix-datascope-oss-role-inconsistency）：**已驳回**（基于错误根因分析，修复方案无效）
> 后续修复 PR：!2179（agent/codex/fix-bid-systemadmin-403-real-root-cause）
> 完整审计报告：[docs/reviews/pr-2178-production-risk-review-2026-07-21.md](../reviews/pr-2178-production-risk-review-2026-07-21.md)

### 事故背景

覃超颖（OSS 同步用户，OSS 端配置角色 `bid-SystemAdmin`）访问标讯详情页报 403。

### 关键认知修正（用户指正）

**OSS 端没有属于我们投标系统的 admin 角色**——admin 是我们系统独有的本地超级管理员账户，与 OSS 无关。

OSS 是多系统共用的角色管理平台（承载 Home/CRM/SCM/投标等多个子系统），返回的 `sysRoleList` 中混合了多系统角色。其中属于我们投标系统的只有 7 个角色码（`/bidAdmin`、`bid-TeamLeader`、`bid-SystemAdmin`、`bid-Team`、`bid-projectLeader`、`bid-administration`、`bid-otherDept`）。

如果 OSS 返回的 `sysRoleList` 中包含 `admin`，那是**其他系统**（如 OSS 平台本身、Home 系统、CRM 系统）的 admin，**不是我们投标系统的 admin**，不应该被识别为我们系统的 admin 写入缓存。

### 真正的根因（第二次修正）

**代码缺陷**：`RoleProfileCatalog.DEFINITIONS` 中注册了 `admin`（`ADMIN_CODE = "admin"`，line 19, 108），导致 `canonicalCode("admin")` 返回 "admin"（line 232-240）。当 OSS 返回的多系统 `sysRoleList` 中包含其他系统的 admin 角色时，`OssRoleResolver.resolveRoleCodeFromJobList`（line 75-100）会错误地把其他系统的 admin 识别为我们系统的 admin，写入 Redis 缓存。

**调用链**：
```
OSS 返回 sysRoleList（含其他系统的 admin + 我们系统的 bid-SystemAdmin）
  → OssRoleResolver.resolveRoleCodeFromJobList 遍历 sysRoleList（按顺序）
  → JobRoleLookupResolver.mapOssRoleCodeToInternal("admin")
  → RoleProfileCatalog.canonicalCode("admin") = "admin"（错误！admin 不应在 OSS 识别路径中）
  → 返回 "admin"（如果 admin 排在 bid-SystemAdmin 前面）
  → OssLoginFlowService.cacheOssPermissions 写入 Redis 缓存 oss:perm:09118 = "admin"
  → UserDetailsServiceImpl 读缓存得到 admin，颁发 ROLE_ADMIN
  → 后续访问绕过 @PreAuthorize + hasAdminAccess 短路 → 越权
```

**正确行为应该是**：OSS 角色解析路径中，`admin` 应该被识别为"其他系统的角色"被跳过，继续找下一个 bid-* 角色码（如 bid-SystemAdmin）。

### PR !2178 的错误根因分析（已被审计推翻）

PR !2178 假设的根因：
- `UserDetailsServiceImpl` 用 OSS 缓存 `admin` 颁发 `ROLE_ADMIN`，通过 `@PreAuthorize`
- `TenderProjectAccessGuard.checkAccess` 抛出 "权限不足，无法访问该标讯"（`DataScopeConfigService.getAccessProfile` 读 DB roleProfile 返回 bid-otherDept/self）
- 两套数据源不一致 → "通过 @PreAuthorize 但被拦截"

**错误点**：
1. `TenderProjectAccessGuard` 没有 `checkAccess` 方法，正确方法是 `assertCanAccessTender`
2. 如果 tender 关联了 project，会走 `assertCurrentUserCanAccessProject` → `hasAdminAccess` 短路，**不会调用 `getAccessProfile`**
3. 只有 tender **未关联 project** 时才走 `resolveDataScope` → `getAccessProfile`
4. 修复前后 `dataScope` 都是 `self`，行为完全一致，403 未消灭

### 真正的 3 条故障链（审计推演）

#### 故障链 1（最致命）：覃超颖 OSS 配置正确为 bid-SystemAdmin 时

```
覃超颖重新登录 → OSS 端正确下发 roleCode=bid-SystemAdmin（清缓存后预期场景）
  → UserDetailsServiceImpl.ossAuthorities 颁发 authority
    → LoginRoleWhitelist.isAllowed("bid-SystemAdmin") = true
    → applyRoleCodeAuthorities(authorities, "bid-SystemAdmin", skipLegacyCompat=true)
    → authorities = {"bid-SystemAdmin", "ROLE_BID_SYSTEMADMIN"}
  → @PreAuthorize hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM')
    → ROLE_BID_SYSTEMADMIN 不在列表
    → 拒绝 → 403
```

**证据**：`TenderController.java:86` `@PreAuthorize` 列表不含 `BID_SYSTEMADMIN`

**结论**：即使 OSS 端配置正确，覃超颖登录后仍会被 `@PreAuthorize` 在 Controller 入口直接拒绝。这是真正的根因之一。

#### 故障链 2：覃超颖 OSS 缓存 admin + tender 60 已关联 project

```
覃超颖登录 → OSS 缓存 admin → 颁发 ROLE_ADMIN
  → @PreAuthorize 通过（ADMIN 在列表）
  → TenderProjectAccessGuard.assertCanAccessTender
    → linkedProjects(tender) nonEmpty
    → assertCurrentUserCanAccessProject(projectId)
      → hasAdminAccess(authentication) = true（因为 ROLE_ADMIN）
      → return; (短路)
  → 通过 → 覃超颖能访问（绕过 dataScope 检查）
```

**证据**：`ProjectAccessScopeService.java:184-186` `if (hasAdminAccess(authentication)) { return; }`，hasAdminAccess 不检查 user.isOssUser()

**结论**：PR !2178 完全不触及此路径。OSS admin 用户绕过 dataScope 越权访问。

#### 故障链 3：覃超颖 OSS 缓存 admin + tender 60 未关联 project

```
覃超颖登录 → OSS 缓存 admin → 颁发 ROLE_ADMIN
  → @PreAuthorize 通过
  → TenderProjectAccessGuard.assertCanAccessTender
    → linkedProjects(tender) empty
    → resolveDataScope(user)
      → dataScopeConfigService.getAccessProfile(user)
        → 修复前：读 DB roleProfile (bid-otherDept) → dataScope=self
        → 修复后：走 EffectiveRoleResolver → OSS admin → fail-closed null → roleRule=self → dataScope=self
      → 两者返回 dataScope=self（一致）
    → "all" != "self" → isSelfVisibleTender → false → throw AccessDeniedException
  → 403
```

**证据**：`TenderProjectAccessGuard.java:47-55` 未关联项目走 dataScope 分支；`RoleProfileAccessRuleResolver.java:51-55` roleCode=null 返回 self 兜底

**结论**：PR !2178 完全不能消灭此 403。修复前后 `dataScope` 都是 `self`，`isSelfVisibleTender` 检查结果一致，都抛 403。

### 真正的根因（P0，第二次修正）

1. **`RoleProfileCatalog` 未区分本地角色与 OSS 角色**（核心根因）
   - `DEFINITIONS` 中注册了 `admin`（`ADMIN_CODE = "admin"`，line 19, 108），`canonicalCode("admin")` 返回 "admin"（line 232-240）
   - OSS 返回的多系统 `sysRoleList` 中包含其他系统的 admin 时，被错误地识别为我们系统的 admin
   - **admin 是本地独有的超级管理员，与 OSS 无关；OSS 角色解析路径不应识别 admin**
2. **TenderController @PreAuthorize 列表缺 BID_SYSTEMADMIN**（故障链 1）
   - 所有 `@PreAuthorize hasAnyRole` 列表不含 `BID_SYSTEMADMIN`，导致 bid-SystemAdmin 角色用户被 Spring Security 在 Controller 入口直接拒绝
3. **UserDetailsServiceImpl 不走 EffectiveRoleResolver**（故障链 2/3）
   - 直接读 OSS 缓存颁发 `ROLE_ADMIN`，绕过 EffectiveRolePolicy 的 fail-closed 拦截
4. **hasAdminAccess 不检查 user.isOssUser()**（故障链 2）
   - OSS 缓存 admin 的用户绕过 dataScope 检查越权访问

### 真正的修复方案（按优先级，第二次修正）

1. **P0 代码（核心）**：`RoleProfileCatalog` 区分"本地角色"与"OSS 角色"——新增 `OSS_ELIGIBLE_CODES` 集合（7 个 bid-* 角色码，不含 admin），`canonicalCode` 在 OSS 解析路径中只识别这 7 个角色码。或新增 `canonicalOssCode(String)` 方法，对 admin 返回 null。
2. **P0 代码**：`TenderController` 所有 `@PreAuthorize hasAnyRole` 列表加入 `BID_SYSTEMADMIN`（与 `RoleProfileCatalog.GLOBAL_ACCESS_ROLES` 对齐）
3. **P0 代码**：改造 `UserDetailsServiceImpl` 走 `EffectiveRoleResolver`，或在颁发 `ROLE_ADMIN` 前调用 `EffectiveRolePolicy.decide` 校验，统一数据源
4. **P1 代码**：改造 `ProjectAccessScopeService.hasAdminAccess` 增加 `!user.isOssUser()` 条件，防止 OSS admin 越权

**无需运营操作**：覃超颖在 OSS 端的配置本来就是正确的 `bid-SystemAdmin`，不需要修改 OSS 端配置。问题是我们的代码错误地把其他系统的 admin 识别为我们系统的 admin。

### PR !2178 的有益部分（可保留）

虽然 PR !2178 不能消灭 403 bug，但以下改动本身是合理的防御性加固，可在新 PR 中保留：
- `EffectiveRolePolicy` 添加 OSS admin 拦截（纯核心层 fail-closed）—— 防御性兜底
- `EffectiveRoleResolver` 添加 `OSS_ADMIN_REJECTED` warn 日志 —— 可观测性
- `DataScopeConfigService.getAccessProfile` 改走 `EffectiveRoleResolver` —— 统一数据源（但不足以消灭 bug）
- 拆分 `RoleProfileAccessRuleResolver` —— 解决 300 行预算

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 基于错误根因分析的修复方案必败 | PR !2178 假设根因是 "DataScopeConfigService 读 DB roleProfile"，实际根因是 "RoleProfileCatalog 未区分本地角色与 OSS 角色，导致 OSS 返回的其他系统 admin 被错误识别" + "TenderController @PreAuthorize 列表缺 BID_SYSTEMADMIN" | 修复前必须画完整调用链，定位真正的拦截点；不能凭日志猜测 |
| OSS 角色识别未排除本地独有角色 | `RoleProfileCatalog.DEFINITIONS` 注册了 admin，`canonicalCode("admin")` 返回 "admin"，导致 OSS sysRoleList 中其他系统的 admin 被错误识别为我们系统的 admin | OSS 角色解析路径必须排除本地独有角色（admin）；`RoleProfileCatalog` 应区分"本地角色"与"OSS 角色"两个集合 |
| OSS 是多系统共用平台被忽视 | OSS 返回的 sysRoleList 混合了多系统角色（OSS/Home/CRM/投标），不是所有角色都属于我们系统 | OSS 角色解析必须通过系统标识或映射表过滤出属于本系统的 7 个 bid-* 角色码，其他角色（包括其他系统的 admin）一律跳过 |
| 修复方案未覆盖所有故障链 | PR !2178 只覆盖故障链 3（且无效），未覆盖故障链 1 和 2 | 修复前必须推演所有可能的故障链，每条给出代码证据；修复后必须验证所有故障链都被消灭 |
| 测试覆盖不足以证明 bug 消灭 | EffectiveRolePolicyTest +4 case 只覆盖纯核心决策，未覆盖端到端业务路径 | 关键 bug 修复必须补端到端测试（覃超颖登录 → 访问 /bidding/60 → 200） |
| 未审计 PR 是否真的消灭 bug 就合入 | PR !2178 CI 全绿就提交，未做 Production Risk Review | 上线后 PR 必须过 Production Risk Review v2.0（9 阶段审计），证明"不存在足以导致线上回归的风险" |
| `@PreAuthorize` 列表与 `RoleProfileCatalog` 不同步 | `TenderController` 的 `hasAnyRole` 列表不含 `BID_SYSTEMADMIN`，与 `RoleProfileCatalog.GLOBAL_ACCESS_ROLES` 不一致 | 新增 RoleProfile 时必须同步检查所有 `@PreAuthorize hasAnyRole` 列表；建议用 `RoleProfileCatalog.GLOBAL_ACCESS_ROLES` 常量替代硬编码列表 |
| `hasAdminAccess` 短路不检查 isOssUser | OSS 缓存 admin 的用户绕过 dataScope 越权 | `hasAdminAccess` 必须增加 `!user.isOssUser()` 条件，OSS 用户永远不走 admin 短路 |

### 操作规范

1. **PR 修复 bug 前必须先画完整调用链**：从 HTTP 入口 → Filter → Controller → Service → Guard → Repository，每一步标注可能的拦截点
2. **必须推演所有故障链**：覆盖 OSS 配置正确/错误、tender 关联/未关联 project、admin/非 admin 角色等多场景
3. **必须补端到端测试**：覆盖覃超颖登录 → 访问 /bidding/60 → 200/403 的完整路径，不能只测纯核心
4. **上线后 PR 必须过 Production Risk Review v2.0**：[docs/reviews/pr-2178-production-risk-review-2026-07-21.md](../reviews/pr-2178-production-risk-review-2026-07-21.md)
5. **`@PreAuthorize` 列表必须与 `RoleProfileCatalog` 同步**：新增 RoleProfile 时同步检查所有 `@PreAuthorize hasAnyRole` 列表
6. **`hasAdminAccess` 必须检查 isOssUser**：OSS 用户永远不走 admin 短路
7. **OSS 角色解析必须排除本地独有角色**：`admin` 是本地独有的超级管理员，OSS 角色解析路径不应识别 admin；OSS 返回的 sysRoleList 中如果包含 admin，那是其他系统的 admin，应该跳过
8. **OSS 是多系统共用平台**：OSS 返回的 sysRoleList 混合了多系统角色，必须通过系统标识或映射表过滤出属于本系统的 7 个 bid-* 角色码

### 处置记录

- 立即修复（已执行）：生产 Redis `DEL oss:perm:09118` 清缓存（返回 1）—— 临时解决，重新登录后如果 OSS sysRoleList 仍含其他系统 admin 会复发
- PR !2178 审计（已执行）：Production Risk Review v2.0 9 阶段审计，裁决 FAIL，禁止上线
- PR !2178 驳回（已执行）：保留分支作为参考，但不合入 main
- 根因第一次修正（已执行）：审计报告认为根因是 "TenderController @PreAuthorize 缺 BID_SYSTEMADMIN" + "UserDetailsServiceImpl 不走 EffectiveRoleResolver"
- **根因第二次修正（本次，用户指正）**：真正的核心根因是 `RoleProfileCatalog` 未区分本地角色与 OSS 角色——OSS 返回的多系统 sysRoleList 中包含其他系统的 admin，被 `canonicalCode("admin")` 错误识别为我们系统的 admin。覃超颖在 OSS 端的配置本来就是正确的 bid-SystemAdmin，无需运营操作修改 OSS 端配置。
- 后续修复 PR !2179（已合并 2026-07-21）：分支 `agent/codex/fix-bid-systemadmin-403-real-root-cause`，按第二次修正的根因实施
- 知识沉淀（2026-07-21）：修正 §78 根因分析（第二次修正），追加完整审计报告到 `docs/reviews/`
- 后续 PR 治理（2026-07-21）：PR !2178 标记 superseded 关闭（被 !2179 覆盖），PR !2176 rebase 后合入 main（详见 §79）

### 相关文档

- [docs/reviews/pr-2178-production-risk-review-2026-07-21.md](../reviews/pr-2178-production-risk-review-2026-07-21.md) — PR !2178 完整审计报告（9 阶段 + 3 故障链 + 证据）
- [CLAUDE.md](../../CLAUDE.md) §角色清单 — OSS 用户永远不映射为 admin 硬约束
- [CLAUDE.md](../../CLAUDE.md) §角色码解析强约束（CO-373 治理）— 统一走 EffectiveRoleResolver
- [ARCHITECTURE.md](../../ARCHITECTURE.md) §Agent Contract — FP-Java 纯核心可单测、不依赖框架
- [SECURITY.md](../../SECURITY.md) — Mock 政策、权限守卫、安全审计
- spec 033 `specs/033-oss-local-permission-path-separation/spec.md` — OSS 与本地用户权限代码路径分离根治方案
- PR !2178 — 错误根因分析的修复方案（已驳回）
- PR !2179 — 真正根因修复方案（已合并 2026-07-21）
- CO-373 — 角色码统一解析入口治理（5 次反复修复的根因）

## 79. E2E 测试修复 5 大踩坑模式——端口 fallback、strict mode violation、权限重定向、selector 过期、路由路径漂移（2026-07-21 / 5 个 spec 修复 / PR !2181）

### 事故背景

延续 PR !2176（E2E stale roles 修复），剩余 5 个 spec 持续失败：`workbench-quick-start`、`project-evaluation-flow`、`form-engine-adaptive-flow`、`knowledge-case-precipitation-flow`、`project-result-confirm-competitor-flow`。初次分析归因为"H13 token missing"被用户指正，实际只有 1.6% 与 H13 相关。本次 PR !2181 完成全部 5 个 spec 修复（22 passed, 10 skipped）。

### 5 大踩坑模式

#### 模式 1：端口 fallback 与 CLAUDE.md 约定不一致（影响全部 spec）

- `e2e/auth-helpers.js` 中 `apiBaseUrl` fallback 为 `18080`，但 CLAUDE.md 约定后端端口为 `18089`
- `playwright.config.js` 中 `baseURL` fallback 为 `1314`，但 CLAUDE.md 约定前端端口为 `1323`
- **根因**：历史多 Agent worktree 时期分配的独立端口（claude=1315/18081, codex=1316/18082, ...）已在 2026-06-21 统一到主工作区后失效，但 fallback 值未同步更新
- **修复**：fallback 值对齐 CLAUDE.md §端口约定（18089 / 1323）
- **防御规范**：端口 fallback 变更必须同步更新 `e2e/auth-helpers.js` + `playwright.config.js`，且与 CLAUDE.md §端口约定保持一致

#### 模式 2：Playwright strict mode violation——子串匹配陷阱

- `project-evaluation-flow.spec.js` 中 `page.locator('.status-chip').filter({ hasText: '评标结果已出' })` 匹配了 2 个元素：
  1. `评标结果已出，待上会`（包含子串"评标结果已出"）
  2. `评标结果已出`（完全匹配）
- Playwright strict mode 下，locator 匹配多个元素时直接抛错，不会自动选择第一个
- **修复**：改用 `page.getByText('评标结果已出', { exact: true })` 精确匹配
- **通用规则**：当选项文本存在包含关系时（如"评标结果已出"是"评标结果已出，待上会"的子串），**禁止**用 `filter({ hasText })`，必须用 `getByText(text, { exact: true })`

#### 模式 3：跨角色 E2E 测试未把用户加入 teamMembers 导致权限重定向

- `project-evaluation-flow.spec.js` test #2 用 bid-Team 角色访问 admin 创建的项目，被重定向到 `/notifications`
- **根因**：后端 ProjectAccessScopeService 检查项目访问权限，bid-Team 用户不在 teamMembers 列表中时无权访问，前端路由守卫检测到 403 后重定向到通知中心
- **修复**：`createEvaluationProject` 新增 `extraTeamMembers` 参数，把 staff 用户 ID 加入项目 teamMembers
- **通用规则**：跨角色权限测试必须确保被测试用户在项目的 teamMembers/managerId 中，否则会被权限守卫拦截重定向

#### 模式 4：CO-XXX 重构后 selector 过期未同步

- `.page-kicker` → `.workbench`（Workbench.vue 重构后根 class 变更）
- `.el-select` → `.status-chip`（CO-495 EvaluationStage 重构后状态选项从下拉改为 chip）
- `.el-radio` → `.result-chip`（ResultConfirmStage 投标结果按钮）
- `.stage-view` → `.result-stage`（ResultConfirmStage 根 class）
- `.competitor-table .add-row-btn` → `page.locator('.add-row-btn')`（按钮是 table 的兄弟元素，不是子元素）
- **根因**：CO-XXX 重构前端组件时，未同步更新 E2E 测试的 selector
- **防御规范**：前端组件重构（特别是根 class、DOM 结构变更）必须同步检查 E2E 测试 selector，并在 PR 描述中标注"E2E 影响"

#### 模式 5：前端路由路径漂移——E2E 测试硬编码路径与实际路由不一致

- `project-evaluation-flow.spec.js`: `/project/:id/stages/evaluation` → `/project/:id/evaluation`（router 实际路径）
- `form-engine-adaptive-flow.spec.js`: `/bidding/tender/create` → `/bidding/create`、`/bidding/list` → `/bidding`
- `form-engine-adaptive-flow.spec.js`: 页面字段「标讯标题」→「项目名称」（TenderCreatePage.vue 实际字段）
- **根因**：前端路由重构或页面字段调整后，E2E 测试硬编码的路径/字段未同步
- **防御规范**：E2E 测试路径必须从 `src/router/index.js` 验证，字段名必须从实际 Vue 组件验证，禁止凭文档/记忆硬编码

### 防御规范（汇总）

1. **端口 fallback 对齐**：`e2e/auth-helpers.js` + `playwright.config.js` 的 fallback 值必须与 CLAUDE.md §端口约定一致
2. **selector 子串陷阱**：选项文本存在包含关系时，禁止 `filter({ hasText })`，必须用 `getByText(text, { exact: true })`
3. **跨角色权限测试**：被测试用户必须在项目的 teamMembers/managerId 中，否则会被权限守卫重定向
4. **CO-XXX 重构同步**：前端组件根 class/DOM 结构变更必须同步检查 E2E selector
5. **路由路径验证**：E2E 路径必须从 `src/router/index.js` 验证，字段名必须从实际 Vue 组件验证

### 处置记录

- 修复 commit: `7c0be5e2b`
- PR !2181: https://gitee.com/allinai888/bid/pulls/2181（squash 合并，sha=`bec8511b43`）
- 验证结果：22 passed, 10 skipped（5 个 spec 全部通过）

### 相关文档

- PR !2176 — 前序 E2E stale roles 修复（已合并）
- PR !2181 — 本次 5 个剩余失败 spec 修复
- [CLAUDE.md](../../CLAUDE.md) §端口约定 — 后端 18089 / 前端 1323
- EvaluationStage.vue — CO-495/CO-550/CO-571 重构后的实际 DOM 结构
- ResultConfirmStage.vue — 投标结果 chip + 竞争对手表结构

---

## 80. PR 收尾治理与多 Agent worktree 协作陷阱（2026-07-21 / PR !2176/!2178/!2179 治理 session）

> 来源：2026-07-21 处理 PR !2179 合并后 !2178 和 !2176 的冲突，发现 superseded PR 处置、跨 worktree 编辑限制、agent-finish-task 调用时机、Gitee MCP 工具 bug 等一系列多 Agent 协作陷阱
> 涉及工具：agent-finish-task.sh / Gitee MCP / SearchReplace / git worktree / agent-lock
> 影响等级：中（流程不顺畅 + 收尾遗漏会产生噪音）

### 事故背景

PR !2179 合并后，!2178 和 !2176 出现冲突。决策：!2178 被 !2179 覆盖（superseded）关闭，!2176 有独立价值（isLocalSystemAccount 语义放宽 + e2e 修复）rebase 后保留。整个治理过程中踩到多个多 Agent worktree 协作陷阱。

### 关键陷阱与教训

| 问题 | 教训 | 规范 |
|------|------|------|
| `agent-finish-task.sh` 在 init 分支执行被拒绝 | 脚本要求必须在任务分支上运行，切到 init 分支后执行会被拒 | 必须先切回任务分支再调用 `agent-finish-task.sh`；若任务分支已被本地删除但远端未删，需手动 `git push origin --delete` |
| Gitee PR merge 后远端任务分支被自动删除 | Gitee 在 PR 合并时自动删除 head 分支（保留 base 分支），manual `git push origin --delete` 报 "remote ref does not exist" | `agent-finish-task.sh --include-remote` 应先 `git ls-remote` 验证远端分支存在再 delete；或者捕获 "does not exist" 错误视为成功 |
| SearchReplace/DeleteFile 不能跨 worktree 操作 | 工具报 "Edit operations are restricted to the working directory"，只能在 cwd 所属 worktree 内编辑 | 跨 worktree 编辑必须用 `RunCommand` + `python3`/`sed` 在目标 worktree 内执行；或者 `cd` 到目标 worktree 后再用编辑工具 |
| 两个 PR 改同一区域，如何识别 superseded | 比对 diff：若一个 PR 的所有改动都被另一个 PR 覆盖（功能等价），即可标记 superseded 关闭 | 识别信号：① 同一文件同一区域的语义等价改动；② 拆分类名不同但功能一致；③ 一个 PR 已合并后另一个 PR 的所有 diff 都已被覆盖。处置：update_pull state=closed + title 加 `[Superseded by !XXXX]` 前缀 |
| 锁文件随 PR 合入 main 后的清理 | 任务结束后，main 上的 `.agent-locks/<task>.yml` 是过期锁；删除要走新分支 PR 太重 | **推荐保留**，不影响后续——agent-lock-acquire 同路径会提示已有锁但可 lock-release 释放；agent-start-task 创建新任务会写新锁文件覆盖 |
| Gitee MCP `create_comment` 工具 bug | 调用报 `CLIENT_HTTP_NOT_IMPLEMENTED` 错误（缺 resource_type），无法发评论 | 用 `update_pull` 替代——改 title 加前缀（如 `[Superseded by !2179]`）+ 改 state 即可，无需评论 |
| `get_pull_detail` 参数名易错 | 参数名是 `number`（非 `pull_number`），缺参报 "Invalid parameter" | 查 MCP 描述符 `.json` 文件确认参数名，不要凭直觉传参 |
| 多 Agent 共享 worktree 的分支切换污染 | trae worktree 中分支被其他 agent 任务切换（fix-e2e-remaining-specs → fix-e2e-remaining-specs-v2 → deploy-report-106th-test），WIP 改动 stash 后又被另一个任务 pop | 共享 worktree 中的 stash 必须带清晰的任务标识（"deploy-report-106th-test WIP (本次收尾不动)"）；切换分支前 `git status` 确认工作区干净；不要清理别的 agent 的 stash |
| `GIT_EDITOR` 未设置导致 rebase --continue 失败 | 报 "Terminal is dumb, but EDITOR unset" | 跨 worktree rebase 用 `GIT_EDITOR=true git rebase --continue` 跳过 commit message 编辑；或者 `git config core.editor true` |

### 操作规范

1. **执行 `agent-finish-task.sh` 前**：
   - 确认当前分支是任务分支（非 init 分支）
   - 若 init 分支执行被拒，先 `git checkout <任务分支>` 再执行
   - 若任务分支已本地删除但远端未删，先 `git ls-remote origin <分支名>` 验证再 `git push origin --delete`

2. **跨 worktree 编辑文件**：
   - 优先 `cd /path/to/worktree` 后再用编辑工具
   - 或者用 `RunCommand` 在目标 worktree 内执行 `python3`/`sed` 脚本
   - 不要试图用 SearchReplace 跨 worktree（会被拒）

3. **识别 superseded PR**：
   - `git diff main..branch-A` vs `git diff main..branch-B` 比对同一文件的改动
   - 若 branch-B 的所有改动都被 branch-A 覆盖（功能等价），branch-B 标记 superseded
   - 处置：MCP `update_pull state=closed` + `title` 加 `[Superseded by !A]` 前缀

4. **Gitee MCP 工具避坑**：
   - `create_comment` 有 bug，用 `update_pull` 改 title/state 替代
   - `get_pull_detail` 参数名是 `number`（非 `pull_number`），查描述符 `.json` 确认
   - PR merge 后远端分支自动删除，manual delete 前 `git ls-remote` 验证

5. **锁文件清理策略**：
   - 任务结束 PR 合入后，main 上的 `.agent-locks/<task>.yml` 是过期锁
   - **推荐保留**，下次 `agent-lock-acquire` 同路径会提示已有锁但可 `lock-release` 释放
   - 删除走新分支 PR 太重，不值得

6. **共享 worktree 的 stash 管理**：
   - stash 必须带清晰任务标识：`git stash push -m "<任务名> WIP (本次收尾不动)"`
   - 切换分支前 `git status` 确认工作区干净
   - 不要清理别的 agent 的 stash（即使看起来像残留）

7. **rebase 跨 worktree**：
   - 分支被某个 worktree 占用时，必须在该 worktree 内执行 rebase
   - `GIT_EDITOR=true git rebase --continue` 跳过 commit message 编辑
   - 冲突解决用 `python3` heredoc 脚本（SearchReplace 跨 worktree 不行）

### 处置记录

- PR !2178 处置（已执行）：MCP `update_pull state=closed` + title 加 `[Superseded by !2179]` 前缀
- PR !2176 rebase（已执行）：trae worktree 内 `git rebase origin/main`，python3 脚本解决 UserDetailsServiceImpl.java 冲突
- 锁文件补加（已执行）：`agent-lock-acquire --path backend/src/main/java/com/xiyu/bid/entity/User.java`，commit 进 PR
- PR !2176 force-push（已执行）：`--force-with-lease`，pre-push 门禁 23/23 通过
- PR !2176 合并（已执行）：2026-07-21 22:05:11 自动合并，Gitee 自动删除远端分支
- 收尾执行（部分成功）：`agent-finish-task.sh` 在 init 分支被拒后，手动完成清理（本地分支删除 + stash 保护 deploy-report WIP + 切回 init 锚点）
- 知识沉淀（本次）：§79 追加到 lessons-learned.md

### 相关文档

- [CLAUDE.md](../../CLAUDE.md) §多 Agent 执行手册 — worktree 协作规范
- [RELIABILITY.md](../../RELIABILITY.md) §agent-finish-task.sh — 收尾脚本使用
- [scripts/agent-finish-task.sh](../../scripts/agent-finish-task.sh) — 收尾脚本源码
- [scripts/sync-env.sh](../../scripts/sync-env.sh) — 早操 SOP
- PR !2176 — isLocalSystemAccount 语义放宽（已合并）
- PR !2178 — 被 !2179 覆盖的 PR（已关闭，superseded）
- PR !2179 — OSS 权限根因修复（已合并）
