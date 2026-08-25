# E2E 对齐 Handoff 文档

> 日期：2026-08-23 · 前一 session：gemini worktree · 状态：**后端/前端/构建全绿，E2E 71F 待清**

## 一、当前状态（main @ 7cb7e6f19）

| 层 | 结果 |
|---|---|
| 后端 mvn test | ✅ 8386 / 0F / 0E / 9 skipped |
| 前端单测 vitest | ✅ 2963 通过 / 3 跳过 |
| 前端构建链 | ✅ 全过 |
| E2E chromium（51 spec 子集） | ⚠️ **106 通过 / 71 失败** |

原始基线是 109 失败，本 session 修复了约 38 个。剩余 71 个为纯内容级 spec 漂移。

## 二、已修复的根因（不要重复做）

以下问题已在本 session 解决并合入 main（!2382/!2385/!2386/!2387/!2388），新 session 不需要再碰：

1. **FormDefinitionE2eSeeder schema 过旧**：tender.entry 已更新为 V1166 新字段集 + pastedText。如果未来 fallback 表单字段变更，需同步此 seeder。
2. **27 个 spec 硬编码端口**：已全部改为 env 驱动 `apiBaseUrl`/`frontendBaseUrl`。
3. **角色码归一化**：auth-helpers.js 的 LEGACY_ROLE_CODES 映射表处理 STAFF/MANAGER 等遗留名。
4. **E2E 栈 preview 代理 target**：start-api-e2e-stack.sh 已显式传 VITE_PREVIEW_API_TARGET。
5. **测试数据唯一化**：fillAndSave 的招标主体+日期已随机化，不会撞去重 400。

## 三、剩余 71 个失败的分类

错误签名分布：
- toBeVisible failed: ~22
- toBeTruthy: ~22（API 返回 falsy，数据依赖）
- Test timeout 90s: ~16
- element(s) not found: ~15
- fill timeout: ~6
- 其他: ~10

按文件分布见 `docs/reports/e2e-failure-distribution-20260823.txt`。

最大簇（无共享根因，每条独立）：

| spec | 数量 | 初步诊断 |
|---|---|---|
| performance-management-flow | 5 | 需对照 PerformanceStage 当前 UI |
| warehouse-export-flow | 4 | 导出台账按钮/对话框定位器漂移 |
| project-list-filter-fields | 4 | 区域筛选已改版（cascader→textbox） |
| dashboard-metric-drilldown | 4 | 下钻 tab 定位漂移 |
| tender-manual-create | 3 | CRM 商机选择框交互 |
| bidding-permission-buttons | 3 | 权限按钮可见性 |
| 其余 ~30 个文件 | 各 1-3 条 | 同类定位器/断言漂移 |

## 四、修复模式（已验证可复用）

每个失败的修法都遵循同一流程：

1. `run-e2e.sh <spec>` 单跑失败 spec
2. 读 `test-results/<test>/error-context.md`（aria 快照，看页面实际渲染了什么）
3. 对照当前组件源码修正定位器或断言
4. 单跑确认绿 → 提交

常用工具脚本（已在 `/var/folders/hb/fw83wt2j5psblns41081bgh40000gn/T/opencode/` 但 tmp 会过期，建议重建）：
- `run-e2e.sh`：设置端口 env + PLAYWRIGHT_DISABLE_API_BOOTSTRAP=1 后运行 playwright

```bash
#!/bin/bash
export BACKEND_PORT=18091 FRONTEND_PORT=1325
export SPRING_APPLICATION_JSON='{"app":{"doc-insight":{"sidecar-url":"http://127.0.0.1:8009"}}}'
export PLAYWRIGHT_API_BASE_URL=http://127.0.0.1:18091
export PLAYWRIGHT_BASE_URL=http://127.0.0.1:1325
export PLAYWRIGHT_DISABLE_API_BOOTSTRAP=1
cd /Users/user/xiyu/worktrees/gemini
npx playwright test --config playwright.config.js --project=chromium "$@"
```

⚠️ 每次必须带同一组 env；不带会拆掉别人的栈（teardown 按 .rehearsal marker/pid 清理）。

## 五、栈操作速查

```bash
# 启动隔离栈（避开主工作区端口）
BACKEND_PORT=18091 FRONTEND_PORT=1325 \
SPRING_APPLICATION_JSON='{"app":{"doc-insight":{"sidecar-url":"http://127.0.0.1:8009"}}}' \
bash scripts/test/start-api-e2e-stack.sh

# 停止
bash scripts/test/stop-api-e2e-stack.sh

# 种子账号密码（E2eDemoDataInitializer 创建）
lizong/xiaowang/xiaochen 等 → 密码统一 123456
```

## 六、注意事项 / 已知陷阱

- el-date-picker 必须在 fill 后 `press('Enter')` 才能提交值到 v-model
- el-cascader 两级面板：点省级后再点第二列首个市级叶子
- 连续开合下拉时用键盘 ArrowDown+Enter 代替鼠标点击选项（动画竞态）
- spring.factories 的 TestExecutionListener key 是**单数**接口名
- 测试种子只写 H2 内存库（JDBC URL 前缀守卫），绝不触碰 MySQL 容器库
- 测试数据必须唯一（后端有唯一键约束），否则同轮测试互撞

## 七、下一步建议

1. 新 session 用 `agent-start-task.sh gemini e2e-tier2-alignment origin/main --in-place` 开分支
2. 起 E2E 栈（上面的命令）
3. 按失败分布文件逐 spec 对齐，优先做数量最多的前 5 个
4. 全部修完后跑一次完整 chromium 基线，把数字写入 PR 描述
5. 如果要纳入门禁：将核心链路 spec 加入 pre-push-gate.sh 或 CI workflow
