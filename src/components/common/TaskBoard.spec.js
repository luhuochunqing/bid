// Input: mocked projectStore taskStatuses + task fixtures + el-* stubs
// Output: regression spec covering dynamic columns, terminal-based progress, dict-driven dropdown
// Pos: src/components/common/ - TaskBoard component tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mockStatuses = [
  { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false },
  { code: 'IN_PROGRESS', name: '进行中', category: 'IN_PROGRESS', color: '#409eff', sortOrder: 20, initial: false, terminal: false },
  { code: 'REVIEW', name: '待审核', category: 'REVIEW', color: '#e6a23c', sortOrder: 30, initial: false, terminal: false },
  { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, initial: false, terminal: true },
  { code: 'ARCHIVED', name: '已归档', category: 'CLOSED', color: '#c0c4cc', sortOrder: 50, initial: false, terminal: true },
]

const loadTaskStatusesMock = vi.fn()
const addDeliverableMock = vi.hoisted(() => vi.fn())
const removeDeliverableMock = vi.hoisted(() => vi.fn())

vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({
    taskStatuses: mockStatuses,
    taskStatusesLoaded: true,
    loadTaskStatuses: loadTaskStatusesMock,
    addDeliverable: addDeliverableMock,
    removeDeliverable: removeDeliverableMock,
    submitToBidDocument: vi.fn(),
  }),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    userName: '测试用户',
    currentUser: { id: 9 },
  }),
}))

import TaskBoard from './TaskBoard.vue'

// Light stubs — keep slot text visible so `.text()` assertions work, and avoid
// the cost of spinning up the full element-plus registry.
const globalStubs = {
  ElTag: { props: ['type', 'size', 'closable'], template: '<span class="el-tag-stub"><slot /></span>' },
  ElBadge: { props: ['value'], template: '<span class="el-badge-stub">{{ value }}<slot /></span>' },
  ElButton: { props: ['type', 'size', 'disabled'], template: '<button class="el-button-stub" :disabled="disabled"><slot /></button>' },
  ElIcon: { template: '<i class="el-icon-stub"><slot /></i>' },
  ElEmpty: { props: ['description', 'imageSize'], template: '<div class="el-empty-stub">{{ description }}</div>' },
  ElLink: { props: ['href', 'type'], template: '<a class="el-link-stub" :href="href"><slot /></a>' },
  ElDropdown: { template: '<div class="el-dropdown-stub"><slot /><slot name="dropdown" /></div>' },
  ElDropdownItem: {
    name: 'ElDropdownItem',
    props: ['divided', 'disabled'],
    template: '<div class="el-dropdown-item" :class="{ disabled }"><slot /></div>',
  },
  ElDialog: { props: ['modelValue', 'title', 'width'], template: '<div v-if="modelValue" class="el-dialog-stub"><slot /><slot name="footer" /></div>' },
  ElForm: { props: ['model', 'labelWidth'], template: '<form class="el-form-stub"><slot /></form>' },
  ElFormItem: { props: ['label'], template: '<div class="el-form-item-stub"><label>{{ label }}</label><slot /></div>' },
  ElInput: {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<input class="el-input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElSelect: {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<select class="el-select-stub" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
  },
  ElOption: { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  ElUpload: { template: '<div class="el-upload-stub"><slot /><slot name="tip" /></div>' },
  draggable: {
    name: 'draggable',
    props: ['modelValue', 'group', 'itemKey', 'disabled'],
    emits: ['change', 'update:modelValue'],
    template: '<div class="vuedraggable-stub"><template v-for="(item, idx) in modelValue" :key="itemKey ? itemKey(item) : idx"><slot name="item" :element="item" :index="idx" /></template><slot name="footer" /></div>',
  },
}

function mountBoard(props = {}) {
  return mount(TaskBoard, {
    props,
    global: { stubs: globalStubs },
  })
}

describe('TaskBoard (dynamic columns)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    loadTaskStatusesMock.mockClear()
    addDeliverableMock.mockReset()
    removeDeliverableMock.mockReset()
  })

  it('renders one column per enabled status from the dict except the merged in-progress lane', async () => {
    const wrapper = mountBoard({ tasks: [] })
    await flushPromises()
    expect(wrapper.findAll('.board-column').length).toBe(4)
  })

  it('progress is terminal-based, not the string "done"', async () => {
    const wrapper = mountBoard({
      tasks: [
        { id: 1, status: 'COMPLETED' },
        { id: 2, status: 'ARCHIVED' },
        { id: 3, status: 'IN_PROGRESS' },
        { id: 4, status: 'TODO' }
      ]
    })
    await flushPromises()
    // 2 terminal out of 4 → 50%
    expect(wrapper.text()).toContain('50%')
  })

  it('legacy lowercase task status is normalized to uppercase for bucketing', async () => {
    const legacyStatus = 'TODO'.toLowerCase()
    const wrapper = mountBoard({
      tasks: [
        { id: 1, status: legacyStatus }
      ]
    })
    await flushPromises()
    const todoBadge = wrapper.findAll('.board-column')[0].find('.el-badge-stub')
    expect(todoBadge.text()).toContain('1')
  })

  it('dropdown items reflect dict (no hardcoded 4)', async () => {
    const wrapper = mountBoard({ tasks: [{ id: 1, name: 'T1', status: 'TODO' }] })
    await flushPromises()
    const items = wrapper.findAllComponents({ name: 'ElDropdownItem' })
    // At least 5 status transitions + 1 "上传交付物" = 6
    expect(items.length).toBeGreaterThanOrEqual(5)
  })

  it('uploads the selected file through projectStore and emits the saved deliverable', async () => {
    const file = new File(['技术方案'], '技术方案.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    const saved = {
      id: 501,
      name: '技术方案.docx',
      deliverableType: 'TECHNICAL',
      url: 'project-documents://12/技术方案.docx',
    }
    addDeliverableMock.mockResolvedValue(saved)
    const task = { id: 31, name: '编写技术方案', status: 'TODO' }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    wrapper.vm.handleUploadDeliverable(task)
    wrapper.vm.deliverableForm.name = '技术方案.docx'
    wrapper.vm.deliverableForm.type = 'technical'
    wrapper.vm.handleFileChange({ raw: file })
    await wrapper.vm.handleSaveDeliverable()

    expect(addDeliverableMock).toHaveBeenCalledWith('12', 31, expect.objectContaining({
      name: '技术方案.docx',
      deliverableType: 'TECHNICAL',
      file,
      uploaderId: 9,
      uploaderName: '测试用户',
    }))
    expect(wrapper.emitted('add-deliverable')?.[0]).toEqual([31, saved])
  })
})

