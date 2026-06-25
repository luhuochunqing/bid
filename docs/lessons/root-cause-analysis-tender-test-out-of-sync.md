
# 投标服务测试与生产代码不同步导致打包失败

> 日期: 2026-06-25
> 排查者: trae

---

## 现场还原

**症状素描**: 部署打包阶段执行 `mvn -DskipTests package` 时报编译错误，提示 `TenderSubmissionService` 构造函数参数不匹配，导致整个部署打包失败，打包中断。

**报错信息:

```
TenderSubmissionServiceTest.java:[54,29] constructor TenderSubmissionService in class TenderSubmissionService cannot be applied to given types;
  required: TenderRepository, TenderEvaluationRepository, UserRepository,
            TenderAssignmentPermissions, TenderProjectAccessGuard,
            ObjectMapper, ApplicationEventPublisher, NotificationApplicationService
  found:    TenderRepository, TenderEvaluationRepository, UserRepository, TaskService,
            TenderAssignmentPermissions, TenderProjectAccessGuard,
            ObjectMapper, ApplicationEventPublisher, NotificationApplicationService
  reason: actual and formal argument lists differ in length
```

**边界划定**:
- 触发场景：本地打包发布时，从 Gitee 最新代码，包含 CO-349 相关提交（删除投标时自动创建的待立项任务
- 影响范围：仅影响所有调用 TenderSubmissionServiceTest.java 测试文件
- 不影响：生产代码本身没问题，测试代码未同步更新

**思维沙箱**:
1.  `-DskipTests` 应该跳过测试，为什么还会编译失败？
2. 为什么生产代码变了，测试代码没跟着变？
3. CI 为什么没拦住这个问题？

---

## 剥洋葱：逆向调用链

### Layer 1 — Maven 构建流程

```
mvn -DskipTests package
  → resources:resources
  → compiler:compile (生产代码编译 ✅)
  → resources:testResources
  → compiler:testCompile (测试代码编译 ❌)
  → surefire:test (被 -DskipTests 跳过)
  → jar:jar
```

**关键发现**：`-DskipTests` **只跳过测试运行（surefire:test 阶段），不跳过测试代码编译（compiler:testCompile 阶段）。只要测试代码有编译错误，打包照样失败。

### Layer 2 — 根因定位

生产代码变更（CO-349: 删除投标时自动创建的待立项任务）：

```java
// TenderSubmissionService.java（生产代码）
@Service
@RequiredArgsConstructor
public class TenderSubmissionService {
    private final TenderRepository tenderRepository;
    private final TenderEvaluationRepository tenderEvaluationRepository;
    private final UserRepository userRepository;
    // TaskService 已被移除 ← CO-349
    private final TenderAssignmentPermissions permissions;
    private final TenderProjectAccessGuard accessGuard;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationApplicationService notificationAppService;
}
```

测试代码未同步：

```java
// TenderSubmissionServiceTest.java（测试代码）
@Mock private TaskService taskService;  // ← 仍在

submissionService = new TenderSubmissionService(
    tenderRepository, tenderEvaluationRepository,
    userRepository, taskService,  // ← 还传 taskService
    permissions, accessGuard, objectMapper, eventPublisher, notificationAppService);
```

### Layer 3 — 为什么 CI 没拦住？

待确认：可能原因可能是：
1. CI 中该测试类被排除在 CI 运行的测试集合之外
2. 或者 CI 运行时测试被忽略/禁用了
3. 或者提交时没跑全量测试，只跑了部分

---

## 零号病人

**文件**: `backend/src/test/java/com/xiyu/bid/tender/service/TenderSubmissionServiceTest.java

**根因**: CO-349 提交中，生产代码 `TenderSubmissionService` 移除了 `TaskService` 依赖，但对应的单元测试类 `TenderSubmissionServiceTest` 未同步更新，导致测试编译失败。

---

## 修复方案

### 修复内容:
1. 移除 `TaskService` 的 mock 和相关 import
2. 更新构造函数调用，移除 `taskService` 参数
3. 更新 `participateBid_Success` 测试的预期返回值（不再返回 "投标成功"，todoId 为 null）

### 修复的文件: [TenderSubmissionServiceTest.java](file:///Users/user/xiyu/xiyu-bid-poc/backend/src/test/java/com/xiyu/bid/tender/service/TenderSubmissionServiceTest.java)

---

## 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 生产代码变更时忘记同步更新测试代码 | 修改服务类依赖时，必须同步检查对应的单元测试 | 重构/修改服务类构造函数参数变更时，grep 所有测试类中该类的实例化 |
| `-DskipTests` 不跳过测试编译 | 不要以为加了 `-DskipTests` 测试代码有问题也能打包 | 要用 `-Dmaven.test.skip=true` 才会完全跳过测试编译和运行 |
| 测试代码与生产代码不同步 | 单元测试是生产代码的影子，必须同步演进 | 提交前跑一下全量测试编译 `mvn test-compile` 验证测试代码能编译 |

---

## 验证命令

```bash
# 只编译测试代码，快速验证测试代码是否能编译
cd backend && mvn test-compile

# 完全跳过测试（编译+运行
mvn -Dmaven.test.skip=true package

# 搜索某个服务类的测试引用
grep -rn "new TenderSubmissionService" backend/src/test/
```

---

## 相关文档

- [build-gotchas.md](file:///Users/user/xiyu/xiyu-bid-poc/docs/lessons/build-gotchas.md) — Maven 构建陷阱
- [lessons-learned.md](file:///Users/user/xiyu/xiyu-bid-poc/docs/lessons/lessons-learned.md) — 通用工程教训
