import { describe, expect, it } from 'vitest'
import {
  applyTenderDetailPrefill,
  buildProjectPrefillFromTender,
  hasGlobalHttpErrorMessage,
  normalizeTenderBudgetWan,
  normalizeTenderDeadlineDate
} from './createTenderPrefill.js'

describe('createTenderPrefill', () => {
  it('maps tender detail fields into project create prefill values', () => {
    const prefill = buildProjectPrefillFromTender({
      title: '兵工集团电子商城供应商引入项目',
      purchaserName: '南方工业科技贸易有限公司',
      budget: '400000',
      industry: '电商',
      region: '北京',
      source: '中国政府采购网',
      deadline: '2024-10-14T09:57:37',
      description: '供应商引入框架协议',
      tags: '框架协议, 自营商品'
    })

    expect(prefill).toMatchObject({
      name: '兵工集团电子商城供应商引入项目',
      customer: '南方工业科技贸易有限公司',
      budget: 40,
      industry: '电商',
      region: '北京',
      platform: '中国政府采购网',
      deadline: '2024-10-14',
      description: '供应商引入框架协议',
      tags: ['框架协议', '自营商品']
    })
  })

  it('leaves framework agreement budget empty when tender budget is missing', () => {
    expect(normalizeTenderBudgetWan(null)).toBeNull()
    expect(normalizeTenderBudgetWan('')).toBeNull()
  })

  it('normalizes date-like tender deadlines without requiring a future date', () => {
    expect(normalizeTenderDeadlineDate('2024-10-14T09:57:37')).toBe('2024-10-14')
  })

  it('fills only blank project create fields from tender detail', () => {
    const basicForm = {
      name: '用户已改项目名',
      customer: '',
      budget: null,
      industry: '',
      region: '',
      platform: '',
      deadline: ''
    }
    const detailForm = { description: '', tags: [] }

    applyTenderDetailPrefill({ basicForm, detailForm }, {
      name: '标讯标题',
      customer: '采购单位',
      budget: 40,
      industry: '电商',
      region: '北京',
      platform: '中国政府采购网',
      deadline: '2024-10-14',
      description: '项目描述',
      tags: ['框架协议']
    })

    expect(basicForm.name).toBe('用户已改项目名')
    expect(basicForm.deadline).toBe('2024-10-14')
    expect(detailForm.tags).toEqual(['框架协议'])
  })

  it('recognizes HTTP errors that already have a global message', () => {
    expect(hasGlobalHttpErrorMessage({ response: { status: 403 } })).toBe(true)
    expect(hasGlobalHttpErrorMessage(new Error('local validation'))).toBe(false)
  })
})
