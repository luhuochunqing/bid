---
title: 合订本流式下载空正文与异步 401
space: engineering
category: lesson
tags: [下载, StreamingResponseBody, AccessLogFilter, Spring Security, 401]
sources:
  - backend/src/main/java/com/xiyu/bid/config/AccessLogFilter.java
  - backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java
  - backend/src/main/java/com/xiyu/bid/warehouse/controller/WarehouseExportController.java
  - src/views/Knowledge/components/PerformanceBundleExportDialog.vue
backlinks:
  - _index
created: 2026-10-23
updated: 2026-10-23
health_checked: 2026-10-23
---

# 合订本流式下载空正文与异步 401

## 现象

- 2026-10-22 生产：业绩合订本和仓库导出包下载，应用 access_log 是 200、耗时 3–13ms，Nginx 是 401 且 `body_bytes_sent=0`。
- 业绩 `handleDownload` 没有接住拒绝的 Promise。Vue 3.4 把它交给 `App.vue` 的 `errorCaptured`，整页显示「页面出现异常」。
- 仓库对话框本来就有 `.catch`，失败只出现提示，不会整页崩溃。

## 根因

1. `AccessLogFilter` 把响应包进 `ContentCachingResponseWrapper`，在 `finally` 里 `copyBodyToResponse()`。`StreamingResponseBody` 在过滤器返回之后才写，当时缓存是空的。
2. Spring Security 6.2 默认对异步和错误分发再鉴权。异步分发时 `JwtAuthenticationFilter` 是 `OncePerRequestFilter`，不会重建登录态。匿名入口把响应写成不打日志的 401。
3. 仓库 `Content-Disposition` 含裸中文。Tomcat 会删掉这个头。这不是整页崩溃的原因；正文修好之后，它会变成文件名丢失。

## 修复

- 响应体不再缓存。请求体缓存保留，异常日志仍能读到请求体。
- `authorizeHttpRequests` 调用 `shouldFilterAllDispatcherTypes(false)`。未登录的第一次请求仍是 401。
- 仓库文件名改为 ASCII `filename` 加 RFC 5987 `filename*`。
- 业绩下载失败用 `ElMessage` 提示，文案保留 HTTP 状态。

## 不要做

- 不要改回 `Files.readAllBytes`。仓库导出曾因为约 463MB 的 direct buffer OOM 才改成流式。
- 不要等异步结束再 `copyBodyToResponse`。那会把整个文件再缓冲进内存。
- 不要给下载接口加 `@Idempotent`。`IdempotencyFilter` 在带 `Idempotency-Key` 时仍会缓存整段响应。

## 副作用

- `shouldFilterAllDispatcherTypes(false)` 会把 `observeOncePerRequest` 设为 true。同一次请求里的嵌套 FORWARD/INCLUDE 不再做第二次 URL 鉴权。当前仓库没有 `RequestDispatcher.forward`。
- 访问日志记下的是过滤器返回时的状态，不是流写完之后的状态。修好之后，异步分发不再把 200 改成 401。

## 验收缺口

- 本地测试同时锁住状态码和正文字节。
- 测试环境真实文件的 Nginx `status=200` 且 `body_bytes_sent` 等于文件大小还没做。不能据此说生产已经好。
