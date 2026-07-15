// CO-583 PerformanceFormDates 移除 totalExpiryDate 输入项测试
// 需求：表单不再渲染"总截止日期"输入框
// Pos: src/views/Knowledge/components/__tests__/ - PerformanceFormDates test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PerformanceFormDates from '../PerformanceFormDates.vue'

const stubs = {
  'el-form-item': {
    template: '<div class="el-form-item" :data-label="label"><slot /></div>',
    props: ['label', 'prop']
  },
  'el-date-picker': {
    template: '<input class="el-date-picker" />',
    props: ['modelValue', 'type', 'valueFormat', 'placeholder']
  }
}

function getLabels(wrapper) {
  return wrapper.findAll('.el-form-item').map(fi => fi.attributes('data-label'))
}

describe('CO-583 PerformanceFormDates 移除 totalExpiryDate', () => {
  it('不渲染"总截止日期"表单项', () => {
    const wrapper = mount(PerformanceFormDates, {
      props: { form: { signingDate: '', expiryDate: '' } },
      global: { stubs }
    })
    expect(getLabels(wrapper)).not.toContain('总截止日期')
  })

  it('保留"签约日期"和"截止日期"表单项', () => {
    const wrapper = mount(PerformanceFormDates, {
      props: { form: { signingDate: '', expiryDate: '' } },
      global: { stubs }
    })
    const labels = getLabels(wrapper)
    expect(labels).toContain('签约日期')
    expect(labels).toContain('截止日期')
  })

  it('只渲染 2 个表单项（签约日期 + 截止日期）', () => {
    const wrapper = mount(PerformanceFormDates, {
      props: { form: { signingDate: '', expiryDate: '' } },
      global: { stubs }
    })
    expect(wrapper.findAll('.el-form-item')).toHaveLength(2)
  })
})
