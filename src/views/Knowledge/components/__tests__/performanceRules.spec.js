// CO-583 业绩表单默认值测试
// 需求：移除 totalExpiryDate 用户输入字段，createDefaultForm 不再包含 totalExpiryDate
// Pos: src/views/Knowledge/components/__tests__/ - Performance rules test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect } from 'vitest'
import { createDefaultForm, rules } from '../performanceRules.js'

describe('CO-583 createDefaultForm 移除 totalExpiryDate', () => {
  it('createDefaultForm 返回对象不含 totalExpiryDate 字段', () => {
    const form = createDefaultForm()
    expect(form).not.toHaveProperty('totalExpiryDate')
  })

  it('createDefaultForm 保留其他必要字段', () => {
    const form = createDefaultForm()
    expect(form).toHaveProperty('contractName')
    expect(form).toHaveProperty('signingDate')
    expect(form).toHaveProperty('expiryDate')
    expect(form).toHaveProperty('groupCompany')
    expect(form).toHaveProperty('customerType')
    expect(form).toHaveProperty('attachmentMap')
  })

  it('rules 不含 totalExpiryDate 校验规则', () => {
    expect(rules).not.toHaveProperty('totalExpiryDate')
  })
})
