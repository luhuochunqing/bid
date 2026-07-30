# 第 112 次测试环境部署报告

> **环境**：测试环境 `winbid-01` (172.16.38.78)
> **Release ID**：`e9084f530`
> **上一版本**：`22802f80a`（第 111 次）
> **部署时间**：2026-07-29 22:46:44 CST
> **部署方式**：remote-deploy.sh
> **操作人**：trae agent

## 部署内容（增量 6 commits, 2 PRs）

从 `22802f80a` → `e9084f530`，核心是招标主体识别修复：

### PR !2222 fix(tender-intake): purchaserName 正则兜底，修复招标主体漏识别（本次核心）
- **根因**：purchaserName 纯 AI 抽取、零兜底，张家口银行文档"招标人/代理机构"共现 20+ 次，AI 漏抽即彻底丢失
- **修复**：新增 `PurchaserNameExtractor`（正则兜底提取器），AI 返回空时用正则从候选文本按 `PurchaserAliases.ALL` 标签行提取，排除代理机构与叙事性行，多标签取多数
- **接线**：`OpenAiTenderDocumentAnalyzer.mergeAndMap` 仅 AI 空值时兜底，AI 有值不覆盖

### PR !2223 docs(lessons): 第 87 条 - 结构化标签字段死磕 Prompt 不如正则兜底
- 沉淀教训：结构化标签行走正则兜底，不死磕 Prompt

## 迁移变更

**无新迁移文件**。本次部署不涉及数据库结构变更。

## 部署前预检

| 项目 | 结果 |
|---|---|
| 早操同步 | ✅ HEAD=e9084f530 与 origin/main 一致 |
| 工作区状态 | ✅ clean |
| 增量 commit 分析 | ✅ 6 commits, 2 PRs, 无新迁移 |
| 服务器现状 | ✅ 22802f80a 健康 200 |
| Flyway 预检 | ⏭️ 跳过（无新迁移，上次刚验证 241 migrations validate OK） |

## 部署过程

| 步骤 | 结果 | 备注 |
|---|---|---|
| 本地打包 | ✅ | Release e9084f530, obsEnabled=true, PurchaserNameExtractor 已入 jar, 归档 153M |
| 产物校验 | ✅ | jarName=bid-platform-1.0.3.jar, 前端入口=assets/index-DY4s5YDD.js, PurchaserNameExtractor.class=1 |
| 上传归档 | ✅ | scp 到 /opt/xiyu-bid/incoming/ |
| 远端部署 | ✅ | remote-deploy.sh 执行 (SYSTEMCTL_SUDO=true) |
| 服务重启 | ✅ | xiyu-bid-backend active (running) since 22:46:45 CST |
| 健康检查 | ✅ | 3/3 通过（78 次尝试，约 2.5 分钟） |
| 前端一致性 | ✅ | src="/assets/index-DY4s5YDD.js" 匹配 |

## 验证结果

### Smoke 测试

| 测试项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 | 200 | ✅ |
| `/api/auth/me` (未登录) | 403 | 403 | ✅ |
| `/api/tenders` (未登录) | 403 | 403 | ✅ |
| `/` 前端入口 (服务器内部) | 200 | 200 | ✅ |

### 招标主体修复验证

- `PurchaserNameExtractor.class` 已确认打入部署 jar ✅
- 本地已用张家口银行 PDF 经 sidecar 提取的真实候选文本端到端验证，兜底输出正确 `张家口银行股份有限公司`（排除代理机构）✅

### 前端资源保留

- 上一版 release 目录 `/opt/xiyu-bid/releases/22802f80a/` assets 已 `cp -rn` 保留（24h 过渡期）✅

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| Gitee main | `e9084f530fea118c9f24a133a5cc01c4bb1a8292` |
| GitHub main | `e9084f530fea118c9f24a133a5cc01c4bb1a8292` |
| 状态 | ✅ 完全一致 |

## 回滚状态

**未需要**。上一版本 `22802f80a` release 目录仍存在，可快速回滚。

## 待 UAT 验证项

1. **招标主体识别（核心）**：重新上传 `张家口银行股份有限公司宣传品、办公用品和办公耗材电商平台采购项目(最终版).pdf`，验证招标主体正确识别为"张家口银行股份有限公司"
2. **回归验证**：验证其他招标文件 AI 抽取无回归（AI 有值时不被正则兜底覆盖）
