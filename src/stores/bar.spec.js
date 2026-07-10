// Input: bar.js Pinia store, mocked resourcesApi
// Output: unit tests for useBarStore batch concurrency and state management
// Pos: src/stores/ - BAR store test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api', () => {
  const barSiteAccountsGetList = vi.fn()
  const certificatesGetList = vi.fn()
  const certificatesGetBorrowRecords = vi.fn()
  const barSitesGetList = vi.fn()
  const barSitesGetDetail = vi.fn()
  const barSitesGetVerificationRecords = vi.fn()
  const barSiteSopGet = vi.fn()
  const barSiteAttachmentsGetList = vi.fn()

  return {
    resourcesApi: {
      barSites: {
        getList: barSitesGetList,
        getDetail: barSitesGetDetail,
        getVerificationRecords: barSitesGetVerificationRecords,
      },
      barSiteAccounts: {
        getList: barSiteAccountsGetList,
      },
      certificates: {
        getList: certificatesGetList,
        getBorrowRecords: certificatesGetBorrowRecords,
      },
      barSiteSop: {
        get: barSiteSopGet,
      },
      barSiteAttachments: {
        getList: barSiteAttachmentsGetList,
      },
    },
  }
})

import { resourcesApi } from '@/api'
import { useBarStore } from './bar.js'

const {
  barSites: { getList: barSitesGetList, getDetail: barSitesGetDetail, getVerificationRecords: barSitesGetVerificationRecords },
  barSiteAccounts: { getList: barSiteAccountsGetList },
  certificates: { getList: certificatesGetList, getBorrowRecords: certificatesGetBorrowRecords },
  barSiteSop: { get: barSiteSopGet },
  barSiteAttachments: { getList: barSiteAttachmentsGetList },
} = resourcesApi

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('useBarStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('getSites 按批次并发拉取站点详情，避免一次性 burst', async () => {
    const store = useBarStore()
    const sites = [
      { id: 1, name: 'site-1', lastVerifyTime: '' },
      { id: 2, name: 'site-2', lastVerifyTime: '' },
      { id: 3, name: 'site-3', lastVerifyTime: '' },
      { id: 4, name: 'site-4', lastVerifyTime: '' },
      { id: 5, name: 'site-5', lastVerifyTime: '' },
    ]
    barSitesGetList.mockResolvedValue({ success: true, data: sites })

    let inFlight = 0
    let maxInFlight = 0

    const trackApi = (fn) => vi.fn(async (...args) => {
      inFlight += 1
      maxInFlight = Math.max(maxInFlight, inFlight)
      await flushPromises()
      inFlight -= 1
      return fn(...args)
    })

    barSiteAccountsGetList.mockImplementation(trackApi((siteId) => ({ success: true, data: [{ id: siteId, owner: `owner-${siteId}` }] })))
    certificatesGetList.mockImplementation(trackApi((siteId) => ({ success: true, data: [{ id: siteId * 10, type: 'UKEY', serialNo: `sn-${siteId}` }] })))
    barSitesGetVerificationRecords.mockImplementation(trackApi((siteId) => ({ success: true, data: [{ verifiedAt: `2026-07-0${siteId}`, verifiedBy: 'system' }] })))

    await store.getSites()

    expect(store.sites).toHaveLength(5)
    expect(maxInFlight).toBeLessThanOrEqual(6) // 每批 2 个站点，每个站点内部 3 个 GET
    expect(barSiteAccountsGetList).toHaveBeenCalledTimes(5)
    expect(certificatesGetList).toHaveBeenCalledTimes(5)
    expect(barSitesGetVerificationRecords).toHaveBeenCalledTimes(5)
  })

  it('getSiteById 按批次并发拉取证书借用记录', async () => {
    const store = useBarStore()
    const certificates = [
      { id: 11, type: 'UKEY', serialNo: 'sn-11' },
      { id: 12, type: 'UKEY', serialNo: 'sn-12' },
      { id: 13, type: 'UKEY', serialNo: 'sn-13' },
      { id: 14, type: 'UKEY', serialNo: 'sn-14' },
      { id: 15, type: 'UKEY', serialNo: 'sn-15' },
      { id: 16, type: 'UKEY', serialNo: 'sn-16' },
    ]
    barSitesGetDetail.mockResolvedValue({ success: true, data: { id: 1, name: 'site-1' } })
    barSiteAccountsGetList.mockResolvedValue({ success: true, data: [] })
    certificatesGetList.mockResolvedValue({ success: true, data: certificates })
    barSitesGetVerificationRecords.mockResolvedValue({ success: true, data: [] })
    barSiteSopGet.mockResolvedValue({ success: true, data: null })
    barSiteAttachmentsGetList.mockResolvedValue({ success: true, data: [] })

    let inFlight = 0
    let maxInFlight = 0

    certificatesGetBorrowRecords.mockImplementation(async (siteId, certificateId) => {
      inFlight += 1
      maxInFlight = Math.max(maxInFlight, inFlight)
      await flushPromises()
      inFlight -= 1
      return { success: true, data: [{ id: certificateId, borrower: `user-${certificateId}`, status: 'BORROWED' }] }
    })

    await store.getSiteById(1)

    expect(store.currentSite.uks).toHaveLength(6)
    expect(maxInFlight).toBeLessThanOrEqual(5)
    expect(certificatesGetBorrowRecords).toHaveBeenCalledTimes(6)
  })

  it('getSites 失败时返回原始响应', async () => {
    const store = useBarStore()
    barSitesGetList.mockResolvedValue({ success: false, msg: '服务端错误' })

    const result = await store.getSites()

    expect(result.success).toBe(false)
    expect(result.msg).toBe('服务端错误')
    expect(barSiteAccountsGetList).not.toHaveBeenCalled()
  })
})
