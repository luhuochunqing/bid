# R2一致性与因果证据

旧周期第三轮NEEDS_FIXES保持有效；用户继续后采用新角色/来源契约。R2仅working_tree，HEAD adbb2facf614b2299c95bee19fc3dd65e574a7fa，无提交、上线或线上重算。

| 验收 | 实现/验证 | 当前状态 |
| --- | --- | --- |
| AC01明确协议义务 | requiredRole先于OTHER_MATERIAL；真实Engine+模型PASS对照 | 11项新测试GREEN；独立审查中 |
| AC02参考/冲突 | MaterialRole区分样例和冲突；人工不遮蔽后续明确FAIL | 新测试GREEN；独立审查中 |
| AC03全文无分页 | EvidenceTextSlice(null,fullText)，两个策略同源 | 新测试GREEN；独立审查中 |
| AC04输入页与摘录 | 命中切片直接携带页号/索引，违规不再二次定位 | 新/既有回归验证中 |
| AC05既有行为 | 完整compliance与三架构；无前端变化 | r2-full.log运行中 |

## RED/GREEN

新增Quality1360RoleAndLocationTest原实现运行11项，4断言失败+2缺少结果错误，证据/tmp/xiyu-1360-diagnosis/r2-red.log。修复后11项零失败/错误/跳过，r2-green.log。RED锁定真实业务漏洞，不是编译失败。所有源码和测试(含untracked)26项冻结清单source-inputs-r2-round1.json，SHA4780aae3d73fc506888c54b298325b9f5efab6d0a590f112b98f712781a7a6fe。

## Mode B有界清理

删除资格策略合成page1及违规全文重新定位；删除不再需要的bidDoc参数、评分重复空页分支。EvidenceTextSlice仅两个真实调用者，未新增接口、依赖或通用框架。角色使用不可变命中记录，不把可变Matcher传出后继续扫描。清理后全量相关回归重跑；独立审查结论待写。

## 保留限制

上游DocumentContent可能带虚拟分页，本周期只禁止策略内部伪造页号；不声称DOCX已具备物理分页来源。25项其他潜在缺口、真实图像验收与同输入线上重算不因本批测试而消失。

## R2独立round1：NEEDS_FIXES

两个审查者又独立复现后接动作/参考用途导致角色丢失、否定义务被反向解析、全文目录遮蔽真实正文、冗余fullText空遮蔽合法分页。原报告保留，不能用1112项零失败掩盖这些反例。

新增6项形成17项测试，round2 RED有5断言失败及1缺少结果错误，r2-round2-red.log。指令解析明确区分modality/object/action-purpose；非必填角色独立NOT_REQUIRED，实际应答与其他角色同时存在则冲突人工。全文不作为物理目录页剔除；合法分页不依赖冗余全文。新源码26项清单source-inputs-r2-round2.json，SHA bee2264c850980061f4969434a8061de06342dc46e34acc10e046c3c40dc632d，独立round2与当前完整回归待完成。

## R2独立round2：NEEDS_FIXES

并列指令及括注用途仍被对象截断丢弃。新增4项真实Engine回归；隔离编译测试类，旧策略运行同一JUnit断言的3场景全失败（其中2场景最终PASS），当前策略3场景全通过，日志r2-round3-red/red.log与r2-round3-current/green.log。没有修改共享target或线上数据。

最终实现以并列指令为非贪婪匹配边界，逐个保留动作对象；括注的参考用途参与角色判定。Mode B继续删除贪婪单对象假设，不另建通用解析框架。冻结清单source-inputs-r2-round3.json，SHA e14d66e6d51b5e1c09d7315fceb117503dc1d6d934b2294a04fffff0c2ac1113。完整相关/三架构r2-round3-full.log和双轴round3正在执行；仍不宣称整个25项准确率达标。

## 最终R2 round3：未关闭，停止修改

Standards复现“无需提供服务协议并填写响应时间，以上两项均不作要求”仍硬FAIL；Spec复现“须填写服务协议、响应时间”丢失第一对象义务，实际协议留白最终PASS。分别违反AC02和AC01。文本来源两条修复未出现新阻断，但不能因此判整个周期完成。详细反例、日志和下一周期契约见handoff.md当前R2章节。按技能三轮上限停止源码/测试修改；最终完整日志仍只证明已有覆盖。

## 最终执行证据

最终相关+三架构命令exit0，1122项、0失败、0错误、1外部PDF跳过，其中21项R2目标回归全通过；日志r2-round3-full.log。最终源码清单26项逐项一致。diff/行预算通过；Wiki全量197项原有陈旧日期问题，本任务页无违规。独立第三轮剩余两个反例未写入测试，按三轮上限停止，R2-AC01/02仍未满足，不宣称固定完毕。正式验证工件.omc/verify-quality1360-r2.md。
