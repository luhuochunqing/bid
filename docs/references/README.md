<!-- 一旦我所属的文件夹有所变化，请更新我。 -->
# 外部知识内部化 (docs/references/)

AI 代理无法访问 Google Docs 或外部网页。本目录把**本项目实际依赖、但 AI 训练集覆盖薄弱**的外部框架/专有 SDK 文档，以纯文本格式内部化投喂。

> 模板原示例 `nixpacks`、`uv` 经确认本项目**未使用**，已剔除；下面是本项目真实盲区。

## 目录结构

| 文件 | 覆盖内容 | 盲区原因 |
|---|---|---|
| `ehsy-client-sdk.md` | 西域集团专有 org-event ClientSDK | `<scope>system</scope>` 专有 jar，未公开，依赖 `backend/libs/ClientSDK-release_0.0.2.jar` |
| `wangeditor-notes.md` | wangEditor 富文本编辑器（`@wangeditor/editor`） | 中文生态开源项目，西方训练语料覆盖弱 |
| `markitdown-sidecar-notes.md` | markitdown 文档转换库 | 非 PyPI 安装，从工作 venv 拷贝，坑点多 |
| `crm-field-mapping.md` | 西域 CRM 商机字段映射与查询策略 | CRM 为外部系统，字段语义、匹配策略、日期精度需项目内约定 |
| `crm-integration-lessons.md` | 外部系统对接经验教训（CRM 事故复盘） | 跨系统语义、测试、兜底、Feature Flag、日期契约等隐性知识 |
| `jpa-hibernate-lessons.md` | JPA/Hibernate 持久化层经验教训 | INSERT-before-DELETE flush 顺序、Lombok @Data 循环引用等陷阱 |
| `vue-gotchas.md` | Vue 3 陷阱与调试经验 | el-upload 事件绑定、内联表达式 vs 函数引用等陷阱 |
| `spring-boot-gotchas.md` | Spring Boot 陷阱与调试经验 | Bean 名冲突（文件移动未删旧文件）等陷阱 |
| `rollback-recovery-playbook.md` | 回退恢复 Playbook（内部经验） | cherry-pick 优先纪律；CO-338 恢复时手工重写导致 git blame 丢失的复盘 |

## 新增约定

- 仅放**本项目真实依赖、且 AI 容易写错**的外部知识；以及**少量高价值内部经验复盘**（命名 `<topic>-playbook.md`，如 `rollback-recovery-playbook.md`）。
- 文件名约定：外部知识用 `<tool>-notes.md` 或 `<tool>-llls.txt`；内部经验用 `<topic>-playbook.md`。
- 内容应为可投喂的纯文本/Markdown，不要放二进制。
- 引用本项目实际代码位置（如 `document-converter-sidecar/requirements.txt`）以便交叉验证。

## 外部参考

- 后端依赖清单：`backend/pom.xml`
- 前端依赖清单：`package.json`
- sidecar 依赖清单：`document-converter-sidecar/requirements.txt`
