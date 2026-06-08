import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ImportResultDialog from './ImportResultDialog.vue'

const globalStubs = {
  'el-dialog': {
    template: '<div class="el-dialog" :data-title="title"><slot /><slot name="footer" /></div>',
    props: ['title', 'modelValue']
  },
  'el-table': {
    template: '<div class="el-table"><slot /></div>'
  },
  'el-table-column': { template: '<span />' },
  'el-button': { template: '<button class="el-button"><slot /></button>', props: ['type'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' }
}

describe('ImportResultDialog', () => {
  const mountDialog = (props = {}) => {
    return mount(ImportResultDialog, {
      props: {
        modelValue: true,
        data: { total: 3, success: 2, failed: 1, errors: [{ row: 5, certificateNo: 'DUP-001', reason: '证书编号已存在' }] },
        ...props
      },
      global: { stubs: globalStubs }
    })
  }

  it('应展示导入统计卡片', async () => {
    const wrapper = mountDialog()
    await nextTick()

    const numbers = wrapper.findAll('.stat-number')
    expect(numbers[0].text()).toBe('3')
    expect(numbers[1].text()).toBe('2')
    expect(numbers[2].text()).toBe('1')
  })

  it('应展示失败明细区域', async () => {
    const wrapper = mountDialog()
    await nextTick()

    expect(wrapper.find('.error-section').exists()).toBe(true)
    expect(wrapper.text()).toContain('DUP-001')
    expect(wrapper.text()).toContain('证书编号已存在')
  })

  it('全部成功时应展示成功提示', async () => {
    const wrapper = mountDialog({
      data: { total: 3, success: 3, failed: 0, errors: [] }
    })
    await nextTick()

    expect(wrapper.find('.all-success').exists()).toBe(true)
    expect(wrapper.text()).toContain('全部导入成功')
  })

  it('有失败时应展示下载修正文件按钮', async () => {
    const wrapper = mountDialog()
    await nextTick()

    expect(wrapper.text()).toContain('下载修正文件')
  })

  it('关闭时应触发 closed 事件', async () => {
    const wrapper = mountDialog()
    await nextTick()

    wrapper.find('.el-dialog').trigger('closed')
    await nextTick()

    expect(wrapper.emitted('closed')).toBeTruthy()
  })
})
