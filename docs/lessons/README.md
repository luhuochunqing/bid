# 经验沉淀索引

本目录记录开发过程中积累的根因分析、技术陷阱和工程教训。

## 目录结构

| 文件 | 类型 | 主题 | 日期 |
|------|------|------|------|
| `root-cause-analysis-co-259.md` | Bug 根因分析 | 标讯导入"总部所在地"字段值丢失 | 2026-06-18 |
| `root-cause-analysis-co-264.md` | Bug 根因分析 | CRM更换商机双提示+props不同步 | 2026-06-18 |
| `root-cause-analysis-co-266-co-267.md` | Bug 根因分析 | CRM推送客户信息字段名不一致导致前端不显示 | 2026-06-18 |
| `root-cause-analysis-co-274.md` | Bug 根因分析 | 标讯快速投标未创建项目导致列表不显示 | 2026-06-19 |
| `root-cause-analysis-h13-e2e-fix.md` | Bug 根因分析 | H13改造后E2E测试全面修复（token提取、mock断言、速率限制） | 2026-06-19 |
| `lessons-learned.md` | 通用工程教训 | 后端接口契约变更必须同步前端所有入口、字段必填性变更、前端热更新部署、PR 回滚前必须确认根因、部署期间并发部署导致 502、部署后验证四层模型 | 2026-06-20 |
| `crm-integration-lessons.md` | 外部集成经验 | CRM 接口字段映射、405 事故、code 字段错填、status 枚举映射错位、CO-277 id 反查 code、CO-283 附件 URL 双重嵌套、CO-280 跨域 URL 完整地址 | 2026-06-20 |
| `root-cause-analysis-frontend-404.md` | Bug 根因分析 | 前端热更新部署时动态 import chunk 被误删导致 404 | 2026-06-19 |
| `root-cause-analysis-co-279.md` | Bug 根因分析 | 提交立项 `bidOpenTime` 日期格式解析失败 | 2026-06-19 |
| `root-cause-analysis-co-283.md` | Bug 根因分析 | CRM 标讯文件下载 URL 双重嵌套 | 2026-06-20 |
| `root-cause-analysis-co-280.md` | Bug 根因分析 | CRM 附件下载 URL 跨域跳转主页（相对路径跨系统失效） | 2026-06-20 |
| `spring-boot-actuator-gotchas.md` | 技术陷阱 | 业务 API 正常但 actuator health 返回 OUT_OF_SERVICE | 2026-06-19 |
| `element-plus-gotchas.md` | 技术陷阱 | el-cascader 级联选择器与后端字符串字段转换、el-input 宽度不一致 | 2026-06-18 |
| `vue-gotchas.md` | 技术陷阱 | Composable 中 ref 初始化与 props 同步 | 2026-06-18 |
| `root-cause-analysis-bcrypt-invalid-hash.md` | Bug 根因分析 | OSS 同步员工默认密码 BCrypt 哈希无效导致登录失败 | 2026-06-20 |
| `root-cause-analysis-co-282.md` | Bug 根因分析 | 客户信息 14 行残留与游客兜底（固定矩阵展示策略 + 身份 fallback + SPA 缓存） | 2026-06-20 |
| `shell-gotchas.md` | 技术陷阱 | Shell 转义导致 SQL 中 `$` 特殊字符截断，密码值被破坏 | 2026-06-20 |
| `build-gotchas.md` | 技术陷阱 | git-commit-id-plugin 在 worktree 读取主仓库 HEAD，git.properties 失真 | 2026-06-19 |

## 如何使用

- **遇到 Bug 时**：先查阅 `root-cause-analysis-*.md`，看是否有类似根因
- **使用 Element Plus 组件时**：查阅 `element-plus-gotchas.md` 避免已知陷阱
- **沉淀新知识**：参考 `.claude/skills/knowledge-capture/` 下的模板
