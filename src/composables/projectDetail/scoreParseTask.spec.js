import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { STATUS_MAP, TYPE_MAP, formatTime, pollTask } from './scoreParseTask.js'

describe('scoreParseTask — spec 041 状态映射与任务轮询纯函数', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('STATUS_MAP / TYPE_MAP', () => {
    it('后端状态枚举映射到抽屉 UI 语义', () => {
      expect(STATUS_MAP).toEqual({ OK: 'ok', DANGER: 'danger', PENDING: 'neutral' })
    })

    it('评分类型枚举映射到中文标签', () => {
      expect(TYPE_MAP).toEqual({ OBJECTIVE: '客观项', SUBJECTIVE: '主观项' })
    })
  })

  describe('formatTime', () => {
    it('空值返回 null', () => {
      expect(formatTime(null)).toBeNull()
      expect(formatTime(undefined)).toBeNull()
      expect(formatTime('')).toBeNull()
    })

    it('无法解析的日期原样返回字符串', () => {
      expect(formatTime('not-a-date')).toBe('not-a-date')
    })

    it('有效时间戳返回本地化时间字符串', () => {
      const result = formatTime('2026-08-15T14:30:00')
      expect(result).toMatch(/2026/)
      expect(result).toMatch(/14:30:00/)
    })
  })

  describe('pollTask', () => {
    it('首次即 COMPLETED 直接返回 task，不等待', async () => {
      const fetcher = vi.fn().mockResolvedValue({
        data: { taskId: 't1', status: 'COMPLETED', completedAt: '2026-08-15T14:00:00' },
      })

      const task = await pollTask(fetcher)

      expect(task.status).toBe('COMPLETED')
      expect(fetcher).toHaveBeenCalledTimes(1)
    })

    it('PROCESSING → COMPLETED：跨轮询间隔后返回最终 task', async () => {
      const fetcher = vi
        .fn()
        .mockResolvedValueOnce({ data: { taskId: 't1', status: 'PENDING' } })
        .mockResolvedValueOnce({ data: { taskId: 't1', status: 'PROCESSING', progress: 50 } })
        .mockResolvedValueOnce({ data: { taskId: 't1', status: 'COMPLETED', progress: 100 } })

      const promise = pollTask(fetcher)
      // 推进两次轮询间隔（每次 2s）
      await vi.advanceTimersByTimeAsync(2000)
      await vi.advanceTimersByTimeAsync(2000)
      const task = await promise

      expect(task.status).toBe('COMPLETED')
      expect(fetcher).toHaveBeenCalledTimes(3)
    })

    it('FAILED：抛出后端 errorMessage', async () => {
      const fetcher = vi.fn().mockResolvedValue({
        data: { taskId: 't1', status: 'FAILED', errorMessage: 'LLM 调用超时' },
      })

      await expect(pollTask(fetcher)).rejects.toThrow('LLM 调用超时')
    })

    it('FAILED 且无 errorMessage：抛默认错误文案', async () => {
      const fetcher = vi.fn().mockResolvedValue({ data: { taskId: 't1', status: 'FAILED' } })

      await expect(pollTask(fetcher)).rejects.toThrow('任务执行失败')
    })

    it('达到最大轮询次数仍非终态：抛超时错误', async () => {
      const fetcher = vi.fn().mockResolvedValue({ data: { taskId: 't1', status: 'PROCESSING' } })

      const promise = pollTask(fetcher)
      promise.catch(() => {}) // 防止 unhandled rejection
      // 推进足够长时间（150 次 × 2s + 冗余）
      await vi.advanceTimersByTimeAsync(2000 * 151)

      await expect(promise).rejects.toThrow('任务轮询超时')
    })

    it('响应缺少 data 包裹时按空对象处理并进入轮询', async () => {
      const fetcher = vi
        .fn()
        .mockResolvedValueOnce(undefined)
        .mockResolvedValueOnce({ data: { taskId: 't1', status: 'COMPLETED' } })

      const promise = pollTask(fetcher)
      await vi.advanceTimersByTimeAsync(2000)
      const task = await promise

      expect(task.status).toBe('COMPLETED')
      expect(fetcher).toHaveBeenCalledTimes(2)
    })
  })
})
