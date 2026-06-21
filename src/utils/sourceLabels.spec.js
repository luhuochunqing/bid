import { describe, expect, it } from 'vitest'
import {
  CRM_SOURCE_LABEL,
  EXTERNAL_PLATFORM_SOURCE_LABEL,
  LEGACY_CRM_SOURCE_LABEL,
  MANUAL_SOURCE_LABEL,
  SOURCE_PLATFORM_OPTIONS,
} from './sourceLabels.js'

describe('sourceLabels', () => {
  it('keeps tender source platform labels aligned with backend source values', () => {
    expect(MANUAL_SOURCE_LABEL).toBe('人工录入')
    expect(CRM_SOURCE_LABEL).toBe('CRM创建')
    expect(LEGACY_CRM_SOURCE_LABEL).toBe('CRM 创建')
    expect(EXTERNAL_PLATFORM_SOURCE_LABEL).toBe('第三方平台')
    expect(SOURCE_PLATFORM_OPTIONS).toEqual(['人工录入', 'CRM创建', '第三方平台'])
  })
})
