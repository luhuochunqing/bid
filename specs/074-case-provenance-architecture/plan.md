# 实施计划：案例来源回填架构修复

日期2026-10-09；分支agent/gemini/case-provenance-architecture；规格[spec.md](spec.md)。设计已获用户批准。

## Summary
新增专用应用端口和持久化适配器；应用服务只依赖不可变候选与来源更新。现有Markdown章节纯核心、装载编排和统计不变。

## Technical Context
Java21 / Spring Boot3.2 / JPA / MySQL8 / JUnit5 / ArchUnit / JaCoCo。后端分层修复，无API、UI、迁移。严格ACTIVE查询；每项目一次批量读取与保存，避免逐案例查库。

## Constitution Check
不可变record满足FP-Java；框架与实体只在副作用适配器；不修改纯核心定位算法；原架构失败为RED；新持久化测试真实数据库，无生产Mock；方法级REQUIRES_NEW事务按项目隔离；无两参数toMap；文件不超过300行。设计前后检查均满足，无豁免。

## Project Structure
- backend/src/main/java/com/xiyu/bid/casework/application/port/CaseProvenanceBackfillPort.java
- backend/src/main/java/com/xiyu/bid/casework/infrastructure/CaseProvenanceBackfillAdapter.java
- backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillAppService.java
- backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillBoundaryTest.java
- backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillAppServiceTest.java
- backend/src/test/java/com/xiyu/bid/casework/infrastructure/CaseProvenanceBackfillAdapterMysqlTest.java

## 执行顺序
1. 保留已复现架构失败，补服务边界测试与真实数据库适配器测试。
2. 引入端口，应用层把实体修改改为五字段更新命令；适配器事务内批量加载，先核对全部ID再映射保存。
3. 运行全部回填/入口/装载测试、架构和真实数据库测试；原架构规则不动。
4. 完整后端测试与覆盖率生成，区分基线其他测试问题；独立复审、文档与Wiki回填。

## 工作流兼容记录
配置branch_numbering已弃用，应使用feature_numbering，本任务不修改工具配置。before_specify Git扩展脚本缺失，使用现有核心create-new-feature.sh建立074规格；核心脚本未支持GIT_BRANCH_NAME，立即返回已授权独立任务分支并用feature.json关联规格。可选commit hooks延至验证后提交。origin/main新增PR2809落地提交，实施前fast-forward到最新main，保持独立分支。
