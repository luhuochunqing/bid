# Ponytail 低风险清理 — 实施笔记

> 分支：`agent/grok/ponytail-safe-cuts`  
> 范围：审计项 7–11、19。不动 hexagon / Task 表 / npm 别名。

## 做了

- 删 `src/utils/icons.js` + spec（生产零引用，侧栏走 `CommonIcon`/`icons.ts`）
- 删 `typescript`、`@typescript-eslint/*`（eslint ignore `src/**/*.ts`）
- 活脚本/CI/`.env.example` 去掉 `VITE_API_MODE`；健康检查改断言 `config.js` 的 `mode: 'api'`
- 删 `src/utils/demoPersistence.js`；`check-front-data-boundaries` 仍拦截 import
- 删 `src/composables/useDebounce.ts`（零调用）
- 删 `backend/docs/security/security-audit-report-2026-03-19.md`（`docs/architecture/security/` 留一份）

## 没动

- `eslint-import-resolver-typescript`：不在 7–11 名单里
- 归档文档、`db/migration/`、包内 README
- `deploy.sh` 两次 `npm run build` 收成一次：第二次只差一个已死 env

## 风险

健康检查以前在找 `config.js` 里不存在的 `VITE_API_MODE` 字符串。现改为 `mode: 'api'`，这才是真实信号。
