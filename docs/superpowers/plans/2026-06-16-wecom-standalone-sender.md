# 独立企微消息发送能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建独立 `wecom` 模块（按工号、走 CRM `POST /common/sendMessage`、`flag=3`）作为企微消息发送的唯一能力，并把 `notification/outbound` 里镜像企微的那条路改为调用它。

**Architecture:** 通道彻底解耦：站内信归 `notification` 收件箱；企微归新 `wecom` 模块。`wecom → crm`（复用 `CrmMessageService`/`CrmHttpClient`，不重造 HTTP/鉴权），且 `wecom` 不依赖 `notification`。`notification/outbound` 反向依赖 `wecom`：`WeComPushService` 只把"收件人解析 + 传输调用"从直连企微改为按工号调 `WecomMessageSender`，任务/重试/DLQ/出站日志管线不动。

**Tech Stack:** Java 21 / Spring Boot / JPA / ArchUnit / JUnit 5 + Mockito + AssertJ / Maven

**参考 spec:** `docs/superpowers/specs/2026-06-16-wecom-standalone-sender-design.md`

---

## 执行前置（必须先做）

> 本仓库为多 Agent worktree。当前 gemini worktree 的 `agent/gemini-init` 是 **bootstrap 分支，禁止直接 commit 业务代码**（CLAUDE.md §7）。开始 Task 1 前：

- [ ] **建任务分支**（在主 worktree 或本 worktree 执行）：
  ```bash
  cd /Users/user/xiyu/worktrees/gemini
  ./scripts/agent-start-task.sh gemini wecom-standalone-sender origin/main
  ```
  这会创建 worktree `worktrees/gemini-wecom-standalone-sender` + 分支 `agent/gemini/wecom-standalone-sender`。后续所有 Task 在该任务 worktree 内进行。
- [ ] **早操同步**（进入任务 worktree 后）：
  ```bash
  source scripts/dev-env.sh
  ./scripts/sync-env.sh .
  ./scripts/who-touches.sh backend/src/main/java/com/xiyu/bid/notification/outbound backend/src/main/java/com/xiyu/bid/crm
  ```
  退出码 0 且无输出 = 没人撞这两块，可开工。

所有 `mvn` 命令在 `backend/` 目录下执行。本仓库 **禁止 `git push --no-verify`**，commit 走项目 git 包装器（`source scripts/dev-env.sh` 后裸 `git` 即受保护）。

---

## 文件结构

| 文件 | 操作 | 职责 |
|---|---|---|
| `backend/src/main/java/com/xiyu/bid/wecom/WecomSendResult.java` | 新建 | 企微发送结果 record（独立于 notification） |
| `backend/src/main/java/com/xiyu/bid/wecom/WecomMessageSender.java` | 新建 | `@Service`，唯一入口 `send(工号[], title, content)`，`flag=3` 调 `CrmMessageService` |
| `backend/src/main/java/com/xiyu/bid/wecom/README.md` | 新建 | 模块说明 + CRM 配置前置 |
| `backend/src/test/java/com/xiyu/bid/wecom/WecomSendResultTest.java` | 新建 | 结果工厂测试 |
| `backend/src/test/java/com/xiyu/bid/wecom/WecomMessageSenderTest.java` | 新建 | 发送器测试（mock CrmMessageService） |
| `backend/src/main/java/com/xiyu/bid/notification/outbound/service/WeComPushService.java` | 修改 | 换收件人(工号) + 换传输(调 WecomMessageSender)，删企微直连依赖 |
| `backend/src/test/java/com/xiyu/bid/notification/outbound/service/WeComPushServiceTest.java` | 修改 | 适配新依赖与契约 |
| `backend/src/main/java/com/xiyu/bid/notification/outbound/README.md` | 修改 | 注明企微传输已改由 `wecom` 模块承担 |
| `backend/src/test/java/com/xiyu/bid/ArchitectureTest.java` | 修改（可选） | 新增 `wecom` 不得依赖 `notification` 的边界规则 |

---

## Task 1: WecomSendResult 结果类型

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/wecom/WecomSendResult.java`
- Test: `backend/src/test/java/com/xiyu/bid/wecom/WecomSendResultTest.java`

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/xiyu/bid/wecom/WecomSendResultTest.java`：

