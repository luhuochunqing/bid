# 第 126 次测试环境部署报告 — 2026-08-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 126 次（测试） |
| 部署时间 | 2026-08-17 14:09:16 CST（服务启动） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `78facc5b2` |
| 上一版本 Release | `9c9b9d91d`（2026-08-17 11:36:15 CST，第 125 次测试部署） |
| 基线 commit | `78facc5b2`（origin/main HEAD，含 PR #2305 评分解析 % 转义修复） |
| 健康检查通过 | ✅（80 次尝试后 3/3 连续通过） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无（纯 Java + 测试 + 文档变更） |
| Smoke 测试 | 全部通过 |
| GitHub 镜像 | ✅ 已同步（两边 main = `78facc5b2`） |

## 背景：本次为 P1 线上 Bug 热修复

第 125 次部署（`9c9b9d91d`）上线的 **AI 评分标准解析功能 100% 必现崩溃**：

```
⚠ 解析失败: Conversion = '"'
java.util.UnknownFormatConversionException
  at ScoreParsePrompts.buildCandidateExtractionPrompt(ScoreParsePrompts.java:51)
```

### 根因（lessons-learned §117）

`ScoreParsePrompts` 的 prompt 模板（text block + `.formatted()`）第 26 行示例文案 `占30%"` 中，`%` 后紧跟英文双引号（0x25 0x22），构成非法转换符 `%"`。**与招标正文无关**——`chunkText` 经 `%s` 参数透传不参与格式解析，非法格式符在模板自身文案里，任何项目第一步候选提取必炸（项目 225 于 11:42 复现，距部署仅 6 分钟）。

### 修复内容

| 类型 | 文件 | 说明 |
|---|---|---|
| bug fix | `ScoreParsePrompts.java:26` | `占30%` → `占30%%`（Formatter 字面百分号转义） |
| test | `ScoreParsePromptsTest.java`（新建）| 4 个 prompt 构建方法回归测试 |
| docs | `lessons-learned.md §117` + `.wiki/pages/score-parse-service.md` | 教训沉淀 + wiki 回填 |

PR #2305（含全仓同类扫描结论：无其他活 bug）。

## 增量改动（9c9b9d91d → 78facc5b2，5 commits）

```
78facc5b2 auto-merge PR #2305（% 转义修复）
4548fca2b docs: 沉淀 lessons §117 + wiki 回填
09c295bae fix(scoreparse): prompt 模板字面 % 转义
6d451ba9c !2304 docs: 第 125 次部署报告
9f4eae4c5 docs: 第 125 次部署报告
```

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK（251 migrations，checksums match） |
| Step 2: DB 版本对比 | 源码无新迁移（V1190 已是最新），无需应用 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

## 部署步骤与验证

1. ✅ 环境门禁（AskUserQuestion 确认测试环境 + PR 合并授权）
2. ✅ 合并 PR #2305（squash → `78facc5b2`）
3. ✅ 锚点分支同步 main + 工作区干净
4. ✅ 服务器现状检查（`9c9b9d91d` 健康 UP）
5. ✅ Flyway 预检 3 步法
6. ✅ 本地打包（154M，`obsEnabled=true`、`apiBaseUrl=""` 同源、250 V 迁移无重复、Detail chunk `.upload(`×2）
7. ✅ scp 上传 + MD5 校验一致（`fdfaa146...`）
8. ✅ remote-deploy（SYSTEMCTL_SUDO=true + DB 备份 `winbid-78facc5b2-*.sql.gz`）
9. ✅ 健康检查 3/3 通过，前端一致性（`index-D7w8-u8O.js`）
10. ✅ 旧 assets 保留（防跨版本 404）
11. ✅ Smoke：health UP / readiness 200 / login 400（预期）/ projects 403（预期）
12. ✅ **修复字节级验证**：部署 jar 的 `ScoreParsePrompts.class` 常量池含 `占30%%`×1、`占30%"`×0
13. ✅ GitHub 镜像同步（两边 main 一致）

## 回滚信息

- 回滚点：`/opt/xiyu-bid/releases/9c9b9d91d/`（完整保留）
- DB 备份：`/opt/xiyu-bid/db-backups/winbid-78facc5b2-*.sql.gz`
- 本次无 schema 变更，回滚仅需切回旧 release 目录 + 重启服务

## 后续验证（待用户）

**项目 225 重新触发 AI 评分标准解析**：https://winbid-test.ehsy.com/project/225
预期：第一阶段候选提取不再抛 `Conversion = '"'`，解析正常推进（LLM 阶段耗时分钟级属正常）。

## 经验沉淀应用

- Lesson §117（本次新增）：text block + `.formatted()` 模板字面 `%` 必须写 `%%`
- 全仓扫描方法已写入 §117 操作规范
- 部署 SOP 18 条经验全程遵循（产物校验 / assets 保留 / MD5 校验 / SYSTEMCTL_SUDO）
