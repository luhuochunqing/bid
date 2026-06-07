# 上线前破坏性验收报告 — 2026-05-05

> 一旦本目录文件发生变化，请更新本报告与上层 `docs/` 索引。

## 概览

针对 2026-05-05 main `ce9af077` 进行的上线前验收最初判定为 **[FAIL]**，已识别 P0/P1/P2 三类阻断。修复完成后重新跑取证，本文记录证据。

## 修复清单与定位

| 等级 | 问题 | 修复定位 |
|---|---|---|
| P0 | logout 不撤销 access JWT | `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java`（加 jti）; 新增 `TokenRevocationService` 接口 + `Redis*` / `InMemory*` 两实现；`JwtAuthenticationFilter` 接黑名单查询；`AuthService.logout(accessToken, refreshToken)` 重载；`AuthController.logout` 透传 Authorization 头 |
| P1 | 标讯创建无幂等键 | 新增 `backend/src/main/java/com/xiyu/bid/idempotency/`：`@Idempotent` 注解、`IdempotencyStore` 接口 + Redis/InMemory 两实现、`IdempotencyFilter`（pre-buffer body 防 input stream 消费导致 400）；`SecurityConfig` 接入 filter；`TenderController.createTender` 加 `@Idempotent`；前端 `src/api/modules/tenders.js` 在 POST 时自动塞 `Idempotency-Key` |
| P2 | 取证缺口 + 缺回滚 SOP | 见本文 §证据；新增 `docs/release/ROLLBACK.md` |
| P3 | ESLint 239 warning | **记为技术债**（见 `TECHNICAL_DEBT.md`），未在本次清理 |

## 证据

### A. 编译与单测

| 项 | 命令 | 结果 |
|---|---|---|
| 后端编译 | `cd backend && mvn -DskipTests compile` | exit 0 / BUILD SUCCESS |
| 后端全量测试 | `cd backend && mvn test -fae` | **`Tests run: 1790, Failures: 0, Errors: 0, Skipped: 3`** / BUILD SUCCESS |
| 前端单测 | `npm run test:unit` | **`Test Files 139 passed / Tests 783 passed | 1 skipped (784)`** |
| 前端 build | `npm run build` | exit 0（含 governance/version-sync/data-boundaries/task-status-literal）|
| ESLint | `npm run lint` | 0 errors / 239 warnings（P3 技术债，未阻断） |

### B. P0 端到端：logout 后 access token 立即失效

```text
=== P0: logout token revocation ===
Token received (len=234)
--- step1 /api/auth/me ---           HTTP 200  (token 有效)
--- step2 logout ---                 HTTP 200  ({"success":true,"message":"Logout successful"})
--- step3 /api/auth/me after logout ---  HTTP 403  ✅
--- step4 /api/projects after logout ---  HTTP 403  ✅
```

**结论**：原 token 在 logout 后立即被 Redis 黑名单（或本地内存兜底）撤销；之前 24 小时窗口已闭合。

### C. P1 端到端：Idempotency-Key

```text
=== P1: Idempotency-Key (real run) ===
--- first POST (key=idem-1777981569-ABCDEF) ---  HTTP 201, id=3
--- second POST (same key, same body) ---        HTTP 201, id=3   ✅ 同一记录
id1=3 id2=3 — PASS
--- same key, different body (EXPECT 422) ---    HTTP 422 ✅
{"success":false,"code":422,"message":"Idempotency-Key conflict: request body differs from first request"}
```

**结论**：相同 key 重放只生成一条；body 不一致返回 422 拒绝。

### D. P2 — DB down 行为（fail-fast）

```text
--- baseline ---           health 200 / readiness 200 / status UP
--- pause MySQL ---        docker pause xiyu-bid-local-mysql
--- with DB paused ---     health 503 (×3 次)  ✅ fail-fast
                           readiness 200       ⚠️ 见下面备注
--- /api/auth/login (no DB) --- HTTP 500 + 通用错误信息（不泄露细节）  ✅
--- unpause MySQL ---      docker unpause
--- recover ---            health 200, status UP                  ✅ 自动恢复
```

**备注**：`/actuator/health` 走 DataSource health indicator → DB 挂时立刻 503。`/actuator/health/readiness` 当前只反映 Spring `ReadinessState`，未挂 DB indicator；**建议把 `dataSource` 加入 readiness group**，作为下个迭代项（不阻断本次上线，因为 `/actuator/health` 已 503，反向代理可以以 health 为 probe）。

### E. 状态机抽查（只读）

