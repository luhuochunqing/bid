import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { useQualityCheckDrawer } from './useQualityCheckDrawer'
import { complianceApi } from '@/api/modules/ai.js'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { ElMessage } from 'element-plus'

vi.mock('@/api/modules/ai.js', () => ({
  complianceApi: {
    getBidQualityReport: vi.fn(),
    triggerBidQualityCheck: vi.fn(),
    getBidQualityProgress: vi.fn()
  }
}))

vi.mock('@/api/modules/scoreParse.js', () => ({
  scoreParseApi: {
    getItems: vi.fn(),
    getResults: vi.fn()
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn()
  },
  ElMessageBox: {
    confirm: vi.fn()
  }
}))

describe('useQualityCheckDrawer', () => {
  const props = { projectId: 123 }
  const emit = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('打开抽屉且报告已完成时正常加载报告并不开启轮询', async () => {
    const mockReport = {
      id: 1,
      taskId: 'task_001',
      status: 'COMPLETED',
      totalCount: 25,
      passedCount: 20,
      failedCount: 2,
      manualReviewCount: 3,
      items: []
    }
    vi.mocked(complianceApi.getBidQualityReport).mockResolvedValueOnce({ data: mockReport })
    vi.mocked(scoreParseApi.getItems).mockResolvedValueOnce({ data: { items: [] } })
    vi.mocked(scoreParseApi.getResults).mockResolvedValueOnce({ data: { results: [] } })

    const drawer = useQualityCheckDrawer(props, emit)
    await drawer.open()

    expect(drawer.visible.value).toBe(true)
    expect(drawer.report.value).toEqual(mockReport)
    expect(drawer.polling.value).toBe(false)
  })

  it('打开抽屉发现进行中任务时进入轮询并在完成后加载报告', async () => {
    const runningTask = {
      id: 1,
      taskId: 'task_002',
      status: 'CHECKING',
      progress: 40,
      currentStep: '正在核查...'
    }
    const completedReport = {
      id: 1,
      taskId: 'task_002',
      status: 'COMPLETED',
      totalCount: 25,
      items: []
    }
    vi.mocked(complianceApi.getBidQualityReport)
      .mockResolvedValueOnce({ data: runningTask })
      .mockResolvedValueOnce({ data: completedReport })

    vi.mocked(complianceApi.getBidQualityProgress).mockResolvedValueOnce({
      data: { taskId: 'task_002', status: 'COMPLETED', progress: 100 }
    })

    const drawer = useQualityCheckDrawer(props, emit)
    await drawer.open()

    expect(drawer.polling.value).toBe(true)
    // 尚未完成时不直接把 runningTask 设为 report
    expect(drawer.report.value).toBeNull()

    // 快进 2 秒触发轮询
    await vi.advanceTimersByTimeAsync(2000)

    expect(drawer.polling.value).toBe(false)
    expect(drawer.progressPercent.value).toBe(100)
    expect(drawer.report.value).toEqual(completedReport)
    expect(ElMessage.success).toHaveBeenCalledWith('AI 标书质量核查已完成！')
  })

  it('重新核查时清空旧报告并进入轮询', async () => {
    const drawer = useQualityCheckDrawer(props, emit)
    drawer.report.value = { id: 99, status: 'COMPLETED' }

    vi.mocked(complianceApi.triggerBidQualityCheck).mockResolvedValueOnce({
      data: { taskId: 'task_new_001', status: 'INIT', progress: 0 }
    })

    await drawer.triggerCheck(true)

    expect(drawer.report.value).toBeNull()
    expect(drawer.polling.value).toBe(true)
    expect(drawer.progressPercent.value).toBe(10)
  })

  it('轮询窗口到顶（30 分钟）后转入后台执行态，不误报失败', async () => {
    const runningTask = {
      id: 1,
      taskId: 'task_timeout_001',
      status: 'CHECKING',
      progress: 60
    }
    vi.mocked(complianceApi.getBidQualityReport).mockResolvedValueOnce({ data: runningTask })
    vi.mocked(complianceApi.getBidQualityProgress).mockResolvedValue({
      data: { taskId: 'task_timeout_001', status: 'CHECKING', progress: 60 }
    })

    const drawer = useQualityCheckDrawer(props, emit)
    await drawer.open()

    expect(drawer.polling.value).toBe(true)

    // 快进 10 分钟：旧逻辑在此误报超时，新逻辑保持轮询（大文件核查需要更久）
    await vi.advanceTimersByTimeAsync(600000)
    expect(drawer.polling.value).toBe(true)

    // 快进越过 30 分钟窗口（多推一个 2s 轮询周期）：转入后台执行态，不设置 errorMessage、不弹错误
    await vi.advanceTimersByTimeAsync(1202000)
    expect(drawer.polling.value).toBe(false)
    expect(drawer.backgroundRunning.value).toBe(true)
    expect(drawer.errorMessage.value).toBe('')
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('后台执行态刷新后恢复轮询（任务仍在运行）', async () => {
    const drawer = useQualityCheckDrawer(props, emit)
    drawer.backgroundRunning.value = true

    vi.mocked(complianceApi.getBidQualityReport).mockResolvedValueOnce({
      data: { id: 1, taskId: 'task_bg', status: 'CHECKING', progress: 70 }
    })

    await drawer.loadLatestReport()

    expect(drawer.backgroundRunning.value).toBe(false)
    expect(drawer.polling.value).toBe(true)
  })

  it('后台执行态重新触发核查时清除后台态', async () => {
    const drawer = useQualityCheckDrawer(props, emit)
    drawer.backgroundRunning.value = true

    vi.mocked(complianceApi.triggerBidQualityCheck).mockResolvedValueOnce({
      data: { taskId: 'task_new_bg', status: 'INIT', progress: 0 }
    })

    await drawer.triggerCheck(true)

    expect(drawer.backgroundRunning.value).toBe(false)
    expect(drawer.polling.value).toBe(true)
  })

  it('轮询中任务完成时清除后台态并加载报告', async () => {
    const completedReport = { id: 1, taskId: 'task_bg_done', status: 'COMPLETED', items: [] }
    vi.mocked(complianceApi.getBidQualityReport)
      .mockResolvedValueOnce({ data: { id: 1, taskId: 'task_bg_done', status: 'CHECKING', progress: 90 } })
      .mockResolvedValueOnce({ data: completedReport })
    vi.mocked(complianceApi.getBidQualityProgress).mockResolvedValueOnce({
      data: { taskId: 'task_bg_done', status: 'COMPLETED', progress: 100 }
    })

    const drawer = useQualityCheckDrawer(props, emit)
    await drawer.open()
    await vi.advanceTimersByTimeAsync(2000)

    expect(drawer.backgroundRunning.value).toBe(false)
    expect(drawer.report.value).toEqual(completedReport)
  })

  it('拉取到 FAILED 状态报告时记录 errorMessage 且 report 为 null', async () => {
    const failedReport = {
      id: 1,
      taskId: 'task_failed_001',
      status: 'FAILED',
      errorMessage: 'Data too long for column check_details'
    }
    vi.mocked(complianceApi.getBidQualityReport).mockResolvedValueOnce({ data: failedReport })

    const drawer = useQualityCheckDrawer(props, emit)
    await drawer.open()

    expect(drawer.report.value).toBeNull()
    expect(drawer.errorMessage.value).toBe('Data too long for column check_details')
    expect(drawer.polling.value).toBe(false)
  })

  it('扫描件失败时提供专用的前台提示语', async () => {
    vi.mocked(complianceApi.getBidQualityReport).mockResolvedValueOnce({
      data: {
        id: 1,
        taskId: 'task_scan_001',
        status: 'FAILED',
        errorMessage: '未能从投标文件提取文本：投标书.pdf。该文件为图片扫描件或无可复制文本（无文本层）',
      },
    })

    const drawer = useQualityCheckDrawer(props, emit)
    await drawer.open()

    expect(drawer.errorPresentation.value).toMatchObject({
      scanned: true,
      title: 'PDF 扫描件暂不支持识别',
    })
  })

  it('轮询过程中遇到 FAILED 状态时记录 errorMessage 并终止轮询', async () => {
    const runningTask = {
      id: 1,
      taskId: 'task_fail_poll',
      status: 'CHECKING',
      progress: 30
    }
    vi.mocked(complianceApi.getBidQualityReport).mockResolvedValueOnce({ data: runningTask })
    vi.mocked(complianceApi.getBidQualityProgress).mockResolvedValueOnce({
      data: { taskId: 'task_fail_poll', status: 'FAILED', errorMessage: '后台比对异常' }
    })

    const drawer = useQualityCheckDrawer(props, emit)
    await drawer.open()

    expect(drawer.polling.value).toBe(true)

    await vi.advanceTimersByTimeAsync(2000)

    expect(drawer.polling.value).toBe(false)
    expect(drawer.errorMessage.value).toBe('后台比对异常')
    expect(ElMessage.error).toHaveBeenCalledWith('后台比对异常')
  })

  it('后台执行态刷新失败时保持后台态（不闪回空白态）', async () => {
    const drawer = useQualityCheckDrawer(props, emit)
    drawer.backgroundRunning.value = true

    vi.mocked(complianceApi.getBidQualityReport).mockRejectedValueOnce(new Error('network down'))

    await drawer.loadLatestReport()

    expect(drawer.backgroundRunning.value).toBe(true)
    expect(drawer.report.value).toBeNull()
  })
})
