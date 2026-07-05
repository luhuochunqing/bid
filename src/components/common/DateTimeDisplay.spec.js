// Input: DateTimeDisplay.vue public display component for CO-472
// Output: unit tests covering datetime/date formats and fallback rendering
// Pos: src/components/common/ - Component test
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DateTimeDisplay from './DateTimeDisplay.vue'

describe('DateTimeDisplay', () => {
  it('默认 datetime 格式：把 T 分隔符替换为空格', () => {
    const wrapper = mount(DateTimeDisplay, {
      props: { value: '2026-07-05T11:33:30' }
    })
    expect(wrapper.text()).toBe('2026-07-05 11:33:30')
  })

  it('format="date" 仅显示日期部分', () => {
    const wrapper = mount(DateTimeDisplay, {
      props: { value: '2026-07-05T11:33:30', format: 'date' }
    })
    expect(wrapper.text()).toBe('2026-07-05')
  })

  it('空值显示默认 fallback "-"', () => {
    const wrapper = mount(DateTimeDisplay, {
      props: { value: null }
    })
    expect(wrapper.text()).toBe('-')
  })

  it('空值显示自定义 fallback', () => {
    const wrapper = mount(DateTimeDisplay, {
      props: { value: null, fallback: '—' }
    })
    expect(wrapper.text()).toBe('—')
  })

  it('截断微秒部分', () => {
    const wrapper = mount(DateTimeDisplay, {
      props: { value: '2026-07-05T11:33:30.123456' }
    })
    expect(wrapper.text()).toBe('2026-07-05 11:33:30')
  })

  it('渲染为 span 元素', () => {
    const wrapper = mount(DateTimeDisplay, {
      props: { value: '2026-07-05T11:33:30' }
    })
    expect(wrapper.element.tagName).toBe('SPAN')
  })
})
