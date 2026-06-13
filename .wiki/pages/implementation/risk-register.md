---
title: 实施风险台账
space: implementation
category: guide
tags: [implementation, delivery]
sources:
  - .wiki/sources/implementation/西域数智化投标管理平台实施计划书SOW2026V1.4(格式校准).docx
  - docs/specs/UAT_PLAN.md
  - .wiki/extracts/contract__西域数智化投标管理平台建设项目合同-V1_0420.docx.md
  - .wiki/sources/contract/附件3-合同报价清单人工摘录.md
backlinks:
  - _index
  - contract-constraints
  - implementation/delivery-playbook
  - implementation/document-delivery-ledger
  - implementation/sow-2026-v1-4
  - implementation/weekly-status
  - implementation/xiyu-pending-confirmations
created: 2026-04-21
updated: 2026-05-28
health_checked: 2026-06-13
---
# 实施风险台账

| 风险 | 等级 | 触发信号 | 应对策略 | 追溯页面 |
|---|---|---|---|---|
| 集成链路不稳定 | 高 | API 联调失败率升高 | 预演 + 回滚演练 + 降级开关 | [[deployment]] |
| 范围漂移 | 高 | 非白名单需求插入 | 严格按正式范围核对 | [[requirements]] |
| 验收证据不足 | 中 | UAT 记录缺失 | 固化报告模板 + 每周补齐 | [[implementation/acceptance-and-closure]] |
| 合同阶段延期 | 高 | 任一阶段文档、测试上线或上线确认延期 | 每周核对合同里程碑，提前升级并形成书面计划变更 | [[contract-constraints]] |
| 第三方前置条件不足 | 高 | API 文档、账号、授权、白名单、测试环境未就绪 | 由甲方或第三方负责事项单独建台账，阻塞项进入周会升级 | [[contract-constraints]] |
| 人员配置不满足合同 | 高 | 项目经理/方案负责人/骨干顾问未按要求驻场或变更 | 人员变更需甲方审查和现场交接，首付款前必须确认人员配置 | [[team-and-timeline]] |
| 报价用户数口径冲突 | 中 | “包含 200 注册用户”与“使用人数 100 人”同时存在 | 按主合同和最终盖章报价单确认，必要时补充书面说明 | [[contract-constraints]] |
| 演示功能混入正式版 | 高 | 客户环境出现未闭环入口、Mock 数据或本地 demo 适配 | 第三阶段按 SOW 要求隔离演示入口并清理 API 模式 Mock 硬编码路径 | [[implementation/sow-2026-v1-4]] |
| UAT 组织不充分 | 高 | 关键用户无法集中参与，UAT 用例缺少签字证据 | 提前锁定 UAT 窗口，按角色安排场景和责任人，验收证据进入问题台账 | [[implementation/acceptance-and-closure]] |
| 上线切换准备不足 | 高 | 回滚脚本、备份、Go/No-Go、生产验证清单未确认 | 发布前完成演练、备份、回滚脚本和 Go/No-Go 评审 | [[deployment]] |

## 关联

- [[implementation/sow-2026-v1-4]]
- [[implementation/weekly-status]]
- [[team-and-timeline]]
- [[contract-constraints]]
