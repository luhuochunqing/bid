import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'
import { sourceText } from './projectListFormatters.js'

describe('sourceText (CO-286: 与标讯中心来源平台列显示一致)', () => {
  it.each([
    // 新写入的 project.sourceModule 是 Tender.SourceType 中文 label —— 透传/归一显示
    ['人工录入', '人工录入'],
    ['CRM创建', 'CRM创建'],
    ['第三方平台', '第三方平台'],
  ])('passes through Tender.SourceType 中文 label "%s" as-is', (input, expected) => {
    expect(sourceText(input)).toBe(expected)
  })

  it.each([
    // 历史数据兼容：旧版 ProjectTenderPopulator 写入的是英文枚举名，应显示为对应中文 label
    ['EXTERNAL_PLATFORM', '第三方平台'],
    ['CRM_OPPORTUNITY', 'CRM创建'],
    ['MANUAL_SINGLE', '人工录入'],
    ['BULK_IMPORT', '人工录入'],
  ])('maps historical enum name "%s" to localized label "%s"', (input, expected) => {
    expect(sourceText(input)).toBe(expected)
  })

  it('normalizes legacy spaced CRM source label for project list display', () => {
    expect(sourceText('CRM 创建')).toBe('CRM创建')
  })

  it('falls back to raw string for unknown values (e.g. 真实平台名"建工招采")', () => {
    expect(sourceText('建工招采')).toBe('建工招采')
  })

  it('returns "-" for null', () => {
    expect(sourceText(null)).toBe('-')
  })

  it('returns "-" for undefined', () => {
    expect(sourceText(undefined)).toBe('-')
  })

  it('returns "-" for empty string', () => {
    expect(sourceText('')).toBe('-')
  })

  it('does NOT contain the Cyrillic homoglyph bug (M U+041C) that would silently drop CRM_OPPORTUNITY mapping', () => {
    // regression guard：源代码中不能出现西里尔 М (U+041C)，否则 EXTERNAL_PLATFORM/CRM_OPPORTUNITY 的映射键会变成不可达 dead code
    const here = dirname(fileURLToPath(import.meta.url))
    const source = readFileSync(resolve(here, 'projectListFormatters.js'), 'utf8')
    expect(source).not.toMatch(/[Ѐ-ӿ]/) // 任何西里尔字母
  })
})
