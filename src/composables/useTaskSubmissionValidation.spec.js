import { describe, it, expect } from 'vitest'
import { validateSubmitForReview } from './useTaskSubmissionValidation.js'

describe('validateSubmitForReview', () => {
  it('有交付物 + 有完成情况 → valid', () => {
    const result = validateSubmitForReview({
      deliverables: [{ id: 1, name: 'file.pdf' }],
      completionNotes: '已完成'
    })
    expect(result.valid).toBe(true)
    expect(result.message).toBe('')
  })

  it('有交付物 + 无完成情况 → valid', () => {
    const result = validateSubmitForReview({
      deliverables: [{ id: 1, name: 'file.pdf' }],
      completionNotes: ''
    })
    expect(result.valid).toBe(true)
    expect(result.message).toBe('')
  })

  it('无交付物（所有字段都空/false）→ invalid, 交付物消息', () => {
    const result = validateSubmitForReview({
      deliverables: [],
      deliverableFiles: [],
      hasDeliverable: false,
      completionNotes: '已完成'
    })
    expect(result.valid).toBe(false)
    expect(result.message).toBe('提交审核时必须上传交付物')
  })
})
