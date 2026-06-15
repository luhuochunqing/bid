# FRONTEND.md — 前端专属规范与入口

写前端代码前先读这里。技术栈：Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router 4 + Axios + ECharts + Sass。

## 本主题现有内容（按需查阅）

| 概念 | 位置 |
|---|---|
| 前端真实数据治理（唯一 API 源，禁 Mock） | `docs/specs/FRONTEND_REAL_DATA_GOVERNANCE.md` |
| Mock 政策（已清空，硬约束） | `SECURITY.md §Mock 政策` |
| 目录结构约定 | `src/` 各模块 README |
| 设计系统落地 | `docs/design-system/MASTER.md` |
| 动态表单引擎设计（artifact） | `docs/artifacts/dynamic-form-engine-v1-*.html` |
| CA 管理设计（artifact） | `docs/artifacts/ca-management-design-*.html` |
| wangEditor 用法（盲区） | `docs/references/wangeditor-notes.md` |
| 前后端对比 | `docs/architecture/frontend-backend-comparison.md` |

## 关键硬约束（一句话）

- API-only：`src/api/config.js` 硬编码 `mode: 'api'`，禁止在 `src/mock` 或非 API 路径写代码。
- 富文本入库前必走 `dompurify.sanitize()`。

## 新增前端规范请放

- 模块/视图 README：对应 `src/views/<Module>/README.md`、`src/components/<group>/README.md`
- 设计/原型：`docs/design-system/`、`docs/prototypes/`
