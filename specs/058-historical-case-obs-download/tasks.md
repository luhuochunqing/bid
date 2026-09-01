# Tasks: 历史案例原文直接下载

**Feature**: 058-historical-case-obs-download | **Branch**: agent/qoder/case-obs-download

**Format**: `- [ ] [TaskID] [P?] [Story?] Description with file path`

## Phase 1: Setup

- [ ] T001 确认任务分支基线：`agent/qoder/case-obs-download` 基于 origin/main（4876a395e），工作区无未提交改动
- [ ] T002 任务 worktree 安装前端依赖（`pnpm install --prefer-offline`），确认 `npx vitest --version` 可用

## Phase 2: Foundational（阻塞所有用户故事）

- [ ] T003 编写迁移 `backend/src/main/resources/db/migration-mysql/V1205__add_obs_bid_file_key_to_knowledge_case.sql`：`ALTER TABLE knowledge_case ADD COLUMN obs_bid_file_key VARCHAR(512) NULL COMMENT '历史切片原版投标文件 OBS 对象键，导入管道写入'`，并跑 `bash scripts/check-flyway.sh`（或等价门禁）确认版本号无冲突
- [ ] T004 `backend/src/main/java/com/xiyu/bid/casework/domain/model/KnowledgeCase.java` 增加 `obsBidFileKey` 字段（含 JPA `@Column(name="obs_bid_file_key")` 映射）
- [ ] T005 [P] `backend/src/main/java/com/xiyu/bid/casework/infrastructure/persistence/CaseMapper.java` 与 `CaseDTO` 透出 `obsBidFileKey`，使 `GET /api/cases` 响应包含该字段
- [ ] T006 [P] 编写纯核心策略类测试 `backend/src/test/java/com/xiyu/bid/file/domain/core/ObsObjectKeyPolicyTest.java`：basename 提取（中文/空格/括号）、`..` 与绝对路径拒绝、历史根前缀校验
- [ ] T007 实现纯核心策略类 `backend/src/main/java/com/xiyu/bid/file/domain/core/ObsObjectKeyPolicy.java` 使 T006 全绿（无框架依赖）

## Phase 3: User Story 1 - 历史案例一键下载原版标书 (P1)

- [ ] T008 [US1] 编写签发编排测试 `backend/src/test/java/com/xiyu/bid/casework/application/service/CaseDownloadUrlAppServiceTest.java`（Mockito mock `KnowledgeCaseRepository` + `ObsDownloadUrlGateway`）：有 key→签发成功返回 url+filename+300s；key 为空→业务异常"该案例暂无原版标书"；案例不存在→异常；签发网关异常→可降级错误
- [ ] T009 [US1] 实现 `backend/src/main/java/com/xiyu/bid/casework/application/service/CaseDownloadUrlAppService.java`：查案例→取 `obs_bid_file_key`→`ObsObjectKeyPolicy.filename`→`ObsDownloadUrlGateway.signDownloadUrl(配置桶, key, 300)`（桶名与有效期取自 `application.yml` obs 配置）
- [ ] T010 [US1] 编写 Controller 层测试 `backend/src/test/java/com/xiyu/bid/casework/controller/KnowledgeCaseControllerDownloadUrlTest.java`：200 结构（url/filename/expireSeconds）、404 案例不存在、409 无原文、502 签发失败（对齐 `specs/058-historical-case-obs-download/contracts/case-download-url.md`）
- [ ] T011 [US1] `backend/src/main/java/com/xiyu/bid/casework/controller/KnowledgeCaseController.java` 增加 `GET /api/cases/{caseId}/download-url` 端点，使 T010 全绿
- [ ] T012 [US1] `scripts/slice_all_v9.py`：JSONL 记录增加 `obs_bid_file_key` 字段（`2026年（投标文件）/{project_dir}/{docx文件名}` 相对桶根路径），保留其余字段不变
- [ ] T013 [US1] `scripts/import-case-slices-to-knowledge-case.py`：写入 `obs_bid_file_key` 列；`--self-test` 增断言（该字段非空且以 `2026年（投标文件）/` 开头）
- [ ] T014 [US1] 前端 API 客户端：`src/api/modules/` 相应模块（案例库现有模块）新增 `getCaseDownloadUrl(caseId)`，返回 `{url, filename, expireSeconds}`
- [ ] T015 [US1] 编写分流契约测试（US1 分支）`src/views/Knowledge/views/__tests__/useBidCaseSourceActions.spec.js`：`obsBidFileKey` 非空→调签发 API→用返回 url+filename 触发 `<a download>`；端点报错→提示且不落盘
- [ ] T016 [US1] 修改 `src/views/Knowledge/views/useBidCaseSourceActions.js`：`openBidDocument` 增加最高优先级分支（`obsBidFileKey` 非空走直链），使 T015 全绿
- [ ] T017 [US1] `src/views/Knowledge/views/CaseGrid.vue` L126-132 内联 `openBidDocument` 改为复用 `useBidCaseSourceActions`（消除第二套实现），保持现有测试绿

