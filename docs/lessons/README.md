# 经验沉淀索引

本目录记录开发过程中积累的根因分析、技术陷阱和工程教训。

## 目录结构

| 文件 | 类型 | 主题 | 日期 |
|------|------|------|------|
| `root-cause-analysis-co-438-fontconfig-head-null.md` | Bug 根因分析 | POI autoSizeColumn "Fontconfig head is null"（systemd 缺 headless + 三层防御 + 多 Agent 并行 PR 教训） | 2026-06-30 |
| `root-cause-analysis-crm-leader-priority.md` | Bug 根因分析 | CRM 商机负责人被自动分配覆盖（tryAutoAssign guard clause 修复） | 2026-06-26 |
| `root-cause-analysis-co-301.md` | Bug 根因分析 | 标讯创建去重拦截报错文案改为"投标管理系统该标讯已存在"（错误消息缺少系统上下文） | 2026-06-22 |
| `root-cause-analysis-tender-test-out-of-sync.md` | Bug 根因分析 | TenderSubmissionServiceTest 测试代码与生产代码不同步（CO-349 移除 TaskService 后测试未更新）导致打包失败 | 2026-06-25 |
| `root-cause-analysis-co-259.md` | Bug 根因分析 | 标讯导入"总部所在地"字段值丢失 | 2026-06-18 |
| `root-cause-analysis-co-264.md` | Bug 根因分析 | CRM更换商机双提示+props不同步 | 2026-06-18 |
| `root-cause-analysis-co-266-co-267.md` | Bug 根因分析 | CRM推送客户信息字段名不一致导致前端不显示 | 2026-06-18 |
| `root-cause-analysis-co-274.md` | Bug 根因分析 | 标讯快速投标未创建项目导致列表不显示 | 2026-06-19 |
| `root-cause-analysis-h13-e2e-fix.md` | Bug 根因分析 | H13改造后E2E测试全面修复（token提取、mock断言、速率限制） | 2026-06-19 |
| `lessons-learned.md` | 通用工程教训 | 后端接口契约变更必须同步前端所有入口、字段必填性变更、前端热更新部署、PR 回滚前必须确认根因、部署期间并发部署导致 502、部署后验证四层模型、stash 丢失找回、同一接口错误形态变化先看日志、PR 已合入后追加修复先确认 merge-base、业务异常消息应包含系统上下文、服务器部署 jar 验证四原则、Bug 修复前必须先验证实际行为、部署前必须验证 jar 中 Flyway 迁移脚本无重复版本、分阶段修复存量数据策略、agent-finish-task.sh 锚点分支占用处理、Policy canUpload/canDelete 权限矩阵必须对称设计、前端禁止 catch silent 吞掉 API 错误、联动回填链路 4 层全链路验证 SOP、权限 Bug 必须审视同一业务动作的所有 UI 入口 + 前后端对称修复（CO-400 五轮 + CO-415 归纳） | 2026-06-30 |
| `crm-integration-lessons.md` | 外部集成经验 | CRM 接口字段映射、405 事故、code 字段错填、status 枚举映射错位、CO-277 id 反查 code、CO-283 附件 URL 双重嵌套、CO-280 跨域 URL 完整地址、CO-262 GAP 附件持久化、projectManagerId 存 User.id 不存工号、调用链覆盖风险 | 2026-06-26 |
| `root-cause-analysis-frontend-404.md` | Bug 根因分析 | 前端热更新部署时动态 import chunk 被误删导致 404 | 2026-06-19 |
| `root-cause-analysis-co-279.md` | Bug 根因分析 | 提交立项 `bidOpenTime` 日期格式解析失败 | 2026-06-19 |
| `root-cause-analysis-co-283.md` | Bug 根因分析 | CRM 标讯文件下载 URL 双重嵌套 | 2026-06-20 |
| `root-cause-analysis-okhttp3-get-body-resttemplate.md` | Bug 根因分析 | OkHttp3 传递依赖导致 RestTemplate GET 请求带 body 抛异常（多轮修复：PR #1362 workaround → #1369 sidecar 根因 → #1373 organization 根因） | 2026-06-30 |
| `root-cause-analysis-co-280.md` | Bug 根因分析 | CRM 附件下载 URL 跨域跳转主页（相对路径跨系统失效） | 2026-06-20 |
| `spring-boot-actuator-gotchas.md` | 技术陷阱 | 业务 API 正常但 actuator health 返回 OUT_OF_SERVICE | 2026-06-19 |
| `element-plus-gotchas.md` | 技术陷阱 | el-cascader 级联选择器与后端字符串字段转换、el-input 宽度不一致 | 2026-06-18 |
| `vue-gotchas.md` | 技术陷阱 | Composable 中 ref 初始化与 props 同步、身份 UI 不要用业务角色做 fallback、UserPicker 统一控件规范 mode=search + 统一接口 | 2026-06-29 |
| `root-cause-analysis-bcrypt-invalid-hash.md` | Bug 根因分析 | OSS 同步员工默认密码 BCrypt 哈希无效导致登录失败 | 2026-06-20 |
| `root-cause-analysis-co-282.md` | Bug 根因分析 | 客户信息 14 行残留与游客兜底（固定矩阵展示策略 + 身份 fallback + SPA 缓存） | 2026-06-20 |
| `root-cause-analysis-co262-crm-eval-gap-files.md` | Bug 根因分析 | CRM 商机关联回填 GAP 附件未持久化导致详情页附件列表为空 | 2026-06-20 |
| `decisions.md` | 架构决策记录 | GAP 附件加载统一通过 DocumentService.getDocuments() 入口、阶段变更通知必须携带明确 actor、CRM 商机负责人优先于本地采购人映射、Controller @PreAuthorize 放宽为 isAuthenticated() 真权限交给 Service 层 Policy | 2026-06-29 |
| `root-cause-analysis-co-375-uploader-delete-permission.md` | Bug 根因分析 | 项目文档删除权限链路不一致导致上传者本人 403（Controller 早过滤 + Policy upload/delete 不对称 + 缺少 uploaderId 维度） | 2026-06-29 |
| `root-cause-analysis-co-390-unified-picker.md` | Bug 根因分析 | 绑定联系人未用统一 UserPicker + /api/admin/users 权限 403 被吞导致投标组长/专员无法搜索 | 2026-06-29 |
| `root-cause-analysis-submit-bid-review-gate.md` | Bug 根因分析 | 提交投标误复用任务完成闸门导致审核通过后仍 409 | 2026-06-21 |
| `root-cause-analysis-stage-notification-created-by.md` | Bug 根因分析 | 阶段变更通知 created_by 为空导致提交投标 500 | 2026-06-21 |
| `shell-gotchas.md` | 技术陷阱 | Shell 转义导致 SQL 中 `$` 特殊字符截断，密码值被破坏 | 2026-06-20 |
| `build-gotchas.md` | 技术陷阱 | git-commit-id-plugin 在 worktree 读取主仓库 HEAD，git.properties 失真；Maven `-DskipTests` 只跳过测试运行不跳过编译；Maven target 目录残留旧 Flyway 迁移文件导致打包后版本冲突 | 2026-06-25 |

## 如何使用

- **遇到 Bug 时**：先查阅 `root-cause-analysis-*.md`，看是否有类似根因
- **使用 Element Plus 组件时**：查阅 `element-plus-gotchas.md` 避免已知陷阱
- **沉淀新知识**：参考 `.claude/skills/knowledge-capture/` 下的模板