describe('TaskBoard (drag to change status)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    loadTaskStatusesMock.mockClear()
  })

  it('emits status-change with target column code when task is dropped in another column', async () => {
    const task = { id: 31, name: 'T', status: 'TODO', priority: 'medium' }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    wrapper.vm.onDragChange({ added: { element: task, newIndex: 0 } }, 'IN_PROGRESS')
    await flushPromises()

    const emitted = wrapper.emitted('status-change')
    expect(emitted).toBeTruthy()
    expect(emitted[0]).toEqual([task, 'IN_PROGRESS'])
  })

  it('emits status-change through mouse drag fallback after movement threshold', async () => {
    const task = { id: 37, name: 'T', status: 'TODO', priority: 'medium' }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    wrapper.vm.handleMouseDragStart(task, { button: 0, clientX: 10, clientY: 10 })
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 40, clientY: 10 }))
    wrapper.vm.handleMouseDrop('COMPLETED')
    await flushPromises()

    expect(wrapper.emitted('status-change')?.[0]).toEqual([task, 'COMPLETED'])
  })

  it('ignores mouse drop when pointer movement stays under drag threshold', async () => {
    const task = { id: 38, name: 'T', status: 'TODO', priority: 'medium' }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    wrapper.vm.handleMouseDragStart(task, { button: 0, clientX: 10, clientY: 10 })
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 12, clientY: 12 }))
    wrapper.vm.handleMouseDrop('COMPLETED')
    await flushPromises()

    expect(wrapper.emitted('status-change')).toBeFalsy()
  })

  it('ignores same-column moves (no status-change emit)', async () => {
    const task = { id: 32, name: 'T', status: 'TODO' }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    wrapper.vm.onDragChange({ added: { element: task, newIndex: 0 } }, 'TODO')
    await flushPromises()

    expect(wrapper.emitted('status-change')).toBeFalsy()
  })

  it('ignores non-added drag events (moved/removed)', async () => {
    const task = { id: 33, name: 'T', status: 'TODO' }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    wrapper.vm.onDragChange({ moved: { element: task } }, 'IN_PROGRESS')
    wrapper.vm.onDragChange({ removed: { element: task } }, 'IN_PROGRESS')
    await flushPromises()

    expect(wrapper.emitted('status-change')).toBeFalsy()
  })

  it('normalizes legacy lowercase status on source task before comparing to target column', async () => {
    const legacyLower = 'TODO'.toLowerCase()
    const task = { id: 34, name: 'T', status: legacyLower }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    wrapper.vm.onDragChange({ added: { element: task, newIndex: 0 } }, 'TODO')
    await flushPromises()

    expect(wrapper.emitted('status-change')).toBeFalsy()
  })
})

describe('TaskBoard (store bootstrap)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    loadTaskStatusesMock.mockClear()
  })

  it('calls projectStore.loadTaskStatuses if not yet loaded', async () => {
    // Re-import with a fresh mock that reports taskStatusesLoaded=false
    vi.resetModules()
    const loadFn = vi.fn()
    vi.doMock('@/stores/project', () => ({
      useProjectStore: () => ({
        taskStatuses: [],
        taskStatusesLoaded: false,
        loadTaskStatuses: loadFn,
        addDeliverable: vi.fn(),
        removeDeliverable: vi.fn(),
        submitToBidDocument: vi.fn(),
      }),
    }))
    const Comp = (await import('./TaskBoard.vue')).default
    mount(Comp, { props: { tasks: [] }, global: { stubs: globalStubs } })
    await flushPromises()
    expect(loadFn).toHaveBeenCalled()
  })
})
