// Input: mocked projectStore taskStatuses + task fixtures + el-* stubs
// Output: regression spec covering dynamic columns, terminal-based progress, dict-driven dropdown
// Pos: src/components/common/ - TaskBoard component tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mockStatuses = [
  { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false },
  { code: 'REVIEW', name: '待审核', category: 'REVIEW', color: '#e6a23c', sortOrder: 30, initial: false, terminal: false },
  { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, initial: false, terminal: true },
  { code: 'ARCHIVED', name: '已归档', category: 'CLOSED', color: '#c0c4cc', sortOrder: 50, initial: false, terminal: true },
]

const mockProjectStore = vi.hoisted(() => ({
  taskStatuses: [],
  taskStatusesLoaded: true,
  loadTaskStatuses: vi.fn(),
  addDeliverable: vi.fn(),
  removeDeliverable: vi.fn(),
  removeTask: vi.fn(),
  submitToBidDocument: vi.fn(),
  currentProject: null,
}))

const mockUserStore = vi.hoisted(() => ({
  userName: '测试用户',
  currentUser: { id: 9 },
  isBidManager: false,
}))

const mockConfirm = vi.hoisted(() => vi.fn())

vi.mock('@/stores/project', () => ({
  useProjectStore: () => mockProjectStore,
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserStore,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessageBox: { confirm: mockConfirm } }
})

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
    mockProjectStore.taskStatuses = mockStatuses
    mockProjectStore.loadTaskStatuses = vi.fn()
    mockProjectStore.addDeliverable = vi.fn()
    mockProjectStore.removeDeliverable = vi.fn()
    mockProjectStore.removeTask = vi.fn()
    mockProjectStore.currentProject = null
    mockUserStore.isBidManager = false
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
        { id: 3, status: 'REVIEW' },
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
    const wrapper = mountBoard({ tasks: [{ id: 1, name: 'T1', status: 'TODO', assigneeId: 9 }] })
    await flushPromises()
    const items = wrapper.findAllComponents({ name: 'ElDropdownItem' })
    // 4 status transitions + 1 "上传交付物" = 5
    expect(items.length).toBeGreaterThanOrEqual(5)
  })

  it('non-assignee does not see "上传交付物" dropdown item', async () => {
    const wrapper = mountBoard({ tasks: [{ id: 1, name: 'T1', status: 'TODO', assigneeId: 999 }] })
    await flushPromises()
    const items = wrapper.findAllComponents({ name: 'ElDropdownItem' })
    // 4 status transitions only — "上传交付物" hidden by v-if
    expect(items.length).toBe(4)
  })

  it('task assignee sees "上传交付物" dropdown item', async () => {
    const wrapper = mountBoard({ tasks: [{ id: 1, name: 'T1', status: 'TODO', assigneeId: 9 }] })
    await flushPromises()
    const items = wrapper.findAllComponents({ name: 'ElDropdownItem' })
    // 4 status transitions + "上传交付物" = 5
    expect(items.length).toBeGreaterThanOrEqual(5)
    const allText = wrapper.text()
    expect(allText).toContain('上传交付物')
  })

  it('status-change items are disabled for non-assignee', async () => {
    const wrapper = mountBoard({ tasks: [{ id: 2, name: 'T2', status: 'TODO', assigneeId: 999 }] })
    await flushPromises()
    const items = wrapper.findAllComponents({ name: 'ElDropdownItem' })
    // All visible items (status changes) should be disabled for non-assignee
    items.forEach((item) => {
      expect(item.props('disabled')).toBe(true)
    })
  })

  it('project lead can review tasks (primaryLeadUserId matches)', async () => {
    mockProjectStore.currentProject = { primaryLeadUserId: 9, secondaryLeadUserId: null }
    const wrapper = mountBoard({ tasks: [{ id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 }] })
    await flushPromises()
    const items = wrapper.findAllComponents({ name: 'ElDropdownItem' })
    // REVIEW 状态下：4 个状态选项（TODO/REVIEW/COMPLETED/ARCHIVED），无上传交付物（非执行人），无删除（非TODO）
    expect(items.length).toBe(4)
    // 索引：0=TODO, 1=REVIEW, 2=COMPLETED, 3=ARCHIVED
    expect(items.at(0).props('disabled')).toBe(false) // REVIEW → TODO (驳回)
    expect(items.at(1).props('disabled')).toBe(true) // 当前状态 REVIEW
    expect(items.at(2).props('disabled')).toBe(false) // REVIEW → COMPLETED (通过)
    expect(items.at(3).props('disabled')).toBe(true) // 不能直接到ARCHIVED
  })

  it('secondary project lead can review tasks', async () => {
    mockProjectStore.currentProject = { primaryLeadUserId: null, secondaryLeadUserId: 9 }
    const wrapper = mountBoard({ tasks: [{ id: 2, name: 'T2', status: 'REVIEW', assigneeId: 999 }] })
    await flushPromises()
    const items = wrapper.findAllComponents({ name: 'ElDropdownItem' })
    expect(items.length).toBe(4)
    expect(items.at(0).props('disabled')).toBe(false) // REVIEW → TODO (驳回)
    expect(items.at(1).props('disabled')).toBe(true) // 当前状态 REVIEW
    expect(items.at(2).props('disabled')).toBe(false) // REVIEW → COMPLETED (通过)
    expect(items.at(3).props('disabled')).toBe(true)
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
    mockProjectStore.addDeliverable.mockResolvedValue(saved)
    const task = { id: 31, name: '编写技术方案', status: 'TODO', assigneeId: 9 }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    // 直接调用 handleSaveDeliverable 的等价逻辑来测试
    // 由于上传对话框拆分为子组件，这里测试组件的 add-deliverable 事件触发逻辑
    wrapper.vm.handleUploadDeliverable(task)
    await flushPromises()
    // 模拟子组件 emit save 事件，触发 handleSaveDeliverable
    const payload = { name: '技术方案.docx', type: 'technical', file }
    await wrapper.vm.handleSaveDeliverable(payload)

    expect(mockProjectStore.addDeliverable).toHaveBeenCalledWith('12', 31, expect.objectContaining({
      name: '技术方案.docx',
      deliverableType: 'TECHNICAL',
      file,
      uploaderId: 9,
      uploaderName: '测试用户',
    }))
    expect(wrapper.emitted('add-deliverable')?.[0]).toEqual([31, saved])
  })

  // Regression for IJSVX7 问题二：分配人创建任务后，看板「待办」列必须出现该任务
  // taskBackendToCard 把后端 status (e.g. 'TODO') 通过 normalizeTaskStatusFromApi 转为前端大写 status
  // 这里我们直接传入后端形态的 task 来确保看板能把"刚创建的任务"正确归到 TODO 列
  it('newly created task returned by backend lands in the TODO column', async () => {
    const { taskBackendToCard } = await import('@/views/Project/project-utils.js')
    const backendTask = taskBackendToCard({
      id: 100,
      title: '新建任务',
      content: '任务描述',
      status: 'TODO',
      priority: 'MEDIUM',
      dueDate: '2026-12-31',
      assigneeId: 9,
      assigneeName: '新建人',
    })
    const wrapper = mountBoard({ tasks: [backendTask] })
    await flushPromises()

    // TODO 列在第 0 列（mockStatuses 排序 10,30,40,50）
    const todoColumn = wrapper.findAll('.board-column')[0]
    const todoBadge = todoColumn.find('.el-badge-stub')
    expect(todoBadge.text()).toContain('1')
  })

  // CO-398: 截止时间显示为 YYYY-MM-DD（截断 ISO 时间），与 TaskKanban.vue line 40 格式一致
  it('deadline is rendered as YYYY-MM-DD (sliced from ISO), with "-" fallback', async () => {
    const wrapper = mountBoard({
      tasks: [
        { id: 1, name: 'T1', status: 'TODO', deadline: '2026-06-30T00:00:00' },
        { id: 2, name: 'T2', status: 'TODO', deadline: null },
      ]
    })
    await flushPromises()
    const deadlineSpans = wrapper.findAll('.task-deadline span')
    expect(deadlineSpans).toHaveLength(2)
    expect(deadlineSpans[0].text()).toBe('2026-06-30')
    expect(deadlineSpans[1].text()).toBe('-')
  })
})

