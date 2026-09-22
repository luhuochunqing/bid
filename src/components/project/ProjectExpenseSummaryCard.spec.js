// Input: ProjectExpenseSummaryCard with 067 removal (费用管理 jump button removed)
// Output: assertion that metrics render but the dead "费用管理" entry is gone
// Pos: src/components/project/ - project detail expense card spec
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectExpenseSummaryCard from './ProjectExpenseSummaryCard.vue'

const stubs = {
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElIcon: { template: '<span><slot /></span>' },
  ElButton: { template: '<button><slot /></button>' },
  ElAlert: { template: '<div class="el-alert" />' },
  ElTable: { template: '<div class="el-table"><slot /></div>' },
  ElTableColumn: { template: '<div />' },
  Coins: true,
  Coin: true,
}

const summary = {
  totalAmount: 12.5,
  paidAmount: 8,
  pendingAmount: 4.5,
  returnedAmount: 0,
}

describe('ProjectExpenseSummaryCard', () => {
  it('renders expense metrics without the removed 费用管理 entry (067 验收导出剔除)', () => {
    const wrapper = mount(ProjectExpenseSummaryCard, {
      props: { expenses: [], summary, loading: false, error: '' },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('费用汇总')
    expect(wrapper.text()).toContain('费用总额')
    // 067: 指向已删除费用管理页的跳转按钮不得存在
    expect(wrapper.text()).not.toContain('费用管理')
    expect(wrapper.emitted('manage')).toBeUndefined()
  })
})
