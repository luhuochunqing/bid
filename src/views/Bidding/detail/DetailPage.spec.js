import { describe, expect, it } from 'vitest'
import detailPageSource from './DetailPage.vue?raw'

describe('Bidding/detail/DetailPage.vue label compliance', () => {
  describe('US2 — 标讯标题 → 项目名称', () => {
    it('转派对话框使用 项目名称 标签', () => {
      expect(detailPageSource).toContain('label="项目名称"')
      expect(detailPageSource).not.toContain('label="标讯标题"')
    })
  })

  describe('US1 — 详情页标签与项目列表保持一致', () => {
    it('基本 tab 中 purchaserName 显示为 招标主体（与项目列表 ownerUnit 语义一致）', () => {
      expect(detailPageSource).toContain('招标主体')
      expect(detailPageSource).not.toContain('label="业主单位"')
    })
  })
})

describe('CO-309 — 评估表第一/二部分彻底锁死只读', () => {
  it('canFillEvaluation computed 直接 return false,不含 TRACKING/sales 等条件分支', () => {
    const match = detailPageSource.match(/const canFillEvaluation = computed\([\s\S]*?\n\}\)/)
    expect(match).not.toBeNull()
    const body = match[0]
    // 不应再依赖 tender 状态、角色判断
    expect(body).not.toContain('return tender.value.status')
    expect(body).not.toContain("=== 'sales'")
    expect(body).not.toContain('isBidManager')
    // 应直接 return false
    expect(body).toContain('return false')
  })
})
