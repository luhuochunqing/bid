---
name: standard-workflow
description: Everything Claude Code 标准作业流程 - 4阶段工作法：规划→开发(TDD)→质检(code-review)→维护(refactor-clean)
---

# Standard Workflow - Everything Claude Code 标准作业流程

这是 **Everything Claude Code** 的标准 4 阶段工作流程，确保代码质量和开发效率。

---

## 🔄 工作流程图

```
┌─────────┐    ┌────────┐    ┌─────────────┐    ┌──────────────┐
│  1.规划  │ → │  2.开发  │ → │  3.质检      │ → │  4.维护       │
│ /plan   │    │  /tdd   │    │ /code-review  │    │ /refactor-clean│
│ 架构师  │    │  工程师 │    │  审查员      │    │  保洁员      │
└─────────┘    └────────┘    └─────────────┘    └──────────────┘
     │              │                │                    │
     ▼              ▼                ▼                    ▼
 分析需求      测试驱动开发      安全+质量审查        清理死代码
 拆解步骤      先写测试          阻塞提交            优化结构
 等待确认      RED→GREEN        生成报告            测试验证
```

---

## 📋 阶段详情

### 1️⃣ 规划 `/plan` - 架构师

**核心指令:** `/plan`

**作用:**
- 需求重述 - 明确要做什么
- 风险识别 - 发现潜在问题
- 步骤拆解 - 分阶段实施计划
- **等待确认** - 必须得到用户批准才继续

**最佳使用场景:**
- ✅ 接到新需求时
- ✅ 修复复杂 Bug 前
- ✅ 多文件/组件改动
- ✅ 需求不明确时

**输出:** `.md` 计划文档

---

### 2️⃣ 开发 `/tdd` - 工程师

**核心指令:** `/tdd`

**作用:**
- 定义接口类型
- **先写测试** (RED) - 测试失败
- 最小实现 (GREEN) - 通过测试
- 重构优化 (REFACTOR) - 保持测试通过
- 验证覆盖率 80%+

**TDD 循环:**
```
RED → GREEN → REFACTOR → REPEAT
```

**最佳使用场景:**
- ✅ 编写具体功能函数
- ✅ 确保代码逻辑严密
- ✅ 避免"能跑但全是Bug"的代码

---

### 3️⃣ 质检 `/code-review` - 审查员

**核心指令:** `/code-review`

**作用:**
- 检查 **安全问题** (CRITICAL) - 硬编码密钥、SQL注入、XSS
- 检查 **代码质量** (HIGH) - 函数>50行、嵌套>4层、缺少错误处理
- 检查 **最佳实践** (MEDIUM) - 变更模式、可访问性
- 生成报告并阻塞严重问题提交

**最佳使用场景:**
- ✅ 代码写完准备提交前
- ✅ 感觉代码写得"有点乱"
- ✅ 想要优化现有代码

---

### 4️⃣ 维护 `/refactor-clean` - 保洁员

**核心指令:** `/refactor-clean`

**作用:**
- 运行死代码分析工具 (knip, depcheck, ts-prune)
- 生成报告 `.reports/dead-code-analysis.md`
- 分类: SAFE, CAUTION, DANGER
- **测试验证后删除** - 运行测试→应用→再测试→失败则回滚

**最佳使用场景:**
- ✅ 项目臃肿时
- ✅ 大版本迭代后
- ✅ 清理未使用的依赖

---

## 🚀 快速开始

### 典型工作流

```bash
# 1. 规划
/plan 我需要添加用户认证功能

# 2. 开发
/tdd 实现 JWT 认证中间件

# 3. 质检
/code-review

# 4. 维护 (可选)
/refactor-clean
```

---

## 🎯 各角色职责

| 阶段 | 角色 | 核心职责 |
|------|------|----------|
| 规划 | 架构师 | 分析需求、拆解步骤、评估风险 |
| 开发 | 工程师 | TDD 开发、确保覆盖率 |
| 质检 | 审查员 | 安全检查、质量把关 |
| 维护 | 保洁员 | 清理死代码、优化结构 |

---

## 📌 关键原则

1. **必须按顺序执行** - 规划→开发→质检→维护
2. **规划必须确认** - `/plan` 后等待用户 `yes`
3. **测试先行** - `/tdd` 必须先写测试
4. **安全第一** - `/code-review` 发现严重问题必须修复
5. **测试后删除** - `/refactor-clean` 必须验证测试

---

## 🔗 相关 Agent

- `~/.claude/agents/planner.md` - 规划代理
- `~/.claude/agents/tdd-guide.md` - TDD 指导
- `~/.claude/agents/code-reviewer.md` - 代码审查代理
- `~/.claude/agents/refactor-cleaner.md` - 重构清理代理

---

## 📚 相关 Skills

- **tdd-workflow** - TDD 详细工作流
- **security-review** - 安全审查技能
- **minimalist-refactorer** - 极简重构
- **entropy-reduction** - 熵减重构
