# 第 113 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | test（测试环境） |
| Release ID | `63a78dd06` |
| 部署时间 | 2026-07-30 09:24:25 CST |
| 服务器 | winbid-01（172.16.38.78） |
| 部署类型 | 增量部署（仅后端代码修复） |
| 健康状态 | ✅ UP |
| 回滚状态 | 不需要 |

## 基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae-init`（锚点分支） |
| HEAD commit | `63a78dd06` |
| 上一版本 commit | `e9084f530`（第 112 次部署） |
| GitHub 镜像同步 | ✅ 0 落后 |

## PR 列表

| PR # | 标题 | 类型 |
|---|---|---|
| !2224 | fix(tender-intake): purchaserName 正则优先覆盖 AI 错误值 | bug fix |

## 改动范围

### 根因
张家口银行招标文件招标主体被 AI 识别为"祥安招标代理有限公司"（代理机构），真实招标主体是"张家口银行股份有限公司"。

**直接原因**：原兜底条件（`OpenAiTenderDocumentAnalyzer.java:221`）仅判 AI 返回空值时触发正则兜底，AI 返回错误非空值时兜底未触发。

**深层原因**：
1. PDF 排版空格打断标签行（"招 标 人：张家口银行股份有限公司"），AI 对带空格打断的标签行识别能力弱
2. 代理机构行格式更规整（"代理机构：祥安招标代理有限公司"），AI 被规整的代理机构行吸引
3. AI 缺乏"取多数"的稳健判定

### 修复
将兜底条件从"AI 空值时触发"改为"正则命中标签行即覆盖 AI"：
- 正则命中"标签+冒号+机构名"结构化标签行时，覆盖 AI 结果（无论空值/错误值）
- 正则未命中时保留 AI 结果，避免误伤 AI 已正确但文本无标签行的场景
- 删除死代码 `isBlankValue` 方法

### 影响字段
仅 `purchaserName`。其他字段（deadline/bidOpeningTime/contact）虽有同类风险但无结构化标签可正则，本次不修。

### 改动文件
- `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiTenderDocumentAnalyzer.java`（修复兜底条件 + 删除死代码）
- `backend/src/test/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiTenderDocumentAnalyzerTest.java`（更新测试）

### 测试验证
- 新增 `analyzeTenderIntake_shouldOverrideAiWhenRegexMatchesLabel`（张家口银行真实 case）
- 新增 `analyzeTenderIntake_shouldKeepAiValueWhenRegexCannotMatchLabel`（无标签行保留 AI）
- 删除 `analyzeTenderIntake_shouldNotOverrideAiPurchaserWhenPresent`（与新策略冲突）
- biddraftagent 303 + docinsight 54 = 357 测试全绿
- 架构测试（ArchitectureTest/FPJavaArchitectureTest/MaintainabilityArchitectureTest）全绿

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - 242 migrations, all checksums match |
| Step 2: DB 版本对比 | 无新增迁移，跳过 |
| Step 3: remote-deploy 内置 validate | ✅ 通过 |

## 部署步骤

1. ✅ 环境门禁（用户确认测试环境 172.16.38.78）
2. ✅ 早操三连 + 基线确认（HEAD = `63a78dd06`，GitHub 同步 0 落后）
3. ✅ 服务器现状检查（上一版本 `e9084f530`，health UP）
4. ✅ Flyway 预检（242 migrations, checksums match）
5. ✅ 本地打包（RELEASE_ID=63a78dd06, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1）
6. ✅ 产物校验（jar 内迁移版本无重复，OBS 直传已启用 Detail .upload( 调用数=2）
7. ✅ 上传 + 部署（remote-deploy.sh, SYSTEMCTL_SUDO=true）
8. ✅ 前端资源保留（已从上一版本 release 目录 cp -rn 旧 assets）
9. ✅ 健康检查（78 次尝试，连续 3/3 通过）
10. ✅ Smoke 测试
11. ✅ GitHub 镜像同步检查（0 落后）
12. ✅ 配置清理检查（SHOW_DETAILS=always 用户决定保留）

## 验证结果

### 健康检查
```
http://172.16.38.78:8080/actuator/health → {"status":"UP",...}
http://172.16.38.78:8080/actuator/health/readiness → HTTP 200
```

### API Smoke（经 Nginx 8080 代理）
| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `/api/projects`（无 token） | 403 | 403 | ✅ |
| `/api/integration/crm/health`（无 token） | 401 | 401 | ✅ |

### 前端验证
| 路径 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/` | 200 | 200 | ✅ |
| `/login` | 200 | 200 | ✅ |
| index hash | `assets/index-DY4s5YDD.js` | `assets/index-DY4s5YDD.js` | ✅ |

## GitHub 同步

| 项目 | 值 |
|---|---|
| Gitee main | `63a78dd06` |
| GitHub main | `63a78dd06` |
| 落后 commit 数 | 0 |
| 状态 | ✅ 完全一致 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点 | `e9084f530`（第 112 次部署） |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/e9084f530` |
| 上一版本前端 assets | 已保留到 `/srv/www/xiyu-bid/assets/` |
| DB 备份 | 无需备份（本次无新增迁移） |
| 回滚操作 | `sudo systemctl stop xiyu-bid-backend && cp /opt/xiyu-bid/releases/e9084f530/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl start xiyu-bid-backend` |

## 经验沉淀应用情况

本次部署应用以下经验：
1. **第 87 条教训**（结构化标签字段使用正则兜底）：本次修复正是基于此教训，但发现原实现条件错误（仅判 AI 空值），改为正则优先覆盖 AI
2. **Flyway 预检 3 步法**：部署前主动 validate，避免启动时才发现问题
3. **OBS 直传双保险**：显式传入 `VITE_OBS_ENABLED=true` + 产物校验 `obsEnabled=true`
4. **前端资源保留**：从上一版本 release 目录 cp -rn 旧 assets，防止跨版本 404
5. **Smoke 测试替代方案**：admin 密码未知，用 400/403/401 验证接口路由

## 风险提示

1. **本次修复仅覆盖 purchaserName**：其他字段（deadline/bidOpeningTime/contact）仍有 AI 误识别风险，但无结构化标签可正则，需后续观察
2. **正则兜底依赖标签行格式**：若文档中招标主体仅以描述性文字出现（无"标签+冒号"格式），正则未命中时仍保留 AI 结果
3. **AI provider 仍为 qwen3.7-max**：未切换到豆包/deepseek，不同 provider 的识别准确率可能不同

## 部署确认清单

- [x] 环境门禁通过（用户确认测试环境）
- [x] 基线干净（HEAD = origin/main = `63a78dd06`）
- [x] GitHub 镜像同步（0 落后）
- [x] Flyway 预检通过（242 migrations, checksums match）
- [x] 打包成功（jar + 前端产物完整）
- [x] OBS 直传已启用（obsEnabled=true, Detail .upload( 调用数=2）
- [x] 部署成功（remote-deploy.sh 完成）
- [x] 健康检查通过（UP, readiness UP）
- [x] Smoke 测试通过（health/readiness/login/projects/crm health/前端）
- [x] 前端资源保留（旧 assets 已 cp -rn）
- [x] 配置清理检查（SHOW_DETAILS=always 用户决定保留）
- [x] 回滚就绪（上一版本 release 目录 + jar 已保留）

## 下一步

部署已完成，建议用户重新上传张家口银行招标文件 PDF 验证招标主体识别是否正确（期望识别为"张家口银行股份有限公司"而非"祥安招标代理有限公司"）。
