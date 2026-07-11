import { describe, it, expect } from 'vitest'
import { buildWarehouseListParams } from '../warehouseParams.js'

describe('buildWarehouseListParams', () => {
  describe('CSV 序列化（与后端 @RequestParam String 对齐）', () => {
    it('把多选 types 数组 join 成 CSV 字符串', () => {
      const p = buildWarehouseListParams({ types: ['SELF_OPERATED', 'CLOUD'] }, 0, 15)
      expect(p.types).toBe('SELF_OPERATED,CLOUD')
    })

    it('把多选 regions 数组 join 成 CSV 字符串', () => {
      const p = buildWarehouseListParams({ regions: ['华北', '华东'] }, 0, 15)
      expect(p.regions).toBe('华北,华东')
    })

    it('把多选 provinces 数组 join 成 CSV 字符串', () => {
      const p = buildWarehouseListParams({ provinces: ['北京市', '上海市'] }, 0, 15)
      expect(p.provinces).toBe('北京市,上海市')
    })

    it('空数组不传对应参数', () => {
      const p = buildWarehouseListParams({ types: [], regions: [], provinces: [] }, 0, 15)
      expect(p).not.toHaveProperty('types')
      expect(p).not.toHaveProperty('regions')
      expect(p).not.toHaveProperty('provinces')
    })
  })

  describe('statuses 与 UI「全部」语义对齐（含已关仓）', () => {
    it('用户未选状态时，不传 statuses（后端返回含 CLOSED 的全量）', () => {
      const p = buildWarehouseListParams({}, 0, 15)
      expect(p).not.toHaveProperty('statuses')
    })

    it('用户只选"使用中"时，只传 IN_USE（不强制附加其他状态）', () => {
      const p = buildWarehouseListParams({ statuses: ['IN_USE'] }, 0, 15)
      expect(p.statuses).toBe('IN_USE')
    })

    it('用户选多个非 CLOSED 状态时，原样传用户选择', () => {
      const p = buildWarehouseListParams({ statuses: ['IN_USE', 'EXPIRED'] }, 0, 15)
      expect(p.statuses).toBe('IN_USE,EXPIRED')
    })

    it('用户选了 CLOSED 时，原样传用户选择（含 CLOSED）', () => {
      const p = buildWarehouseListParams({ statuses: ['IN_USE', 'CLOSED'] }, 0, 15)
      expect(p.statuses).toBe('IN_USE,CLOSED')
    })

    it('用户只选 CLOSED 时，只传 CLOSED', () => {
      const p = buildWarehouseListParams({ statuses: ['CLOSED'] }, 0, 15)
      expect(p.statuses).toBe('CLOSED')
    })

    it('空数组 statuses 与未选等价：不传 statuses', () => {
      const p = buildWarehouseListParams({ statuses: [] }, 0, 15)
      expect(p).not.toHaveProperty('statuses')
    })
  })

  describe('其他筛选字段保持原行为', () => {
    it('keyword / contactPersonKeyword 字符串原样传', () => {
      const p = buildWarehouseListParams({ keyword: '朝阳仓', contactPersonKeyword: '张三' }, 0, 15)
      expect(p.keyword).toBe('朝阳仓')
      expect(p.contactPersonKeyword).toBe('张三')
    })

    it('endDateFrom / endDateTo 透传', () => {
      const p = buildWarehouseListParams({ endDateFrom: '2026-01-01', endDateTo: '2026-12-31' }, 0, 15)
      expect(p.endDateFrom).toBe('2026-01-01')
      expect(p.endDateTo).toBe('2026-12-31')
    })

    it('附件 boolean 开关为 true 时透传 true，false 时不传', () => {
      const p = buildWarehouseListParams({
        hasPropertyCert: true,
        hasInvoice: false,
        hasPhotos: true,
        hasLeaseContract: false
      }, 0, 15)
      expect(p.hasPropertyCert).toBe(true)
      expect(p.hasPhotos).toBe(true)
      expect(p).not.toHaveProperty('hasInvoice')
      expect(p).not.toHaveProperty('hasLeaseContract')
    })

    it('page / size 始终透传', () => {
      const p = buildWarehouseListParams({}, 2, 30)
      expect(p.page).toBe(2)
      expect(p.size).toBe(30)
    })

    it('filters 为 null 时不抛错，且不传 statuses', () => {
      const p = buildWarehouseListParams(null, 0, 15)
      expect(p.page).toBe(0)
      expect(p.size).toBe(15)
      expect(p).not.toHaveProperty('statuses')
    })
  })
})
