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

## Decision 5: 历史补发按当前完整接收人独立重发

**Decision**: 正常 09:00 扫描继续负责从未提醒的到期项目。一次性历史补发只选择 `predictedBidDate <= cutoff` 且原提醒时间早于执行日零点的既有提醒记录，并对每个项目向当前实时在职项目经理和全部目标角色的去重并集创建一条独立历史补发通知。旧通知关系和旧企微状态不作为“已送达”依据。

**Rationale**: 测试库 274 个到期项目全部已有提醒日志，但只有 150 个项目曾把经理加入站内关系，项目经理企微成功数为 0；按“只补缺失角色”会再次把日志或站内关系误当成真实送达。独立通知既保留历史审计事实，又能让当前完整接收人明确收到一次补发。

**Alternatives considered**: 删除日志再跑正常扫描会重复发布商机事件并破坏审计；直接 SQL 写通知会绕过应用事务和企微异步链路；只重试历史失败接收人无法覆盖被旧本地 `enabled` 错误排除的经理和实时角色变化。

## Decision 6: 一次性任务复用 09:00 调度但默认关闭

**Decision**: 增加一个独立薄任务，与正常扫描同在 09:00 调度。`enabled=true` 时，应用启动完成后先调用同一服务执行一次强制 preview，输出候选与预计关系数且绝不写入；到 `runDate` 当天 09:00，只有配置同时具备明确 `cutoff` 且 `mode=execute` 才允许产生副作用，其余配置仍只 preview。候选要求旧提醒时间早于 `runDate` 零点，因此两个 09:00 任务的执行先后不会造成当天新提醒重复。测试和生产执行完成后关闭配置，最终清理一次性任务代码。

**Rationale**: 这符合用户要求的 09:00 自然执行，又不新增管理页面或长期公开端点。启动预览可在 09:00 前用真实部署配置核对范围；执行日期、截止日、模式和默认关闭构成四层防误触边界，持久通知标记支持中断后重跑。即使服务在 09:00 后重启，启动阶段也只会预览，不会补发。

**Alternatives considered**: 临时管理端点增加权限和删除前暴露面；第二个 Spring Boot 进程会启动其他 runner/scheduler；修改原扫描日志或 cron 会混淆正常业务语义。

## Decision 7: 使用独立通知来源作为无迁移幂等标记

**Decision**: 历史补发通知继续使用 `OPPORTUNITY_PREDICTION` 类型，但使用固定的独立 `sourceEntityType` 和项目 ID。执行前查询同来源、同项目、同类型通知；存在即跳过。当前部署为单实例，一次任务只解析一份实时收件人快照并串行创建通知。

**Rationale**: 现有 `notification` 已持久化且可按来源、项目、类型查询，无需为一次性动作新增表或迁移。通知创建仍走 `NotificationApplicationService`，自动生成站内关系和企微异步任务。

**Alternatives considered**: 新增 replay 表能提供数据库唯一键但对一次性动作过重；内存标记无法跨重启；复用原通知 ID 无法让已失败的经理重新产生一条清晰可见的新提醒。
