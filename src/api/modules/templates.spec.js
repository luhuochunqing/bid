// Input: templates API module with mocked HTTP client
// Output: template module coverage for three-dimensional classification and request normalization
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
import { templatesApi } from './templates.js'

describe('templatesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getList(): passes official filters to the real templates endpoint without re-filtering backend results', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: [
        {
          id: 101,
          name: '智慧交通技术方案模板',
          category: 'TECHNICAL',
          productType: '智慧交通',
          industry: '交通',
          documentType: '技术方案',
          description: '交通项目模板',
          tags: ['交通', '模板'],
          currentVersion: '1.2',
          updatedAt: '2026-04-19T08:00:00Z'
        },
        {
          id: 102,
          name: '政府商务应答模板',
          category: 'COMMERCIAL',
          productType: '智慧园区',
          industry: '政府',
          documentType: '商务应答',
          description: '政府商务模板',
          tags: ['政府'],
          currentVersion: '1.0',
          updatedAt: '2026-04-18T08:00:00Z'
        }
      ]
    })

    const result = await templatesApi.getList({
      category: 'technical',
      productType: '智慧交通',
      industry: '交通',
      documentType: '技术方案',
      name: '交通'
    })

    expect(httpClient.get).toHaveBeenCalledWith('/api/knowledge/templates', {
      params: {
        category: 'TECHNICAL',
        productType: '智慧交通',
        industry: '交通',
        documentType: '技术方案',
        name: '交通'
      }
    })
    expect(result.success).toBe(true)
    expect(result.data).toHaveLength(2)
    expect(result.data[0]).toMatchObject({
      id: 101,
      name: '智慧交通技术方案模板',
      category: 'technical',
      productType: '智慧交通',
      industry: '交通',
      documentType: '技术方案',
      version: '1.2'
    })
  })

  it('create(): includes controlled three-dimensional fields in the payload', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: {
        id: 201,
        category: 'TECHNICAL',
        currentVersion: '1.0'
      }
    })

    await templatesApi.create({
      name: '智慧园区实施方案模板',
      category: 'implementation',
      productType: '智慧园区',
      industry: '制造业',
      documentType: '实施方案',
      description: '园区实施模板',
      tags: ['园区'],
      fileSize: '1.5 MB'
    })

    expect(httpClient.post).toHaveBeenCalledWith('/api/knowledge/templates', {
      name: '智慧园区实施方案模板',
      category: 'OTHER',
      productType: '智慧园区',
      industry: '制造业',
      documentType: '实施方案',
      fileUrl: '',
      description: '园区实施模板',
      fileSize: '1.5 MB',
      tags: ['园区'],
      createdBy: null
    })
  })

  it('create(): keeps unknown three-dimensional values intact so the backend can reject them explicitly', async () => {
    httpClient.post.mockResolvedValue({
      success: false,
      message: '不支持的产品类型: 火星产品'
    })

    await templatesApi.create({
      name: '未知分类模板',
      category: 'technical',
      productType: '火星产品',
      industry: '火星行业',
      documentType: '火星文档',
      description: '等待后端显式拒绝'
    })

    expect(httpClient.post).toHaveBeenCalledWith('/api/knowledge/templates', {
      name: '未知分类模板',
      category: 'TECHNICAL',
      productType: '火星产品',
      industry: '火星行业',
      documentType: '火星文档',
      fileUrl: '',
      description: '等待后端显式拒绝',
      fileSize: '',
      tags: [],
      createdBy: null
    })
  })
})
