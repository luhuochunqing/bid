# R3实现计划

**Goal:** 关闭R2两个角色阻断，减少以字符串整体比较代替指令作用域的错误。
**Architecture:** 纯核心ScoringRequirementRolePolicy返回显式角色；内部指令值保存动作、对象集合、极性和原始作用范围。ScoringResponsePolicy保留字段/占位与EvidenceTextSlice证据责任；外壳、DTO与持久化不改。
**Tech Stack:** Java21/JUnit/现有Engine，无新增依赖。controller=none，由主Agent实现，双轴独立只读审查。

1. [x] 新真实Engine回归矩阵，先运行两个已知反例和正常/反向对照RED，固定基线及日志。
2. [x] 解析顶层句/指令与括注，不让括注内部动作冒充新应答；每个动作对象拆集合。省略模态继承前一协调极性，显式模态重置。条件或完整绑定不明时角色未知/人工。
3. [x] ScoringResponsePolicy消费新角色，删除旧REQUESTED_FIELD/OBJECT_END及局部布尔聚合，不改来源模型。
4. [x] Mode B清理，目标/全部相关+三架构及构建；隔离恢复关键缺陷证明同一断言RED；独立全量源码清单审查最多三轮。
5. [x] Spec一致性、教训/Wiki和当前handoff回填，只交付本地代码；未通过则保留明确阻断。

拒绝方案：继续新增句尾正则（此前多轮已反证）；通用中文语义平台/新AI调用（超范围、不确定性未消除）；统一人工（掩盖明确应填缺陷）。

完成：双轴round2通过，仅本地交付；证据与限制见instruction-r3-analysis.md。

## PR重跑 ai-slop-cleaner 第1轮清理计划（2026-11-06）

范围为基线200fa5bf80b86b425e488ebb39a6e49aaf6d3260至工作树的全部差异，含未跟踪文件；本阶段只做行为保真清理。先运行四类评分/角色回归及身份证/Engine回归。按风险顺序：合并身份证命中分支重复的anchor/snippet计算；将Engine出口嵌套校准表达式展开为顺序局部值，保持调用顺序与参数。无新增依赖、公共接口或语义变更，不删除仍有调用者的兼容重载。修改后重跑受影响行为、架构与格式检查；监听端口测试由主执行器verify阶段执行。

## 本次阶段补充清理计划

六个无监听目标类162项保护测试通过后，仅消除PricingSubmissionPolicy.reconcile中纸面材料要求的重复布尔计算，复用同方法已有requiresPaper；不改短路结果、状态、证据或公共接口。无死代码和可安全合并的角色解析边界则保留，避免扩大清理。修改后重跑上述保护集及报价、偏离相关测试和架构检查。

## 本次调度fix第1/3轮补充

仅处理独立Google两项Major：先运行报价/评分无监听保护集，再加动作转折与纸面渠道切换、被否定要求的反例RED；最小修改PricingSubmissionPolicy和ScoringRequirementRolePolicy，不改外壳。保留税价对象、真实必填FAIL、纸面义务及原文页号对照，执行受影响纯核心回归和三架构检查。主执行器负责后续verify及独立审查；本阶段禁止提交、push、PR、部署、嵌套门禁和自批凭证。
