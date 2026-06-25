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
  ElSelectV2: {
    name: 'ElSelectV2',
    props: ['modelValue', 'filterable', 'options', 'remote', 'remoteMethod', 'loading', 'placeholder', 'disabled', 'clearable', 'multiple', 'valueKey'],
    emits: ['update:modelValue', 'change'],
    template: '<div class="el-select-v2-stub"><slot name="empty" /></div>',
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

  it('renders el-select-v2 with filterable and remote when mode=search', () => {
    const wrapper = mount(UserPicker, {
      props: { mode: 'search' },
      global: { stubs: elementStubs },
    })
    const select = wrapper.findComponent({ name: 'ElSelectV2' })
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
    await wrapper.findComponent({ name: 'ElSelectV2' }).vm.$emit('change', 2)
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
    await wrapper.findComponent({ name: 'ElSelectV2' }).vm.$emit('change', 1)
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
    await wrapper.findComponent({ name: 'ElSelectV2' }).vm.$emit('change', '张三')

    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toBe('张三')
    expect(wrapper.emitted('select')[0][0]).toMatchObject({ id: 1, name: '张三' })
  })

  it('passes placeholder to el-select-v2', () => {
    const wrapper = mount(UserPicker, {
      props: { mode: 'search', placeholder: '请选择执行人' },
      global: { stubs: elementStubs },
    })
    expect(wrapper.findComponent({ name: 'ElSelectV2' }).props('placeholder')).toBe('请选择执行人')
  })

  it('shows empty state when options are empty', () => {
    mockComposable({ options: ref([]) })
    const wrapper = mount(UserPicker, {
      props: { mode: 'search' },
      global: { stubs: elementStubs },
    })
    expect(wrapper.text()).toContain('无匹配用户')
  })

  it('passes selectOptions as options prop with value/label format', () => {
    mockComposable({ options: ref([mockUser]) })
    const wrapper = mount(UserPicker, {
      props: { mode: 'search' },
      global: { stubs: elementStubs },
    })
    const select = wrapper.findComponent({ name: 'ElSelectV2' })
    expect(select.props('options')).toEqual([
      { value: 1, label: '张三（20260509）' },
    ])
    expect(select.props('valueKey')).toBe('id')
  })

  it('excludes users listed in excludeIds from options', () => {
    const otherUser = { ...mockUser, id: 2, name: '李四', employeeNumber: '20260510' }
    mockComposable({ options: ref([mockUser, otherUser]) })
    const wrapper = mount(UserPicker, {
      props: { mode: 'search', excludeIds: [1] },
      global: { stubs: elementStubs },
    })
    const select = wrapper.findComponent({ name: 'ElSelectV2' })
    // mockUser (id=1) 被排除，只剩 otherUser (id=2)
    expect(select.props('options')).toEqual([
      { value: 2, label: '李四（20260510）' },
    ])
  })

  it('filters options by roleFilter when provided', () => {
    const salesUser = { ...mockUser, id: 3, name: '王五', roleCode: 'sales' }
    const teamUser = { ...mockUser, id: 4, name: '赵六', roleCode: 'bid-Team' }
    mockComposable({ options: ref([salesUser, teamUser]) })
    const wrapper = mount(UserPicker, {
      props: { mode: 'search', roleFilter: 'bid-Team' },
      global: { stubs: elementStubs },
    })
    const select = wrapper.findComponent({ name: 'ElSelectV2' })
    // 只剩 roleCode === 'bid-Team' 的赵六
    expect(select.props('options')).toHaveLength(1)
    expect(select.props('options')[0].value).toBe(4)
  })
})