```java
package com.xiyu.bid.wecom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WecomSendResult — 工厂构造正确")
class WecomSendResultTest {

    @Test
    @DisplayName("success 工厂置位 success=true")
    void success_factory() {
        WecomSendResult r = WecomSendResult.success(0, "ok");

        assertThat(r.success()).isTrue();
        assertThat(r.code()).isEqualTo(0);
        assertThat(r.message()).isEqualTo("ok");
    }

    @Test
    @DisplayName("failure 工厂置位 success=false")
    void failure_factory() {
        WecomSendResult r = WecomSendResult.failure(500, "boom");

        assertThat(r.success()).isFalse();
        assertThat(r.code()).isEqualTo(500);
        assertThat(r.message()).isEqualTo("boom");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd backend && mvn test -Dtest=WecomSendResultTest
```
Expected: 编译失败（`WecomSendResult` 不存在）。

- [ ] **Step 3: 最小实现**

创建 `backend/src/main/java/com/xiyu/bid/wecom/WecomSendResult.java`：

```java
package com.xiyu.bid.wecom;

/**
 * 企微消息发送结果。独立于 notification 模块的自有结果类型。
 *
 * @param success CRM /common/sendMessage 是否成功
 * @param code    CRM 响应 code（成功时通常为 0）
 * @param message CRM 响应 msg 或本地错误描述
 */
public record WecomSendResult(boolean success, int code, String message) {

    public static WecomSendResult success(int code, String message) {
        return new WecomSendResult(true, code, message);
    }

    public static WecomSendResult failure(int code, String message) {
        return new WecomSendResult(false, code, message);
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
cd backend && mvn test -Dtest=WecomSendResultTest
```
Expected: `Tests run: 2, Failures: 0` + `BUILD SUCCESS`。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/xiyu/bid/wecom/WecomSendResult.java \
        backend/src/test/java/com/xiyu/bid/wecom/WecomSendResultTest.java
git commit -m "feat(wecom): add WecomSendResult result type"
```

---

## Task 2: WecomMessageSender 发送器

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/wecom/WecomMessageSender.java`
- Test: `backend/src/test/java/com/xiyu/bid/wecom/WecomMessageSenderTest.java`

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/xiyu/bid/wecom/WecomMessageSenderTest.java`：

```java
package com.xiyu.bid.wecom;

import com.xiyu.bid.crm.application.CrmMessageService;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler.CrmApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WecomMessageSender — flag=3 调 CRM /common/sendMessage")
class WecomMessageSenderTest {

    @Mock private CrmMessageService crmMessageService;
    private WecomMessageSender sender;

    @BeforeEach
    void setUp() {
        sender = new WecomMessageSender(crmMessageService);
    }

    @Test
    @DisplayName("CRM 成功 -> success，透传 recipientNos 与 flag=3")
    void crmSuccess_returnsSuccess() {
        when(crmMessageService.sendMessage(eq(List.of("E001", "E002")), eq("标题"), eq("内容"), eq(3)))
            .thenReturn(new CrmApiResponse(0, "ok", null, true));

        WecomSendResult result = sender.send(List.of("E001", "E002"), "标题", "内容");

        assertThat(result.success()).isTrue();
        assertThat(result.code()).isEqualTo(0);
        assertThat(result.message()).isEqualTo("ok");
    }

    @Test
    @DisplayName("CRM 失败 -> failure")
    void crmFailure_returnsFailure() {
        when(crmMessageService.sendMessage(anyList(), anyString(), anyString(), eq(3)))
            .thenReturn(new CrmApiResponse(500, "boom", null, false));

        WecomSendResult result = sender.send(List.of("E001"), "标题", "内容");

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(500);
        assertThat(result.message()).isEqualTo("boom");
    }

