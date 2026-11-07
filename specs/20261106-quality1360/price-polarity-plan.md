# P4实现计划

Goal：消除正向“不含税”对象被当作否定动作的已复现回归。
Architecture：PricingSubmissionPolicy纯Java核心在现有匹配器中显式分隔控制段/动作/对象，仅在控制段判极性；Source索引映射与引擎出口保持。外壳、接口、数据库及其他核查项不改。

1. [ ] PricingSubmissionPolicyTest新增正负矩阵，真实Engine PASS/FAIL双向与纸面/原位对照，业务RED。
2. [ ] 修改指令组及局部极性识别，删除整句扫描，不加税词白名单。Mode B清理重复正则/未用中间值。
3. [ ] 目标与全量相关/三架构、组件/build，原反例旧策略重新失败，当前通过。独立审查至多3轮。
4. [ ] 回填Spec一致性、Wiki/教训与本轮证据；门禁通过后commit/push/PR并校验remote head，保留所有历史停止记录。
