import { describe, expect, it } from 'vitest'
import m2Source from './index.vue?raw'

describe('m2-customer-type 饼图标签', () => {
  it('扇区标签只显示客户类型与比例，不显示数量', () => {
    expect(m2Source).toContain("formatter: '{b}\\n{d}%'")
    expect(m2Source).not.toContain("formatter: '{b}\\n{c}个 · {d}%'")
  })

  it('tooltip 悬停详情保留数量（产品要求仅扇区标签去数量）', () => {
    expect(m2Source).toContain("formatter: '{b}: {c}个 · {d}%'")
  })
})
