# §4.2.3 标讯创建代码审查报告

**分支**: `cursor-revert-pr-429-tender-entry`
**基准**: `origin/main`
**提交**: `a92d8678` - fix(tender): 回退 PR #429 标讯手工录入相关改动
**审查时间**: 2026-05-26
**审查人**: code-reviewer

---

## 一、概述

本分支将 PR #429 中引入的标讯手工录入相关功能回退，涉及：
- 后端去重策略调整（从「业主单位」改为「招标主体」三字段匹配）
- 前端 `DuplicateWarningDialog` 组件删除
- `TenderDuplicateException` 构造器签名变更
- `GlobalExceptionHandler` 异常响应格式调整

---

## 二、验证结果

| 验证项 | 结果 | 备注 |
|--------|------|------|
| `npm run check:front-data-boundaries` | ✅ 通过 | 无 Mock 残留 |
| `mvn test -Dtest=ArchitectureTest` | ✅ 通过 | 20 tests, 0 failures |
| `npm run build` | ❌ 失败 | `TenderCreatePage.vue` 缺失（待清理路由） |

---

## 三、问题清单

### 🔴 CRITICAL（阻塞构建）

#### #1 前端路由引用已删除页面
- **位置**: `src/router/index.js:91-95`
- **问题**: 路由配置仍引用 `TenderCreatePage.vue`，但该文件已被删除
- **影响**: `npm run build` 失败，前端无法构建
- **证据**:
  ```
  npm run build 2>&1 | tail -15
  error during build:
  Could not load /Users/user/xiyu/worktrees/.../src/views/Bidding/TenderCreatePage.vue
  ```
- **修复建议**: 
  1. 方案A（推荐）：从 `src/router/index.js` 移除 `bidding/create` 路由
  2. 方案B：恢复 `TenderCreatePage.vue` 文件

---

### 🟡 HIGH（功能一致性）

#### #2 去重字段变更未同步更新调用方
- **位置**:
  - `TenderDeduplicationService.java:24-30` (findDuplicates 方法)
  - `TenderCommandService.java:64` (createTender 调用)
- **问题**: `TenderDeduplicationService.findDuplicates()` 方法未在 `TenderCommandService.createTender()` 中被调用（git diff 显示从 `checkDuplicate()` 改回 `findDuplicates()`），但方法本身返回 `List<Tender>` 而非直接抛异常
- **影响**: 重复检测逻辑存在，但前端已无法收到 409 响应
- **修复建议**: 确认 `findDuplicates()` 返回非空列表时是否应抛出 `TenderDuplicateException`

#### #3 GlobalExceptionHandler 响应格式变更
- **位置**: `GlobalExceptionHandler.java:181-191`
- **问题**: 从 `ApiResponse.success("检测到重复标讯", data)` 改为 `ApiResponse.error(409, ex.getMessage(), duplicateDTOs)`
- **影响**: 前端 `useManualTenderCreate.js` 的 409 状态码处理逻辑（`error?.response?.status === 409`）仍可工作，但响应结构变化
- **风险**: 中等（需确认前端能否正确解析新响应格式）
- **修复建议**: 验证前端 `error.response.data` 结构是否与新格式兼容

---

### 🟢 MEDIUM（代码质量）

#### #4 TenderDuplicateException 构造器变更
- **位置**: `TenderDuplicateException.java:17`
- **问题**: 构造器从 `TenderDuplicateException(List<Tender>, Tender)` 简化为 `TenderDuplicateException(List<Tender>)`
- **影响**: 不影响功能，但异常信息丢失当前录入的标讯详情
- **修复建议**: 如需在前端显示「您正在录入」信息，考虑增强异常信息

#### #5 删除的测试文件
- **位置**:
  - `TenderDeduplicationPolicyTest.java`
  - `TenderDeduplicationServiceTest.java`
- **问题**: 去重策略的单元测试被删除
- **影响**: 去重逻辑的测试覆盖缺失
- **修复建议**: 恢复单元测试或确保集成测试覆盖去重场景

#### #6 useManualTenderCreate.js 仍有 TODO 注释
- **位置**: `src/views/Bidding/list/useManualTenderCreate.js` (已删除)
- **问题**: `handleNotifyAdmin()` 中的 TODO 注释「发送系统待办 + 企微通知」未实现
- **影响**: 功能不完整（但本次回退已删除该功能）

---

## 四、安全审查

| 检查项 | 结果 |
|--------|------|
| SQL 注入 | ✅ `findByPurchaserNameAllIgnoreCase` 使用 JPA 参数化查询 |
| XSS | ✅ 后端无用户输入直接输出 |
| 权限校验 | ✅ API 端点使用 Spring Security |
| 敏感数据泄露 | ✅ 异常信息不包含完整 Tender 数据 |
| JWT 硬编码 | ✅ 无 |

---

## 五、架构审查

| 检查项 | 结果 |
|--------|------|
| 纯核心无副作用 | ✅ `TenderDeduplicationPolicy` 符合 FP-Java Profile |
| Split-First | ✅ 去重判断逻辑在 Policy，查询编排在 Service |
| 单文件行数 | ✅ 无文件超过 300 行 |
| 循环依赖 | ✅ 无 |

---

## 六、前端审查

| 检查项 | 结果 |
|--------|------|
| Design Token | ✅ 无硬编码颜色（删除的 `DuplicateWarningDialog.vue` 使用 CSS 变量） |
| Mock 残留 | ✅ 无 |
| 数据边界 | ✅ 通过 `check:front-data-boundaries` |

---

## 七、审查结论

| 级别 | 数量 | 通过标准 |
|------|------|----------|
| CRITICAL | 1 | 必须修复 |
| HIGH | 2 | 建议修复 |
| MEDIUM | 3 | 可接受 |

**总体结论**: ❌ **不通过**

**阻塞原因**:
1. `npm run build` 失败 - 前端路由引用已删除页面

**下一步行动**:
1. 修复 `src/router/index.js` 中的 `TenderCreatePage.vue` 引用
2. 确认 `findDuplicates()` 与 `TenderDuplicateException` 的调用链完整性
3. 恢复被删除的单元测试文件

---

## 八、变更文件清单

```
backend/src/main/java/com/xiyu/bid/exception/GlobalExceptionHandler.java   | -29
backend/src/main/java/com/xiyu/bid/repository/TenderRepository.java      | -7
backend/src/main/java/com/xiyu/bid/tender/core/TenderDeduplicationPolicy.java | +14
backend/src/main/java/com/xiyu/bid/tender/service/TenderDeduplicationService.java | +29
backend/src/test/java/com/xiyu/bid/tender/core/TenderDeduplicationPolicyTest.java | -128
backend/src/test/java/com/xiyu/bid/tender/service/TenderDeduplicationServiceTest.java | -138
src/views/Bidding/List.spec.js                                            | -3
src/views/Bidding/List.vue                                                | -7
src/views/Bidding/list/components/DuplicateWarningDialog.vue               | -121
src/views/Bidding/list/useManualTenderCreate.js                           | -23
```
