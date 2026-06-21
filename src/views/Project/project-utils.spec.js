import { describe, it, expect } from 'vitest'
import { taskBackendToCard, taskFormDtoToBackend } from './project-utils.js'

describe('taskBackendToCard', () => {
  it('maps attachments from backend DTO', () => {
    const dto = {
      id: 1,
      title: '任务1',
      attachments: [
        { id: 101, name: '附件1.pdf', fileType: 'application/pdf' },
        { id: 102, name: '附件2.docx', fileType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
      ],
    }
    const card = taskBackendToCard(dto)
    expect(card.attachments).toHaveLength(2)
    expect(card.attachments[0].id).toBe(101)
    expect(card.attachments[1].name).toBe('附件2.docx')
  })

  it('returns empty attachments array when backend has none', () => {
    const dto = { id: 1, title: '任务1' }
    const card = taskBackendToCard(dto)
    expect(card.attachments).toEqual([])
  })

  it('returns empty attachments array when attachments is null', () => {
    const dto = { id: 1, title: '任务1', attachments: null }
    const card = taskBackendToCard(dto)
    expect(card.attachments).toEqual([])
  })

  it('preserves deliverables alongside attachments', () => {
    const dto = {
      id: 1,
      title: '任务1',
      attachments: [{ id: 101, name: '附件.pdf' }],
      deliverables: [{ id: 201, name: '交付物.pdf' }],
    }
    const card = taskBackendToCard(dto)
    expect(card.attachments).toHaveLength(1)
    expect(card.deliverables).toHaveLength(1)
    expect(card.hasDeliverable).toBe(true)
  })
})

describe('taskFormDtoToBackend', () => {
  it('maps attachments field to dto', () => {
    const form = {
      name: '任务1',
      attachments: [{ id: 101, name: '附件.pdf' }],
    }
    const dto = taskFormDtoToBackend(form)
    expect(dto.attachments).toEqual([{ id: 101, name: '附件.pdf' }])
  })

  it('does not include attachments when undefined', () => {
    const form = { name: '任务1' }
    const dto = taskFormDtoToBackend(form)
    expect(dto.attachments).toBeUndefined()
  })
})
