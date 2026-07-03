import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'
import { sourceText, customerTypeLabel } from './projectListFormatters.js'

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

describe('customerTypeLabel (PR !1571 回归修复：后端归一化为枚举名后展示位需翻译回中文)', () => {
  it.each([
    ['GOVERNMENT', '政府机关/事业单位/高校'],
    ['CENTRAL_SOE', '央企'],
    ['LOCAL_SOE', '地方国企'],
    ['PRIVATE', '民企'],
    ['FOREIGN', '港澳台及外企'],
    ['OTHER', '其他'],
  ])('maps CustomerType enum "%s" to localized label "%s"', (input, expected) => {
    expect(customerTypeLabel(input)).toBe(expected)
  })

  it('falls back to raw value for unknown enum (避免丢失数据)', () => {
    expect(customerTypeLabel('UNKNOWN_TYPE')).toBe('UNKNOWN_TYPE')
  })

  it('falls back to historical Chinese raw value (历史数据兼容)', () => {
    // PR !1571 之前数据库可能存中文，归一化未覆盖时仍能正常显示
    expect(customerTypeLabel('央企')).toBe('央企')
  })

  it('returns "-" for null', () => {
    expect(customerTypeLabel(null)).toBe('-')
  })

  it('returns "-" for undefined', () => {
    expect(customerTypeLabel(undefined)).toBe('-')
  })

  it('returns "-" for empty string', () => {
    expect(customerTypeLabel('')).toBe('-')
  })
})
