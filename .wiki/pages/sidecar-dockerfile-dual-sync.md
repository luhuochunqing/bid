---
title: Sidecar Dockerfile 双文件同步陷阱
space: engineering
category: pitfall
tags: [Sidecar, Dockerfile, 部署, 陷阱, 文档转换]
created: 2026-09-19
updated: 2026-09-19
health_checked: 2026-10-25
sources:
  - document-converter-sidecar/Dockerfile
  - document-converter-sidecar/Dockerfile.cn
  - scripts/release/package-release.sh
  - scripts/release/remote-deploy.sh
backlinks:
  - _index
  - deployment
---
# Sidecar Dockerfile 双文件同步陷阱

## 陷阱描述

`document-converter-sidecar/` 下存在**两个 Dockerfile**：

| 文件 | 用途 |
| --- | --- |
| `Dockerfile` | 直连 Docker Hub 默认源版 |
| `Dockerfile.cn` | 国内镜像源加速版（阿里云 debian + 清华 PyPI），**服务器实际构建使用此文件** |

`scripts/release/remote-deploy.sh` 构建镜像时硬编码使用 `-f "$SIDECAR_DIR/Dockerfile.cn"`，而 `package-release.sh` 将两个文件都打包进 `sidecar.tar.gz`。**新增 sidecar Python 模块时若只更新一个文件，另一个漏更新，服务器构建的镜像就会缺模块。**

## 真实事故（2026-09-18 第 190 次测试部署）

- PR !2697（docx 截断自愈）新增 `docx_repair.py` 模块，只更新了 `Dockerfile` 的 COPY 行，**漏更新 `Dockerfile.cn`**。
- 服务器构建镜像时（用 `Dockerfile.cn`）缺少 `docx_repair.py` → `ModuleNotFoundError` → Sidecar 容器 crash-loop。
- `remote-deploy.sh` 的 Sidecar 健康门禁（30 次重试）拦截 rollout，旧版本继续运行，服务未中断。
- 修复：PR #2699 同步两个 Dockerfile 的 COPY 行，重新打包部署成功。

## 防复发清单

1. **新增/删除 sidecar Python 模块时**，必须同时检查 `Dockerfile` 与 `Dockerfile.cn` 的 `COPY` 行（两文件业务逻辑必须一致）。
2. **打包后校验**：解压 `.release/<id>/sidecar/sidecar.tar.gz`，grep 两个 Dockerfile 的 COPY 行确认新模块都在。
3. 部署日志中若看到 `#N CACHED` 且 COPY 行内容与预期不符，说明构建上下文用的是旧文件——立即检查 `Dockerfile.cn`。
