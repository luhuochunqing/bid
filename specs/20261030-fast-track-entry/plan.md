# 实施计划

1. 用失败测试证明入口不得优先调用模型。
2. 删除错误模型主通道及其专用完成器、快照、测试；结构化代码通道优先，旧流程惰性兜底。
3. 保留 StructuredTableResult / VersionedScoreParseCompletion 的原文证据与发布保护。
4. 执行入口、结构化管线、双轨、金标与架构回归，独立核对1135真实文件验证条件。
5. 删除优先清理、两轴审查、更新现有PR2930，不合并不部署。

文件范围：ScoreParseAppService、ScoreParseEntryRouter及测试；撤回本PR模型端口/请求/完成器/快照改动及专用用例；对应spec、教训及wiki。
