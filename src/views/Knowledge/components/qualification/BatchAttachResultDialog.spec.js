import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import BatchAttachResultDialog from './BatchAttachResultDialog.vue'

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

describe('BatchAttachResultDialog', () => {
  const mountDialog = (props = {}) => {
    return mount(BatchAttachResultDialog, {
      props: {
        modelValue: true,
        data: {
          total: 3,
          success: 2,
          failed: 1,
          matched: [
            { fileName: 'QUAL_QC-001_01_doc.pdf', certificateNo: 'QC-001', qualificationName: 'ISO认证' }
          ],
          unmatched: [
            { fileName: 'QUAL_UNKNOWN_01_x.pdf', reason: '证书编号不存在' }
          ]
        },
        ...props
      },
      global: { stubs: globalStubs }
    })
  }

  it('应展示关联统计卡片', async () => {
    const wrapper = mountDialog()
    await nextTick()

    const numbers = wrapper.findAll('.stat-number')
    expect(numbers[0].text()).toBe('3')
    expect(numbers[1].text()).toBe('2')
    expect(numbers[2].text()).toBe('1')
  })

  it('应展示成功关联列表区域', async () => {
    const wrapper = mountDialog()
    await nextTick()

    expect(wrapper.find('.matched-section').exists()).toBe(true)
    expect(wrapper.text()).toContain('成功关联')
  })

  it('应展示未匹配文件列表区域', async () => {
    const wrapper = mountDialog()
    await nextTick()

    expect(wrapper.find('.unmatched-section').exists()).toBe(true)
    expect(wrapper.text()).toContain('未匹配文件')
  })

  it('全部成功时应展示成功提示', async () => {
    const wrapper = mountDialog({
      data: { total: 2, success: 2, failed: 0, matched: [{ fileName: 'a.pdf', certificateNo: 'A', qualificationName: 'Q' }], unmatched: [] }
    })
    await nextTick()

    expect(wrapper.find('.all-success').exists()).toBe(true)
    expect(wrapper.text()).toContain('全部附件关联成功')
  })
})
