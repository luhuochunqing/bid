# Research: 商机预测实时收件人与历史补发

## Decision 1: 本地缓存负责枚举身份，OSS 批量结果负责当前事实

**Decision**: 每轮扫描从本地 `users` 读取有工号身份及站内用户 ID，把全部去重工号传给 `OrganizationDirectoryGateway#getUserJobAndRoleListByJobNumbersStrict`。提醒专用严格查询对失败批次短暂重试一次，再递归二分隔离组合型异常；`code=0/200` 的空数据是完整成功。任一单工号仍失败或超过恢复预算时整轮 fail-closed，不创建通知和项目提醒日志。普通组织同步继续使用原有允许部分结果的查询，不改变既有语义。只有 OSS 实际返回且判定在职的条目可成为项目经理或目标角色接收人。

**Rationale**: OSS 批量接口只能按工号查询，无法直接按角色列成员；若先用本地 `enabled` 或 `role_id` 缩小候选，会继续遗漏状态或角色缓存错误的人员。生产实测约 8,593 个工号、约 9 秒，符合每日一次、60 秒内完成的约束。测试环境实测存在一个 50 人组合返回 HTTP 500、拆分后分别为空成功和有数据成功的确定性边界，因此必须同时区分“失败”和“成功但无匹配”。

**Alternatives considered**: 仅查本地 enabled 用户会漏掉本地状态过期的在职人员；仅查本地目标角色会重现当前 0 人根因；先执行全量组织同步会改变大量用户缓存，扩大本次修复半径。

## Decision 2: 角色对象先过滤活动状态，再保留 roleCode 或 roleName

**Decision**: OSS `sysRoleList` 为对象时，布尔型 `del=true`（兼容数字 `1`）或 `status=0` 的角色不进入 DTO；活动角色优先保留 `roleCode`，缺少 code 时保留 `roleName`。字符串型旧响应保持兼容。目标角色文本统一通过已有大小写安全映射归一化。

**Rationale**: 当前 mapper 丢弃 roleCode 且不看角色状态，无法证明提醒命中的是当前有效角色。复用已有 `JobRoleLookupResolver.mapOssRoleTextToInternal` 能同时识别规范角色码和中文角色名。

**Alternatives considered**: 给 DTO 新增完整角色对象会波及所有构造点和同步代码；继续只保留 roleName 无法在同名或映射缺失时使用 OSS 规范 code。

## Decision 3: 账号状态采用提醒专用 fail-closed 规则

**Decision**: `status=1` 或明确“启用/在职”才视为可接收；`status=0`、离职、停用或缺失状态均不发送。若工号未出现在批量返回中，也不发送。

**Rationale**: 这是外部消息接收人选择，误发给离职人员的风险高于漏发一轮；正常同步模块的“字段缺失默认启用”不适用于通知发送。

**Alternatives considered**: 复用本地 `enabled` 已被生产证据证伪；复用同步模块宽松兜底会在不完整响应中误发。

## Decision 4: 每轮只解析一次实时快照

**Decision**: `OpportunityPredictionReminderService.runScan` 在处理项目循环前调用一次专用 resolver，得到目标角色 ID 集合和实时在职用户 ID 集合。循环内只做集合查找和去重。

**Rationale**: 避免每项目外部调用和 N+1；与既有批量加载结果、标讯、负责人模式一致。

**Alternatives considered**: 每个经理单独查询 OSS 会产生数百请求且故障面更大；全局缓存会让“实时”口径漂移并增加失效策略。

## Decision 5: 历史补发不删除提醒日志

**Decision**: 从未提醒项目在修复后调用现有 `runScan(cutoff)`；已有经理提醒项目由一次性脚本查询现有通知与接收人，只为缺失的实时核心角色调用现有通知创建 API，复制原通知文案与目标链接。脚本默认 dry-run，按项目和用户检查已有关系，允许中断后重跑。

**Rationale**: 删除日志会让 114 个项目的经理重复收信，也会扰动事件库投递状态；直接插数据库会绕过站内通知事务和企微异步任务。复用现有 API 的副作用最完整。

**Alternatives considered**: 新增永久后台页面或通用补发框架属于一次性需求的过度设计；Flyway/SQL 无法复用预测和外发链路；重置日志会制造重复通知。

## Decision 6: 手动扫描入口只作为生产执行中间态

**Decision**: 先尝试通过现有运行设施调用 `runScan(cutoff)`；若不存在可调用入口，则增加一个 `/api/admin/**` 下、显式确认且默认禁用的临时操作端点，部署并执行后在同一任务中删除、再次部署。最终产品代码不保留该入口。

**Rationale**: 生产现状没有定时方法的手动调用接口，而等待下一日 09:00 不满足立即补发。临时端点可复用运行中 Spring 上下文，且比启动第二实例更能避免并发调度与端口副作用。

**Alternatives considered**: 第二个 Spring Boot 进程会启动其他 runner/scheduler；临时修改 cron 容易重复触发；JVM 调试注入不可审计。
