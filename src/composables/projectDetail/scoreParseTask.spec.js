import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { STATUS_MAP, TYPE_MAP, formatTime, pollTask, normalizeScoreItem, normalizeScoreResult } from './scoreParseTask.js'

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

  describe('normalizeScoreItem — spec 044 FR-001/003 空值语义', () => {
    it('客观项 estScore 为空：保留 null（不得转 0），依据兜底为待人工确认', () => {
      const item = normalizeScoreItem({ code: 'D2', dim: '资质业绩', detail: 'CMMI 5 级', weight: 5, scoreType: 'OBJECTIVE', status: 'PENDING' }, 1)
      expect(item.estScore).toBeNull()
      expect(item.estBasis).toBe('待人工确认预计得分')
      expect(item.status).toBe('neutral')
    })

    it('客观项 estScore 为数字：原样数值化，依据兜底为知识库匹配完成', () => {
      const item = normalizeScoreItem({ code: 'D1', dim: '资质业绩', detail: 'ISO9001', weight: 6, scoreType: 'OBJECTIVE', status: 'OK', estScore: 6 }, 0)
      expect(item.estScore).toBe(6)
      expect(item.estBasis).toBe('知识库匹配完成')
    })

    it('客观项真实零分：保留数字 0（区别于空值待确认）', () => {
      const item = normalizeScoreItem({ code: 'D3', dim: '资质业绩', detail: '资质', weight: 5, scoreType: 'OBJECTIVE', status: 'DANGER', estScore: 0 }, 2)
      expect(item.estScore).toBe(0)
    })

    it('客观项空值但后端已给依据：以后端依据为准', () => {
      const item = normalizeScoreItem({ code: 'B1', dim: '商务', detail: '仓库', weight: 5, scoreType: 'OBJECTIVE', status: 'PENDING', estBasis: '未识别到知识库匹配类别，待人工确认预计得分' }, 3)
      expect(item.estScore).toBeNull()
      expect(item.estBasis).toBe('未识别到知识库匹配类别，待人工确认预计得分')
    })

    it('主观项：estScore 固定"待确认"，空值与数字得分均不计入', () => {
      const item = normalizeScoreItem({ code: 'A1', dim: '技术方案', detail: '架构', weight: 10, scoreType: 'SUBJECTIVE', status: 'PENDING', estScore: 8 }, 0)
      expect(item.estScore).toBe('待确认')
      expect(item.estBasis).toBe('主观项需专家评审')
    })
  })

  describe('normalizeScoreResult — spec 044 回归', () => {
    it('客观项部分得分：数值化且状态映射', () => {
      const r = normalizeScoreResult({ code: 'D2', scoreType: 'OBJECTIVE', status: 'PENDING', actualScore: 3, matchRatio: 60, evidence: '补充说明', quote: '第 7 章' })
      expect(r.actualScore).toBe(3)
      expect(r.status).toBe('neutral')
      expect(r.matchRatio).toBe(60)
    })

    it('阶段 2 空值得分（解析失败兜底）：null + 待确认文案', () => {
      const r = normalizeScoreResult({ code: 'B1', scoreType: 'OBJECTIVE', status: 'PENDING', actualScore: null, missedReason: '投标文件解析失败' })
      expect(r.actualScore).toBeNull()
      expect(r.evalText).toBe('待确认')
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
      // 推进足够长时间（900 次 × 2s + 冗余）
      await vi.advanceTimersByTimeAsync(2000 * 901)

      await expect(promise).rejects.toThrow('解析超时，请检查文件大小或稍后重试')
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
