# 验证记录

- 基线：fe6407f7c（原始 RED 基线为2bc6de989）；任务分支 agent/trae/warehouse-reminder-investigation，当前为未提交变更。
- RED：`mvn -f backend/pom.xml -Dtest=ReminderConcurrencyIntegrationTest test`，4 tests / 4 failures（生成2次、发送2次、未来重试被执行、状态未回滚）；日志 /tmp/warehouse-reminder-red.log。
- 首轮 GREEN：受影响三类16 tests，0 failure/error；日志 /tmp/warehouse-reminder-green.log。
- 扩展 MySQL8：同组三类19 tests，0 failure/error，独立 reminder_verify 库；日志 /tmp/warehouse-reminder-mysql.log。
- `npm run build` exit 0；包含版本、前端边界、文档、令牌、蓝图、行预算与产物检查；日志 /tmp/warehouse-reminder-frontend-build.log。
- Spec Kit 一致性：FR001→T001/T002；FR002→T003/T004；FR003/004→T005；FR005→隔离测试配置；无业务范围漂移。保持显式任务分支，使用 SPECIFY_FEATURE 指定规格目录。模板与仓库旧版扩展入口命名不同，使用仓库现有 create-new-feature.sh 复用分支。可选自动提交 hooks 不执行，交付目标 working_tree。
- 生产风险边界：仓库/投递行短粒度事务，无全批次锁；投递持锁期间一次外发有3s连接/10s读取超时。无表结构、权限、接收人、API与配置变更。现有实例需要统一升级；外发成功但本地提交失败仍可能重试，本次不承诺跨系统 exactly-once。
- 最终验证与独立审查记录在 Git 私有 review-gate 工件；没有部署或线上验收结论。

- 独立审查 round1 Major：真实推送依赖 REQUIRED 事务异常导致外层 rollback-only；新增测试 RED：1 test / 1 failure，期望 PENDING_RETRY、实际 PENDING。修复采用 NOT_SUPPORTED 隔离，保留外层任务行锁。原共享构建目录全量结果已作废；后续全量验证使用独立 CoW 构建副本。基线变更导致首轮自动门禁 BLOCKED，当前基线已同步。

- 第二轮 MySQL8：20 tests / 0 failures / 0 errors，包含真实代理 rollback-only 防护；日志 /tmp/warehouse-reminder-mysql-round2.log。首轮清理后架构与受影响用例62项通过；本轮代码更新后须重新跑。

## fix 第1/3轮：单仓库失败隔离（2026-11-06）

- 检查范围为 fe6407f7c8f53c44b668511bb77fe0a47a3f6426 到当前工作区的完整差异，含未跟踪文件。本轮仅修复审查指出的逐仓库失败隔离及对应测试/记录。
- 修改前保护：`mvn -q -f backend/pom.xml -Dtest=WarehouseExpiryScanTaskTest,NotificationDeliveryJobServiceTest test`，12项通过；日志 `/tmp/warehouse-fix-protection.log`。
- RED：新增 `shouldContinueAfterRejectedWarehouse`，以200字符合法仓库名调用真实 NotificationDispatchPolicy；即将到期/已到期两输入均复现未捕获的 IllegalStateException（9项运行、2 errors），命令 `mvn -q -f backend/pom.xml -Dtest=WarehouseExpiryScanTaskTest test`；日志 `/tmp/warehouse-fix-red.log`。
- 最小修复：单仓库 TransactionTemplate.execute 外捕获 RuntimeException；失败事务回滚后记录仓库ID及异常，继续下一仓库，失败不计入成功数。纯核心及提醒策略未修改。
- GREEN：`mvn -q -f backend/pom.xml -Dtest=WarehouseExpiryScanTaskTest,NotificationDeliveryJobServiceTest,ReminderConcurrencyIntegrationTest test`，23项、0 failures/errors/skipped。H2内存真实事务测试验证拒绝仓库状态回滚、正常仓库通知持久化及下轮去重；既有创建异常后通知及状态回滚断言保留。日志 `/tmp/warehouse-fix-green.log`。
- `git diff --check` 通过。未启动开发服务或监听端口；MySQL真实环境重验交主执行器 verify。本轮没有提交、push、PR、部署或自批凭证。

## PR 提交前更新（2026-11-06）

- 用户已授权提交 PR；任务分支同步到 `200fa5bf80b86b425e488ebb39a6e49aaf6d3260`，仅知识记录追加冲突，保留双方内容并将本次教训顺延为494。
- 上一轮本地修复的66项 MySQL/架构验证、构建及三技能审查通过，代码已同步回任务工作区。旧固定基线凭证不复用到新基线，PR前重新运行正式流程。
- 旧基线FE全量快照11610项、91失败、25跳过；干净FE基线两组50项中42失败，其中41项明确是缺少可用的Sidecar Python，另1项是评分解析断言差异，不能统称业务代码缺陷。本轮使用开发宿主已安装的Sidecar解释器实跑推送必需的FullRule/CrossFormat回放，不通过跳过开关放行。
- 创建 PR 不等于合并或部署；投递副作用的跨系统崩溃窗口、所有共享数据库调度实例应统一升级的条件保持不变。