    @Test
    @DisplayName("空 recipientNos -> failure，且不调用 CRM")
    void emptyRecipients_returnsFailureWithoutCall() {
        WecomSendResult result = sender.send(List.of(), "标题", "内容");

        assertThat(result.success()).isFalse();
        verify(crmMessageService, never()).sendMessage(anyList(), anyString(), anyString(), eq(3));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd backend && mvn test -Dtest=WecomMessageSenderTest
```
Expected: 编译失败（`WecomMessageSender` 不存在）。

- [ ] **Step 3: 最小实现**

创建 `backend/src/main/java/com/xiyu/bid/wecom/WecomMessageSender.java`：

```java
package com.xiyu.bid.wecom;

import com.xiyu.bid.crm.application.CrmMessageService;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler.CrmApiResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 独立的企微消息发送能力。任何模块需要发企微时，按工号直接调用本服务。
 *
 * <p>传输经 crm 模块委托给西域统一消息服务 {@code POST /common/sendMessage}，
 * 固定 {@code flag=3}（仅企微）。站内信不在本能力范围（由 notification 收件箱承担）。
 *
 * <p>不做重试：CRM 层（{@code CrmHttpClient}）已对 5xx 内置重试；
 * 任务级重试由调用方（如 notification 投递管线）负责。
 */
@Service
public class WecomMessageSender {

    /** CRM /common/sendMessage 的"仅企微"推送方式。 */
    public static final int FLAG_WECOM_ONLY = 3;

    private final CrmMessageService crmMessageService;

    public WecomMessageSender(CrmMessageService crmMessageService) {
        this.crmMessageService = crmMessageService;
    }

    /**
     * 按工号发送企微消息。
     *
     * @param recipientNos 接收人工号列表；空则返回 failure，不调用 CRM
     * @param title        消息标题
     * @param content      消息内容
     * @return 发送结果
     */
    public WecomSendResult send(List<String> recipientNos, String title, String content) {
        if (recipientNos == null || recipientNos.isEmpty()) {
            return WecomSendResult.failure(-1, "recipientNos is empty");
        }
        CrmApiResponse api = crmMessageService.sendMessage(recipientNos, title, content, FLAG_WECOM_ONLY);
        return api.success()
            ? WecomSendResult.success(api.code(), api.msg())
            : WecomSendResult.failure(api.code(), api.msg());
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
cd backend && mvn test -Dtest=WecomMessageSenderTest
```
Expected: `Tests run: 3, Failures: 0` + `BUILD SUCCESS`。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/xiyu/bid/wecom/WecomMessageSender.java \
        backend/src/test/java/com/xiyu/bid/wecom/WecomMessageSenderTest.java
git commit -m "feat(wecom): add WecomMessageSender via CRM /common/sendMessage (flag=3)"
```

---

## Task 3: WeComPushService 改调独立能力

把 `WeComPushService` 的收件人解析从 `wecomUserId` 改为 `employeeNumber`，传输从 `WeComMessagePublisher.sendTextMessage` 改为 `WecomMessageSender.send`，移除企微直连依赖。入口签名不变 → `NotificationDeliveryJobService` 零改动。

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/notification/outbound/service/WeComPushService.java`
- Modify: `backend/src/test/java/com/xiyu/bid/notification/outbound/service/WeComPushServiceTest.java`

- [ ] **Step 1: 重写测试（定义新契约）**

用以下内容**整体替换** `backend/src/test/java/com/xiyu/bid/notification/outbound/service/WeComPushServiceTest.java`：

```java
package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.wecom.WecomMessageSender;
import com.xiyu.bid.wecom.WecomSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComPushService — 按工号委托 WecomMessageSender 发企微")
class WeComPushServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WecomMessageSender wecomMessageSender;

    private WeComPushService service;

    private static NotificationCreatedEvent event() {
        return new NotificationCreatedEvent(100L, List.of(7L), "MENTION", "你被提到", "PROJECT", 42L);
    }

    private static User userWithEmployee(String employeeNumber) {
        return User.builder().id(7L).username("u").email("a@x.com").password("p")
            .fullName("User").role(User.Role.STAFF).employeeNumber(employeeNumber).build();
    }

    @BeforeEach
    void setUp() {
        service = new WeComPushService(userRepository, wecomMessageSender, "https://xiyu.example.com");
    }

    @Test
    @DisplayName("用户不存在 -> skip，不调用发送器")
    void userNotFound_skipped() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isTrue();
        assertThat(result.message()).contains("employee number");
        verify(wecomMessageSender, never()).send(anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("用户无工号 -> skip，不调用发送器")
    void noEmployeeNumber_skipped() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("")));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isTrue();
        assertThat(result.message()).contains("employee number");
        verify(wecomMessageSender, never()).send(anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("发送成功 -> sent，收件人为工号")
    void successfulSend_returnsSuccess() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(wecomMessageSender.send(eq(List.of("E007")), anyString(), anyString()))
            .thenReturn(WecomSendResult.success(0, "ok"));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isFalse();
        assertThat(result.errcode()).isEqualTo(0);
    }

    @Test
    @DisplayName("发送器返回 failure -> failed")
    void failedSend_returnsFailure() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(wecomMessageSender.send(anyList(), anyString(), anyString()))
            .thenReturn(WecomSendResult.failure(500, "crm down"));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isFalse();
        assertThat(result.errcode()).isEqualTo(500);
        assertThat(result.message()).isEqualTo("crm down");
    }

    @Test
    @DisplayName("content 含格式化描述与深链 URL")
    void send_passesFormattedContent() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(wecomMessageSender.send(anyList(), anyString(), anyString()))
            .thenReturn(WecomSendResult.success(0, "ok"));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(wecomMessageSender).send(eq(List.of("E007")), anyString(), content.capture());
        assertThat(content.getValue()).contains("https://xiyu.example.com");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd backend && mvn test -Dtest=WeComPushServiceTest
```
Expected: 编译失败（`WeComPushService` 构造器签名不匹配 —— 旧构造器有 5 个企微直连参数，测试按新签名 `(userRepository, wecomMessageSender, platformBaseUrl)` 构造）。

- [ ] **Step 3: 重写实现**

用以下内容**整体替换** `backend/src/main/java/com/xiyu/bid/notification/outbound/service/WeComPushService.java`：

```java
package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.outbound.application.NotificationDeliveryCommand;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter;
import com.xiyu.bid.notification.outbound.core.WeComMessageFormatter.FormattedMessage;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.wecom.WecomMessageSender;
import com.xiyu.bid.wecom.WecomSendResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 站内通知镜像到企微的编排。企微传输委托给独立能力 {@link WecomMessageSender}
 * （按工号、走 CRM /common/sendMessage、flag=3），不再直连企微 API。
 *
 * <p>收件人解析使用 User.employeeNumber（工号）。投递任务/重试/DLQ 由
 * {@code NotificationDeliveryJobService} 负责，本类只做单次推送并返回结果。
 */
@Service
public class WeComPushService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WeComPushService.class);

