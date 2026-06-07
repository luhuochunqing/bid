# CI 故障模式知识库

> 基于 2026-05-29 ~ 2026-06-05 期间 45 条 CI 相关提交、17 个纯 CI 修复 PR 提取。
> 维护者：所有 Agent
> 更新规则：遇到新的 CI 故障模式时，在本文件中添加条目，附带精确恢复命令。

---

## 故障模式一览

| # | 模式 | 频率 | 典型恢复时间 | 已覆盖预防 |
|---|------|------|------------|-----------|
| 1 | agent-locks 锁冲突 | 极高（44 chore） | 5-15 min | `check-agent-locks.mjs` |
| 2 | e2e-scope 误检测 | 高（10+ PRs） | 5-20 min | `check-e2e-ui-sync.mjs` |
| 3 | Flyway 版本号冲突 | 高（12+ PRs） | 10-30 min | `check-flyway-versions.sh` |
| 4 | Spring DI 配置缺失 | 中（8+ PRs） | 5-30 min | `FPJavaArchitectureTest` |
| 5 | 编译失败（main 分支） | 中（6+ PRs） | 5-15 min | `pre-push-dry-run.sh` |
| 6 | 测试回归（mock 缺失） | 中（7+ PRs） | 5-20 min | `check-vue-test-boilerplate.mjs` |
| 7 | 回滚脚本缺失 | 中（3+ PRs） | 5-10 min | `check-flyway-rollback.sh` |
| 8 | 令牌颜色超限 | 低-中 | 2-5 min | `check-token-coverage.mjs` |
| 9 | 行数超限 | 低-中 | 5-15 min | `check-line-budgets.mjs` |
| 10 | 硬编码枚举映射 | 中（7+ PRs） | 5-15 min | `check-vue-enum-mapping.mjs` |

---

## 模式 1: agent-locks 锁冲突

**场景 A: 锁定文件命名不一致导致 hot-path 门禁**
```
❌ Hot-path 锁缺失！以下文件属于高风险路径，必须先在锁文件中注册才能提交：
  文件: .githooks/pre-commit
  匹配模式: .githooks/**
```

**根因**：`check-hot-path-locks.mjs` 用 `branch.replace(/\//g, '-')` 搜索锁文件，
而 `manage-agent-locks.mjs` 用 `resolveTaskSlug()`（去除 agent 前缀）创建锁文件。
分支 `agent/claude/task-name` 时，前者查找 `agent-claude-task-name.yml` 但文件是 `task-name.yml`。

**恢复步骤**：
```bash
# 检查实际锁文件名
ls .agent-locks/
# 如果已有的锁文件名不含 agent 前缀，创建别名
cp .agent-locks/$(ls .agent-locks/ | grep -v "$(git branch --show-current | sed 's|/|-|g')" | head -n -0) .agent-locks/$(git branch --show-current | sed 's|/|-|g').yml
git add .agent-locks/
```

**预防**：创建锁时同时创建两个命名版本（带 agent 前缀 + 不带）。

---

**场景 B: 已合入分支的过期锁未清理**
```
agent-lock-check: blocked by active lock
  file=.githooks/pre-commit owner=codex branch=codex/pre-commit-gates
```

**根因**：PR 合入 main 后，锁文件仍在 `.agent-locks/` 中，新分支无法对该文件加锁。

**恢复步骤**：
```bash
# 检查过期锁文件
ls .agent-locks/*.yml | while read f; do
  branch=$(grep 'branch:' "$f" | head -1 | sed 's/.*branch: "//;s/".*//')
  git merge-base --is-ancestor "$branch" origin/main 2>/dev/null && echo "STALE: $f ($branch)" || echo "ACTIVE: $f ($branch)"
done

# 删除过期锁
npm run agent:lock-cleanup
```

**预防**：合入 PR 前使用 `npm run agent:lock-release -- --all` 释放锁。

---

## 模式 2: e2e-scope 误检测

**场景 A: workflow_dispatch 时 base-ref 为空**
```
e2e-scope: base-ref 为空，无法检测变更范围
```

**根因**：`workflow_dispatch` 触发时 `github.base_ref` 为空，
CI 脚本需通过其他方式确定 base branch。

**恢复步骤**：在 workflow_dispatch 中手动指定 base branch，或添加 fallback 到 origin/main。

**预防**：本地运行 `check-e2e-ui-sync.mjs` 确认变更范围，然后使用 `[skip e2e-scope]` 标记。

---

**场景 B: UI 标签变更后 E2E 测试未同步**
```
FAILED: bidding-list.spec.js — 找不到文本 "项目来源"
```

**根因**：Vue 组件中修改了列名或标签文本，但 E2E 测试仍在用旧文本定位。

**恢复步骤**：
```bash
# 查找所有用旧文本的 E2E 选择器
grep -r "旧文本内容" e2e/
# 更新为新的 UI 文本
```

**预防**：`check-e2e-ui-sync.mjs` 会警告 UI 变更需要同步 E2E。

