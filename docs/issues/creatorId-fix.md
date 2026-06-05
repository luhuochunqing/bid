---
title: "[修复] 创建标讯时 creatorId/creatorName 未回填导致权限误判"
labels: bug, backend
---

## 问题描述

使用 STAFF 角色创建标讯时，前端同时弹出"标讯已成功入库"和"没有权限访问该资源"两个提示。

## 根因

`TenderCommandService.createTender()` 未设置 `creatorId` 和 `creatorName` 字段，实体中这两个字段为空。
标讯创建后前端调用 `fetchTenderDetail()` 触发 `TenderProjectAccessGuard.assertCanAccessTender()` 的权限校验，
该校验通过 `isSelfOwnedTender()` 判断当前用户是否为创建人，因 `creatorId` 为空导致校验失败，抛出 `AccessDeniedException`。

同时，批量导入、表单引擎、外部 API 同步三条路径也同样存在 `creatorId` 为空的问题。

## 修复内容

- `TenderCommandService`: 新增 `resolveCreator()` 方法，在创建标讯时通过 `userRepository` 查询用户全名并回填 `creatorName`
- `TenderController.createTender`: 传入 `@AuthenticationPrincipal` 解析用户 ID
- `TenderController.importTenders`: 同理传入操作用户 ID
- `TenderImportService`: 接收 `userId` 参数并传递给 `createTender`
- `FormSubmissionRouter`: 使用已有的 `operatorUsername` 通过 `AuthService` 解析用户 ID
- `TenderSyncController`: 从 API Key 的 `createdBy` 字段解析用户 ID

## 涉及文件

- `backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandService.java`
- `backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java`
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderImportService.java`
- `backend/src/main/java/com/xiyu/bid/formengine/application/FormSubmissionRouter.java`
- `backend/src/main/java/com/xiyu/bid/integration/external/TenderSyncController.java`
- `backend/src/main/java/com/xiyu/bid/apikey/application/ApiKeyService.java`
