import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreItemSelect from './ScoreItemSelect.vue'

const LONG_DETAIL = '技术方案实施方案完整可行，包含架构设计、部署方案与应急预案等关键要素'

const items = [
  { id: 1, code: '3.1', dim: '技术评分', detail: LONG_DETAIL },
  { id: 2, code: '1.1', dim: '商务', detail: '注册资本不低于规定金额' }
]

const stubs = {
  'el-select': {
    props: ['modelValue', 'noDataText', 'filterMethod'],
    emits: ['update:modelValue', 'change', 'visible-change'],
    template: `<div class="el-select-stub" :data-no-data="noDataText"><slot />
      <input class="select-filter-input" @input="filterMethod && filterMethod($event.target.value)" />
      <button class="select-close-btn" @click="$emit('visible-change', false)" />
      <button class="select-pick-btn" @click="$emit('update:modelValue', 1); $emit('change', 1)" />
    </div>`
  },
  'el-option': {
    props: ['label', 'value'],
    template: `<div class="el-option-stub" :data-label="label"><slot /></div>`
  },
  'el-tooltip': {
    props: ['content', 'disabled', 'popperClass'],
    template: `<span class="el-tooltip-mock" :data-content="content" :data-disabled="String(disabled)"><slot /></span>`
  }
}

function mountSelect(props = {}) {
  return mount(ScoreItemSelect, {
    props: { items, modelValue: 1, ...props },
    global: { stubs }
  })
}

describe('ScoreItemSelect.vue', () => {
  it('shows only serial and score item name, original text in tooltip and preview', () => {
    const wrapper = mountSelect()
    const optionLine = wrapper.find('.score-option-line')
    expect(optionLine.text()).toBe('3.1 技术评分')
    expect(optionLine.text()).not.toContain(LONG_DETAIL)
    expect(wrapper.find('.el-tooltip-mock').attributes('data-content')).toBe(LONG_DETAIL)
    expect(wrapper.find('.score-item-detail-preview').text()).toContain(LONG_DETAIL)
  })

  it('filters options by original detail then restores the full list on close', async () => {
    const wrapper = mountSelect()
    expect(wrapper.findAll('.el-option-stub')).toHaveLength(2)

    await wrapper.find('.select-filter-input').setValue('应急预案')
    expect(wrapper.findAll('.el-option-stub')).toHaveLength(1)
    expect(wrapper.find('.score-option-line').text()).toBe('3.1 技术评分')

    await wrapper.find('.select-filter-input').setValue('不存在的词')
    expect(wrapper.findAll('.el-option-stub')).toHaveLength(0)
    expect(wrapper.find('.el-select-stub').attributes('data-no-data')).toBe('无匹配评分项')

    await wrapper.find('.select-close-btn').trigger('click')
    expect(wrapper.findAll('.el-option-stub')).toHaveLength(2)
    expect(wrapper.find('.el-select-stub').attributes('data-no-data')).toBe('暂无评分项')
  })

  it('forwards inner select change and v-model updates to the parent', async () => {
    const wrapper = mountSelect({ modelValue: null })
    await wrapper.find('.select-pick-btn').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[1]])
    expect(wrapper.emitted('change')).toEqual([[1]])
  })

  it('hides preview and disables tooltip when detail is blank', async () => {
    const wrapper = mountSelect({
      items: [{ id: 1, code: 'A1', dim: 'x', detail: '  ' }],
      modelValue: 1
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.score-item-detail-preview').exists()).toBe(false)
    expect(wrapper.find('.el-tooltip-mock').attributes('data-disabled')).toBe('true')
  })
})
