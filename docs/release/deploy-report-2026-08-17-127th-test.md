# 第 127 次测试环境部署报告 — 2026-08-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 127 次（测试） |
| 部署时间 | 2026-08-17 14:44:56 CST（服务启动） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `7a09da3b6` |
| 上一版本 Release | `78facc5b2`（2026-08-17 14:09 CST，第 126 次测试部署） |
| 基线 commit | `7a09da3b6`（origin/main HEAD，含 PR !2307 null section NPE 修复） |
| 健康检查通过 | ✅（79 次尝试后 3/3 连续通过，Kafka readiness 延迟属已知行为） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无（纯 Java 修复 + 测试 + 文档变更） |
| Smoke 测试 | 全部通过 |
| GitHub 镜像 | ✅ 已同步（两边 main = `7a09da3b6`） |

## 背景：本次为评分解析召回一/三 NPE 修复上线

第 126 次部署（`78facc5b2`，% 转义修复）上线后，项目 226 触发 AI 评分标准解析出现新崩溃：

```
java.lang.NullPointerException: Cannot invoke "...ScoreSection.sectionTitle()"
  because "section" is null
  at OpenAiScoreAnalyzer.toCandidate(OpenAiScoreAnalyzer.java)
```

### 根因（lessons-learned §118）

`OpenAiScoreAnalyzer.toCandidate` 私有转换方法直接调用 `section.sectionTitle()` 等方法，而召回一（正则规则）与召回三（评分语义）调用时**合法传入 `null` section**（无章节定位上下文），导致 NPE 必现。% 转义修复后第一层崩溃消失，暴露出这第二层缺陷。

### 修复内容

| 类型 | 文件 | 说明 |
|---|---|---|
| bug fix | `OpenAiScoreAnalyzer.java` `toCandidate` | `section` 判空降级：null 时 contextNote/sourceText/location 返回空串 |
| test | `OpenAiScoreAnalyzerTest.java`（新建）| `recallCandidates_nullSectionDoesNotThrowNpe` 回归测试 |
| docs | `lessons-learned.md §118` + `.wiki/pages/score-parse-service.md` | 教训沉淀 + wiki 回填 |

PR !2307（已合入，squash commit `bbd318e93`）。

## 增量改动（78facc5b2 → 7a09da3b6，5 commits）

```
7a09da3b6 !2306 docs(release): 第 126 次测试环境部署报告
5e6a32a3f !2307 docs: 沉淀 lessons §118 null section NPE + wiki 回填
a0f47da4f docs: 沉淀 lessons §118 null section NPE + wiki 回填
bbd318e93 fix(scoreparse): toCandidate 判空 section，修复召回一/三 NPE 崩溃
80be28b5c docs(release): 第 126 次测试环境部署报告
```

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK（251 migrations，checksums match） |
| Step 2: DB 版本对比 | 源码无新迁移（本次增量 0 个迁移文件），无需应用 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

## 部署步骤与验证

1. ✅ 环境门禁（AskUserQuestion 确认测试环境 172.16.38.78）
2. ✅ 锚点分支同步 main（HEAD = origin/main = `7a09da3b6`，工作区干净）
3. ✅ 服务器现状检查（`78facc5b2` 健康 UP、服务 active）
4. ✅ Flyway 预检 3 步法
5. ✅ 本地打包（154M，`obsEnabled=true`、`apiBaseUrl=""` 同源、Flyway 迁移无重复、Detail chunk `.upload(`×2）
6. ✅ 产物校验：`OpenAiScoreAnalyzer.class`（16156 bytes，14:43）入 jar
7. ✅ scp 上传 + remote-deploy（SYSTEMCTL_SUDO=true + DB 备份 `winbid-7a09da3b6-20260817144446.sql.gz` 11M）
8. ✅ 健康检查 3/3 通过（79 次尝试），前端一致性（`index-D7w8-u8O.js`）
9. ✅ 旧 assets 保留（自 `78facc5b2`，防跨版本 404）
10. ✅ Smoke：health 200 / readiness 200 / login 400（预期）/ projects 403（预期）/ crm-health 401（预期）/ 前端 `/`、`/login` 200
11. ✅ **修复验证**：服务器部署 jar 内 `OpenAiScoreAnalyzer.class` 与本地构建产物字节级一致（16156 bytes / 08-17 14:43）；重启后服务日志 0 次 NPE
12. ✅ 临时配置检查：仅 `SHOW_DETAILS=always`（用户此前决定保留，非本次新增）
13. ✅ GitHub 镜像同步（落后 5 → 0，两边 main 一致）

## 回滚信息

- 回滚点：`/opt/xiyu-bid/releases/78facc5b2/`（完整保留）
- DB 备份：`/opt/xiyu-bid/db-backups/winbid-7a09da3b6-20260817144446.sql.gz`
- 本次无 schema 变更，回滚仅需切回旧 release 目录 + 重启服务

## 后续验证（待用户）

**项目 226 / 225 重新触发 AI 评分标准解析**：https://winbid-test.ehsy.com/project/226
预期：召回一/三产出的候选不再抛 `section is null` NPE，解析正常推进到合并与结构化提取阶段（LLM 阶段耗时分钟级属正常）。注意：此前项目 226 曾上传扫描件（提取文本仅 3 字符），若仍报"未识别到评分标准章节"，需换含文字层的招标文件，与本修复无关。

## 经验沉淀应用

- Lesson §117（% 转义）：第 126 次已上线，本次回归无复发
- Lesson §118（本次上线）：私有转换方法对 null 参数必须判空降级，上游传 null 即全线崩溃
- 部署 SOP 18 条经验全程遵循（产物校验 / assets 保留 / SYSTEMCTL_SUDO / SHOW_DETAILS 检查）
