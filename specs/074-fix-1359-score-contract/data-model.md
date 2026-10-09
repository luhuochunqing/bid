# Data Model

- score_item: 38条既有项保留id/code/dim/detail/来源；weight由原始分变为最终贡献；新增价格公式项，raw max100 × 0.3=30。
- score_parse_fact: 旧原始事实不改；恢复材料保留原始分及来源cell。
- score_result: 权重尺度相关的旧结果先备份，不将原始尺度分数保留为新规则得分。
- score_parse_task: 最新295的待复核状态不改；旧207的original_total_weight=200为归一化前原始合计，保持不变；manualRecovery记录39/100并备份旧审计字段，不伪造模型运行成功。
- score_parse_review: 逐项人工调整审计记录，原因带原文分部比例和恢复批次。

验收范围经用户裁定：只恢复评分标准39/100，AI实际打分暂不可用，按钮未强制禁用。MySQL修复/回滚全部DML封装在临时存储过程的EXIT HANDLER（ROLLBACK/RESIGNAL）内，即使客户端--force继续也不能部分提交。
