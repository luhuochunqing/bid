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

  it('有 deliverables 但无完成情况 → invalid, 完成情况消息', () => {
    const result = validateSubmitForReview({
      deliverables: [{ id: 1 }],
      completionNotes: ''
    })
    expect(result.valid).toBe(false)
    expect(result.message).toBe('提交审核时必须填写完成情况')
  })

  it('有 deliverableFiles 但无完成情况 → invalid', () => {
    const result = validateSubmitForReview({
      deliverableFiles: [new File([''], 'test.pdf')],
      completionNotes: ''
    })
    expect(result.valid).toBe(false)
    expect(result.message).toBe('提交审核时必须填写完成情况')
  })

  it('hasDeliverable = true 但无完成情况 → invalid', () => {
    const result = validateSubmitForReview({
      hasDeliverable: true,
      completionNotes: ''
    })
    expect(result.valid).toBe(false)
    expect(result.message).toBe('提交审核时必须填写完成情况')
  })

  it('completionNotes 为空白字符串 → invalid', () => {
    const result = validateSubmitForReview({
      deliverables: [{ id: 1 }],
      completionNotes: '   '
    })
    expect(result.valid).toBe(false)
    expect(result.message).toBe('提交审核时必须填写完成情况')
  })

  it('completionNotes 为 null → invalid', () => {
    const result = validateSubmitForReview({
      deliverables: [{ id: 1 }],
      completionNotes: null
    })
    expect(result.valid).toBe(false)
    expect(result.message).toBe('提交审核时必须填写完成情况')
  })
})
