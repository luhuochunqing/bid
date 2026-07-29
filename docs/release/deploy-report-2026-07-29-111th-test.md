# 第 111 次测试环境部署报告

> **环境**：测试环境 `winbid-01` (172.16.38.78)
> **Release ID**：`22802f80a`
> **上一版本**：`e3d3c4580`（第 110 次）
> **部署时间**：2026-07-29 20:58:31 CST
> **部署方式**：remote-deploy.sh
> **操作人**：trae agent

## 部署内容（增量 8 commits, 3 PRs）

从 `e3d3c4580` → `22802f80a`，共 8 commits，涉及 3 个 PR：

### PR !2218 fix(tender-intake): 系统性扩展归一化字符集 + Prompt 引导处理多变体与多位置标签行
- 修复 PDF 关键词被空格打断导致漏识别招标主体
- 系统性扩展归一化字符集
- 扩展 Prompt 引导到其他字段（projectName/deadline/bidOpeningTime/contact）

### PR !2219 docs(lessons): 记录第 86 条 - 招标文件 PDF 关键词识别陷阱
- 追加第 86 条教训：空格变体 + 描述性文字混淆 + 多位置标签行

### PR !2220 docs(release): 第 110 次测试环境部署报告 (test)
- 第 110 次测试环境部署报告合入 main

## 迁移变更

**无新迁移文件**。本次部署不涉及数据库结构变更。

## 部署前预检

| 项目 | 结果 |
|---|---|
| 早操同步 | ✅ HEAD=22802f80a 与 origin/main 一致 |
| 工作区状态 | ✅ clean |
| 增量 commit 分析 | ✅ 8 commits, 3 PRs, 无新迁移 |
| 服务器现状 | ✅ e3d3c4580 健康 200 |
| Flyway 预检 | ⏭️ 跳过（无新迁移，上次刚验证 241 migrations validate OK） |

## 部署过程

| 步骤 | 结果 | 备注 |
|---|---|---|
| 本地打包 | ✅ | Release 22802f80a, obsEnabled=true, 241 迁移文件, 归档 153M |
| 产物校验 | ✅ | jarName=bid-platform-1.0.3.jar, 前端入口=assets/index-DY4s5YDD.js |
| 上传归档 | ✅ | scp 到 /opt/xiyu-bid/incoming/ |
| 远端部署 | ✅ | remote-deploy.sh 执行 |
| 服务停止 | ✅ | xiyu-bid-backend.service |
| 后端 artifact 更新 | ✅ | /opt/xiyu-bid/shared/backend/app.jar |
| 前端 assets 激活 | ✅ | /opt/xiyu-bid/frontend/public/ |
| 服务启动 | ✅ | active (running) since 20:58:31 CST |
| 健康检查 | ✅ | 3/3 通过（79 次尝试，约 2.5 分钟） |
| 前端一致性 | ✅ | src="/assets/index-DY4s5YDD.js" 匹配 |

## 验证结果

### Smoke 测试

| 测试项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 | 200 | ✅ |
| `/actuator/info` | 200/403 | 403 | ✅ |
| `/api/auth/me` (未登录) | 401/403 | 403 | ✅ |
| `/api/tenders` (未登录) | 401/403 | 403 | ✅ |
| `/` 前端入口 | 200 | 200 (服务器内部) | ✅ |

### 前端资源保留

- 上一版 release 目录 `/opt/xiyu-bid/releases/e3d3c4580/` ✅ 存在
- 上一版 frontend/assets 已 `cp -rn` 保留到当前 public/assets ✅（24h 过渡期）

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| Gitee main | `22802f80a6746a83dff00af30d12ad64dc55d4b1` |
| GitHub main | `22802f80a6746a83dff00af30d12ad64dc55d4b1` |
| 状态 | ✅ 完全一致 |

## 回滚状态

**未需要**。上一版本 `e3d3c4580` release 目录仍存在，可快速回滚。

## 待 UAT 验证项

1. **tender-intake AI 抽取**：上传带空格打断关键词的招标文件 PDF，验证招标主体识别准确性
2. **tender-intake 其他字段**：验证 projectName/deadline/bidOpeningTime/contact 字段抽取的 Prompt 引导效果
3. **回归验证**：验证标讯中心列表、详情页等核心功能无回归
