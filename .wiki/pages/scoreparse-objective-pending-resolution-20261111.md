---
title: 项目1117客观评分项全为待确认根因剖析与代码修复
space: engineering
category: lessons
tags: [scoreparse, objective-score, certificate, stale, evidence-requirement, regional-warehouse]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/WarehouseRegionalScorePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/match/CertMatchService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreEstimateFreshness.java
backlinks:
  - _index
created: 2026-11-11
updated: 2026-11-12
health_checked: 2026-11-12
---

# 项目1117客观评分项全为待确认根因剖析与代码修复

## 1. 现象与影响

在生产环境项目 1117（招商积余）中，虽然评分项均为“客观项”，但在前台界面呈现时：
1. **管理体系认证（10 分）**：客观项，满足状态显示为“待确认”，预计得分为空；
2. **公司业绩（30 分）**：客观项，满足状态显示为“待确认”，预计得分为空；
3. **仓储能力（25 分）**：客观项，满足状态显示为“待确认”，预计得分为空。

## 2. 根因剖析

### 2.1 管理体系认证（证书项跨天失效与投影抹去）
- **致错机制**：`CertMatchService.fingerprint` 曾将动态的 `LocalDate.now()`（即当天日期）放入 SHA-256 计算指纹。当跨过午夜或隔天重新读取时，只读投影层 `ScoreEstimateFreshness` 检测到指纹变动判定为 `STALE`，`ScoreParseItemsReadSupport` 强制清空 `estScore` 并将 `status` 覆盖为 `PENDING`。
- **修复措施**：指纹仅由【规则配置 + 资质库事实】决定；引入 `hasCertificateExpiredSince` 精准过期检测，跨天读取保持 `FRESH`。

### 2.2 公司业绩（发票/银行流水证据一刀切短路）
- **致错机制**：招标文件条款包含“该合同甲方的最少一次付款银行流水及对应的发票扫”等佐证要求，命中 `ProjectEvidenceRequirementPolicy.assess(item.getDetail())`。系统原逻辑采取过度防御策略：只要命中需要线下发票/流水，直接执行 `item.setEstScore(null); item.setStatusStage1("PENDING");`，导致即使已经精准匹配出合格的历史合同业绩，得分也被强制丢弃。
- **修复措施**：解除短路；无匹配候选时保持人工确认，有匹配候选时按业绩规则计算出客观分值（满分 30 分或阶梯分），并在依据中明确注入“提示：招标文件包含强制材料要求（银行流水、发票），请在投标文件制作时备齐佐证文件”。

### 2.3 仓储能力（多区域配送仓与超额增仓复合规则缺失）
- **致错机制**：系统仅支持固定面积数值阈值（`extractMinArea(...)`）；若条款未提供具体平米数门槛，直接防御退出判定为待确认。忽略了条款中明确的“六大地区内各一个配送仓库得 15 分，缺一扣 3 分；六大区每多一个仓库得 1 分，满分 5 分；面积横向排序 5 分”的规则。
- **修复措施**：引入 FP-Java 纯领域策略 `WarehouseRegionalScorePolicy`，识别华北、东北、华东、中南、西南、西北六大区域，结合知识库在用仓库计算出 20 分确定性得分，剩余 5 分横向排序项在依据中提示现场评比。

## 3. 防护与回归边界

## 2026-11-12 刷新入口漏执行非证书项

- 现场1119：按钮发出POST estimates/refresh并GET items，返回200但全部11项仍待确认。
- 根因：refreshForProject调用calculate(...,certificateOnly=true)，无证书时遍历空集仍返回true；业绩/仓储策略修复未接入手动刷新路径。不能归因为必须重新解析。
- 本次本地修复：全部生效项重新计算，保留CAS保存、项目鉴权与候选/阶段2不变边界；refresh摘要来自本次计算并成功保存的集合，0分算有数值得分，缺分值逐项说明。
- UI刷新工具栏移出第一部分折叠区并固定可见；过期统计明确只指证书，不能用证书0项推断整项目无需更新。
- 规格specs/086-score-estimate-refresh。生产1117/1119尚需授权部署后的真实按钮→MySQL→UI验收；本地测试或部署健康检查不能替代。

- 补充 `WarehouseRegionalScorePolicyTest`、`ScoreEstimateFreshnessTest`、`CertMatchServiceTest`；
- 全量 48 项单元与集成测试绿灯通过；
- 通过 `ArchitectureTest`、`FPJavaArchitectureTest` 等 43 项 ArchUnit 门禁。
