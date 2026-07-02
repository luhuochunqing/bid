// Input: src/api/modules/personnel.js — personnelApi (normalize/buildPayload)
// Output: CO-467 编辑页面字段丢失的回归测试
// Pos: src/api/modules/__tests__/ — API 层单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
// 维护声明: normalize/buildPayload 字段映射改动时，同步更新对应测试用例。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

import httpClient from '@/api/client.js'
import personnelApi from '../personnel.js'

describe('personnelApi — CO-467 编辑页面字段丢失回归', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ============ 读路径：normalize 必须保留 4 个丢失字段 ============

  it('getList 返回的 educations 保留 isHighestEducationSchool', async () => {
    httpClient.get.mockResolvedValue({
      data: [{
        id: 1,
        name: '张三',
        educations: [{
          id: 10, schoolName: '清华', isHighestEducationSchool: true
        }],
        certificates: []
      }]
    })
    const res = await personnelApi.getList()
    const edu = res.data[0].educations[0]
    expect(edu.isHighestEducationSchool).toBe(true)
  })

  it('getList 返回的 certificates 保留 title / isPermanent / remark', async () => {
    httpClient.get.mockResolvedValue({
      data: [{
        id: 1,
        name: '张三',
        educations: [],
        certificates: [{
          id: 20, name: '一建', title: '高级', isPermanent: true, remark: '长期有效'
        }]
      }]
    })
    const res = await personnelApi.getList()
    const cert = res.data[0].certificates[0]
    expect(cert.title).toBe('高级')
    expect(cert.isPermanent).toBe(true)
    expect(cert.remark).toBe('长期有效')
  })

  it('getDetail 返回的 4 字段均保留', async () => {
    httpClient.get.mockResolvedValue({
      data: {
        id: 1, name: '张三',
        educations: [{ id: 10, isHighestEducationSchool: true }],
        certificates: [{ id: 20, title: '高级', isPermanent: true, remark: '备注X' }]
      }
    })
    const res = await personnelApi.getDetail(1)
    expect(res.data.educations[0].isHighestEducationSchool).toBe(true)
    expect(res.data.certificates[0].title).toBe('高级')
    expect(res.data.certificates[0].isPermanent).toBe(true)
    expect(res.data.certificates[0].remark).toBe('备注X')
  })

  // ============ 写路径：buildPayload 必须发送 4 个丢失字段 ============

  it('update 发送的 certificates 包含 title / isPermanent / remark', async () => {
    httpClient.put.mockResolvedValue({ data: { id: 1, name: '张三', educations: [], certificates: [] } })
    await personnelApi.update(1, {
      name: '张三',
      certificates: [{
        name: '一建', title: '高级', isPermanent: true, remark: '长期有效'
      }],
      educations: []
    })
    const payload = httpClient.put.mock.calls[0][1]
    const cert = payload.certificates[0]
    expect(cert.title).toBe('高级')
    expect(cert.isPermanent).toBe(true)
    expect(cert.remark).toBe('长期有效')
  })

  it('update 发送的 educations 包含 isHighestEducationSchool', async () => {
    httpClient.put.mockResolvedValue({ data: { id: 1, name: '张三', educations: [], certificates: [] } })
    await personnelApi.update(1, {
      name: '张三',
      certificates: [],
      educations: [{
        schoolName: '清华', isHighestEducationSchool: true
      }]
    })
    const payload = httpClient.put.mock.calls[0][1]
    expect(payload.educations[0].isHighestEducationSchool).toBe(true)
  })

  it('create 发送的 4 字段均包含', async () => {
    httpClient.post.mockResolvedValue({ data: { id: 1, name: '张三', educations: [], certificates: [] } })
    await personnelApi.create({
      name: '张三',
      certificates: [{ name: '一建', title: '高级', isPermanent: true, remark: '备注Y' }],
      educations: [{ schoolName: '清华', isHighestEducationSchool: true }]
    })
    const payload = httpClient.post.mock.calls[0][1]
    expect(payload.certificates[0].title).toBe('高级')
    expect(payload.certificates[0].isPermanent).toBe(true)
    expect(payload.certificates[0].remark).toBe('备注Y')
    expect(payload.educations[0].isHighestEducationSchool).toBe(true)
  })
})