| 类 | 文件 | 抽查结论 |
|---|---|---|
| TenderTaskStateMachine | `backend/src/main/java/.../tender/processing/TenderTaskStateMachine.java` | 显式 transitions map，未扫到 "任意→任意" 豁口 |
| TenderStatusTransitionPolicy | `backend/src/main/java/.../tender/...` | 同上 |
| TaskTransitionPolicy | `backend/src/main/java/.../task/...` | 同上 |
| FormInstanceStatusPolicy | 同 | 同上 |

**结论**：状态机均显式 enum，未发现 free-form 字符串赋值。

**F-4 状态机 fuzz 专项已完成**（2026-05-06）：补充 4 份穷举 + 随机注入测试套件，共新增 288 个用例，覆盖：
- 全 enum × enum 矩阵（与权威 spec 镜像比对，policy 偏离立即红）
- 终态汇集不变量（BIDDED / COMPLETED / OA_REJECTED+OA_FAILED+BUSINESS_APPLIED 无外向迁移）
- 自迁移策略差异锁定（Tender/Task 允许；Form 不允许）
- null 输入永不返回 true（policy 各自的 fail-safe 行为已明确）
- 种子固定的随机注入与随机生命周期：attempts 单调递增、retry≤maxRetries 后必入 DLQ、错误信息恒定 ≤ 900 字符且非空白
- 决定性（同入参重复 16 次结果一致）

测试文件：
- `backend/src/test/java/com/xiyu/bid/batch/core/TenderStatusTransitionPolicyFuzzTest.java`
- `backend/src/test/java/com/xiyu/bid/task/core/TaskTransitionPolicyFuzzTest.java`
- `backend/src/test/java/com/xiyu/bid/workflowform/domain/FormInstanceStatusPolicyFuzzTest.java`
- `backend/src/test/java/com/xiyu/bid/tenderupload/service/TenderTaskStateMachineFuzzTest.java`

## 验收口径

参考 `Hard Rules`：

- [x] 构建成功
- [x] 测试全绿（mvn 1790 + vitest 783）
- [x] 核心链路：登录 / 标讯创建（含幂等）/ 退出登录均闭环
- [x] 权限：未登录 403、错 token 403、logout 后 token 立即 403
- [x] 数据一致性：幂等键防重复、422 防覆盖
- [x] 安全：JWT 撤销、CORS 收敛（dev profile 测试）、actuator 暴露面符合预期
- [x] 回滚方案：见 `docs/release/ROLLBACK.md`

**最终判定**：**[PASS]**（有两个非阻断改进项：readiness 加 DB indicator、ESLint 技术债）

## 复现命令

```bash
# 后端
cd backend
mvn -DskipTests compile        # 期望 BUILD SUCCESS
mvn test -fae                   # 期望 Tests run: 1790, Failures: 0, Errors: 0

# 前端（仓库根目录）
npm run build                   # 期望 exit 0
npm run test:unit               # 期望 783 passed | 1 skipped

# P0 端到端（需要后端在 18081）
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | jq -r .data.token)
curl -s http://127.0.0.1:18081/api/auth/me -H "Authorization: Bearer $TOKEN" -w "%{http_code}\n"   # 200
curl -s -X POST http://127.0.0.1:18081/api/auth/logout -H "Authorization: Bearer $TOKEN" -w "%{http_code}\n"  # 200
curl -s http://127.0.0.1:18081/api/auth/me -H "Authorization: Bearer $TOKEN" -w "%{http_code}\n"   # 403 ← 关键

# P1 端到端
KEY="idem-$(date +%s)"; BODY='{"title":"X-'"$(date +%s)"'","source":"acc","budget":100,"publishDate":"2026-05-05","deadline":"2026-05-30T00:00:00","status":"TRACKING"}'
for i in 1 2; do
  curl -s -X POST http://127.0.0.1:18081/api/tenders \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
    -d "$BODY" | jq .data.id    # 两次应输出相同 id
done

# P2 端到端
docker pause xiyu-bid-local-mysql
curl -s http://127.0.0.1:18081/actuator/health -w " %{http_code}\n"   # 503
docker unpause xiyu-bid-local-mysql
sleep 5
curl -s http://127.0.0.1:18081/actuator/health -w " %{http_code}\n"   # 200
```

## 后续追踪项

| 编号 | 项 | 优先级 | 落点 |
|---|---|---|---|
| F-1 | `/actuator/health/readiness` 加 dataSource indicator | P2 | ✅ 已完成（2026-05-06，PR #172） |
| F-2 | ESLint 160 个 vue/no-mutating-props | P3 | 技术债批次 |
| F-3 | Flyway down 脚本补全 | P2 | ✅ 已完成（2026-05-06，PR #173，由 `FlywayRollbackScriptCoverageTest` 守护） |
| F-4 | 状态机 fuzz 专项 | P3 | ✅ 已完成（2026-05-06，288 用例，详见 §E） |
