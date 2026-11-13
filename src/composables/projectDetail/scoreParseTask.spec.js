import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { STATUS_MAP, TYPE_MAP, formatTime, pollTask, normalizeScoreItem, normalizeScoreResult, mapScoreResults, lookupScoreResult, sanitizeCode, getDecisionBadgeType, getDecisionBadgeText, shouldHideFailureBadge, categoryLabel } from './scoreParseTask.js'

describe('scoreParseTask — spec 041 状态映射与任务轮询纯函数', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('sanitizeCode — 编号清洗与防线', () => {
    it('清洗复合 null/undefined 脏编号', () => {
      expect(sanitizeCode('null.1')).toBe('1')
      expect(sanitizeCode('null-2')).toBe('2')
      expect(sanitizeCode('null_3')).toBe('3')
      expect(sanitizeCode('null/4')).toBe('4')
      expect(sanitizeCode('1.null')).toBe('1')
      expect(sanitizeCode('1.null.2')).toBe('1.2')
      expect(sanitizeCode('undefined.3')).toBe('3')
    })

    it('清洗边缘字符：全角 ｎｕｌｌ、零宽字符、(null)', () => {
      expect(sanitizeCode('ｎｕｌｌ.1')).toBe('1')
      expect(sanitizeCode('(null)1')).toBe('1')
      expect(sanitizeCode('（null）2')).toBe('2')
      expect(sanitizeCode('null.\u200B1')).toBe('1')
      expect(sanitizeCode('null.(null)')).toBe('')
    })

    it('NFKC 与后端对齐：全角分隔符、表意空格归一', () => {
      expect(sanitizeCode('ｎｕｌｌ／1')).toBe('1')
      expect(sanitizeCode('　1　')).toBe('1')
      expect(sanitizeCode('null\\1')).toBe('1')
    })

    it('NFKC 真增量：圈号/上标归一（旧手写映射下恒为原样）', () => {
      expect(sanitizeCode('①')).toBe('1')
      expect(sanitizeCode('²')).toBe('2')
    })

    it('括号空格容忍：( null ) 与后端对齐', () => {
      expect(sanitizeCode('( null )1')).toBe('1')
    })

    it('纯占位符统一置空', () => {
      expect(sanitizeCode(null)).toBe('')
      expect(sanitizeCode(undefined)).toBe('')
      expect(sanitizeCode('null')).toBe('')
      expect(sanitizeCode('NULL')).toBe('')
      expect(sanitizeCode('(null)')).toBe('')
      expect(sanitizeCode('null.0')).toBe('')
      expect(sanitizeCode('n/a')).toBe('')
      expect(sanitizeCode('none')).toBe('')
    })

    it('严格保留合法的中文数字章节与普通标号', () => {
      expect(sanitizeCode('一、')).toBe('一、')
      expect(sanitizeCode('四、综合评价')).toBe('四、综合评价')
      expect(sanitizeCode('A1')).toBe('A1')
      expect(sanitizeCode('1.2.3')).toBe('1.2.3')
    })
  })

  describe('STATUS_MAP / TYPE_MAP', () => {
    it('后端状态枚举映射到抽屉 UI 语义', () => {
      expect(STATUS_MAP).toEqual({ OK: 'ok', DANGER: 'danger', PENDING: 'neutral' })
    })

    it('评分类型枚举映射到中文标签', () => {
      expect(TYPE_MAP).toEqual({ OBJECTIVE: '客观项', SUBJECTIVE: '主观项' })
    })

    it('评分类别统一显示中文', () => {
      expect(categoryLabel('business')).toBe('商务')
      expect(categoryLabel('technology')).toBe('技术')
      expect(categoryLabel('other')).toBe('其他')
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

  describe('normalizeScoreItem — Phase 5 证据字段透传', () => {
    it('sourceCellIds 为 JSON 数组字符串时正确解析为数组', () => {
      const item = normalizeScoreItem({ code: 'D1', dim: '资质', detail: '证书', weight: 5, scoreType: 'OBJECTIVE', status: 'OK', sourceCellIds: '["R2C1","R2C2"]' }, 0)
      expect(item.sourceCellIds).toEqual(['R2C1', 'R2C2'])
    })

    it('sourceCellIds 已为数组时原样透传', () => {
      const item = normalizeScoreItem({ code: 'D1', dim: '资质', detail: '证书', weight: 5, scoreType: 'OBJECTIVE', status: 'OK', sourceCellIds: ['R2C1', 'R2C2'] }, 0)
      expect(item.sourceCellIds).toEqual(['R2C1', 'R2C2'])
    })

    it('sourceCellIds 为 null 时返回空数组', () => {
      const item = normalizeScoreItem({ code: 'D1', dim: '资质', detail: '证书', weight: 5, scoreType: 'OBJECTIVE', status: 'OK' }, 0)
      expect(item.sourceCellIds).toEqual([])
    })

    it('extractionMethod / confidence / validationStatus 正常透传', () => {
      const item = normalizeScoreItem({
        code: 'D1', dim: '资质', detail: '证书', weight: 5, scoreType: 'OBJECTIVE', status: 'OK',
        extractionMethod: 'TABLE_CELL', confidence: 0.95, validationStatus: 'AUTO_APPROVED',
      }, 0)
      expect(item.extractionMethod).toBe('TABLE_CELL')
      expect(item.confidence).toBe(0.95)
      expect(item.validationStatus).toBe('AUTO_APPROVED')
    })

    it('weightEvidence 为对象时原样透传', () => {
      const evidence = { type: 'EXPLICIT', rawText: '5分' }
      const item = normalizeScoreItem({ code: 'D1', dim: '资质', detail: '证书', weight: 5, scoreType: 'OBJECTIVE', status: 'OK', weightEvidence: evidence }, 0)
      expect(item.weightEvidence).toEqual(evidence)
    })

    it('缺失证据字段时提供合理兜底值', () => {
      const item = normalizeScoreItem({ code: 'D1', dim: '资质', detail: '证书', weight: 5, scoreType: 'OBJECTIVE', status: 'OK' }, 0)
      expect(item.sourceCellIds).toEqual([])
      expect(item.weightEvidence).toBeNull()
      expect(item.extractionMethod).toBe('')
      expect(item.confidence).toBeNull()
      expect(item.validationStatus).toBe('')
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

  describe('mapScoreResults / lookupScoreResult', () => {
    it('mapScoreResults 按 code 建表', () => {
      const map = mapScoreResults([
        { code: 'D1', scoreType: 'OBJECTIVE', actualScore: 6, status: 'OK' },
      ])
      expect(lookupScoreResult(map, { code: 'D1' }).actualScore).toBe(6)
    })

    it('支持 raw+sanitized 双键索引：脏 null.1 建键时干净 1 能命中，且 raw null.1 查键不 miss', () => {
      const map = mapScoreResults([
        { code: 'null.1', scoreType: 'OBJECTIVE', actualScore: 8, status: 'OK' },
      ])
      // 干净 code 查键命中
      expect(lookupScoreResult(map, { code: '1' })?.actualScore).toBe(8)
      // 原始 code 查键命中
      expect(lookupScoreResult(map, { code: 'null.1' })?.actualScore).toBe(8)
    })

    it('有 id 时不得回退 code：重复编号互不串行', () => {
      const map = mapScoreResults([
        { scoreItemId: 101, code: '1', actualScore: null, status: 'PENDING' },
        { scoreItemId: 102, code: '1', actualScore: 10, status: 'OK' },
      ])
      expect(lookupScoreResult(map, { id: 102, code: '1' }).actualScore).toBe(10)
      expect(lookupScoreResult(map, { id: 101, code: '1' }).status).toBe('neutral')
      expect(lookupScoreResult(map, { id: 999, code: '1' })).toBeNull()
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

  describe('getDecisionBadgeType & getDecisionBadgeText', () => {
    it('reviewSubmitted 为 true 时恒为 success 和 已完成复核', () => {
      expect(getDecisionBadgeType('REVIEW_REQUIRED', true)).toBe('success')
      expect(getDecisionBadgeText('REVIEW_REQUIRED', true)).toBe('已完成复核')
    })

    it('未复核时按决策状态映射', () => {
      expect(getDecisionBadgeType('AUTO_APPROVED', false)).toBe('success')
      expect(getDecisionBadgeText('AUTO_APPROVED', false)).toBe('自动通过')
      expect(getDecisionBadgeType('MANUAL_APPROVED', false)).toBe('success')
      expect(getDecisionBadgeText('MANUAL_APPROVED', false)).toBe('人工采纳通过')
      expect(getDecisionBadgeType('REVIEW_REQUIRED', false)).toBe('warning')
      expect(getDecisionBadgeText('REVIEW_REQUIRED', false)).toBe('待人工复核')
      expect(getDecisionBadgeType('STRUCTURE_INCOMPLETE', false)).toBe('danger')
      expect(getDecisionBadgeText('STRUCTURE_INCOMPLETE', false)).toBe('结构异常')
      expect(getDecisionBadgeType('PARSE_FAILED', false)).toBe('info')
      expect(getDecisionBadgeText('PARSE_FAILED', false)).toBe('解析失败')
      expect(getDecisionBadgeType('UNKNOWN', false)).toBe('')
      expect(getDecisionBadgeText('UNKNOWN', false)).toBe('')
    })
  })

  describe('shouldHideFailureBadge — 本次解析失败但仍有历史评分项生效时不展示失败徽章', () => {
    it('当次失败且已有生效评分项时隐藏（580：结构异常 + 9 条历史项）', () => {
      expect(shouldHideFailureBadge('STRUCTURE_INCOMPLETE', 9)).toBe(true)
      expect(shouldHideFailureBadge('PARSE_FAILED', 9)).toBe(true)
    })

    it('没有生效评分项时保留失败提示，避免掩盖真实失败', () => {
      expect(shouldHideFailureBadge('STRUCTURE_INCOMPLETE', 0)).toBe(false)
      expect(shouldHideFailureBadge('PARSE_FAILED', 0)).toBe(false)
    })

    it('非失败状态不受影响', () => {
      expect(shouldHideFailureBadge('REVIEW_REQUIRED', 9)).toBe(false)
      expect(shouldHideFailureBadge('AUTO_APPROVED', 9)).toBe(false)
      expect(shouldHideFailureBadge('', 9)).toBe(false)
    })
  })
})

it('preserves unknown active weight instead of inventing zero', () => {
  expect(normalizeScoreItem({ id: 9, weight: null, scoreType: 'OBJECTIVE' }).weight).toBeNull()
})

it('binds polling to the requested task and explicitly observes a replacement', async () => {
  const changed = vi.fn()
  const fetcher = vi.fn().mockResolvedValue({ data: { taskId: 'new', status: 'COMPLETED' } })
  await expect(pollTask(fetcher, '打分', { taskId: 'old', onTaskChanged: changed })).resolves.toMatchObject({ taskId: 'new', observedReplacement: true })
  expect(changed).toHaveBeenCalledWith('new', 'old')
})

it('cancels local polling immediately without waiting or requesting another status', async () => {
  vi.useFakeTimers()
  const controller = new AbortController()
  const fetcher = vi.fn().mockResolvedValue({ data: { taskId: 't', status: 'PROCESSING' } })
  const promise = pollTask(fetcher, '打分', { taskId: 't', signal: controller.signal })
  const stopped = expect(promise).rejects.toMatchObject({ name: 'AbortError' })
  await Promise.resolve()
  controller.abort()
  await stopped
  await vi.advanceTimersByTimeAsync(4000)
  expect(fetcher).toHaveBeenCalledTimes(1)
  vi.useRealTimers()
})
