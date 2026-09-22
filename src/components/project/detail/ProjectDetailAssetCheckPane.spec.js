// Input: ProjectDetailAssetCheckPane with 067 removal (dead BAR jumps removed)
// Output: assertion that asset info renders but dead action buttons are gone
// Pos: src/components/project/detail/ - project detail asset check pane spec
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import ProjectDetailAssetCheckPane from './ProjectDetailAssetCheckPane.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const stubs = {
  ElIcon: { template: '<i><slot /></i>' },
  ElTag: { template: '<span class="el-tag"><slot /></span>' },
  ElButton: { template: '<button><slot /></button>' },
  ElEmpty: { template: '<div class="el-empty">{{ description }}<slot /></div>', props: ['description'] },
  CircleCheck: true,
  CircleClose: true,
  Warning: true,
  User: true,
}

const buildDetail = (assetCheckResult) => ({
  assetCheckResult,
})

const foundResult = ref({
  found: true,
  site: { name: '苏州交易中心', url: 'https://example.com' },
  capability: { status: 'available', hasAccount: true, accountCount: 2, ukCount: 3, hasAvailableUK: true, primaryOwner: '张三', primaryPhone: '138' },
})

const notFoundResult = ref({ found: false })

describe('ProjectDetailAssetCheckPane', () => {
  it('renders asset info without dead BAR jump buttons (067 验收导出剔除)', () => {
    const wrapper = mount(ProjectDetailAssetCheckPane, {
      global: { stubs, provide: { [projectDetailKey]: buildDetail(foundResult) } },
    })

    expect(wrapper.text()).toContain('苏州交易中心')
    expect(wrapper.text()).toContain('可投标')
    // 067: 指向已删除 BAR 页面的 4 个跳转按钮不得存在
    expect(wrapper.text()).not.toContain('查看详情')
    expect(wrapper.text()).not.toContain('借用UK')
    expect(wrapper.text()).not.toContain('查看SOP')
    expect(wrapper.find('.check-actions').exists()).toBe(false)
  })

  it('renders empty state without the removed 前往资产管理 button (067 验收导出剔除)', () => {
    const wrapper = mount(ProjectDetailAssetCheckPane, {
      global: { stubs, provide: { [projectDetailKey]: buildDetail(notFoundResult) } },
    })

    expect(wrapper.text()).toContain('未找到该站点的资产信息')
    expect(wrapper.text()).not.toContain('前往资产管理')
  })
})
