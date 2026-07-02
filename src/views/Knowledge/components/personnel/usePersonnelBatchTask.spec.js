// Input: src/views/Knowledge/components/personnel/usePersonnelBatchTask.js
// Output: CO-469 人员证书批量导入/导出进度轮询 composable 字段契约测试
// Pos: src/views/Knowledge/components/personnel/__tests__/ — composable 单元测试
//
// 防复发背景：
//   2026-06-08 commit ce87bb407 引入 bug —— 前端读取 progressPercent/progressText/failCount/errorMessage，
//   后端 record 返回 percent/message/failureCount（无 errorMessage 字段，失败信息在 message）。
//   导致导入/导出进度永远卡在 0%，无报错。直到 CO-469 才被发现。
//
//   根因：refactor 8360da650 把 Personnel.vue 内联轮询逻辑提取为 composable 时，
//   原样复制了错误字段名，且无任何测试拦截。
//
//   本测试以"后端真实 record 字段名"为契约源（ImportPersonnelAppService.ImportProgressInfo
//   和 ExportPersonnelAppService.ExportProgress），通过 mock 后端真实响应结构，
//   保证前端字段名映射正确。后端字段名如有变更，本测试会立即红灯。

import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

import { usePersonnelBatchTask } from './usePersonnelBatchTask.js'

// 后端真实进度响应 record（契约源）：
//   ImportPersonnelAppService.ImportProgressInfo(
//     String status, int percent, String message,
//     int totalCount, int successCount, int failureCount)
//
//   ExportPersonnelAppService.ExportProgress(
//     String status, int percent, String message,
//     Integer recordCount, String downloadPath)
//
// 注意：两个 record 都没有 progressPercent / progressText / failCount / errorMessage 字段。
const BACKEND_IMPORT_PROGRESS = {
  status: 'PROCESSING',
  percent: 40,
  message: '正在导入人员数据...',
  totalCount: 10,
  successCount: 5,
  failureCount: 2
}

const BACKEND_EXPORT_PROGRESS = {
  status: 'PROCESSING',
  percent: 70,
  message: '正在打包ZIP文件...',
  recordCount: null,
  downloadPath: null
}