describe('TaskBoard (drag to change status)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockProjectStore.loadTaskStatuses = vi.fn()
  })

  it('emits status-change with target column code when task is dropped in another column', async () => {
    const task = { id: 31, name: 'T', status: 'TODO', priority: 'medium', assigneeId: 9 }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    wrapper.vm.onDragChange({ added: { element: task, newIndex: 0 } }, 'REVIEW')
    await flushPromises()

    const emitted = wrapper.emitted('status-change')
    expect(emitted).toBeTruthy()
    expect(emitted[0]).toEqual([task, 'REVIEW'])
  })

  it('blocks direct TODO → COMPLETED transition (must go through REVIEW)', async () => {
    const task = { id: 37, name: 'T', status: 'TODO', priority: 'medium', assigneeId: 9 }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    wrapper.vm.handleMouseDragStart(task, { button: 0, clientX: 10, clientY: 10 })
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 40, clientY: 10 }))
    wrapper.vm.handleMouseDrop('COMPLETED')
    await flushPromises()

    // TODO → COMPLETED 被禁止，不触发 status-change 事件
    expect(wrapper.emitted('status-change')).toBeFalsy()
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

    wrapper.vm.onDragChange({ moved: { element: task } }, 'REVIEW')
    wrapper.vm.onDragChange({ removed: { element: task } }, 'REVIEW')
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