## Phase 4: User Story 2 - 系统生成案例保留档案打包下载 (P1)

- [ ] T018 [US2] 补分流契约测试（US2 分支）`src/views/Knowledge/views/__tests__/useBidCaseSourceActions.spec.js`：无 `obsBidFileKey` 且 `sourceProjectId` 存在→档案预检→`exportProjectArchive`（行为与现状一致，防回归）
- [ ] T019 [US2] 核查 `useBidCaseSourceActions.js` 现有 ZIP 分支零改动通过 T018（本故事以回归保障为主，不新增代码；若 T017 复用重构引入偏差则在此修复）

## Phase 5: User Story 3 - 无原文案例的明确反馈 (P2)

- [ ] T020 [US3] 补分流契约测试（US3 分支）`src/views/Knowledge/views/__tests__/useBidCaseSourceActions.spec.js`：两者皆无→提示"标书原文暂不可用"，不调用任何下载接口
- [ ] T021 [US3] 确认 `useBidCaseSourceActions.js` 现有兜底分支满足 T020（现有实现已覆盖，仅验证；如需微调提示文案在此完成）

## Phase 6: Polish & Cross-Cutting

- [ ] T022 全量测试：`npx vitest run`（前端相关 spec）+ `cd backend && mvn test -Dtest='ObsObjectKeyPolicyTest,CaseDownloadUrlAppServiceTest,KnowledgeCaseControllerDownloadUrlTest'`，收集证据
- [ ] T023 测试环境数据重导：scp 两脚本到 172.16.38.78 → 重跑切片 → `--purge` + 重导 300 条 → SQL 验证 `obs_bid_file_key` 300 条非空
- [ ] T024 端点抽测：部署后端后用真实案例 id 调 `GET /api/cases/{id}/download-url`，curl 下载验证 200 + 文件可打开（含 1 条中文文件名样本）
- [ ] T025 部署测试环境（`xiyu-server-deploy` 环境门禁确认后执行），完成 quickstart 四场景 + 系统案例 ZIP 可解压（zip-verify）
- [ ] T026 `speckit-analyze` 一致性验证 + pre-push 14 道门禁 + 知识沉淀（docs/lessons + .wiki 检查）+ 创建 PR

## Dependencies

- T003→T004→T005（数据基础）
- T006→T007→T009→T011（后端签发链）
- T012→T013（导入管道）可与后端并行
- T014→T015→T016→T017（前端链）依赖 T005（字段透出）与 T010 契约
- T018-T021 依赖 T016/T017 完成
- T022-T026 串行，依赖全部代码任务

## Parallel Opportunities

- T005 ∥ T006（不同文件）
- T012/T013（脚本）∥ T008-T011（后端）∥ T014（前端 API 模块）

## Implementation Strategy

- MVP = US1（T003-T017）：历史案例直链下载可用即交付核心价值
- US2/US3 为回归保障与兜底确认，成本极低
- 数据与部署（T022-T026）作为独立交付阶段，部署前需用户环境确认
