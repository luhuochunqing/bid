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

  it('getList(): sends case filters as query params and paginates the normalized list', async () => {
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
    expect(result.success).toBe(true)
    expect(result.total).toBe(1)
    expect(result.data).toHaveLength(1)
    expect(result.data[0]).toMatchObject({
      id: 1,
      title: '智慧城市一体化平台',
      customer: '杭州市人民政府',
      industry: 'government',
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

  it('getDetail(): normalizes the backend case payload', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: {
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
    })

    const result = await casesApi.getDetail(7)

    expect(httpClient.get).toHaveBeenCalledWith('/api/knowledge/cases/7')
    expect(result.success).toBe(true)
    expect(result.data).toMatchObject({
      id: 7,
      title: '电力调度自动化系统',
      customer: '国网浙江省电力有限公司',
      industry: 'energy',
      amount: 4200,
      year: 2023,
      location: '浙江杭州',
      period: '2023.03 - 2023.12',
      tags: ['能源', '实时监控'],
      highlights: ['实现电网运行实时监控与智能预警'],
      technologies: ['C++', 'Qt'],
      viewCount: 8,
      useCount: 4
    })
  })
})
