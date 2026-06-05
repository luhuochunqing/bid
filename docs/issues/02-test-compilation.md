# 修复: 三个测试文件因方法签名变更导致编译失败

**标签**: `bug` `test`

## 问题描述

`TenderCommandService.createTender()` 改为双参、`TenderImportService.importFromExcel()` 新增 userId 参数、`FormSubmissionRouter` 新增 AuthService 依赖后，三个测试文件未同步更新，编译失败。

## 涉及测试

1. **TenderImportServiceTest** — 7 处 `importFromExcel(file)` 少传 userId 参数；4 处 `verify(createTender(any()))` 只匹配单参重载
2. **FormSubmissionRouterTest** — 构造器少传 AuthService 参数；所有 `createTender` mock/verify 只匹配单参重载，运行时 NPE
3. **TenderCommandServiceTest** — 构造器少传 UserRepository 参数

## 修复

- 所有 importFromExcel 调用补充 `userId` 参数（传 `1L`）
- FormSubmissionRouterTest 添加 `@Mock AuthService` + 构造参数 + stub `resolveUserIdByUsername`
- 所有 `createTender` mock/verify 改为双参匹配 `any(TenderDTO.class), any()`
- TenderCommandServiceTest 构造器补充 `userRepository`
