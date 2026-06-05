# 修复: 创建标讯时 creatorId/creatorName 未回填导致权限误判

**标签**: `bug` `backend`

## 问题描述

使用 STAFF 角色创建标讯时，前端同时弹出「标讯已成功入库」和「没有权限访问该资源」两个提示。

## 根因

`TenderCommandService.createTender()` 未设置 `creatorId` 和 `creatorName`，实体中这两个字段为空。标讯创建后前端调用 `fetchTenderDetail()` 触发 `TenderProjectAccessGuard.assertCanAccessTender()` 权限校验，因 `creatorId` 为空导致 `isSelfOwnedTender()` 返回 false，抛出 `AccessDeniedException`。

同时批量导入、表单引擎、外部 API 同步三条路径也存在相同问题。

## 修复内容

- `TenderCommandService`: 新增 `resolveCreator()` 方法，创建标讯时通过 `userRepository` 查询用户全名并回填 `creatorName`
- `TenderController.createTender`: 传入 `@AuthenticationPrincipal` 解析用户 ID
- `TenderController.importTenders`: 同理传入操作用户 ID
- `TenderImportService`: 接收 `userId` 参数并传递给 `createTender`
- `FormSubmissionRouter`: 用已有的 `operatorUsername` 通过 `AuthService` 解析用户 ID
- `TenderSyncController`: 从 API Key 的 `createdBy` 字段解析用户 ID

## 涉及文件

- `TenderCommandService.java`
- `TenderController.java`
- `TenderImportService.java`
- `FormSubmissionRouter.java`
- `TenderSyncController.java`
- `ApiKeyService.java`
