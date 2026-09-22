# Quickstart — 验证跑法

**Branch**: `agent/qoder/export-hide-features` | **Worktree**: `/Users/user/xiyu/worktrees/qoder`（非开发宿主，不起 dev 服务）

## 前置

```bash
cd /Users/user/xiyu/worktrees/qoder
node -v && npm -v          # 需 Node 18+
ls node_modules/.bin/vitest 2>/dev/null || npm ci   # 临时 worktree 纯源码，先装依赖
```

## 基线（删除前必须先确认）

```bash
npm test        # 期望：全绿（记录用时与用例数）
npm run build   # 期望：构建成功
```

基线不绿则先停止并向用户报告，不得在红基线上做删除。

## 分批验证（每批执行后跑）

```bash
npm test                                        # 批内回归
bash scripts/check-line-budget.sh 2>/dev/null   # 行数预算（若脚本存在则跑）
```

## 终验（三件套）

```bash
# 1. 构建
npm run build

# 2. 全量单测
npm test

# 3. 剔除契约（见 contracts/removal-contract.md Part I/II）
bash specs/067-export-hide-features/verify-removal.sh   # 实现阶段生成的断言脚本
```

## 抽查清单（对应 SC-005/SC-006，构建产物层面）

由于本机为非开发宿主（不起 dev 服务），运行时抽查依赖构建成功 + 单测覆盖；开发宿主侧（trae）如需冒烟：

1. 工作台"快速发起"→ 借阅对话框仅有资质模式
2. 项目详情 → 无"投标结果"跳转
3. 系统设置 → 无"审计日志"页签；"日志说明"页正常
4. 知识库 → 无"模板库"子菜单，其余 7 个子页正常
5. 标讯列表 → 收藏按钮正常；文档编辑器 → 案例/知识选择正常
6. 直接输入旧地址（如 `/resource/expense`）→ 不再渲染该页面

## 收尾

```bash
git status                 # 工作区干净
git log --oneline main..HEAD   # 原子提交序列
bash scripts/pre-push-gate.sh  # 推送前门禁
```
