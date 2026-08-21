# 第 140 次测试环境部署报告 — 2026-08-21

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | sidecar OOM 根治落地（流式落盘 + 进程内根因修复）+ AI 案例推荐 UI 调整 + **sidecar Dockerfile COPY 缺陷修复** |
| 部署序号 | 第 140 次（测试） |
| 部署时间 | 2026-08-21 16:32 CST（backend 激活）；sidecar 容器替换完成于 17:00 前后 |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `a1f09384e` |
| 上一版本 Release | `c14ccbf7f`（2026-08-21 13:39 CST 激活） |
| 基线 commit | `a1f09384e`（= origin/main，本次打包源码） |
| 健康检查 | ✅ remote-deploy 内通过（79 次尝试后连续 3/3 UP） |
| 部署结果 | ✅ 成功（含一次 sidecar 容器崩溃循环修复） |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | **无**（增量 commit 不含 `migration-mysql/` 改动） |

## 变更内容（14 个 commit）

### sidecar OOM 根治（PR !2373 / !2375）
- `672b5d93d` 根治 mammoth 图片 base64 内联导致的内存膨胀（OOM root cause）
- `6b2e1f9e9` /convert 接口流式落盘与增量 SHA-256 计算（修复超大标书 1GB 容器内 OOM）
- `692c63801` 测试/生产容器内存限制加固 + README 启动命令固化 --memory 参数
- `b0cbcd325` lessons-learned §143 sidecar Docker 容器无内存限制挤占宿主内存事故沉淀
- `7e245ec81` sidecar OOM 根治知识沉淀——根因分析文件 + §144 猴补丁陷阱 + 决策 #6 进程池隔离
- `a1f09384e` / `6be484973` README 补 SIDECAR_MAX_CONVERSIONS_PER_WORKER / SIDECAR_MAX_IMAGE_BYTES 配置表行

> 以上即第 139 次部署报告遗留待办「sidecar 内存泄漏根治」的落地交付。

### AI 案例推荐 UI 调整（PR !2374）
- `69fa9b850` AI 案例推荐去掉复用次数并改详情三模块布局
- `c7e022192` E2E：AI 推荐卡片不展示复用次数

### 文档归档
- `026ff95e4` 第 139 次测试环境部署报告（随 PR !2371 合入）

## ⚠️ 特殊事件：sidecar 容器崩溃循环（Dockerfile COPY 缺陷，已修复）

### 时间线（CST）

| 时间 | 事件 |
|---|---|
| 16:38 左右 | sidecar 新镜像首次构建成功（`5240aa7df334`，Dockerfile.cn，约 8 分钟） |
| 16:46+ | 容器替换后进入崩溃循环：`ModuleNotFoundError: No module named 'image_extractor'` |
| ~16:55 | 定位根因：仓库 `Dockerfile` / `Dockerfile.cn` 的 COPY 行均为 `COPY app.py converter.py ./`，**漏掉 `image_extractor.py`**；新 app.py 已 `from image_extractor import extract_images` |
| ~16:58 | 服务器侧 sed 修正 COPY 行 → 层缓存秒级重建（镜像 `6dea120bfbab`）→ 容器替换 |
| ~17:00 | sidecar 健康 `{"status":"up"}`；后端 health 的 sidecar 组件显示 reachable |

### 根因分析

- `image_extractor.py`（/convert-images 抽图模块）此前已入库，但两个 Dockerfile 的 COPY 行从未包含它。
- 旧容器能正常运行是因为其镜像构建自更早的 app.py（尚无该 import）——缺陷处于潜伏状态。
- 本次增量 app.py 改动使 import 在启动期生效，缺陷第一次暴露为启动失败。
- **本质**：sidecar 应用源文件新增时，Dockerfile COPY 列表无门禁约束同步更新。

### 处置

1. 服务器侧：sed 修正 `/opt/xiyu-bid/sidecar/Dockerfile.cn` COPY 行并重建镜像（apt/pip 层全部命中缓存，重建仅数秒）。
2. 仓库侧：本 PR 同步修复 `document-converter-sidecar/Dockerfile` 与 `Dockerfile.cn`（`COPY app.py converter.py image_extractor.py ./`），消除服务器与仓库漂移。

### Sidecar 容器最终参数（与原容器一致）

```bash
docker run -d --name xiyu-sidecar --restart=always -p 8000:8000 \
  --memory=1g --memory-swap=1g \
  -e SIDECAR_HOST=0.0.0.0 -e SIDECAR_PORT=8000 -e SIDECAR_MAX_UPLOAD_MB=3072 \
  document-converter-sidecar:latest
```

## 验证结果

### Flyway

| 检查项 | 结果 |
|---|---|
| validate（remote-deploy 内置） | ✅ 通过（260 迁移 checksum 全匹配） |
| 本地源码最新版本 | V1199（与 DB 一致，本次无新增迁移） |
| 启动日志 Flyway 行为 | ✅ 仅既有 `outOfOrder mode is active` WARN，未执行任何迁移、无错误 |

### 部署后 Smoke（经 Nginx 8080）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ UP（db / redis 6.2.19 / jwt STRONG / aiProvider gemini-3.5-flash / **sidecar reachable `{"status":"up"}`** 全绿） |
| `/actuator/readiness`（8080） | 403（预期：内部端点不经 Nginx 暴露；health 组件 readinessState=UP） |
| POST `/api/auth/login`（错误凭据） | ✅ 401（拒绝生效） |
| GET `/api/projects`（无 token） | ✅ 403 |
| GET `/api/crm/health`（无 token） | ✅ 403 |
| 前端首页 | ✅ 200（入口 `assets/index-CGylnuIL.js` 与 jar 内一致性校验通过） |
| sidecar `/health`（容器内直连） | ✅ `{"status":"up"}` |

### 前端旧资源保留（跨版本 404 防护）

- 从上一版本 release 目录 `cp -rn /opt/xiyu-bid/releases/c14ccbf7f/frontend/assets/. /srv/www/xiyu-bid/assets/`
- 保留 **80 个**旧 hash 文件（178 → 258），属主修正为 nginx:nginx。

## 其他收尾

| 项目 | 结果 |
|---|---|
| GitHub 镜像同步 | ✅ `sync-to-github.sh` 完成，Gitee main = GitHub main = `a1f09384e`（同步前落后 15 commits） |
| 配置清理检查 | ✅ 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（测试环境标准运维配置，非调试残留） |
| DB 直连查询 | RDS 账号授权受限（Access denied），迁移验证改用启动日志证据（见上），不影响应用 |

## 经验教训

1. **Dockerfile COPY 清单是隐形契约**：向 sidecar 新增 Python 模块文件时必须同步更新 `Dockerfile` 与 `Dockerfile.cn` 两处 COPY 行。建议后续增加轻量门禁（如 CI 校验 app.py/converter.py import 的本地模块均出现在 COPY 列表）。
2. **潜伏缺陷的暴露时机不可控**：旧镜像"能跑"不代表 Dockerfile 正确——它只是还没被新代码触发。镜像重建类操作（哪怕只是例行同步）都可能成为暴露点，部署窗口要预留崩溃循环的处置时间。
3. **层缓存是应急重建的朋友**：只改 COPY 行时 apt/pip 层全命中，重建从 8 分钟降到数秒，服务器侧热修成本低。

## 关联

- 上一份报告：`docs/release/deploy-report-2026-08-21-139th-test.md`（遗留待办「sidecar 内存泄漏根治」本次闭环）
- 修复分支：`agent/trae/sidecar-dockerfile-copy-fix`（含 Dockerfile×2 修复 + 本报告）
