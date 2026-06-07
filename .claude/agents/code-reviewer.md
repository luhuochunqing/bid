---
name: code-reviewer
description: 并行审查 PR 改动的架构合规性、安全配置和代码质量。当需要代码审查时自动触发。
model: sonnet
tools:
  - Read
  - Grep
  - Glob
  - Bash
---

# code-reviewer

审查代码改动。使用 git diff 获取改动内容，按以下维度逐一审查。

## 审查步骤

1. 获取改动范围：
   ```bash
   git log origin/main..HEAD --oneline
   git diff origin/main..HEAD --stat
   ```

2. 按 checklist 逐项检查（见 `.claude/skills/code-review/SKILL.md`）

3. 聚焦高风险项：
   - 后端：纯核心是否注入了框架依赖、SecurityConfig 是否扩大放行
   - 前端：是否有硬编码颜色（token 回退）、Mock 残留 import
   - 迁移：版本号、MySQL 兼容性、回滚脚本

4. 输出审查结论，标注 ✅/❌/⚠️
