# Wiki 操作日志 / Operation Log

> 按时间倒序记录所有 Wiki 操作。每条记录以 `## [日期] 操作类型 | 说明` 格式开头。
> 可用 `grep "^## \[" .wiki/log.md | tail -5` 查看最近 5 条。

## [2026-04-24] ingest+build | SOW V1.4 执行基准入库
- 新增源文件：`.wiki/sources/implementation/西域数智化投标管理平台实施计划书SOW2026V1.4(格式校准).docx`
- 新增抽取件：`.wiki/extracts/implementation__西域数智化投标管理平台实施计划书SOW2026V1.4(格式校准).docx.md`
- 新增页面：`pages/implementation/sow-2026-v1-4.md`（开发排期、产品规划、实施推进、验收判断、上线切换和运维保障主基准）
- 更新页面：`overview.md`、`requirements.md`、`architecture.md`、`data-model.md`、`roles-and-permissions.md`、`team-and-timeline.md`、`deployment.md`、`contract-constraints.md`、`implementation/{delivery-playbook,milestones,acceptance-and-closure,risk-register,weekly-status}.md`
- 执行口径：后续开发、产品规划和项目实施均优先核对 SOW V1.4；真实 API 为唯一交付路径，历史 Mock/demo 适配仅作为待清理遗留
- 版本口径：Wiki 统一按 `SOW V1.4` 执行；若原始 Word 正文仍出现 `V1.3`，作为对外签发前需校准的显示问题处理

## [2026-04-23] ingest+build | 合同与附件硬约束入库
- 新增源文件：`.wiki/sources/contract/` 下合同正文、附件 3 报价清单 PDF、附件 4 需求任务书
- 新增人工摘录：`.wiki/sources/contract/附件3-合同报价清单人工摘录.md`（扫描 PDF 无文本层）
- 新增页面：`pages/contract-constraints.md`（范围、付款、里程碑、验收、运维、违约责任约束）
- 更新页面：`overview.md`、`requirements.md`、`team-and-timeline.md`、`deployment.md`、`implementation/{milestones,acceptance-and-closure,risk-register}.md`
- 更新脚本：`scripts/wiki-ingest.mjs` 将 `contract/` 纳入源目录说明
- 校验结果：`npm run wiki:ingest`、`npm run wiki:build`、`npm run wiki:check`、`npm run check:doc-governance` 均通过（pages=20）

## [2026-04-22] build | 设计系统知识页入库与总览口径更新
- 新增页面：`pages/design-system.md`（正式 DESIGN.md 基线、落地策略、实施回链）
- 更新页面：`pages/overview.md`（切换为真实 API 唯一路径口径，补充设计系统建制信息）
- 自动重编：`pages/_index.md`、`PAGE_INDEX.md`、`catalog/page-catalog.json`
- 校验结果：`npm run wiki:build` 与 `npm run wiki:check` 均通过（pages=19）

## [2026-04-15] ingest | 附件5：需求任务书 + 附件6：功能清单
- 来源：`.wiki/sources/bidding/` 下 2 个文件（.docx + .xlsx）
- 新建页面：`requirements.md`（需求追溯，29 功能点追溯矩阵）
- 更新页面：`_index.md`（新增 requirements 导航）
- 更新文件：`INDEX.md`（新增"招标需求文档"分类，重编章节号）

## [2026-04-15] init | Wiki 知识库初始化
- 创建三层架构：`WIKI.md`（Schema）+ `.wiki/INDEX.md`（源索引）+ `.wiki/pages/`（知识页面）
- 创建源文档目录：`.wiki/sources/{bidding,industry,competitor,customer,technical,internal}/`
- 源文档编目：11 个分类，70+ 源文件
- 生成 11 个 Wiki 页面：overview, architecture, business-process, modules, ai-capabilities, data-model, roles-and-permissions, glossary, team-and-timeline, deployment, _index
- 交叉引用校验通过：所有 `[[wiki-link]]` 指向有效页面
- 更新根文件：CLAUDE.md, README.md

## [2026-04-22] upgrade | 双栈 Wiki 升级（研发 + 实施）
- 新增自动化脚本：`scripts/wiki-ingest.mjs`、`scripts/wiki-build.mjs`、`scripts/wiki-check.mjs`
- 新增目录：`.wiki/extracts/`、`.wiki/outputs/`、`.wiki/catalog/`
- 双索引落地：`.wiki/INDEX.md`（Source Catalog）+ `.wiki/index.md`（Page Catalog）
- 新增 Implementation Space 页面：`implementation/{delivery-playbook,milestones,risk-register,weekly-status,acceptance-and-closure}.md`
- 执行真实增量演示：摄入 `docx + xlsx` 源文件并生成抽取结果与 catalog
- pre-commit 门禁新增 `npm run wiki:check`

## [2026-04-30] update | 正式上线时间统一为 2026-07-10
- 变更依据：根据本周与客户达成的一致，正式上线时间统一为 2026-07-10
- 更新页面：`implementation/sow-2026-v1-4.md`、`implementation/milestones.md`、`team-and-timeline.md`、`overview.md`、`requirements.md`、`contract-constraints.md`、`implementation/document-delivery-ledger.md`、`implementation/attachment4-requirement-task-book.md`
- 执行口径：当前以 2026-04-27 启动准备、2026-05-07 项目启动会、2026-05-09 首场正式客户访谈、2026-07-10 正式上线为项目里程碑基线
- 来源同步说明：`.wiki/sources/`、`.wiki/extracts/` 与 `.wiki/pages/` 中涉及正式上线时间的口径已统一为 2026-07-10
