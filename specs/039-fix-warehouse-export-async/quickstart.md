# Quickstart: 修复仓库全量合订本导出任务创建失败

**Date**: 2026-07-17
**Feature**: 039-fix-warehouse-export-async

## 验证步骤

### 1. 后端单元测试

```bash
cd /Users/user/xiyu/worktrees/claude/backend
mvn test -Dtest=WarehouseExportAsyncExecutorTest,WarehouseExportAppServiceTest
```

**预期**: 
- `WarehouseExportAsyncExecutorTest`: 验证 doExport 流程 + Word 合订本降级语义
- `WarehouseExportAppServiceTest`: 验证 export() 委托调用 asyncExecutor + createTask 事务

### 2. 后端架构测试

```bash
cd /Users/user/xiyu/worktrees/claude/backend
mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

**预期**: 全绿。新增 WarehouseExportAsyncExecutor 在 application/ 包，符合 FP-Java 分层。

### 3. 后端 Controller 契约测试（回归验证）

```bash
cd /Users/user/xiyu/worktrees/claude/backend
mvn test -Dtest=WarehouseExportControllerTest
```

**预期**: 全绿。API 契约不变。

### 4. 后端全量测试

```bash
cd /Users/user/xiyu/worktrees/claude/backend
mvn test
```

**预期**: 全绿，无回归。

### 5. 前端构建验证

```bash
cd /Users/user/xiyu/worktrees/claude
npm run build
```

**预期**: 构建成功（前端无改动，仅验证无回归）。

### 6. 集成验证（主工作区 trae）

在主工作区 `/Users/user/xiyu/worktrees/trae` 启动开发环境：

```bash
cd /Users/user/xiyu/worktrees/trae
export XIYU_DEV_CONFIRMED=1
npm run dev:all
```

以 `bid_admin` 登录 `http://127.0.0.1:1323`，进入仓库管理页：
1. 点击"导出台账（含附件）"
2. 保持默认选项（filter 模式 + ALL + WORD_COMBINED）
3. 点击"开始导出"
4. **预期**: 30 秒内显示"导出任务排队中..."，不再显示"创建导出任务失败"
5. 查看后端日志：执行线程名以 `warehouse-export-` 开头

### 7. 降级语义验证

构造一个损坏的 PDF 附件放入仓库附件目录，触发全量合订本导出：
1. **预期**: 任务状态 COMPLETED
2. **预期**: ZIP 包含 仓库信息台账.xlsx + 附件目录，但 Word 合订本可能缺失或部分内容
3. **预期**: 后端日志有 `log.warn` Word 合订本生成失败，但任务未 FAILED

## 已知技术债

### P3-3 `getExportFile` 全量读入内存

**位置**: `WarehouseExportAppService.getExportFile`

**问题**: 当前使用 `Files.readAllBytes(path)` 把整个 ZIP 加载到内存。Constitution `Export Limit: 单次导出最多 500 条记录`，但 500 条 + 附件 + Word 合订本的 ZIP 可能达数十 MB，高并发下载会撑爆堆。

**建议**: Controller 层改用 `StreamingResponseBody` 或 `Resource` 流式输出。需要修改 Controller + Service 两层。

**优先级**: 中（当前单用户场景可接受，多用户高并发下载时需修复）

### P3-2 `WarehouseExportZipBuilder.buildZip` 输出到临时目录

**问题**: `buildZip` 在临时目录生成 zip，AsyncExecutor 再 `Files.move` 到 export 目录。虽然已用 `Files.move`（原子操作）替代 `copy + delete`，但理想方案是 `buildZip` 直接生成到 export 目录。

**建议**: 修改 `WarehouseExportZipBuilder.buildZip` 签名，接收输出路径参数。影响范围略大，留待后续优化。

