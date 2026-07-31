// Input: model / schemaFields / presetKeys / scope
// Output: collectCustomFields / mergeCustomFieldsIntoModel 单测 — 收集与回显摊平
// Pos: src/composables/ - 自定义字段纯函数 composable 测试

import { describe, it, expect } from 'vitest'
import { collectCustomFields, mergeCustomFieldsIntoModel } from './useCustomFields.js'

const schemaFields = [
  { key: 'name' }, // 预置
  { key: 'customer' }, // 预置
  { key: 'budgetLevel' }, // 自定义
  { key: 'visitDate' } // 自定义
]
const presetKeys = ['name', 'customer']

describe('collectCustomFields', () => {
  it('schema 减预置清单得到自定义 key 集，从 model 摘值并按 scope 分组', () => {
    const model = { name: '项目A', customer: '客户X', budgetLevel: '重点', visitDate: '2026-08-01' }

    expect(collectCustomFields(model, schemaFields, presetKeys, 'project.basic')).toEqual({
      'project.basic': { budgetLevel: '重点', visitDate: '2026-08-01' }
    })
  })

  it('跳过 undefined 值；null 与空串视为有效值保留（支持清空字段）', () => {
    const model = { name: '项目A', budgetLevel: null, visitDate: '' }

    expect(collectCustomFields(model, schemaFields, presetKeys, 'project.basic')).toEqual({
      'project.basic': { budgetLevel: null, visitDate: '' }
    })
  })

  it('schema 无自定义字段 → 返回空对象（不带 scope 键）', () => {
    const model = { name: '项目A', customer: '客户X' }

    expect(collectCustomFields(model, schemaFields.slice(0, 2), presetKeys, 'project.basic')).toEqual({})
  })

  it('自定义字段值全为 undefined → 返回空对象', () => {
    const model = { name: '项目A' }

    expect(collectCustomFields(model, schemaFields, presetKeys, 'project.basic')).toEqual({})
  })

  it('空入参容错不报错', () => {
    expect(collectCustomFields(null, null, null, 'project.basic')).toEqual({})
    expect(collectCustomFields({}, [], [], '')).toEqual({})
    expect(collectCustomFields({ a: 1 }, [{ key: 'a' }], null, 'project.basic')).toEqual({
      'project.basic': { a: 1 }
    })
  })
})

describe('mergeCustomFieldsIntoModel', () => {
  it('把 customFields[scope] 摊平进 model 顶层', () => {
    const model = { name: '项目A' }

    mergeCustomFieldsIntoModel(model, { 'project.basic': { budgetLevel: '重点' } }, 'project.basic')

    expect(model.budgetLevel).toBe('重点')
  })

  it('不覆盖已有预置键（presetKeys 过滤脏数据）', () => {
    const model = { name: '项目A' }

    mergeCustomFieldsIntoModel(
      model,
      { 'project.basic': { name: '脏数据覆盖', budgetLevel: '重点' } },
      'project.basic',
      ['name']
    )

    expect(model.name).toBe('项目A')
    expect(model.budgetLevel).toBe('重点')
  })

  it('只取当前 scope 分组，其他 scope 键不动', () => {
    const model = { name: '项目A' }

    mergeCustomFieldsIntoModel(
      model,
      { 'project.detail': { siteVisitDone: true } },
      'project.basic'
    )

    expect(model).toEqual({ name: '项目A' })
  })

  it('customFields 为空/缺 scope 键不报错', () => {
    const model = { name: '项目A' }

    expect(() => mergeCustomFieldsIntoModel(model, null, 'project.basic')).not.toThrow()
    expect(() => mergeCustomFieldsIntoModel(model, {}, 'project.basic')).not.toThrow()
    expect(() => mergeCustomFieldsIntoModel(model, undefined, 'project.basic')).not.toThrow()
    expect(model).toEqual({ name: '项目A' })
  })
})
