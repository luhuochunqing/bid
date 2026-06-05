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
