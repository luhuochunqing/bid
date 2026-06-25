import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'

const { useUserPickerMock } = vi.hoisted(() => ({
  useUserPickerMock: vi.fn(),
}))

vi.mock('@/composables/useUserPicker.js', () => ({
  useUserPicker: useUserPickerMock,
}))

import UserPicker from '@/components/common/UserPicker.vue'

const mockUser = {
  id: 1,
  name: '张三',
  employeeNumber: '20260509',
  roleCode: 'bid_admin',
  roleName: '投标管理员',
  deptCode: 'BID',
  deptName: '投标管理部',
}

const elementStubs = {
  ElSelect: {
    name: 'ElSelect',
    props: ['modelValue', 'filterable', 'remote', 'remoteMethod', 'loading', 'placeholder', 'disabled', 'clearable', 'multiple'],
    emits: ['update:modelValue', 'change'],
    template: '<select class="el-select-stub"><slot /><slot name="empty" /></select>',
  },
  ElOption: {
    name: 'ElOption',
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>',
  },
}

function mockComposable(overrides = {}) {
  const defaults = {
    options: ref([]),
    loading: ref(false),
    search: vi.fn(),
    loadCandidates: vi.fn(),
    formatLabel: (user) => user ? `${user.name}（${user.employeeNumber}）` : '—',
  }
  useUserPickerMock.mockReturnValue({ ...defaults, ...overrides })
}

describe('UserPicker', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockComposable()
  })

  it('renders el-select with filterable and remote when mode=search', () => {
    const wrapper = mount(UserPicker, {
      props: { mode: 'search' },
      global: { stubs: elementStubs },
    })
    const select = wrapper.findComponent({ name: 'ElSelect' })
    expect(select.props('filterable')).toBe(true)
    expect(select.props('remote')).toBe(true)
  })

  it('preloads options on mount when mode=candidates', async () => {
    const loadCandidates = vi.fn()
    mockComposable({ loadCandidates })
    mount(UserPicker, {
      props: { mode: 'candidates', context: 'task' },
      global: { stubs: elementStubs },
    })
    await flushPromises()
    expect(loadCandidates).toHaveBeenCalled()
  })

  it('binds v-model with number userId', async () => {
    const wrapper = mount(UserPicker, {
      props: { modelValue: 1, mode: 'search' },
      global: { stubs: elementStubs },
    })
    await wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', 2)
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted[emitted.length - 1][0]).toBe(2)
    expect(typeof emitted[emitted.length - 1][0]).toBe('number')
  })

  it('emits select with full user object on change', async () => {
    mockComposable({ options: ref([mockUser]) })
    const wrapper = mount(UserPicker, {
      props: { mode: 'search' },
      global: { stubs: elementStubs },
    })
    await wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', 1)
    const emitted = wrapper.emitted('select')
    expect(emitted).toBeTruthy()
    expect(emitted[0][0]).toMatchObject({
      id: 1,
      name: '张三',
      employeeNumber: '20260509',
      roleCode: 'bid_admin',
      roleName: '投标管理员',
      deptCode: 'BID',
      deptName: '投标管理部',
    })
  })

  it('supports non-id valueField while still emitting full user object', async () => {
    mockComposable({ options: ref([mockUser]) })
    const wrapper = mount(UserPicker, {
      props: { mode: 'search', valueField: 'name' },
      global: { stubs: elementStubs },
    })

    const option = wrapper.findComponent({ name: 'ElOption' })
    expect(option.props('value')).toBe('张三')

    await wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', '张三')

    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toBe('张三')
    expect(wrapper.emitted('select')[0][0]).toMatchObject({ id: 1, name: '张三' })
  })

  it('passes placeholder to el-select', () => {
    const wrapper = mount(UserPicker, {
      props: { mode: 'search', placeholder: '请选择执行人' },
      global: { stubs: elementStubs },
    })
    expect(wrapper.findComponent({ name: 'ElSelect' }).props('placeholder')).toBe('请选择执行人')
  })

  it('shows empty state when options are empty', () => {
    mockComposable({ options: ref([]) })
    const wrapper = mount(UserPicker, {
      props: { mode: 'search' },
      global: { stubs: elementStubs },
    })
    expect(wrapper.text()).toContain('无匹配用户')
  })

  it('uses formatLabel for option labels', () => {
    mockComposable({ options: ref([mockUser]) })
    const wrapper = mount(UserPicker, {
      props: { mode: 'search' },
      global: { stubs: elementStubs },
    })
    const option = wrapper.findComponent({ name: 'ElOption' })
    expect(option.props('label')).toBe('张三（20260509）')
  })

  describe('multiple mode', () => {
    const mockUser2 = {
      id: 2,
      name: '李四',
      employeeNumber: '20260510',
    }

    beforeEach(() => {
      mockComposable({ options: ref([mockUser, mockUser2]) })
    })

    it('passes multiple prop to el-select', () => {
      const wrapper = mount(UserPicker, {
        props: { mode: 'search', multiple: true },
        global: { stubs: elementStubs },
      })
      expect(wrapper.findComponent({ name: 'ElSelect' }).props('multiple')).toBe(true)
    })

    it('binds v-model with array of userIds', async () => {
      const wrapper = mount(UserPicker, {
        props: { modelValue: [1], mode: 'search', multiple: true },
        global: { stubs: elementStubs },
      })
      await wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', [1, 2])
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted).toBeTruthy()
      expect(emitted[emitted.length - 1][0]).toEqual([1, 2])
    })

    it('emits select with array of user objects in multiple mode', async () => {
      const wrapper = mount(UserPicker, {
        props: { mode: 'search', multiple: true },
        global: { stubs: elementStubs },
      })
      await wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', [1, 2])
      const emitted = wrapper.emitted('select')
      expect(emitted).toBeTruthy()
      expect(emitted[0][0]).toBeInstanceOf(Array)
      expect(emitted[0][0]).toHaveLength(2)
      expect(emitted[0][0][0]).toMatchObject({ id: 1, name: '张三' })
      expect(emitted[0][0][1]).toMatchObject({ id: 2, name: '李四' })
    })

    it('initializes with empty array when multiple and no modelValue', () => {
      const wrapper = mount(UserPicker, {
        props: { mode: 'search', multiple: true },
        global: { stubs: elementStubs },
      })
      expect(wrapper.findComponent({ name: 'ElSelect' }).props('modelValue')).toEqual([])
    })
  })
})
