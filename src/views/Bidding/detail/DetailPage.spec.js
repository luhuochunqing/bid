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

// ===========================================================================
// CO-311: 双 tab 数据源一致性 — 评估表 tab 关联状态需响应 crmLinkFailedSignal
// ===========================================================================
describe('CO-311 — 评估表 tab 关联状态与基本信息 tab 同步回滚', () => {
  it('存在 evaluationTabLinked computed,且检查 crmLinkFailedSignal', () => {
    const match = detailPageSource.match(/const evaluationTabLinked = computed\([\s\S]*?\n\}\)/)
    expect(match).not.toBeNull()
    const body = match[0]
    // 关联失败信号非零时强制返回 false
    expect(body).toContain('crmLinkFailedSignal')
    expect(body).toMatch(/if\s*\(\s*crmLinkFailedSignal\.value\s*\)\s*return\s*false/)
    // 否则按后端真实数据判断
    expect(body).toMatch(/evaluationSource|crmOpportunityName/)
  })

  it('评估表 tab 的"已关联"判断使用 evaluationTabLinked,而非直接读 tender.crmOpportunityName', () => {
    // L94 模板: <div v-if="evaluationTabLinked" class="crm-status-bar">
    expect(detailPageSource).toContain('v-if="evaluationTabLinked"')
    // 不应残留旧的直接判断
    expect(detailPageSource).not.toContain('v-if="tender?.crmOpportunityName" class="crm-status-bar"')
  })

  it('关联成功后重置 crmLinkFailedSignal,避免失败信号长期滞留', () => {
    // 在 onCrmOpportunityLinked 的 try 块中,loadTenderDetail() 之后应重置信号
    const match = detailPageSource.match(/await loadTenderDetail\(\)([\s\S]*?)}\s*catch/)
    expect(match).not.toBeNull()
    const body = match[1]
    expect(body).toContain('crmLinkFailedSignal.value = 0')
  })
})
