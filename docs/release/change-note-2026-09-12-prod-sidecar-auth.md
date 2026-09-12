# 生产环境 Sidecar 鉴权启用 · 变更摘要

> 归档日期：2026-09-12
> 变更类型：安全加固（配置变更，非版本发布）
> 影响环境：生产 `172.16.10.149`（winbid-01）

## 1. 背景与根因

文档转换 Sidecar 监听 `0.0.0.0:8000`，为后端提供文档转换与评分/片段抽取能力。启用前未配置 `SIDECAR_SHARED_KEY`：

- `backend.env` 无 `SIDECAR_SHARED_KEY`，Sidecar 容器 env 亦无该变量
- 实测 `/convert` 无 key 与任意 key 均返回 `200` → 生产文档转换端点完全无鉴权可达

该端口需接收任意来源文档，未鉴权存在资源滥用（DoS）、数据投毒与解析攻击面收敛需求。

## 2. 变更内容

| 项 | 内容 |
|---|---|
| ① `backend.env` | 追加 64 位随机 `SIDECAR_SHARED_KEY`（长度 64，指纹 `510c1b625af0c8b6`） |
| ② Sidecar 容器 | 重建 `xiyu-sidecar`，注入 `-e SIDECAR_SHARED_KEY=同值` |
| ③ 后端 | 重启 `xiyu-bid-backend`，使各适配器从 `${SIDECAR_SHARED_KEY}` 读取并发送 `X-Sidecar-Key` |
| ④ 持久化 | `remote-deploy.sh` 已透传 `SIDECAR_SHARED_KEY`（PR #2629 合入 main `1d23dbeba`），后续部署自动续用 |

密钥两侧同值（`backend.env` 与 Sidecar 容器），后端适配器（MarkItDown / score-parse 系列）统一通过 `${SIDECAR_SHARED_KEY}` 兜底读取。

## 3. 安全加固效果

- 防资源滥用/拒绝服务：未授权方无法提交任意文档耗尽 CPU/内存
- 防数据投毒：拦截绕过后端将任意内容送入下游分析/向量库
- 收窄攻击面：阻断可疑文件试探转换器解析漏洞
- 明确调用方：仅持有共享密钥的后端适配器可调用，入站来源可溯

## 4. 验证证据

| 场景 | 结果 |
|---|---|
| `/convert` 有效文件 + 无 key | 401 ✅ |
| `/convert` 有效文件 + 错误 key | 401 ✅ |
| `/convert` 有效文件 + 正确 key | 200 ✅ |
| 后端 health / readiness | 200 ✅ |
| Nginx 外部 health | 200 ✅ |

## 5. 影响面与回滚

- 影响面：后端→Sidecar 全链路不受影响（任务/评分/抽取正常）；仅阻断未带共享密钥的外部调用
- 回滚：移除 `backend.env` 的 `SIDECAR_SHARED_KEY` 行并重建容器去掉该 env 即可恢复无鉴权态

## 6. 配置一致性

- 口径与测试环境（`172.16.38.78`，指纹 `f1ace7b255eaa82d`，同日启用）一致
- 测试与生产密钥相互独立，各自按环境独立管理

## 7. 密钥保管说明

- `SIDECAR_SHARED_KEY` 为高敏感凭据，明文不进入仓库、日志与对话流
- 负责人保管副本位于本地受控文件（`chmod 600`），指纹用于交接/核对
- 如发生人员变更或疑似泄露，可重新生成并同步 `backend.env` + Sidecar 容器两侧