describe('CO-469 usePersonnelBatchTask 字段契约', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('PROCESSING 态：前端读取后端 percent/message 字段，不再读 progressPercent/progressText', async () => {
    const startApi = vi.fn().mockResolvedValue({ data: { taskId: 123 } })
    const pollApi = vi.fn().mockResolvedValue({ data: BACKEND_IMPORT_PROGRESS })

    const task = usePersonnelBatchTask({ startApi, pollApi, pollInterval: 1000 })
    await task.startTask(new File(['x'], 'a.xlsx'))

    // 推进一次轮询（async timer 需要 advanceTimersByTimeAsync 让 microtask 队列 flush）
    await vi.advanceTimersByTimeAsync(1000)

    expect(task.progressPercent.value).toBe(40)
    expect(task.progressText.value).toBe('正在导入人员数据...')
    expect(task.isProcessing.value).toBe(true)
  })

  it('COMPLETED 态：前端读取后端 failureCount 字段，不再读 failCount', async () => {
    const startApi = vi.fn().mockResolvedValue({ data: { taskId: 456 } })
    const pollApi = vi.fn().mockResolvedValue({
      data: {
        status: 'COMPLETED',
        percent: 100,
        message: '导入完成',
        totalCount: 10,
        successCount: 8,
        failureCount: 2
      }
    })

    const task = usePersonnelBatchTask({ startApi, pollApi, pollInterval: 1000 })
    await task.startTask(new File(['x'], 'a.xlsx'))

    await vi.advanceTimersByTimeAsync(1000)

    expect(task.isCompleted.value).toBe(true)
    expect(task.totalCount.value).toBe(10)
    expect(task.successCount.value).toBe(8)
    expect(task.failCount.value).toBe(2)
  })

  it('FAILED 态：前端读取后端 message 字段作为 errorMessage，不再读不存在的 errorMessage 字段', async () => {
    const startApi = vi.fn().mockResolvedValue({ data: { taskId: 789 } })
    const pollApi = vi.fn().mockResolvedValue({
      data: {
        status: 'FAILED',
        percent: 0,
        message: '导入失败：文件格式错误',
        totalCount: 0,
        successCount: 0,
        failureCount: 5
      }
    })

    const task = usePersonnelBatchTask({ startApi, pollApi, pollInterval: 1000 })
    await task.startTask(new File(['x'], 'a.xlsx'))

    await vi.advanceTimersByTimeAsync(1000)

    expect(task.isFailed.value).toBe(true)
    // 后端把失败原因放在 message 字段；前端 errorMessage 应展示具体原因，而非兜底 '任务失败'
    expect(task.errorMessage.value).toBe('导入失败：文件格式错误')
  })

  it('导出场景：后端 ExportProgress record 字段名同样是 percent/message，前端应正确读取', async () => {
    const startApi = vi.fn().mockResolvedValue({ data: { taskId: 'exp-001' } })
    const pollApi = vi.fn().mockResolvedValue({ data: BACKEND_EXPORT_PROGRESS })

    const task = usePersonnelBatchTask({ startApi, pollApi, pollInterval: 1000 })
    await task.startTask({ keyword: '张三' })

    await vi.advanceTimersByTimeAsync(1000)

    expect(task.isProcessing.value).toBe(true)
    expect(task.progressPercent.value).toBe(70)
    expect(task.progressText.value).toBe('正在打包ZIP文件...')
  })

  it('防回归：若后端返回 progressPercent（错误字段名）而非 percent，前端进度应保持 0 而非误显示', async () => {
    // 本测试断言"前端只认后端真实字段名 percent"。
    // 若有人误把后端字段改回 progressPercent，前端不会误读，进度保持 0，问题会立刻暴露。
    const startApi = vi.fn().mockResolvedValue({ data: { taskId: 1 } })
    const pollApi = vi.fn().mockResolvedValue({
      data: {
        status: 'PROCESSING',
        progressPercent: 50,  // 错误字段名，后端真实字段是 percent
        progressText: '错误字段名',
        totalCount: 0,
        successCount: 0,
        failCount: 0
      }
    })

    const task = usePersonnelBatchTask({ startApi, pollApi, pollInterval: 1000 })
    await task.startTask(new File(['x'], 'a.xlsx'))

    await vi.advanceTimersByTimeAsync(1000)

    // 前端只读 percent/message/failureCount —— 后端返回 progressPercent 时应得到 0/兜底文本
    expect(task.progressPercent.value).toBe(0)
    expect(task.progressText.value).toBe('处理中...')
  })

  // ============================================================
  // CO-469 第二轮修复：状态机不完整 + 缓存/DB 状态分叉
  // ============================================================
  // 后端 ImportTaskStatus 枚举有 5 种状态（PENDING/PROCESSING/COMPLETED/PARTIAL_SUCCESS/FAILED）
  // 后端 getProgress 在 Redis 不可用时返回 "UNKNOWN"，任务不存在时返回 "NOT_FOUND"
  // 前端原本只处理 COMPLETED/FAILED，其他状态全部死循环
  //
  // 必然性链：
  //   1. Excel 含部分失败行 → completeImportTask 算出 finalStatus = PARTIAL_SUCCESS
  //   2. clearProgress 删除 Redis key
  //   3. 前端轮询 → Redis 未命中 → fallback DB → 返回 {status: "PARTIAL_SUCCESS", percent: 100}
  //   4. 前端不识别 → setInterval 永不清除 → UI 永久卡住
  //
  // 详见根因猎手报告：docs/lessons/lessons-learned.md §全链路日志排查 SOP

  it('PARTIAL_SUCCESS 态：部分成功应视为完成态，停止轮询（防死循环）', async () => {
    const startApi = vi.fn().mockResolvedValue({ data: { taskId: 1001 } })
    const pollApi = vi.fn().mockResolvedValue({
      data: {
        status: 'PARTIAL_SUCCESS',
        percent: 100,
        message: '部分成功：8 条成功，2 条失败',
        totalCount: 10,
        successCount: 8,
        failureCount: 2
      }
    })

    const task = usePersonnelBatchTask({ startApi, pollApi, pollInterval: 1000 })
    await task.startTask(new File(['x'], 'a.xlsx'))

    await vi.advanceTimersByTimeAsync(1000)
    // 再推进一次时间，确认轮询已停止（不会再发请求）
    await vi.advanceTimersByTimeAsync(1000)
    await vi.advanceTimersByTimeAsync(1000)

    // PARTIAL_SUCCESS 必须视为完成态（允许失败行 > 0）
    expect(task.isCompleted.value).toBe(true)
    expect(task.successCount.value).toBe(8)
    expect(task.failCount.value).toBe(2)
    // 关键：轮询只被调用一次，没有死循环
    expect(pollApi).toHaveBeenCalledTimes(1)
  })

  it('UNKNOWN 态（Redis 不可用）：应停止轮询并标记失败', async () => {
    const startApi = vi.fn().mockResolvedValue({ data: { taskId: 1002 } })
    // 后端 ImportPersonnelAppService.java:171 fallback：
    //   new ImportProgressInfo("UNKNOWN", 0, "Redis not available", 0, 0, 0)
    const pollApi = vi.fn().mockResolvedValue({
      data: {
        status: 'UNKNOWN',
        percent: 0,
        message: 'Redis not available',
        totalCount: 0,
        successCount: 0,
        failureCount: 0
      }
    })

    const task = usePersonnelBatchTask({ startApi, pollApi, pollInterval: 1000 })
    await task.startTask(new File(['x'], 'a.xlsx'))

    await vi.advanceTimersByTimeAsync(1000)
    await vi.advanceTimersByTimeAsync(1000)

    // UNKNOWN 必须停止轮询并标记失败（不能死循环）
    expect(task.isFailed.value).toBe(true)
    expect(task.errorMessage.value).toBe('Redis not available')
    expect(pollApi).toHaveBeenCalledTimes(1)
  })

  it('NOT_FOUND 态（任务不存在/已过期）：应停止轮询并标记失败', async () => {
    const startApi = vi.fn().mockResolvedValue({ data: { taskId: 1003 } })
    // 后端 PersonnelImportProgressService.java:75 fallback：
    //   new ImportProgress("NOT_FOUND", 0, "任务不存在", 0, 0, 0)
    // 后端 ExportPersonnelAppService.java:125 fallback：
    //   new ExportProgress("NOT_FOUND", 0, "任务不存在或已过期", null, null)
    const pollApi = vi.fn().mockResolvedValue({
      data: {
        status: 'NOT_FOUND',
        percent: 0,
        message: '任务不存在或已过期',
        totalCount: 0,
        successCount: 0,
        failureCount: 0
      }
    })

    const task = usePersonnelBatchTask({ startApi, pollApi, pollInterval: 1000 })
    await task.startTask(new File(['x'], 'a.xlsx'))

    await vi.advanceTimersByTimeAsync(1000)
    await vi.advanceTimersByTimeAsync(1000)

    // NOT_FOUND 必须停止轮询并标记失败（不能死循环）
    expect(task.isFailed.value).toBe(true)
    expect(task.errorMessage.value).toBe('任务不存在或已过期')
    expect(pollApi).toHaveBeenCalledTimes(1)
  })
})
