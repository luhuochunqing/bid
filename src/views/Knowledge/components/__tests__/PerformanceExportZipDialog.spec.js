// 业绩附件 ZIP 导出配置弹窗测试
// 验证：7 种附件类型复选框、全选三态、确认/取消交互、按钮禁用逻辑
// Pos: src/views/Knowledge/components/__tests__/ - Performance export zip dialog tests
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import PerformanceExportZipDialog from '../PerformanceExportZipDialog.vue'

const stubs = {
  'el-dialog': { template: '<div class="el-dialog"><slot /><slot name="footer" /></div>' },
  'el-checkbox': {
    template: `<label class="el-checkbox">
      <input type="checkbox" class="el-checkbox-input" :checked="modelValue" :data-value="value" @change="onChange" />
      <span class="el-checkbox-label"><slot /></span>
    </label>`,
    props: ['modelValue', 'value', 'label', 'indeterminate'],
    emits: ['change', 'update:modelValue'],
    methods: {
      onChange(e) {
        this.$emit('change', e.target.checked)
        this.$emit('update:modelValue', e.target.checked)
      }
    }
  },
  'el-checkbox-group': {
    template: '<div class="el-checkbox-group"><slot /></div>',
    props: ['modelValue']
  },
  'el-button': {
    template: '<button class="el-button" :disabled="disabled" :data-type="type" @click="$emit(\'click\')"><slot /></button>',
    props: ['disabled', 'type', 'loading'],
    emits: ['click']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>' },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' }
}

function createWrapper(props = {}) {
  return mount(PerformanceExportZipDialog, {
    props: {
      visible: true,
      selectedCount: 0,
      totalCount: 10,
      ...props
    },
    global: { stubs }
  })
}

describe('PerformanceExportZipDialog', () => {
  let wrapper

  beforeEach(() => {
    wrapper = createWrapper()
  })

  it('rendersSevenTypeCheckboxes', () => {
    const typeCheckboxes = wrapper.findAll('.el-checkbox-group .el-checkbox')
    expect(typeCheckboxes.length).toBe(7)
  })

  it('selectAllCheckbox_defaultsChecked', () => {
    expect(wrapper.vm.checkedTypes.length).toBe(7)
    const selectAllCheckbox = wrapper.findAll('.el-checkbox').find(c => c.text().includes('全选'))
    expect(selectAllCheckbox.find('input').element.checked).toBe(true)
  })

  it('selectAllCheckbox_togglesAll', async () => {
    const selectAllCheckbox = wrapper.findAll('.el-checkbox').find(c => c.text().includes('全选'))

    await selectAllCheckbox.find('input').setValue(false)
    expect(wrapper.vm.checkedTypes.length).toBe(0)

    await selectAllCheckbox.find('input').setValue(true)
    expect(wrapper.vm.checkedTypes.length).toBe(7)
  })

  it('confirmButton_disabledWhenNoneSelected', async () => {
    wrapper.vm.checkedTypes = []
    await wrapper.vm.$nextTick()
    const confirmBtn = wrapper.findAll('.el-button').find(b => b.text().includes('确认导出'))
    expect(confirmBtn?.attributes('disabled')).toBeDefined()
  })

  it('confirm_emitsCheckedTypes', async () => {
    wrapper.vm.checkedTypes = ['CONTRACT_AGREEMENT', 'BID_NOTICE']
    await wrapper.vm.$nextTick()
    const confirmBtn = wrapper.findAll('.el-button').find(b => b.text().includes('确认导出'))
    await confirmBtn.trigger('click')
    expect(wrapper.emitted('confirm')).toBeTruthy()
    expect(wrapper.emitted('confirm')[0][0]).toEqual(['CONTRACT_AGREEMENT', 'BID_NOTICE'])
  })

  it('cancel_closesDialog', async () => {
    const cancelBtn = wrapper.findAll('.el-button').find(b => b.text().includes('取消'))
    await cancelBtn.trigger('click')
    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible')[0][0]).toBe(false)
  })

  it('showsSelectedCountHintWhenSelected', () => {
    wrapper = createWrapper({ selectedCount: 5, totalCount: 20 })
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('选中')
  })

  it('showsTotalCountHintWhenNoneSelected', () => {
    wrapper = createWrapper({ selectedCount: 0, totalCount: 20 })
    expect(wrapper.text()).toContain('20')
    expect(wrapper.text()).toContain('全部')
  })
})
