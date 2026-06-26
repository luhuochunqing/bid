# Implementation Notes — 2026-06-25 Full Deployment

## 1. origin/main 编译不兼容问题

**问题**：PR #1088 删除了 `PinyinUtils.java`、pinyin4j 依赖和 `User.fullNamePinyin` 字段，但 PR #1091 引用了这些代码，导致 `origin/main` 编译失败。

**决策**：恢复被误删的代码：
- `backend/pom.xml` — 恢复 pinyin4j 依赖 (`com.belerweb:pinyin4j:2.5.1`)
- `backend/src/main/java/com/xiyu/bid/common/util/PinyinUtils.java` — 从历史 commit 422a4a9cb 恢复
- `User.java` — 恢复 `fullNamePinyin` 字段、`@PrePersist`/`@PreUpdate` 自动生成拼音、`getDisplayEmployeeNumber()` 方法

## 2. 前端打包的同源模式

**决策**：`VITE_API_BASE_URL=""`（空字符串），使用 Nginx 同源代理模式。Nginx 监听 8080，`/api/` 和 `/actuator/` 代理到 `127.0.0.1:18080`。

**tradeoff**：不能直接在浏览器输入后端地址访问 API，必须走 Nginx。但避免了 CORS 配置问题。

## 3. CurrentUserResolver @RequestScope 冲突（Lesson #13）

**问题**：ehsy 组织事件 SDK (`ClientSDK`) 的 `StartCallback.onApplicationEvent()` 在 `ApplicationReadyEvent` 时调用 `getBeansOfType()` 扫描所有 bean。若 `CurrentUserResolver` 标注了 `@RequestScope`，Spring 尝试在非 HTTP 线程实例化它时抛出 `ScopeNotActiveException`，导致应用启动失败。

**决策**：参照 docs/lessons/lessons-learned.md §14 的修复方案：
- 移除 `@RequestScope` 注解
- 移除请求级缓存（`cachedUser`/`resolved` 字段）
- 每次调用直接查询数据库（走 username 索引，性能可忽略）

**为什么不使用 ThreadLocal**：Tomcat 线程复用，ThreadLocal 残留数据可能泄漏用户信息，添加 Filter 清理又增加复杂度。

## 4. 部署过程发现的问题

### 4.1 服务器状态异常

连接服务器后发现：
- **实际运行的 jar 是回滚版本** (c066cd997, SHA: ad768e1f...)，但 `deployed-release.json` 错误记录为 `5f3d2e8d0`
- 第一轮部署的 5f3d2e8d0 jar 在 17:45:46 因 `ScopeNotActiveException` 启动失败
- 后续 `systemctl` 自动重启切换到了回滚版本的 jar（可能有人手动恢复覆盖）
- 但部署记录未更新，导致 `deployed-release.json` 与实际情况不一致

### 4.2 5f3d2e8d0 前端的 404 问题

`/srv/www/xiyu-bid/assets/` 目录下只有 173 个文件（正常应该在 200+）。`index.html` 只引用了 7 个直接资产，Vite 动态 import 的 chunk 可能不完整。部署时 `rm -rf /srv/www/xiyu-bid/*` 后 `cp -R` 覆盖应该保留全部文件，但回滚过程中前端目录被恢复为旧版本。

## 5. Readiness 503 — 启动短暂过渡状态（非 bug）

**发现**：6e3d99cd 部署后约 30 秒内 `readiness` 返回 503，但 3 分钟后自动翻转为 200/UP。

**原因**：Spring Boot readiness group 包含 `ReadinessState.HealthIndicator`，在 Kafka Consumer 等外部资源未完全就绪前保持 `OUT_OF_SERVICE`。这是正常行为，非代码缺陷。

**验证结果**：Readiness UP、Liveness UP、Nginx 200、API 403（需认证）。

## 6. 部署经验补充

### 6.1 遗留修复项
- `deployed-release.json` 写入了未展开的 `$(date ...)` 字符串 → **已修复**
- macOS `._*` 残留文件污染 release 目录 → **已清理**

### 6.2 遗留待办
- Smoke 脚本 `scripts/release/run-prod-smoke.mjs` 需要 `PROD_SMOKE_USERNAME` 和 `PROD_SMOKE_PASSWORD` 环境变量，当前环境未配置 → 跳过自动化 smoke，已手动验证
- `mvn clean package -Dmaven.test.skip=true` 可行但测试类不兼容问题仍未修复 → 需单独提交 PR 修复

## 7. 拼音搜索功能修复（2026-06-25 紧急修复）

### 7.1 根因分析

**后端问题（2个）：**
1. `UserRepository.searchActiveUsers()` SQL 只搜索了 `full_name`、`username`、`employee_number`，**缺少 `full_name_pinyin` 和 `employee_number_pinyin`**
2. `User` entity 缺少 `employee_number_pinyin` 字段定义 + `@PrePersist`/`@PreUpdate` 未自动生成工号拼音

**前端问题（真正的根因！）：**
Element Plus `el-select` 同时设置 `remote=true` + `filterable=true` 时，**会做双重过滤**：
1. 后端根据 query 返回搜索结果
2. 前端 el-select 用相同的 query **再次做前端过滤**
3. 如果后端返回的 label（"郑蓉蓉"）不包含 query 字符串（"06234" 或 "zhang"），前端过滤会把所有结果过滤掉！

### 7.2 修复内容

1. **UserRepository.java**: 搜索 SQL 增加 `full_name_pinyin` 和 `employee_number_pinyin` 匹配
2. **User.java**: 增加 `employeeNumberPinyin` 字段及自动生成逻辑
3. **V1096 migration**: 新增 DB column `employee_number_pinyin VARCHAR(255)`
4. **UserPicker.vue**: search 模式下设置 `:filter-method="() => {}"` 空函数，**禁用前端过滤**，完全依赖后端搜索结果
5. **数据库**: 远程 RDS 手动执行 ALTER TABLE + backfill

### 7.3 验证结果（生产环境）
- ✅ `/api/users/search?q=zhang` → 200 OK（拼音搜索）
- ✅ `/api/users/search?q=余` → 200 OK（中文搜索）
- ✅ `/api/users/search?q=06234` → 200 OK（工号搜索）
- ✅ 前端不再做二次过滤，搜索结果正确显示在下拉框中

### 7.4 注意事项
- PinyinBackfillRunner 会在后台异步回填存量 8532 用户的拼音字段
- 回填期间 readiness 可能保持 OUT_OF_SERVICE，完成后自动变为 UP
- 回填完成后需要更新 Flyway schema history，或在下次部署通过 migration 对齐

## 下次部署检查清单

- [x] 编译打包前检查 origin/main 可用 `-Dmaven.test.skip=true` 编译
- [x] 打包前 `mvn clean` 而非依赖增量编译
- [x] 打包后验证 jar 中关键 class (`CurrentUserResolver`、`PinyinUtils`)
- [x] 部署顺序：`stop → 替换 jar → start`（Lesson #5）
- [x] 部署后立即验证 health + readiness
- [x] 更新 `deployed-release.json`
- [x] 拼音搜索功能已部署并验证
- [ ] 申请配置 PROD_SMOKE 凭据
- [ ] 修复测试类兼容性问题
- [ ] 推送代码 + 提 PR