---

## 模式 3: Flyway 版本号冲突

**场景 A: 两个 PR 同时创建同版本号迁移**
```
flyway-versions: CONFLICT — staged V1039 conflicts with origin/main
```

**根因**：两个 Agent 同时创建了 V1039.sql，先合入的占用了版本号。

**恢复步骤**：
```bash
# 确认当前最大版本号
git fetch origin main
git ls-tree -r --name-only FETCH_HEAD -- backend/src/main/resources/db/migration-mysql/ | sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n | tail -3
# 重编号你的迁移文件
mv backend/src/main/resources/db/migration-mysql/V1039_my_migration.sql backend/src/main/resources/db/migration-mysql/V$(($(git ls-tree -r --name-only FETCH_HEAD -- backend/src/main/resources/db/migration-mysql/ | sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n | tail -1) + 1))_my_migration.sql
```

**预防**：`check-flyway-versions.sh --staged` 会在 pre-commit 时检测版本冲突。

---

**场景 B: 回滚脚本缺失**
```
U1039.sql not found for V1039.sql
```

**根因**：创建了 V 迁移文件但忘记创建对应的 U 回滚文件。

**恢复步骤**：
```bash
# 从 V 文件生成 U 文件的骨架
version=$(echo V1039_my_migration.sql | sed 's/^V\([0-9]\+\).*/\1/')
cat > "backend/src/main/resources/db/migration-mysql/U${version}_rollback.sql" << EOF
-- U${version}: Rollback my migration
-- §相关蓝图章节
-- 回滚操作写在这里
EOF
```

**预防**：`check-flyway-rollback.sh` 在 pre-commit 时会强制检查 V 必须有对应的 U。

---

## 模式 4: Spring DI 配置缺失

**场景: 纯核心类未注册为 Bean**
```
NoSuchBeanDefinitionException: No qualifying bean of type 'KnowledgeCaseMatchPolicy'
```

**根因**：将 `domain/` 包下的纯核心类直接 `@Component` 或忘记注册，
导致 `@SpringBootTest` 集成测试 ApplicationContext 加载失败（最多 228 errors）。

**恢复步骤**：
```bash
# 1. 在相关 config 包中添加 @Configuration + @Bean
# 2. 或在类上添加 @Component（仅限无状态纯核心）
# 详见 RULES.md §2.5.1 三种注册模式
```

**预防**：`FPJavaArchitectureTest.pure_core_domain_should_prefer_config_over_component()`
会检测 domain 包中的 @Component 并输出警告。

---

## 模式 5: 编译失败

**场景: main 分支编译失败**
```
[ERROR] COMPILATION ERROR: TenderController.java:123 找不到符号
```

**根因**：PR 合入时漏掉了某个文件的更新（构造函数参数变更、依赖接口变化、DTO 字段增减）。

**恢复步骤**：
```bash
# 本地拉取最新代码
cd /Users/user/xiyu/xiyu-bid-poc
git fetch origin && git checkout origin/main
# 运行编译
cd backend && mvn compile
# 修复编译错误后推送修复
```

**预防**：推送前运行 `npm run agent:pre-push-dry-run -- --backend` 编译验证。

---

## 模式 6: 测试回归（mock 缺失）

**场景: Service 集成测试因缺少 mock 而失败**
```
TenderCommandServiceTest > ... FAILED
  Wanted but not invoked: notificationApplicationService.sendNotification(...)
```

**根因**：ApplicationService 新增了对其他 Service 的调用，
但对应的测试没有提供 mock/stub，导致集成测试加载时调用真实服务。

**恢复步骤**：
```bash
# 查看具体失败的测试
cd backend && mvn test -Dtest=FailedTestClass
# 在 @BeforeEach / setUp 中添加缺失的 mock
# 或使用 @MockBean 在测试类中注入 mock
```

**预防**：新增依赖后检查相关测试是否缺少 mock。

---

## 模式 7-10: 其他门禁违规

### 7. 回滚脚本缺失 → `check-flyway-rollback.sh`（已阻断）
### 8. 令牌颜色 960 超限 → `check-token-coverage.mjs --fail-on-hex --max-hex-total=960`
### 9. 行数 300 超限 → `check-line-budgets.mjs`（已阻断）
### 10. 硬编码枚举映射 → `check-vue-enum-mapping.mjs`（已警告）

---

## 快速恢复脚本

```bash
# 一键查看当前分支的 CI 合规状态
npm run agent:pre-push-dry-run -- --backend --frontend

# 清理过期锁
npm run agent:lock-cleanup

# 检查 Flyway 版本冲突
./scripts/check-flyway-versions.sh --staged

# 检查 Flyway 回滚完整性
./scripts/check-flyway-rollback.sh

# 查看当前 main 最新迁移版本号
git fetch origin main
git ls-tree -r --name-only FETCH_HEAD -- backend/src/main/resources/db/migration-mysql/ | sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n | tail -3

# 查看过期锁
ls -la .agent-locks/
```
