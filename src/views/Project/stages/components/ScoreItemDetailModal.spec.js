import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreItemDetailModal from './ScoreItemDetailModal.vue'

describe('ScoreItemDetailModal.vue', () => {
  const mockItem = {
    code: 'D2',
    dim: '资质业绩',
    detail: 'CMMI 5 级认证',
    weight: 5,
    status: 'danger',
    statusText: '✗ 不满足',
    scoreType: '客观项',
    estScore: 0,
    estBasis: '知识库未匹配 CMMI 5 级证书（最高为 CMMI 3 级），预计 0 分',
  }

  const mockResult = {
    actualScore: 3,
    scoreType: 'objective',
    status: 'partial',
    evidence: '标书已补充 CMMI 3 级证书说明及替代方案，部分满足要求',
    quote: '第 7 章 资质证明（第 28 页）：我方虽未取得 CMMI 5 级认证，但已通过 CMMI 3 级认证...',
    missedReason: 'CMMI 5 级认证未找到匹配证书，标书已补充 CMMI 3 级说明，部分得分',
    suggestion: '建议尽快启动 CMMI 5 级认证评估流程',
  }

  const globalStubs = {
    'el-dialog': {
      template: `
        <div class="el-dialog-mock" v-if="modelValue">
          <div class="el-dialog__header">{{ title }}</div>
          <slot />
        </div>
      `,
      props: ['modelValue', 'title'],
    },
  }

  it('renders estimated mode correctly', () => {
    const wrapper = mount(ScoreItemDetailModal, {
      props: {
        visible: true,
        mode: 'est',
        item: mockItem,
        result: null,
      },
      global: { stubs: globalStubs },
    })

    expect(wrapper.find('.el-dialog__header').text()).toContain('D2 · 资质业绩 — 预计评分详情')
    expect(wrapper.find('.status-cell').text()).toBe('✗ 不满足')
    expect(wrapper.find('.detail-value.zero').text()).toBe('0 / 5')
    expect(wrapper.text()).toContain('知识库未匹配 CMMI 5 级证书')
  })

  it('renders actual score mode with quote, missed reason and suggestion', () => {
    const wrapper = mount(ScoreItemDetailModal, {
      props: {
        visible: true,
        mode: 'actual',
        item: mockItem,
        result: mockResult,
      },
      global: { stubs: globalStubs },
    })

    expect(wrapper.find('.el-dialog__header').text()).toContain('D2 · 资质业绩 — 实际评分详情')
    expect(wrapper.find('.detail-value.partial').text()).toBe('3 / 5')
    expect(wrapper.text()).toContain('标书已补充 CMMI 3 级证书说明及替代方案')
    expect(wrapper.text()).toContain('第 7 章 资质证明')
    expect(wrapper.text()).toContain('CMMI 5 级认证未找到匹配证书')
    expect(wrapper.text()).toContain('建议尽快启动 CMMI 5 级认证评估流程')
  })

  it('handles closed event', async () => {
    const wrapper = mount(ScoreItemDetailModal, {
      props: {
        visible: true,
        mode: 'est',
        item: mockItem,
      },
      global: { stubs: globalStubs },
    })

    wrapper.vm.handleClosed()
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
