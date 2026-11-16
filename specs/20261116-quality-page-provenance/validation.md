# 本地验证记录

## 版本与范围
基线 `eddf178316c17a3f16c333d7d00ae10b771711fe`，任务分支 `agent/gemini/quality1360-page-diagnosis`。以下为独立审查前执行记录，独立审查若有修改，以review-gate验证日志及最终验收凭证为准。未部署、未触发测试站项目1360重算，未取得原件物理页金标；不声称Word精确分页完成。

## RED → GREEN与反证

- `/tmp/quality1360-red.log`：三个实际入口断言失败（无分页DOCX报告伪页、旧缓存被复用、历史页32仍显示）。`/tmp/quality1360-llm-red.log`：缺少模型摘录却提供通用词首命中位置。
- `/tmp/quality1360-ui-red.log`：多册同页被合并与无分页说明缺失两项失败，其余25项通过。
- `/tmp/quality1360-compliance-r3.log`：`MAVEN_OPTS=-Xmx1g mvn -q -o -f backend/pom.xml test '-Dtest=com.xiyu.bid.compliance.**,FPJavaArchitectureTest,ArchitectureTest'`，退出0。compliance报告汇总1382项，0失败/错误，1跳过；同时执行指定架构测试。
- `/tmp/quality1360-ui-green.log`：详情及drawer composable共38项通过。
- `/tmp/quality1360-build.log`：Vite生产构建退出0；`/tmp/quality1360-eslint.log`四个改动前端文件检查退出0。
- 反证：在try/finally保护下仅移除实际异步入口的最终定位投影，运行原无分页DOCX报告测试重新失败（`/tmp/quality1360-mutation.log`）；源码已恢复。后续验证会重新编译恢复后的源码，不能复用变异class文件。

## 既有失败的基线核对

干净 `git archive HEAD backend` 副本 `/tmp/quality1360-baseline` 单独执行 `FullDocumentSemanticRetrievalAcceptanceTest$EndToEndAdjudicationIntegrationTests`，退出1，1项1失败：期望资格性评审PASS，实际既有SemanticEvidenceBoundary策略要求MANUAL。只校正这一旧验收断言，与当前业务策略对齐；未修改资格判定逻辑、未将真实违规FAIL放宽。

## 门禁与独立审查

Spec Kit的旧prerequisite脚本不支持--require-spec，且不识别agent分支；使用已有SPECIFY_FEATURE环境覆盖进行只读检查成功，不修改脚本以绕过。Spec/Plan/Tasks覆盖FR-001至FR-006；纯核心与Jackson外壳分离，无新增迁移。

现有pre-push pages-contract脚本已加入实际行为子集。最终三技能审查、清理、执行命令及发现处置保存在 `git rev-parse --git-path review-gate` 对应私有元数据，任何代码变更均需重新执行验证。


## 提交PR复核（2026-11-16）

用户追加授权推送与Gitee PR，不含合并、部署或现场重算。任务已rebase到23b30761e，业务代码与先前cf849d3f0一致；仅文档追加冲突，双方内容保留。

补充真实组件浏览器验证：`PLAYWRIGHT_CHANNEL=chrome node node_modules/@playwright/test/cli.js test --config e2e/quality-location.playwright.config.js`，3项通过。Vite编译实际Vue/Element Plus弹窗，在独立Chromium页面验证未知页提示、多册相同页号区分与资质原件名/册内页；不拦截API、不启动开发服务。默认可使用Playwright自带Chromium，本机使用已安装Chrome。截图见 [无物理页定位提示](../../docs/testing/quality-page-location-unpaginated.png)。这是组件浏览器证据，不是测试站部署后的业务端到端验收。

旧本地提交验收记录继续保留；本轮新基准和最终PR head的审查、推送门禁以Git私有review-gate日志及PR描述为准。

兼容边界：新后端可读历史array快照；新快照写version/items对象，旧后端不能读取该新格式。后续若部署再回滚旧版本，需同步考虑报告读取兼容，不可把数据库无迁移等同于双向格式兼容。
