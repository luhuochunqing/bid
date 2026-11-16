# 实施计划

## Summary / Technical Context
Java21、Spring Boot、Jackson外壳、纯核心文档及定位策略，Vue现有弹窗。无数据库迁移，无新依赖。
## Constitution Check
真实API；纯核心不依赖框架；真实错误先RED；隔离任务worktree；不改业务语义词表。
## Design
1. PageContent.pageNumber继续作内部检索ID，新增nullable sourcePageNumber与sourceFileName/sourceFileHash。只在结构化物理页或显式分页符来源填物理页。未知页保留文本块。合并重排内部ID但保持原件身份与页。
2. ReportEvidenceLocationPolicy在所有文本/视觉/资质融合结束后执行一次，核对内部定位与引用，投影成对外页号及来源。文本quote不在该页时不重新全局搜索。历史结果无来源时移除未验证页码，保留结论及引用。
3. SemanticPageRetriever使用可区分的内容块ID；LlmVerdict可选evidenceChunk/evidenceQuote，LlmAdjudicator仅绑定经过原文校验的模型摘录。缺少定位不能用通用关键词定位冒充模型证据。
4. QualityReportSnapshot在既有checkDetails JSON外封version/items；旧array仍可读、标记requiresRecheck；缓存只复用同版本合法完成快照。DTO仍输出items列表。
5. 前端物理页按来源文件展示；无物理页显示按原文检索；历史报告显示重新核查提示。复用Element Plus弹窗，不替换UI框架。
## Rejected Alternatives
统一页码偏移不能修复按字符分块；只改提示保留伪页；移除分块会破坏检索预算；引入新Word渲染平台超出本次已有能力与范围。
## Validation
先在现有异步入口与DTO读出加入RED；再验证新纯核心、模型绑定和来源保留；compliance全模块与架构回归、Vitest及build；变异回退；三技能独立审查。源码、测试、部署、真实原件验收分别报告。
## Delivery
用户最新授权提交Gitee PR：允许push任务分支并创建PR，不包含合并或部署。此前本地验收记录保留为历史证据；当前主干23b30761e，重新验证后发布。
