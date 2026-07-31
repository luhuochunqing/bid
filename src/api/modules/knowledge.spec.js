// Input: knowledge API module with mocked HTTP client
// Output: case list parameters, pagination shaping, and normalization coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '@/api/client'
import { casesApi } from './knowledge.js'

describe('casesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getList(): sends case filters as query params and returns normalized list (服务端分页)', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: [
        {
          id: 1,
          title: '智慧城市一体化平台',
          industry: 'INFRASTRUCTURE',
          amount: 3850,
          projectDate: '2024-06-01',
          customerName: '杭州市人民政府',
          locationName: '浙江杭州',
          projectPeriod: '2024.06 - 2024.12',
          tags: ['智慧城市', '大数据'],
          highlights: ['整合12个委办局数据'],
          viewCount: 12,
          useCount: 3
        },
        {
          id: 2,
          title: '省级银行核心业务系统升级改造',
          industry: 'OTHER',
          amount: 5600,
          projectDate: '2023-01-01',
          customerName: '浙江省农村信用社联合社',
          locationName: '浙江杭州',
          projectPeriod: '2023.01 - 2024.06',
          tags: ['金融'],
          highlights: ['实现系统双活架构'],
          viewCount: 10,
          useCount: 2
        }
      ]
    })

    const result = await casesApi.getList({
      keyword: '智慧',
      industry: 'government',
      page: 1,
      pageSize: 1
    })

    expect(httpClient.get).toHaveBeenCalledWith('/api/knowledge/cases', {
      params: {
        keyword: '智慧',
        industry: 'government',
        productLine: undefined,
        outcome: undefined,
        year: undefined,
        amountMin: undefined,
        amountMax: undefined,
        tags: undefined,
        page: 1,
        pageSize: 1,
        sort: undefined
      }
    })
    // 服务端分页：不再做本地二次过滤/分页，后端返回什么前端就展示什么
    expect(result.success).toBe(true)
    expect(result.data).toHaveLength(2)
    expect(result.data[0]).toMatchObject({
      id: 1,
      title: '智慧城市一体化平台',
      customer: '杭州市人民政府',
      industry: '基础设施', // 后端 enum INFRASTRUCTURE → 前端展示名
      amount: 3850,
      year: 2024,
      location: '浙江杭州',
      period: '2024.06 - 2024.12',
      tags: ['智慧城市', '大数据'],
      highlights: ['整合12个委办局数据'],
      viewCount: 12,
      useCount: 3
    })
  })

  it('getList(): 支持后端 {items, total} 分页响应格式', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: {
        items: [{ id: 10, title: '测试案例', industry: 'OTHER', customerName: '测试客户' }],
        total: 100,
        page: 1,
        pageSize: 1
      }
    })

    const result = await casesApi.getList({ page: 1, pageSize: 1 })

    expect(result.total).toBe(100)
    expect(result.data).toHaveLength(1)
    expect(result.data[0]).toMatchObject({ id: 10, title: '测试案例' })
  })

  it('getDetail(): returns the backend case payload directly', async () => {
    const backendData = {
      id: 7,
      title: '电力调度自动化系统',
      industry: 'ENERGY',
      amount: 4200,
      projectDate: '2023-03-01',
      description: '建设新一代电网调度自动化系统',
      customerName: '国网浙江省电力有限公司',
      locationName: '浙江杭州',
      projectPeriod: '2023.03 - 2023.12',
      tags: ['能源', '实时监控'],
      highlights: ['实现电网运行实时监控与智能预警'],
      technologies: ['C++', 'Qt'],
      viewCount: 8,
      useCount: 4
    }
    httpClient.get.mockResolvedValue({ data: backendData })

    const result = await casesApi.getDetail(7)

    expect(httpClient.get).toHaveBeenCalledWith('/api/cases/7')
    expect(result).toEqual(backendData)
  })

  // 回归测试：PR !2236 曾误删此方法导致 useDocumentKnowledge.js:140 运行时崩溃
  it('createReferenceRecord(): 调用 POST /api/knowledge/cases/{id}/references', async () => {
    httpClient.post.mockResolvedValue({ success: true, data: { id: 99 } })

    const result = await casesApi.createReferenceRecord(42, {
      referencedBy: 1,
      referencedByName: '张三',
      referenceTarget: 'doc-001',
      referenceContext: '技术方案章节'
    })

    expect(httpClient.post).toHaveBeenCalledWith('/api/knowledge/cases/42/references', {
      referencedBy: 1,
      referencedByName: '张三',
      referenceTarget: 'doc-001',
      referenceContext: '技术方案章节'
    })
    expect(result.success).toBe(true)
  })

  it('createReferenceRecord(): 非数字 ID 返回 invalidIdMessage', async () => {
    const result = await casesApi.createReferenceRecord('abc', {})
    expect(result.success).toBe(false)
    expect(result.message).toMatch(/numeric.*ID/i)
    expect(httpClient.post).not.toHaveBeenCalled()
  })

  it('getGridList(): 调用 /api/cases 并把 projectTypes/statuses 数组转为逗号分隔字符串', async () => {
    httpClient.get.mockResolvedValue({
      content: [{ id: 1, title: '案例A' }],
      totalElements: 1
    })

    const result = await casesApi.getGridList({
      keyword: '智慧',
      scoringCategory: '技术',
      customerType: 'STATE_OWNED',
      projectTypes: ['INFRASTRUCTURE', 'ENERGY'],
      statuses: ['PUBLISHED'],
      page: 2,
      pageSize: 16
    })

    expect(httpClient.get).toHaveBeenCalledWith('/api/cases', {
      params: {
        keyword: '智慧',
        scoringCategory: '技术',
        customerType: 'STATE_OWNED',
        projectTypes: 'INFRASTRUCTURE,ENERGY',
        uploadDateFrom: undefined,
        uploadDateTo: undefined,
        closeDateFrom: undefined,
        closeDateTo: undefined,
        statuses: 'PUBLISHED',
        sortBy: 'created',
        page: 1, // 内部把 page 从 1-based 转为 0-based
        size: 16
      }
    })
    expect(result.data).toHaveLength(1)
    expect(result.total).toBe(1)
  })

  it('getReferenceRecords(): 调用 GET /api/cases/{id}/references', async () => {
    httpClient.get.mockResolvedValue({ data: [{ id: 1, caseId: 42 }] })

    const result = await casesApi.getReferenceRecords(42)

    expect(httpClient.get).toHaveBeenCalledWith('/api/cases/42/references')
    expect(result).toEqual([{ id: 1, caseId: 42 }])
  })

  it('recommendCases(): 调用 GET /api/cases/recommend 并传 projectId/scoringItem/keyword', async () => {
    httpClient.get.mockResolvedValue({ data: [{ id: 1, score: 0.9 }] })

    await casesApi.recommendCases(100, '技术方案', '智慧城市')

    expect(httpClient.get).toHaveBeenCalledWith('/api/cases/recommend', {
      params: { projectId: 100, scoringItem: '技术方案', keyword: '智慧城市' }
    })
  })

  it('reuseCase(): 调用 POST /api/cases/{id}/reuse', async () => {
    httpClient.post.mockResolvedValue({ success: true })

    await casesApi.reuseCase(42)

    expect(httpClient.post).toHaveBeenCalledWith('/api/cases/42/reuse')
  })

  it('offShelfCase(): 调用 POST /api/cases/{id}/off-shelf', async () => {
    httpClient.post.mockResolvedValue({ success: true })

    await casesApi.offShelfCase(42)

    expect(httpClient.post).toHaveBeenCalledWith('/api/cases/42/off-shelf')
  })

  it('checkPrecipitationReadiness(): 调用 GET /api/cases/precipitation-readiness', async () => {
    httpClient.get.mockResolvedValue({ ready: true })

    await casesApi.checkPrecipitationReadiness(100)

    expect(httpClient.get).toHaveBeenCalledWith('/api/cases/precipitation-readiness', {
      params: { projectId: 100 }
    })
  })

  it('precipitateCases(): 调用 POST /api/cases/precipitate', async () => {
    httpClient.post.mockResolvedValue({ success: true })

    await casesApi.precipitateCases(100)

    expect(httpClient.post).toHaveBeenCalledWith('/api/cases/precipitate', null, {
      params: { projectId: 100 }
    })
  })
})