describe('TaskBoard (CO-387 delete task)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockProjectStore.taskStatuses = mockStatuses
    mockProjectStore.removeTask = vi.fn()
    mockProjectStore.currentProject = null
    mockUserStore.isBidManager = false
    mockConfirm.mockReset()
  })

  it('bid manager sees delete option for TODO task', async () => {
    mockUserStore.isBidManager = true
    const wrapper = mountBoard({ tasks: [{ id: 1, name: 'T1', status: 'TODO', assigneeId: 999 }] })
    await flushPromises()
    expect(wrapper.text()).toContain('删除任务')
  })

  it('primary project lead sees delete option for TODO task', async () => {
    mockProjectStore.currentProject = { primaryLeadUserId: 9, secondaryLeadUserId: null }
    const wrapper = mountBoard({ tasks: [{ id: 1, name: 'T1', status: 'TODO', assigneeId: 999 }] })
    await flushPromises()
    expect(wrapper.text()).toContain('删除任务')
  })

  it('secondary project lead sees delete option for TODO task', async () => {
    mockProjectStore.currentProject = { primaryLeadUserId: null, secondaryLeadUserId: 9 }
    const wrapper = mountBoard({ tasks: [{ id: 1, name: 'T1', status: 'TODO', assigneeId: 999 }] })
    await flushPromises()
    expect(wrapper.text()).toContain('删除任务')
  })

  it('non-manager non-lead does not see delete option', async () => {
    // isBidManager=false, currentProject=null → canDeleteTask=false
    const wrapper = mountBoard({ tasks: [{ id: 1, name: 'T1', status: 'TODO', assigneeId: 999 }] })
    await flushPromises()
    expect(wrapper.text()).not.toContain('删除任务')
  })

  it('delete option hidden for non-TODO status even if manager', async () => {
    mockUserStore.isBidManager = true
    const wrapper = mountBoard({ tasks: [{ id: 1, name: 'T1', status: 'REVIEW', assigneeId: 999 }] })
    await flushPromises()
    expect(wrapper.text()).not.toContain('删除任务')
  })

  it('confirming delete calls removeTask and emits remove-task', async () => {
    mockUserStore.isBidManager = true
    mockConfirm.mockResolvedValue(undefined) // 用户确认
    mockProjectStore.removeTask.mockResolvedValue(undefined)
    const task = { id: 42, name: '要删除的任务', status: 'TODO', assigneeId: 999 }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    await wrapper.vm.handleDeleteTask(task)

    expect(mockProjectStore.removeTask).toHaveBeenCalledWith(42)
    expect(wrapper.emitted('remove-task')?.[0]).toEqual([42])
  })

  it('cancelling delete does not call removeTask', async () => {
    mockUserStore.isBidManager = true
    mockConfirm.mockRejectedValue('cancel') // 用户取消
    const task = { id: 43, name: '不删除的任务', status: 'TODO', assigneeId: 999 }
    const wrapper = mountBoard({ projectId: '12', tasks: [task] })
    await flushPromises()

    await wrapper.vm.handleDeleteTask(task)

    expect(mockProjectStore.removeTask).not.toHaveBeenCalled()
    expect(wrapper.emitted('remove-task')).toBeFalsy()
  })
})

describe('TaskBoard (store bootstrap)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockProjectStore.loadTaskStatuses = vi.fn()
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
