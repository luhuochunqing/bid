# Spec Kit 一致性核对
Source: 用户确认四点需求、spec.md、plan.md、tasks.md、现有constitution只读。仓库旧check-prerequisites不支持--require-spec且直接检查数字分支；通过支持的SPECIFY_FEATURE=086-score-estimate-refresh执行--json --require-tasks --include-tasks成功。
## 需求映射
|要求|实现与证据|
|---|---|
|全部生效项刷新|EstimatedScoreService去掉certificateOnly；无证书RED，MySQL真实业绩/仓储匹配|
|真实处理/数值/无分值统计|Refresh保存集合、0分有效、逐项原因，空集拒绝；Controller响应保留原read形状|
|折叠外入口|ScoreParseDrawer sticky工具栏；真实浏览器collapseVisible=false/buttonVisible=true|
|保存与不变性|真实MySQL行核对、候选和阶段2 SELECT *前后相同；项目权限和CAS回归|
|摘要与结果一致|不可变SavedItem绑定本次保存；Controller回读变化409，POST→GET变化前端拒绝摘要；两个窗口RED→GREEN|
## 范围与限制
没有新增业务评分规则/表结构/外部依赖；未改候选/实际评分写入路径。独立审查round1发现P1已修复，round2 PASS；审查工件固定10份源码与测试SHA-256。后端98项、前端83项、build:api与ESLint通过，最新日志在.omc/verify-agent_claude_score-estimate-refresh.md。
## 未满足的完整交付条件
生产1117/1119尚未部署本次修复，quickstart.md列出的现场完整验收未执行。working_tree为当前授权交付上限；未提交、未推送、未创建PR、未合并、未部署。不能用本地回归替代客户现场验收。
