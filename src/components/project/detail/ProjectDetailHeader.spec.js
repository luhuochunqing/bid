import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

// CO-330: 验证「编辑/结果闭环/智能助手」3 个按钮已隐藏
const context = {
  project: { name: '测试项目', status: 'approved' },
  canSubmit: false,
  canRecordResult: false,
  assistantPanelVisible: false,
  handleEdit: vi.fn(),
  handleSubmitApproval: vi.fn(),
  handleRecordResult: vi.fn(),
  goToResultPage: vi.fn(),
  toggleAssistantPanel: vi.fn(),
  getStatusType: () => 'success',
  getStatusText: () => '已立项',
}

vi.mock('@/composables/projectDetail/context.js', async () => {
  const actual = await vi.importActual('@/composables/projectDetail/context.js')
  return { ...actual, useProjectDetailContext: () => context }
})

const elementStubs = {
  ElPageHeader: {
    template: `
      <section class="el-page-header">
        <div class="el-page-header__content"><slot name="content" /></div>
        <div class="el-page-header__extra"><slot name="extra" /></div>
      </section>
    `,
  },
  ElButton: { template: '<button class="el-button"><slot /></button>' },
  ElIcon: { template: '<i><slot /></i>' },
  ElTag: { template: '<span class="el-tag"><slot /></span>' },
}

describe('ProjectDetailHeader', () => {
  it('hides edit / result-loop / assistant buttons per CO-330', async () => {
    const { default: ProjectDetailHeader } = await import('./ProjectDetailHeader.vue')
    const wrapper = mount(ProjectDetailHeader, { global: { stubs: elementStubs } })

    expect(wrapper.find('.header-actions').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('编辑')
    expect(wrapper.text()).not.toContain('结果闭环')
    expect(wrapper.text()).not.toContain('智能助手')
  })
})
