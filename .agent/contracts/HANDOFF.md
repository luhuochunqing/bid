# GitHub 封号应急处理 — Handoff

> Last updated: 2026-06-03

## 背景
GitHub 账号 `ericforai` 被封（Terms of Service 违规）。代码已迁移到 Gitee。

## 新远端（已全局配置）

```
git@gitee.com:allinai888/bid.git
```

所有 worktree 共享 `.git` 目录，remote 已自动更新。无需手动配置。

验证：`git remote -v` 应显示：
```
origin	git@gitee.com:allinai888/bid.git (fetch)
origin	git@gitee.com:allinai888/bid.git (push)
```

## SSH 公钥（已绑定 Gitee）

```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIvNvkzLWKZxbctuZYNQa9/Llp+COurfOHtoL1ircXMl eric.luwenrong@gmail.com
```

所有 worktree 均可直接使用。

## 各 Worktree 推送步骤

每个 Agent 在自己的 worktree 中：

```bash
# 1. 确认代码已提交
git status

# 2. 推送到 Gitee
git push -u origin HEAD

# 3. 如果报 shallow 错误（历史丢失），用 orphan 分支
BRANCH=$(git rev-parse --abbrev-ref HEAD)
git checkout --orphan gitee-$BRANCH
git commit --no-verify -m "Initial commit: $BRANCH"
git push -u origin gitee-$BRANCH --force
git checkout $BRANCH
git branch -D gitee-$BRANCH
```

## 分支保护（已配置）

| 设置 | 状态 |
|------|:----:|
| main 禁止直接推送 | ✅ |
| 需要 PR 审查（≥1人） | ✅ |
| Issues / Wiki | ✅ |

## Gitee Token

`92de91cc669bdce8684bb70c5bd7d384`

用于 API 操作（创建 PR、配置仓库等）。

## PR 状态

| PR | 说明 | 状态 |
|:---|------|:----:|
| #4 | fix(db): 补充 V1026 回滚脚本 + V1027 扩展 role_key 列宽 | ✅ 已合并 |

## 恢复方案（等 GitHub 解封后）

```bash
git fetch --unshallow origin
git remote add github git@github.com:ericforai/bidding.git
git push -u github --all
```

## 已完成的评分标准功能

蓝图 §3.3.1.2.2 AI评分标准解析 — 全部实现：

| 功能 | 状态 |
|------|:----:|
| 评分标准提取（编号/维度/指标/权重/总分） | ✅ 结构化 ScoringCriterion |
| 资质要求识别（联动知识库三态） | ✅ |
| 技术要点提取（四类标签） | ✅ |
| 商务条款解析（六类） | ✅ |
| 废标红线标记（红色 badge） | ✅ |
| 按钮状态联动 | ✅ 面板 badge |
