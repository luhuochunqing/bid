// CO-583 PerformanceDetailDrawer 移除 totalExpiryDate 显示项测试
// 需求：详情抽屉不再显示"总截止日期（含可续约期）"项
// Pos: src/views/Knowledge/components/__tests__/ - PerformanceDetailDrawer test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/modules/performance', () => ({
  performanceApi: {
    downloadAttachment: vi.fn(),
  },
}))

vi.mock('../PerformanceOperationLogTimeline.vue', () => ({
  default: {
    name: 'PerformanceOperationLogTimeline',
    template: '<div class="log-timeline-stub" />',
    props: ['performanceId', 'loadTrigger']
  }
}))

import PerformanceDetailDrawer from '../PerformanceDetailDrawer.vue'

const stubs = {
  'el-drawer': {
    template: '<div class="el-drawer"><slot /><slot name="header" /></div>',
    props: ['modelValue', 'title', 'size']
  },
  'el-tabs': {
    template: '<div class="el-tabs"><slot /><slot name="header" /></div>',
    props: ['modelValue']
  },
  'el-tab-pane': {
    template: '<div class="el-tab-pane"><slot /></div>',
    props: ['label', 'name']
  },
  'el-descriptions': {
    template: '<div class="el-descriptions"><slot /></div>',
    props: ['column', 'border']
  },
  'el-descriptions-item': {
    template: '<div class="el-descriptions-item" :data-label="label"><slot /></div>',
    props: ['label', 'span']
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>',
    props: ['type', 'effect', 'size']
  },
  'el-alert': {
    template: '<div class="el-alert" />',
    props: ['title', 'type', 'showIcon', 'closable']
  },
  'el-table': {
    template: '<table class="el-table"><slot /></table>',
    props: ['data', 'border']
  },
  'el-table-column': {
    template: '<td class="el-table-column" />',
    props: ['prop', 'label', 'width', 'minWidth', 'align']
  }
}

function createWrapper(data = {}) {
  setActivePinia(createPinia())
  return mount(PerformanceDetailDrawer, {
    props: {
      visible: true,
      data: {
        id: 1,
        contractName: '合同A',
        signingEntity: '签约单位',
        groupCompany: '中核集团',
        customerType: 'CENTRAL_SOE',
        customerTypeLabel: '央企',
        industry: '能源',
        projectTypeLabel: '办公',
        dockingMethodLabel: 'Emall',
        customerLevelLabel: '集团',
        signingDate: '2024-01-01',
        expiryDate: '2025-01-01',
        groupTotalExpiryDate: '2025-12-31',
        daysRemaining: 180,
        status: 'IN_PERFORMANCE',
        statusLabel: '履约中',
        contactPerson: '张三',
        contactInfo: '13800000000',
        territory: '北京市',
        customerAddress: '北京市朝阳区',
        xiyuProjectManager: '李四',
        mallWebsiteUrl: '',
        hasBidNotice: false,
        attachments: [],
        ...data
      }
    },
    global: { stubs }
  })
}

describe('CO-583 PerformanceDetailDrawer 移除 totalExpiryDate 显示', () => {
  it('不渲染"总截止日期（含可续约期）"项', () => {
    const wrapper = createWrapper()
    const items = wrapper.findAll('.el-descriptions-item')
    const labels = items.map(i => i.attributes('data-label'))
    expect(labels).not.toContain('总截止日期（含可续约期）')
  })

  it('保留"签约日期"和"截止日期"项', () => {
    const wrapper = createWrapper()
    const items = wrapper.findAll('.el-descriptions-item')
    const labels = items.map(i => i.attributes('data-label'))
    expect(labels).toContain('签约日期')
    expect(labels).toContain('截止日期')
  })
})
