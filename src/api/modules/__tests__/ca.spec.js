// Input: src/api/modules/ca.js — normalizeCaCertificate
// Output: CO-435 编辑页面字段丢失的回归测试
// Pos: src/api/modules/__tests__/ — API 层单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
// 维护声明: normalizeCaCertificate 函数改动时，同步更新对应的 normalize 测试用例。

import { describe, expect, it } from 'vitest'
import {
  normalizeCaCertificate,
  normalizeBorrowApplication,
  normalizeOperationEvent
} from '../ca.js'

describe('normalizeCaCertificate — CO-435 修复颁发机构/持有人/备注字段映射', () => {

  // CO-435 核心修复：issuer（颁发机构）字段应被正确映射
  it('正确映射 issuer 字段（颁发机构）', () => {
    const input = { id: 1, issuer: '中国金融认证中心' }
    const result = normalizeCaCertificate(input)
    expect(result.issuer).toBe('中国金融认证中心')
  })

  it('issuer 为空时应返回空字符串', () => {
    const input = { id: 1 }
    const result = normalizeCaCertificate(input)
    expect(result.issuer).toBe('')
  })

  // CO-435 核心修复：holderName（持有人）字段应被正确映射
  it('正确映射 holderName 字段（持有人）', () => {
    const input = { id: 1, holderName: '李四' }
    const result = normalizeCaCertificate(input)
    expect(result.holderName).toBe('李四')
  })

  it('holderName 为空时应返回空字符串', () => {
    const input = { id: 1 }
    const result = normalizeCaCertificate(input)
    expect(result.holderName).toBe('')
  })

  // CO-435 核心修复：后端字段名是 remarks（复数），应优先读取 remarks，兼容 remark（单数）
  it('优先从 remarks（复数）读取备注字段', () => {
    const input = { id: 1, remarks: '这是备注内容' }
    const result = normalizeCaCertificate(input)
    expect(result.remark).toBe('这是备注内容')
  })

  it('remarks 为空时兼容 remark（单数）字段', () => {
    const input = { id: 1, remark: '旧字段备注' }
    const result = normalizeCaCertificate(input)
    expect(result.remark).toBe('旧字段备注')
  })

  it('remarks 和 remark 都存在时，remarks 优先', () => {
    const input = { id: 1, remarks: '复数字段', remark: '单数字段' }
    const result = normalizeCaCertificate(input)
    expect(result.remark).toBe('复数字段')
  })

  it('备注字段都为空时应返回空字符串', () => {
    const input = { id: 1 }
    const result = normalizeCaCertificate(input)
    expect(result.remark).toBe('')
  })

  // 边界：normalizeCaCertificate 对 null/undefined 输入返回 null
  it('输入为 null 时返回 null', () => {
    expect(normalizeCaCertificate(null)).toBe(null)
  })

  it('输入为 undefined 时返回 null', () => {
    expect(normalizeCaCertificate(undefined)).toBe(null)
  })

  // 回归保护：已有字段不应被破坏
  it('已有字段映射不受影响 — id', () => {
    const input = { id: 99 }
    expect(normalizeCaCertificate(input).id).toBe(99)
  })

  it('已有字段映射不受影响 — platformIds 为数组', () => {
    const input = { id: 1, platformIds: [101, 102] }
    const result = normalizeCaCertificate(input)
    expect(result.platformIds).toEqual([101, 102])
  })

  it('已有字段映射不受影响 — platformIds 为 JSON 字符串', () => {
    const input = { id: 1, platformIds: '[101, 102]' }
    const result = normalizeCaCertificate(input)
    expect(result.platformIds).toEqual([101, 102])
  })

  it('已有字段映射不受影响 — custodianName 默认 dash', () => {
    const input = { id: 1 }
    const result = normalizeCaCertificate(input)
    expect(result.custodianName).toBe('-')
  })
})

describe('normalizeBorrowApplication — 基本形态回归', () => {
  it('输入为 null 时返回 null', () => {
    expect(normalizeBorrowApplication(null)).toBe(null)
  })

  it('基本字段映射正确', () => {
    const input = { id: 1, applicantName: '王五', status: 'PENDING' }
    const result = normalizeBorrowApplication(input)
    expect(result.applicantName).toBe('王五')
    expect(result.status).toBe('PENDING')
  })

  // CO-459: 盖章承诺书字段（长期借用申请的可下载附件）
  it('正确映射 commitmentLetterUrl 字段', () => {
    const input = { id: 1, commitmentLetterUrl: '/uploads/letter.pdf' }
    const result = normalizeBorrowApplication(input)
    expect(result.commitmentLetterUrl).toBe('/uploads/letter.pdf')
  })

  it('commitmentLetterUrl 为空时返回空字符串', () => {
    const input = { id: 1 }
    const result = normalizeBorrowApplication(input)
    expect(result.commitmentLetterUrl).toBe('')
  })

  // CO-466: 后端 enrich caName 字段（持有人 / 关联平台 / 印章），前端 normalize 必须透传
  it('正确透传 caName 字段（CO-466）', () => {
    const input = { id: 1, caCertificateId: 5, caName: '张三 / 政采云, 国铁采购 / 公章' }
    const result = normalizeBorrowApplication(input)
    expect(result.caName).toBe('张三 / 政采云, 国铁采购 / 公章')
    expect(result.caCertificateId).toBe(5)
  })

  it('caName 缺失时返回空字符串（前端 fallback 到 CA#${id}）', () => {
    const input = { id: 1, caCertificateId: 5 }
    const result = normalizeBorrowApplication(input)
    expect(result.caName).toBe('')
  })

  // CO-465: 申请人字段必须同时输出 applicantName + applicantEmployeeNumber，
  // 否则前端 formatDisplayName 渲染会缺少工号。
  it('正确映射 applicantEmployeeNumber 字段（CO-465）', () => {
    const input = { id: 1, applicantName: '王五', applicantEmployeeNumber: 'EMP20260001' }
    const result = normalizeBorrowApplication(input)
    expect(result.applicantName).toBe('王五')
    expect(result.applicantEmployeeNumber).toBe('EMP20260001')
  })

  it('applicantEmployeeNumber 为空时返回空字符串（CO-465 兼容）', () => {
    const input = { id: 1, applicantName: '李四' }
    const result = normalizeBorrowApplication(input)
    expect(result.applicantEmployeeNumber).toBe('')
  })
})

describe('normalizeOperationEvent — 基本形态回归', () => {
  it('输入为 null 时返回 null', () => {
    expect(normalizeOperationEvent(null)).toBe(null)
  })

  it('基本字段映射正确', () => {
    const input = { id: 1, eventType: 'CREATED', operatorName: '管理员' }
    const result = normalizeOperationEvent(input)
    expect(result.eventType).toBe('CREATED')
    expect(result.eventTypeLabel).toBe('创建')
  })
})
