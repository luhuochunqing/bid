# 回退恢复 Playbook（cherry-pick 优先）

> **入口**：`RELIABILITY.md §回退恢复纪律`（硬规则）→ 本文档（详细 playbook + 真实案例）
> **场景**：发现某 commit 被后续 commit 误回退，需要恢复时
> **核心原则**：**默认 `git cherry-pick`，禁止手工重写**。冲突不是绕过理由。

## 0. TL;DR（30 秒决策树）

```
发现 commit X 被 commit Y 回退？
├─ 先 git cherry-pick X
│  ├─ 成功 → ✅ 推送、提 PR（完）
│  └─ 冲突 → 解决冲突（不是放弃！）
│     ├─ 解决后 git cherry-pick --continue → ✅
│     └─ 命中"三种例外"之一 → 放弃 + 手工重写 + 双重记录
└─ 不要跳过 cherry-pick 直接手工重写
```

## 1. 识别"被回退"

### 信号

- 用户报告"昨天还能用，今天坏了"（功能回归）
- `git log -S "符号名"` 显示某个符号在某 commit 出现又在后续 commit 消失
- PR 标题/commit message 出现"同步 main""重构""revert"等高危词，且改动量巨大（净删千行+）
- Linear issue 标题是"XX 无法 YY"，但搜索代码发现相关函数/组件根本不存在

### 诊断命令

```bash
# 找符号消失点
git log -S "isSelfVisibleTender" --oneline
git log -S "handleTaskClick" --oneline

# 找文件被整体回退
git log --oneline -- <file>
git diff <可疑回退 commit>^..<可疑回退 commit> --stat | grep -E '\|.*\-.*\+'

# 对比"原修复"与"当前"在某文件上的差异
git diff <原修复 commit> HEAD -- <file>
# 如果 diff == 当初回退 commit 的 diff，说明完全未恢复
```

## 2. 标准恢复流程（cherry-pick）

```bash
# Step 1: 从 main 开分支
bash scripts/agent-start-task.sh <agent> recover-<slug> origin/main --in-place

# Step 2: cherry-pick 原修复 commit（保留 author！）
git cherry-pick <原修复 commit>
# → 如果成功，直接跳到 Step 5

# Step 3: 冲突解决（最常见情况）
git status                         # 看哪些文件冲突
# 逐个编辑冲突文件：
#   - 保留原修复的功能语义
#   - 适配冲突处的新代码结构（如函数重命名、组件拆分）
#   - 不要简单删一边，要理解双方意图
git add <解决后的文件>
git cherry-pick --continue

# Step 4: 验证（测试 + build + line-budgets）
npx vitest run <相关测试>
mvn -Dtest=<相关测试> test   # 后端
npx vite build
npm run check:line-budgets

# Step 5: 推送 + PR
git push origin <branch>
PRE_PUSH_GATE=0 ./scripts/pr-create.sh "fix: 恢复 <commit> 被回退的 XX" /tmp/body.md
```

**关键**：cherry-pick 完成后，`git log` 会显示原 commit 的 author + 你的 committer，`git blame` 能正确追溯。这是手工重写给不了的。

## 3. 三种允许手工重写的例外

> 即便命中例外，commit message 必须写 `Restore <原 hash>: ...`，implementation-notes.md 必须记录原 hash + 例外类型。

### 例外 A：原 commit 改动的文件已完全不存在

例：原 commit 改了 `TaskRejectDialog.vue`，但该组件后来被删除并内联到 `TaskKanban.vue`。
→ cherry-pick 会报 "could not apply"，手工把等价逻辑写到 `TaskKanban.vue`。

### 例外 B：原 commit 的改动语义已被重组

例：原 commit 给 `<div class="card-actions">` 加 `@click.stop`，但后来 card-actions 被拆成独立子组件 `<TaskBoardTaskActions>`。
→ cherry-pick 能 apply 但代码位置无意义（@click.stop 加在一个已不存在的 div 上），手工把 @click.stop 加到新的子组件引用上。

**判断标准**：cherry-pick 后代码能跑通，但改动落在了"逻辑上不该承担它的地方"。

### 例外 C：原 commit 跨越多个不相关改动

例：原 commit 同时改了"抽屉恢复"+"无关的样式调整"+"无关的 lint 修复"（违反原子提交）。
→ cherry-pick 会带回无关变更，手工只抽取需要的那部分。

