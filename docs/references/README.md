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

## 新增约定

- 仅放**本项目真实依赖、且 AI 容易写错**的外部知识。
- 文件名约定：`<tool>-notes.md` 或 `<tool>-llms.txt`。
- 内容应为可投喂的纯文本/Markdown，不要放二进制。
- 引用本项目实际代码位置（如 `document-converter-sidecar/requirements.txt`）以便交叉验证。

## 外部参考

- 后端依赖清单：`backend/pom.xml`
- 前端依赖清单：`package.json`
- sidecar 依赖清单：`document-converter-sidecar/requirements.txt`
