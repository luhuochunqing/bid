import { describe, it, expect } from 'vitest'
import { filterAttachmentsByType } from './warehouseAttachmentFilter.js'

const attachments = [
  { id: 1, type: 'PROPERTY_CERTIFICATE', originalFilename: 'cert.pdf', fileSize: 1024 },
  { id: 2, type: 'INVOICE', originalFilename: 'invoice.pdf', fileSize: 2048 },
  { id: 3, type: 'PHOTOS', originalFilename: 'photo.jpg', fileSize: 5120 },
  { id: 4, type: 'LEASE_CONTRACT', originalFilename: 'lease.pdf', fileSize: 4096 },
  { id: 5, type: 'PROPERTY_CERTIFICATE', originalFilename: 'cert2.pdf', fileSize: 512 }
]

describe('filterAttachmentsByType', () => {
  it('typeFilter 为 "ALL" 时返回全部附件', () => {
    expect(filterAttachmentsByType(attachments, 'ALL')).toHaveLength(5)
  })

  it('typeFilter 为空字符串时返回全部附件', () => {
    expect(filterAttachmentsByType(attachments, '')).toHaveLength(5)
  })

  it('typeFilter 为 null/undefined 时返回全部附件', () => {
    expect(filterAttachmentsByType(attachments, null)).toHaveLength(5)
    expect(filterAttachmentsByType(attachments, undefined)).toHaveLength(5)
  })

  it('typeFilter 为 "PROPERTY_CERTIFICATE" 时只返回产权证类型', () => {
    const result = filterAttachmentsByType(attachments, 'PROPERTY_CERTIFICATE')
    expect(result).toHaveLength(2)
    expect(result.every((a) => a.type === 'PROPERTY_CERTIFICATE')).toBe(true)
  })

  it('typeFilter 为 "INVOICE" 时只返回发票类型', () => {
    expect(filterAttachmentsByType(attachments, 'INVOICE')).toHaveLength(1)
    expect(filterAttachmentsByType(attachments, 'INVOICE')[0].originalFilename).toBe('invoice.pdf')
  })

  it('typeFilter 为不存在的类型时返回空数组', () => {
    expect(filterAttachmentsByType(attachments, 'NOT_EXIST')).toHaveLength(0)
  })

  it('不修改入参数组（纯函数）', () => {
    const original = [...attachments]
    filterAttachmentsByType(attachments, 'PROPERTY_CERTIFICATE')
    expect(attachments).toEqual(original)
    expect(attachments).toHaveLength(5)
  })

  it('attachments 为 null/undefined/非数组时返回空数组', () => {
    expect(filterAttachmentsByType(null, 'ALL')).toEqual([])
    expect(filterAttachmentsByType(undefined, 'ALL')).toEqual([])
    expect(filterAttachmentsByType('not array', 'ALL')).toEqual([])
  })

  it('附件元素为 null/undefined 时不抛错（filter 跳过）', () => {
    const list = [null, undefined, { type: 'PHOTOS' }, { type: null }]
    expect(filterAttachmentsByType(list, 'PHOTOS')).toHaveLength(1)
  })

  it('空附件数组 + 任意筛选返回空数组', () => {
    expect(filterAttachmentsByType([], 'ALL')).toEqual([])
    expect(filterAttachmentsByType([], 'PROPERTY_CERTIFICATE')).toEqual([])
  })
})