## 4. 真实案例（2026-06-26 CO-338 恢复）

### 案例背景

`3dcc30097`「同步 GitHub main 重构」（2026-06-25 16:15）声称"同步"，实际 GitHub main 滞后于 Gitee，把当天早些时候的 9 个 Gitee 修复 commit 整体覆盖（净删 1236 行 / 74 文件改 / 18 文件删）。两天后被用户报告"点任务卡片没反应、三列宽度又不一致了"。

### 案例 1：CO-338 前端恢复（命中例外 B）

**原修复**：`90563f6a1` 给 `TaskBoardCard.vue` 根元素加 `@click="emit('task-click', item)"`、给 `<div class="card-actions">` 加 `@click.stop`、加 el-drawer 抽屉到 `TaskBoardPage.vue`。

**回退后中间发生的重组**：`e06af5c06`（CO-339）把 card-actions 拆到了 `TaskBoardTaskActions.vue` / `TaskBoardBidReviewActions.vue` 子组件。

**正确做法本应是**：
```bash
git cherry-pick 90563f6a1
# 冲突：TaskBoardCard.vue 的 card-actions 部分已重组
# 解决：把 @click.stop 从 <div class="card-actions"> 改加到 <TaskBoardTaskActions @click.stop>
# 把 el-drawer/handleTaskClick 部分保留（TaskBoardPage.vue 没重组，直接 apply）
git cherry-pick --continue
```

**实际错误做法**：agent 跳过 cherry-pick，手工重写全部代码。代价：
- `git blame TaskBoardPage.vue` 的抽屉代码指向 agent 当天的 commit，而非 `90563f6a1` 原作者
- 原 commit 的 author 贡献记录丢失
- 未来考古需要靠 implementation-notes.md 里的 hash 反查

**教训**：例外 B（语义重组）允许手工重写，但**必须先尝试 cherry-pick 并在冲突中学到"哪些部分能 apply、哪些需要手工"**，而不是全盘手工。本案例 `TaskBoardPage.vue` 部分其实可以纯 cherry-pick（该文件没重组），只有 `TaskBoardCard.vue` 命中例外 B。

### 案例 2：投标专员标讯可见性恢复（本应纯 cherry-pick）

**原修复**：`a048a0cfd` 给 `TenderProjectAccessGuard` 加 `isSelfVisibleTender` + 批量 assignee 判断 + 4 个测试。

**回退后该文件变化**：只有 `DataScopeAccessProfile` 构造方式从 public 改成 `@Builder`，以及 Mockito 实参类型变化。**这些是小冲突，不是语义重组**。

**正确做法本应是**：
```bash
git cherry-pick a048a0cfd
# 小冲突 1：DataScopeAccessProfile("self", null, null) → builder().dataScope("self").build()
# 小冲突 2：findByTenderIdIn(List.of(1L)) → findByTenderIdIn(any()) （实参已是 Set）
# 解决后 git cherry-pick --continue
```

**实际错误做法**：agent 手工粘贴 diff。这是**纯粹的偷懒**，不命中任何例外。`git blame` 损失最大。

### 案例教训总结

| 案例 | 应该的做法 | 实际做法 | 代价 |
|------|-----------|---------|------|
| CO-338 前端 | 部分 cherry-pick（Page）+ 部分手工（Card，例外 B） | 全盘手工 | 中（部分本可保留 author） |
| 标讯可见性 | 纯 cherry-pick + 解决 2 个小冲突 | 全盘手工 | 高（无任何例外理由） |

## 5. 沉淀的硬规则（写入 RELIABILITY.md）

见 `RELIABILITY.md §回退恢复纪律`。核心 4 条：

1. 默认 cherry-pick
2. 冲突不豁免
3. 只有三种例外允许手工重写
4. 即便手工也必须 commit message + notes 双重记录原 hash

## 6. 防再犯检查清单

恢复 PR 提交前自问：

- [ ] 我是否先尝试了 `git cherry-pick <原 commit>`？
- [ ] 如果有冲突，我是否尝试解决而非立即放弃？
- [ ] 如果手工重写，是否命中三种例外之一？
- [ ] commit message 是否引用了原 commit hash？
- [ ] implementation-notes.md 是否记录了原 hash + 手工重写原因？
- [ ] `git log --grep "<原 hash>"` 能否从新 commit 反查到原 commit？

任一项答"否"，重新评估是否该走 cherry-pick。
