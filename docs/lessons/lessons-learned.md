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
