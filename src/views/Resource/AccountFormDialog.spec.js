import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import AccountFormDialog from './AccountFormDialog.vue'
import httpClient from '@/api/client.js'
import { resourcesApi } from '@/api'

beforeEach(() => {
  setActivePinia(createPinia())
})

vi.mock('@/api/client.js', () => ({
  default: {
    get: vi.fn(),
  },
}))

function mountDialog(props = {}) {
  return mount(AccountFormDialog, {
    props: {
      modelValue: true,
      editRow: null,
      ...props,
    },
    global: {
      plugins: [createPinia()],
      stubs: {
        'el-dialog': { emits: ['open'], mounted() { this.$emit('open') }, template: '<section><slot /><slot name="footer" /></section>' },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { props: ['label', 'required', 'error'], template: '<label>{{ label }}<slot /></label>' },
        'el-row': { template: '<div><slot /></div>' },
        'el-col': { template: '<div><slot /></div>' },
        'el-input': {
          name: 'ElInput',
          props: ['modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        'el-select': {
          name: 'ElSelect',
          props: ['modelValue'],
          template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
        },
        'el-option': { template: '<option><slot /></option>' },
        'el-switch': {
          name: 'ElSwitch',
          props: ['modelValue'],
          template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
        },
        'el-button': { template: '<button><slot /></button>' },
        UserPicker: {
          name: 'UserPicker',
          props: ['modelValue', 'mode', 'placeholder', 'disabled', 'initialOptions', 'loadOnMount'],
          emits: ['update:modelValue', 'select'],
          template: '<div class="user-picker-stub" />',
        },
      },
    },
  })
}

describe('AccountFormDialog', () => {
  it('renders UserPicker for contact person selection (CO-390 userId 升级)', async () => {
    httpClient.get.mockResolvedValue({ data: [] })
    const wrapper = mountDialog()
    await flushPromises()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('mode')).toBe('candidates')
    expect(picker.props('loadOnMount')).toBe(false)
    expect(picker.props('placeholder')).toBe('请选择投标部门人员')
  })

  it('binds contactPerson userId to UserPicker v-model (CO-390)', async () => {
    httpClient.get.mockResolvedValue({ data: [] })
    const wrapper = mountDialog({
      editRow: { id: 42, accountName: '测试平台', contactPerson: 99 }
    })
    await flushPromises()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('modelValue')).toBe(99)
  })

  it('passes bidding users from admin API as initial options (CO-390 含 phone/email)', async () => {
    httpClient.get.mockResolvedValue({
      data: [
        { id: 1, fullName: '张三', username: 'zhangsan', employeeNumber: '20260509', phone: '138', email: 'a@b.com' },
        { id: 2, fullName: '李四', username: 'lisi', employeeNumber: '20260510', phone: '139', email: 'c@d.com' },
      ],
    })
    const wrapper = mountDialog()
    await flushPromises()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.props('initialOptions')).toEqual([
      { id: 1, name: '张三', username: 'zhangsan', employeeNumber: '20260509', phone: '138', email: 'a@b.com' },
      { id: 2, name: '李四', username: 'lisi', employeeNumber: '20260510', phone: '139', email: 'c@d.com' },
    ])
  })

  it('CO-390: selecting a contact person auto-fills phone/email', async () => {
    httpClient.get.mockResolvedValue({ data: [] })
    const wrapper = mountDialog()
    await flushPromises()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    picker.vm.$emit('select', { id: 99, name: '王五', phone: '13800138000', email: 'wangwu@test.com' })
    await nextTick()

    // 联动后 form 中的 phone/email 应被回填
    const inputs = wrapper.findAll('input')
    // 输入顺序：accountName, url, username, password, contactPhone, contactEmail
    expect(inputs[4].element.value).toBe('13800138000')
    expect(inputs[5].element.value).toBe('wangwu@test.com')
  })

  it('CO-390: submit 提交时 contactPerson 为 Long userId（不是字符串）', async () => {
    httpClient.get.mockResolvedValue({ data: [] })
    const createSpy = vi.spyOn(resourcesApi.accounts, 'create').mockResolvedValue({ success: true, data: {} })
    const wrapper = mountDialog()
    await flushPromises()

    // 选择联系人（userId = 99）
    const picker = wrapper.findComponent({ name: 'UserPicker' })
    picker.vm.$emit('update:modelValue', 99)
    picker.vm.$emit('select', { id: 99, name: '王五', phone: '13800138000', email: 'wangwu@test.com' })
    await nextTick()

    // 填写其他必填字段
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('测试平台')
    await inputs[1].setValue('http://test.com')
    await inputs[2].setValue('testuser')
    await inputs[3].setValue('pass123')

    // 点击保存按钮（第二个按钮：取消/保存）
    const buttons = wrapper.findAll('button')
    await buttons[1].trigger('click')
    await flushPromises()

    expect(createSpy).toHaveBeenCalledOnce()
    const payload = createSpy.mock.calls[0][0]
    expect(payload.contactPerson).toBe(99)
    expect(typeof payload.contactPerson).toBe('number')
    // 不应再出现 custodian / caCustodian 字段
    expect(payload.custodian).toBeUndefined()
    expect(payload.caCustodian).toBeUndefined()
    expect(payload.custodianName).toBeUndefined()
  })
})
