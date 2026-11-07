# P4证据与一致性

用户批准后的新有界周期，旧3轮停止历史保留。环境HEAD/base200fa5bf8，任务分支不变，无开发宿主服务启动，交付仍为PR、不合并部署。

| 契约 | 实现/验证 | 状态 |
| --- | --- | --- |
| AC01对象不反转动作 | control/action/object分组，正向真实Engine PASS/FAIL双向端口、原位页80 | 18项新矩阵通过 |
| AC02否定作用域 | NEGATED_CONTROL只识别动作控制语和指令前禁止；含介词/副词控制 | 8项负例通过 |
| AC03纸面与原文 | 不改requiresPaper/hasPaperTableReference及原字符索引 | 纸面与原位对照通过 |
| AC04既有行为/交付 | 相关146项先通过，强制门禁及独立审查待执行 | 尚未发布 |

RED：mvn -q -o -f backend/pom.xml -Dtest=PricingSubmissionPolicyTest test，18项7fail/0error/0skip，exit1，p4-price-red.log。旧策略对整条匹配扫描“不”，不是动作绑定。
GREEN：新18项及既有相关合计146项零失败/错误，p4-price-green.log，exit0。代码删除整句否定扫描及重复prefix正则，复用静态局部极性识别；无税词白名单，无新增依赖/DTO/数据库。

Mode B准备：删除旧instruction字符串/临时negatedAction变量/循环内编译，原索引映射保留；强制流程还将独立清理并验证当前内容。memu retrieve连接失败，不作为成功知识检索；以仓库规格与真实运行记录为依据。

## P4第1轮独立Spec：NEEDS_FIXES（历史）

已复现“不得直接填报”“禁止投标人在……填报”“严禁填报”被suffix规则漏掉，旧不含税反例虽修但不能放行。按反馈使当前候选失效并SIGINT停止CLI，机器记录中断不代表环境超时。旧30项ada5...清单及p4-spec-probe.log/独立报告保留。

新增控制语RED25项6失败（p4-control-scope-red.log），不枚举“直接/投标人”作为后缀，而分别识别渠道、动作、对象；报价人/栏目/依据修饰语不当动作，前置否定从当前分句读取并识别明确转折重置。否定谓词只检查真实控制区，对象不参与；文字视图等长遮罩使原索引保持。为保留旧不跨句约束，新增跨句测试同一JUnit断言先exit1（p4-clause-red/red.log）再修复句界。最终31项报价及既有相关合计159项零失败/错误，p4-scope-final-green.log。

Mode B额外去掉循环重复整篇toString分配，沿用一次compactText；不改变公共接口、其他核查规则及纸面义务。清理后将由强制流程重新验证。当前P4累计1轮，重新run剩余最多2轮，不重置总上限。

## P4第2轮与最后候选

第二轮独立Spec确认渠道引导段吞入否定词；新增40项报价矩阵先RED，2失败、0错误。CLI同时发现同渠道转折与纸质渠道切换问题，其已写入修复及测试保留。当前候选从实际动作之前的完整分句检查极性，栏目遮罩禁止跨系统报价实体。前两轮失败记录保留，本次强制流程最多执行1轮，失败即停止，不自动开启第四候选。完整门禁、独立审查及PR仍待完成。

## P4最后候选：BLOCKED（累计3轮）

40项报价矩阵以及相关合计171项测试通过、零失败/错误/跳过，日志`/tmp/xiyu-1360-diagnosis/p4-last-green.log`。独立审查随后确认两项Major，测试绿色不构成放行：
- `尚未要求投标人必须填写服务协议`仍被识别为必填，真实Engine硬判FAIL；要求谓词与模态之间的施事人仍破坏否定作用域。
- `在系统报价表中在规定时间内填报不含税折扣率`被DESTINATION当作渠道切换，真实Engine错误FAIL；时间状语不能作为提交目的位置。

独立证据`/tmp/xiyu-1360-diagnosis/p4-last-spec-probe.log`，报告`.omc/review-quality1360-p4-last-spec.md`。按AGENTS三轮失败停止规则，SIGINT结束最后候选自动流程（exit254）；无本轮全量验证通过声明，无有效MERGE_READY凭证。累计P4三候选均未获放行，不能重置轮数或创建PR。保留工作树、历史证据及同步前stash，未commit/push/PR/合并/部署/线上重算。
