# Quickstart / verification

## 发布前必须确认（本地实施未执行发布）

**新链路默认 OFF。只部署代码不会启用新提取，也不会改变旧 task 274 的快照或当前生效项。**

1. 按正式发布流程应用 V1219，发布相互匹配的后端、Sidecar、前端。
2. 只读核对实际进程/容器加载的构建 SHA、Sidecar 版本以及 `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=true`（绑定 `app.score-parse.extraction-version.enabled`）。源码 application.yml 的默认值不是运行时已启用的证据。
3. 只读核对 V1219 的 Flyway 记录、快照/active version 表及当前项目生效指纹。不要用修改共享数据库来补造来源或版本。
4. 用户授权真实重解析后创建**新**解析任务，确认持久化任务模式为 `EXTRACTION_VERSION_V1`。旧任务没有该模式不能作为新链路验收。
5. 分别验收最新全候选、生效 13 个计分单位/100 分、警告保留、规则完整原文、重复解析保留旧版本、显式切换 CAS、旧打分拒绝、多角色 HTTP/DB/browser 权限。真实模型三次稳定性另记；固定历史 claims 回放不替代它。

本次未执行以上环境变更、共享 DB、重解析、付费模型或部署验收。

## 本地命令

隔离 worktree：`/Users/user/xiyu/worktrees/xiyu-codex-scoreparse-extraction-v2`。

```sh
# 指向已安装 Sidecar requirements 的 Python；此宿主只读复用开发宿主的 venv。
export SIDECAR_PYTHON=/Users/user/xiyu/worktrees/trae/document-converter-sidecar/.venv/bin/python
export DOCKER_API_VERSION=1.44
# 实际执行隔离 MySQL8 容器；需要 Docker。私有历史语料缺失的两条测试另列 skip。
mvn -q -f backend/pom.xml '-Dtest=com.xiyu.bid.scoreparse.**.*Test,ArchitectureTest,ResponsibilityArchitectureTest,FlywayRollbackScriptCoverageTest,PreAuthorizeAuthorityOssMappingCoverageTest,EntityTableMigrationCoverageTest' test
# 运行前归档 target/surefire-reports，结果不能混入旧子集 XML。
(cd document-converter-sidecar && PYTHONDONTWRITEBYTECODE=1 "$SIDECAR_PYTHON" -B -m pytest -q)
./node_modules/.bin/vitest run src/composables/projectDetail/*Score*.spec.js src/composables/projectDetail/score*.spec.js src/views/Project/stages/components/Score*.spec.js
node --test scripts/check-token-coverage.test.mjs scripts/check-pre-push-dispatch.test.mjs
bash scripts/check-scoreparse-root-cause.sh
npm run build
SPECIFY_FEATURE=073-fix-1382-scoreparse SPECIFY_FEATURE_DIRECTORY=specs/073-fix-1382-scoreparse bash .specify/scripts/bash/check-prerequisites.sh --json --require-tasks --include-tasks
```

根因脚本使用离线 Maven（本机依赖需已准备），不需要私有原件、HTTP 服务或数据库。Java 回归直接用 `SIDECAR_PYTHON` 将十一份提交的合成 DOCX 字节转换为当前 artifact，再经过 binder/qualification/snapshot，转换 30 秒超时或缺依赖会失败，不跳过。

`pre-push-gate.sh` 已接入可执行根因脚本及 token CLI 回归，保留 `--skip-tests` / `SCOREPARSE_GATE_SKIP=1` 原有显式边界。普通 agent 分支不带参数时仍执行新增根因/token 检查；它的自动前端/E2E 快捷策略独立保留。分发测试执行实际脚本片段并记录命令，仅证明路由，不代表整次 push。本地 `.githooks/pre-push` 是任务开始前已有的 S6 wrapper overlay，未覆盖；直接执行根因脚本通过不等于实际本地 hook 被执行，更不等于整份 pre-push 所有检查通过。本次未 push。

## 固定历史源回放与证据界限

实际 PDF/artifact、task274 的全部 40 claims 与人工章节标注保存在忽略目录 `.runtime/root-cause/1382-v2/`。`task274-scope-annotations.json` 按 factId 记录章节和原文 headingLine，校验 source-local.md SHA；历史事实缺 section 字段，不能宣称系统从旧记录自动恢复了所有章节。标注将目标候选绑定第三章评审办法，履约考核绑定真实第四章合同条款及格式（5296–5407 行一带），不再以输入顺序或“第四章技术要求”代替证据。反转 claims 顺序仍得到同一可计算集合；声明总分从实际 artifact/context 取得，未向校验器传入预期 100。

`PrivateExtractionSnapshotReplayTest` 覆盖绑定、完整原文映射与全部 40 候选；`ScoreExtractionPersistenceMysqlTest.privateFortyCandidateSourceFreezesActivatesAndReadsThirteenAt100` 在真实 MySQL8 容器经 completion -> freeze -> activation -> read 保留全部 40、激活 13/100，物流仓储原文未验证警告仍在、履约考核不激活。它使用固定历史模型输出，未执行新模型请求。

真实 HTTP 序列化、多角色授权和浏览器尚未验收。应用测试的 typed 400/404/409、注入权限拒绝与静态 ACL 扫描只能证明各自本地边界。

## 验证记录

任务5历史精确运行日志及 XML 记录在 `.superpowers/sdd/scoreparse-1382-post2767-plan/task-5-report.md`；FBR-I1 最终统一修复以同目录 `final-fix-report.md`、对应冻结 SHA 清单和归档 XML 为准。运行结果与外部验收分别列示，不把历史跳过或未执行环境步骤计为通过。

完整规则回归：`FullRuleSourceReplayTest` 对 actual DOCX 的 exact/name-score/prebound/anchor 四路逐一验证完整规则或保留 MODEL_CLAIM；额外覆盖 XML/逻辑行标识缺失、无关预绑定单元格、文本短引文后编号材料及 PDF 物理连续性。`ScoreExtractionPersistenceMysqlTest` 通过真实 MySQL 检查首次/显式激活、自动重解析保留旧指纹/明细/结果、legacy 公开候选采纳及评分取项的规则文本。已定位引文不表示完整来源单元闭合；`FULL_RULE_UNIT_UNRESOLVED` 阻止自动替换但不抹除已知数值。
