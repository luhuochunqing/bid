# 砍 npm script 别名

> 分支：`agent/grok/cut-npm-aliases`  
> 对应 ponytail 审计项 4。

## 删了什么

33 条 npm script，106 → 73。

- 完全重复：`dev:api`=`dev`，`check:token-governance:warn`=`check:token-governance`，`dev:up`=`docker:up`
- 第二套 CLI：`dev:stable:*`（18）→ `scripts/dev-services.sh` / `dev-services-launchd.sh`
- `dev:docker:*`（5）→ `scripts/local-docker-stack.sh`
- `agent:{morning,up,restart,stop,status,logs}` → `scripts/agent-dev.sh`
- `agent:pre-push-dry-run` → `scripts/pre-push-dry-run.sh`

## 没删

CI / `build` / hook 真正调用的：`build`、`build:api`、`test:unit`、`lint`、`wiki:check`、`agent:lock-*`、`check:*`、`docker:*`、`dev`、`dev:all`。

## 活路径改动

README / RELIABILITY / backend README / 4 个脚本的提示文案改指向 bash。归档文档和 `backend/implementation-notes.md` 历史记录未改。
