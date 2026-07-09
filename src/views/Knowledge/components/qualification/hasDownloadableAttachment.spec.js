import { describe, it, expect } from 'vitest'
import { hasDownloadableAttachment } from './hasDownloadableAttachment.js'

describe('hasDownloadableAttachment - CO-554 v3 判定只认 attachments', () => {
  it('附件表有有效附件应返回 true（多附件）', () => {
    const row = { attachments: [{ fileUrl: 'a.pdf' }, { fileUrl: 'b.pdf' }] }
    expect(hasDownloadableAttachment(row)).toBe(true)
  })

  it('附件表有一个有效附件应返回 true', () => {
    const row = { attachments: [{ fileUrl: 'only.pdf' }] }
    expect(hasDownloadableAttachment(row)).toBe(true)
  })

  it('附件表为空数组应返回 false', () => {
    const row = { attachments: [] }
    expect(hasDownloadableAttachment(row)).toBe(false)
  })

  it('附件字段缺失（undefined）应返回 false', () => {
    expect(hasDownloadableAttachment({})).toBe(false)
    expect(hasDownloadableAttachment({ attachments: undefined })).toBe(false)
  })

  it('附件 fileUrl 全为空字符串/空白应返回 false', () => {
    const row = { attachments: [{ fileUrl: '' }, { fileUrl: '   ' }] }
    expect(hasDownloadableAttachment(row)).toBe(false)
  })

  it('CO-554 v3 关键回归：主表 fileUrl 有脏数据但附件表为空，应返回 false', () => {
    // 这是用户报的 bug 场景：无附件却显示下载按钮 → 点下载得到 txt
    const row = { fileUrl: 'stale-orphan-url.pdf', attachments: [] }
    expect(hasDownloadableAttachment(row)).toBe(false)
  })

  it('CO-554 v3 关键回归：主表 fileUrl 有脏数据且附件缺失，应返回 false', () => {
    const row = { fileUrl: 'legacy.pdf' }
    expect(hasDownloadableAttachment(row)).toBe(false)
  })
})