    private final UserRepository userRepository;
    private final WecomMessageSender wecomMessageSender;
    private final String platformBaseUrl;

    public WeComPushService(
        UserRepository userRepository,
        WecomMessageSender wecomMessageSender,
        @Value("${app.platform.base-url:http://localhost:1314}") String platformBaseUrl
    ) {
        this.userRepository = userRepository;
        this.wecomMessageSender = wecomMessageSender;
        this.platformBaseUrl = platformBaseUrl;
    }

    public NotificationDeliveryResult pushForRecipient(NotificationCreatedEvent event, Long recipientUserId) {
        return push(NotificationDeliveryCommand.fromEvent(event, recipientUserId));
    }

    public NotificationDeliveryResult push(NotificationDeliveryCommand command) {
        Optional<User> userOpt = userRepository.findById(command.recipientUserId());
        if (userOpt.isEmpty() || isBlank(userOpt.get().getEmployeeNumber())) {
            return NotificationDeliveryResult.skip("recipient has no employee number");
        }

        String employeeNumber = userOpt.get().getEmployeeNumber();
        FormattedMessage message = WeComMessageFormatter.format(
            command.title(), command.type(), command.sourceEntityType(), command.sourceEntityId(), platformBaseUrl);
        String content = message.description() + "\n" + message.url();

        try {
            WecomSendResult result = wecomMessageSender.send(List.of(employeeNumber), message.title(), content);
            return result.success()
                ? NotificationDeliveryResult.success(result.code(), result.message())
                : NotificationDeliveryResult.failure(result.code(), result.message());
        } catch (RuntimeException e) {
            log.warn("Wecom send failed for employee {}: {}", employeeNumber, e.getMessage());
            throw e;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
cd backend && mvn test -Dtest=WeComPushServiceTest
```
Expected: `Tests run: 5, Failures: 0` + `BUILD SUCCESS`。

- [ ] **Step 5: 确认下游装配未受影响**

`NotificationDeliveryJobService` 注入的是 `WeComPushService`（接口签名未变）。跑它的既有测试：

```bash
cd backend && mvn test -Dtest=NotificationDeliveryJobServiceTest
```
Expected: `BUILD SUCCESS`（该测试 mock 了 `WeComPushService`，不受其内部依赖变化影响）。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/xiyu/bid/notification/outbound/service/WeComPushService.java \
        backend/src/test/java/com/xiyu/bid/notification/outbound/service/WeComPushServiceTest.java
git commit -m "refactor(notification-outbound): route WeCom push via WecomMessageSender by employee number"
```

---

## Task 4: 文档（模块说明 + 配置前置 + outbound README 注记）

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/wecom/README.md`
- Modify: `backend/src/main/java/com/xiyu/bid/notification/outbound/README.md`

- [ ] **Step 1: 新建 `backend/src/main/java/com/xiyu/bid/wecom/README.md`**

```markdown
# wecom 模块（企微消息发送）

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
独立的企微消息发送能力。任何模块需要发企微时，按工号直接调用 `WecomMessageSender`，
不依赖 notification 站内信管线。通道职责划分：

- **站内信** → `notification` 收件箱
- **企微** → 本模块（经 CRM `/common/sendMessage`，`flag=3` 仅企微）

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `WecomMessageSender.java` | Service | 唯一入口：`send(工号[], title, content)`，`flag=3` 调 CRM |
| `WecomSendResult.java` | Record | 发送结果（success/code/message），模块自有 |

## 依赖方向
`wecom → crm`（复用 `CrmMessageService` / `CrmHttpClient` / `CrmAuthService`，不重造 HTTP/鉴权）。
**本模块不得依赖 `notification`**（由 ArchitectureTest 边界规则保护）。

## 配置前置
企微发送依赖 CRM 统一消息服务，必须先配置（否则发送失败 → 由调用方重试/DLQ）：

- `app.crm.message-base-url`（env `XIYU_CRM_MESSAGE_BASE_URL`）
- `app.crm.client-id` / `app.crm.client-secret`（或 OAuth 账号）

dev 环境 `message-base-url` 默认为空；联调企微前需显式注入。

## 不做
- 不做站内信（`flag=1/2`）。
- 不做重试（CRM 层已有 5xx 重试；任务级重试交给调用方）。
- 不做批量聚合（按调用方单次入参）。
```

- [ ] **Step 2: 更新 `backend/src/main/java/com/xiyu/bid/notification/outbound/README.md`**

在"职责说明"段落末尾追加一句（在"对业务模块零耦合。"之后）：

将原文：
```
站内通知的出站适配层：在 `NotificationApplicationService.createNotification` 发布 `NotificationCreatedEvent` 之后，异步将通知投递到外部通道（当前仅企业微信），并记录出站日志供管理员排查。对业务模块零耦合。
```
改为：
```
站内通知的出站适配层：在 `NotificationApplicationService.createNotification` 发布 `NotificationCreatedEvent` 之后，异步将通知投递到企微通道，并记录出站日志供管理员排查。对业务模块零耦合。

> 企微传输由独立的 `wecom` 模块承担（`WecomMessageSender`，按工号、走 CRM `/common/sendMessage`、`flag=3`）。本层不再直连企微 API；`WeComPushService` 仅负责"按工号解析收件人 + 委托发送 + 结果映射"。站内信仍由 `notification` 收件箱单一承担。
```

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/java/com/xiyu/bid/wecom/README.md \
        backend/src/main/java/com/xiyu/bid/notification/outbound/README.md
git commit -m "docs(wecom,notification-outbound): document standalone 企微 sender & routing change"
```

---

## Task 5（可选·边界加固）：ArchUnit 规则 —— wecom 不得依赖 notification

锁定 spec D1/D4 的通道解耦边界，防止日后回退耦合。

**Files:**
- Modify: `backend/src/test/java/com/xiyu/bid/ArchitectureTest.java`

- [ ] **Step 1: 先看既有规则导入方式**

```bash
cd backend && grep -nE "ClassFileImporter|importPackages|ArchRule|@AnalyzeClasses|com.tngtech" src/test/java/com/xiyu/bid/ArchitectureTest.java | head
```
记下该文件如何取得被测类集合（`JavaClasses` 来源 / 是否 `@AnalyzeClasses`），新增规则沿用同一来源。

- [ ] **Step 2: 新增规则**

在 `ArchitectureTest.java` 中新增一个测试方法（`classes` 变量名按 Step 1 实际替换为该类取得 `JavaClasses` 的变量/字段名）：

```java
@Test
void wecom_模块不得依赖_notification() {
    ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
        .that().resideInAPackage("..wecom..")
        .should().dependOnClassesThat().resideInAPackage("..notification..")
        .because("wecom 是独立企微发送能力，不得反向依赖 notification 站内信模块");
    rule.check(classes);
}
```

- [ ] **Step 3: 跑测试确认通过**

```bash
cd backend && mvn test -Dtest=ArchitectureTest
```
Expected: `BUILD SUCCESS`（`wecom` 包目前只依赖 `crm`，不触碰 `notification`）。

- [ ] **Step 4: 提交**

```bash
git add backend/src/test/java/com/xiyu/bid/ArchitectureTest.java
git commit -m "test(arch): forbid wecom -> notification dependency"
```

---

## Task 6: 全量验证

- [ ] **Step 1: 跑本次新增/改动 + 相关测试**

```bash
cd backend && mvn test -Dtest='WecomSendResultTest,WecomMessageSenderTest,WeComPushServiceTest,NotificationDeliveryJobServiceTest,ArchitectureTest'
```
Expected: 全部 `BUILD SUCCESS`。

- [ ] **Step 2: 编译 + 架构测试整体**

```bash
cd backend && mvn test-compile && mvn test -Dtest=ArchitectureTest
```
Expected: `BUILD SUCCESS`（确认新 `wecom` 包未触发任何 ArchUnit 规则违规）。

- [ ] **Step 3: checkstyle（仅扫新文件，不阻断用 quality-audit）**

```bash
cd backend && mvn -Pjava-quality -Dquality.skip=false \
    -Dquality.includes="**/wecom/**" \
    -Dquality.failOnViolation=false checkstyle:check
```
Expected: 无 violation（新文件遵循既有格式：4 空格缩进、Javadoc、`@Service` 等）。

- [ ] **Step 4: git status 确认只改了授权文件**

```bash
git status
```
Expected: 仅本计划"文件结构"表列出的文件有变更，无意外文件。

- [ ] **Step 5: 推送 WIP 分支（lease 协议要求每日 push）**

```bash
git push origin HEAD:$(git rev-parse --abbrev-ref HEAD)
```
> 通过项目 git 包装器（已 `source scripts/dev-env.sh`），禁止 `--no-verify`。

- [ ] **Step 6: 建 PR**

```bash
./scripts/pr-create.sh
```
PR 描述写明：独立 `wecom` 企微发送能力 + `notification/outbound` 改调它；引用 spec `docs/superpowers/specs/2026-06-16-wecom-standalone-sender-design.md`；列出 §13 plan 阶段待办中已落实/仍待确认项（如 `NotificationFailureClassifier` 对 CRM 错误串的分类、dev CRM 可达性）。

---

## Self-Review（plan 作者已执行）

**Spec 覆盖**：spec §3 G1→Task1+2；G2→Task3；G3(flag=3/站内信归本平台)→Task2 固定 flag=3 + Task3 仅发企微；§6 组件清单逐项对应 Task1-3；§8 结果映射→Task2/3 代码；§9 测试→Task1/2/3 测试；§10 配置前置→Task4 README；§13 ArchUnit→Task5；§13 failure-classifier 待办→Task6 PR 描述。无遗漏。

**占位符扫描**：无 TBD/TODO；ArchUnit Task5 的 `classes` 变量名以"先看既有方式再对齐"的 Step1+Step2 形式给出（非占位，是显式对齐步骤）。

**类型一致性**：`WecomSendResult(success, code, message)` / `WecomMessageSender.send(List<String>, String, String)` / `FLAG_WECOM_ONLY=3` / `CrmApiResponse(code, msg, data, success)` / `NotificationDeliveryResult.success|skip|failure(errcode, message)` —— Task1-3 间命名与签名一致。

## spec §13 待办在 plan 中的落点

- `NotificationFailureClassifier` 对 CRM 错误串分类 → 现有重试/DLQ 复用，**不阻断本期**；Task6 PR 描述列为"仍待确认"。
- `CrmAuthService` 线程安全 → 现有缓存式实现，本期不改动；Task6 PR 描述列为"已复用，待联调验证"。
- dev CRM 可达性/降噪 → Task4 README 已注明前置；降噪开关**本期不做**（保持单一能力，不引入 flag）。
