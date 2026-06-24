import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LoginDevAccountsHint from './LoginDevAccountsHint.vue'

describe('LoginDevAccountsHint', () => {
  it('renders without task_executor role', () => {
    const wrapper = mount(LoginDevAccountsHint)
    expect(wrapper.text()).not.toContain('task_executor')
    expect(wrapper.text()).not.toContain('任务执行人')
  })

  it('contains expected role hints', () => {
    const wrapper = mount(LoginDevAccountsHint)
    expect(wrapper.text()).toContain('/bidAdmin')
    expect(wrapper.text()).toContain('bid-TeamLeader')
    expect(wrapper.text()).toContain('bid-projectLeader')
    expect(wrapper.text()).toContain('bid-Team')
  })
})
