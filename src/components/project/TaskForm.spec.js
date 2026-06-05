import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, it, expect, vi } from 'vitest'
import { defineComponent, nextTick, reactive, ref } from 'vue'
import TaskForm from './TaskForm.vue'

const getTaskAssignmentCandidatesMock = vi.hoisted(() => vi.fn().mockResolvedValue([
  { userId: 9, name: '测试用户', deptCode: 'BID', deptName: '投标管理部', roleCode: 'staff', roleName: '销售' },
  { userId: 10, name: '张经理', deptCode: 'BID', deptName: '投标管理部', roleCode: 'manager', roleName: '经理' },
]))

const defaultAssignmentCandidates = [
  { userId: 9, name: '测试用户', deptCode: 'BID', deptName: '投标管理部', roleCode: 'staff', roleName: '销售' },
  { userId: 10, name: '张经理', deptCode: 'BID', deptName: '投标管理部', roleCode: 'manager', roleName: '经理' },
]

vi.mock('@/api/modules/taskStatusDict.js', () => ({
  taskStatusDictApi: {
    list: vi.fn().mockResolvedValue({ success: true, data: [
      { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false },
      { code: 'IN_PROGRESS', name: '进行中', category: 'IN_PROGRESS', color: '#409eff', sortOrder: 20, initial: false, terminal: false },
      { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, initial: false, terminal: true },
    ]})
  }
}))

vi.mock('@/api/modules/users.js', () => ({
  usersApi: {
    getTaskAssignmentCandidates: getTaskAssignmentCandidatesMock,
  },
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    userName: '测试用户',
    currentUser: {
      id: 9,
      name: '测试用户',
      departmentCode: 'BID',
      departmentName: '投标管理部',
      roleCode: 'staff',
      roleName: '销售',
    },
  }),
}))

// Mutable mock store so individual tests can set taskExtendedFields before mounting.
const mockStoreState = reactive({
  taskExtendedFields: [],
  taskExtendedFieldsLoaded: false,
  loadTaskExtendedFields: vi.fn(async () => mockStoreState.taskExtendedFields),
})

vi.mock('@/stores/project', () => ({
  useProjectStore: () => mockStoreState,
}))

function setExtendedFields(list) {
  mockStoreState.taskExtendedFields = list
  mockStoreState.taskExtendedFieldsLoaded = true
}

async function flushAssigneeLoad() {
  await vi.advanceTimersByTimeAsync(0)
  await flushPromises()
}

// Local stubs: keep labels visible in text() and thread the :disabled prop through el-form.
const globalStubs = {
  ElForm: {
    name: 'ElForm',
    props: ['model', 'labelWidth', 'disabled'],
    template: '<form><slot /></form>',
  },
  ElFormItem: {
    props: ['label', 'required'],
    template: '<div class="form-item"><label>{{ label }}</label><slot /></div>',
  },
  ElInput: {
    props: ['modelValue', 'type', 'rows', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<input class="el-input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElDatePicker: {
    props: ['modelValue', 'type', 'valueFormat'],
    emits: ['update:modelValue'],
    template: '<input class="el-date-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElInputNumber: {
    props: ['modelValue', 'min', 'max'],
    emits: ['update:modelValue'],
    template: '<input class="el-input-number-stub" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
  },
  ElSelect: {
    props: ['modelValue', 'loading', 'filterable', 'remote', 'remoteMethod', 'placeholder'],
    emits: ['update:modelValue', 'change'],
    template: '<select class="el-select-stub" v-bind="$attrs" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
  },
  ElOption: {
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>',
  },
  ElAlert: {
    props: ['title', 'type', 'closable'],
    template: '<div class="el-alert-stub">{{ title }}</div>',
  },
  ElDivider: {
    template: '<div class="el-divider-stub"><slot /></div>',
  },
  ElTabs: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div class="el-tabs-stub"><slot /></div>',
  },
  ElTabPane: {
    props: ['label', 'name'],
    template: '<section class="el-tab-pane-stub"><h3>{{ label }}</h3><slot /></section>',
  },
  ElButton: {
    template: '<button type="button"><slot /></button>',
  },
  ElUpload: {
    name: 'ElUpload',
    props: ['fileList', 'autoUpload', 'disabled', 'accept'],
    emits: ['change', 'remove'],
    template: '<div class="el-upload-stub" data-test="task-attachment-upload"><slot /><slot name="tip" /></div>',
  },
  TaskActivityPanel: {
    name: 'TaskActivityPanel',
    props: ['taskId', 'readonly'],
    template: '<div class="task-activity-panel-stub">动态 {{ taskId }}</div>',
  },
}

describe('TaskForm', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // Reset extended fields before each test — avoids leakage across cases.
    mockStoreState.taskExtendedFields = []
    mockStoreState.taskExtendedFieldsLoaded = false
    mockStoreState.loadTaskExtendedFields.mockClear()
    getTaskAssignmentCandidatesMock.mockReset()
    getTaskAssignmentCandidatesMock.mockResolvedValue(defaultAssignmentCandidates)
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('renders system fields', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: {} },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('任务名称')
    expect(text).toContain('详细描述')
    expect(text).toContain('负责人')
    expect(text).toContain('截止日期')
    expect(text).toContain('优先级')
    expect(text).toContain('状态')
  })

  it('submit() returns {valid:false} when name is empty', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: {} },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(false)
  })

  it('submit() returns {valid:true, data} when name provided', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: { name: 'X' } },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(true)
    expect(r.data.name).toBe('X')
  })

  it('includes selected task attachments in submit payload', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: { name: 'X' } },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    const file = new File(['附件内容'], '任务附件.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })

    await wrapper.findComponent({ name: 'ElUpload' }).vm.$emit('change', { raw: file, name: file.name }, [{ raw: file, name: file.name }])
    const r = wrapper.vm.submit()

    expect(wrapper.text()).toContain('任务附件')
    expect(r.valid).toBe(true)
    expect(r.data.attachments).toEqual([file])
  })

  it('defaults status to dict.initial code on mount when empty', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: {} },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    wrapper.vm.submit()
    const last = wrapper.emitted('update:modelValue')?.slice(-1)?.[0]?.[0]
    expect(last?.status).toBe('TODO')
  })

  it('defaults owner to the current creator and keeps organization fields on create', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: { name: 'X' } },
      global: { stubs: globalStubs },
    })
    await flushAssigneeLoad()

    const r = wrapper.vm.submit()

    expect(getTaskAssignmentCandidatesMock).toHaveBeenCalledWith()
    expect(r.valid).toBe(true)
    expect(r.data).toMatchObject({
      assigneeId: 9,
      owner: '测试用户',
      assignee: '测试用户',
      department: '投标管理部',
      assigneeDeptCode: 'BID',
      assigneeRoleCode: 'staff',
    })
  })

  it('changing the owner selects a real colleague instead of free text', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: { name: 'X' } },
      global: { stubs: globalStubs },
    })
    await flushAssigneeLoad()

    await wrapper.find('[data-test="task-owner-select"]').setValue('10')
    const r = wrapper.vm.submit()

    expect(r.data).toMatchObject({
      assigneeId: 10,
      owner: '张经理',
      assignee: '张经理',
      department: '投标管理部',
      assigneeDeptCode: 'BID',
      assigneeRoleCode: 'manager',
    })
  })

  it('preserves modelValue.status when provided (edit mode)', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'edit', modelValue: { name: 'X', status: 'IN_PROGRESS' } },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    const r = wrapper.vm.submit()
    expect(r.data.status).toBe('IN_PROGRESS')
  })

  it('seeds edit owner from task data before async candidates finish', async () => {
    getTaskAssignmentCandidatesMock.mockImplementationOnce(() => new Promise(() => {}))
    const wrapper = mount(TaskForm, {
      props: {
        mode: 'edit',
        modelValue: {
          name: 'X',
          assigneeId: 28,
          owner: 'ERI-92 E2E',
          assignee: 'ERI-92 E2E',
          assigneeDeptName: '投标管理部',
          assigneeRoleName: '管理员',
        },
      },
      global: { stubs: globalStubs },
    })
    await flushPromises()

    const r = wrapper.vm.submit()

    expect(getTaskAssignmentCandidatesMock).not.toHaveBeenCalled()
    expect(r.data).toMatchObject({
      assigneeId: 28,
      owner: 'ERI-92 E2E',
      assignee: 'ERI-92 E2E',
      department: '投标管理部',
      roleName: '管理员',
    })
  })

  it('does not enter a v-model echo loop when normalizing owner fields', async () => {
    getTaskAssignmentCandidatesMock.mockImplementationOnce(() => new Promise(() => {}))
    const Parent = defineComponent({
      components: { TaskForm },
      setup() {
        const model = ref({
          name: 'X',
          assigneeId: 28,
          owner: 'ERI-92 E2E',
          assignee: 'ERI-92 E2E',
          assigneeDeptName: '投标管理部',
          assigneeRoleName: '管理员',
        })
        const updates = ref(0)
        const onUpdate = (value) => {
          updates.value += 1
          model.value = value
        }
        return { model, updates, onUpdate }
      },
      template: '<TaskForm :model-value="model" mode="edit" @update:modelValue="onUpdate" />',
    })

    const wrapper = mount(Parent, {
      global: { stubs: globalStubs },
    })
    await flushPromises()
    await nextTick()

    expect(wrapper.vm.updates).toBeLessThanOrEqual(2)
    expect(wrapper.vm.model.owner).toBe('ERI-92 E2E')
  })

  it('view mode disables the form', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'view', modelValue: { name: 'X' } },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    const form = wrapper.findComponent({ name: 'ElForm' })
    expect(form.props('disabled')).toBe(true)
  })

  it('renders activity tab for existing tasks', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'edit', modelValue: { id: 99, name: 'X' } },
      global: { stubs: globalStubs },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('动态')
    const panel = wrapper.findComponent({ name: 'TaskActivityPanel' })
    expect(panel.exists()).toBe(true)
    expect(panel.props('taskId')).toBe(99)
  })

  it('does not render extended fields section when store has none', async () => {
    // Default beforeEach sets taskExtendedFields to [].
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: {} },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'DynamicFormRenderer' }).exists()).toBe(false)
    expect(wrapper.find('.el-divider-stub').exists()).toBe(false)
  })

  it('renders extended fields when store has them + prefills from modelValue', async () => {
    setExtendedFields([
      { key: 'tender_chapter', label: '招标章节号', fieldType: 'text', required: false, placeholder: '', options: null },
      { key: 'priority_level', label: '优先级别', fieldType: 'select', required: false, placeholder: '', options: [{ label: '高', value: 'high' }] },
    ])
    const wrapper = mount(TaskForm, {
      props: { mode: 'edit', modelValue: { name: 'X', status: 'TODO', extendedFields: { tender_chapter: 'Ch.3' } } },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'DynamicFormRenderer' }).exists()).toBe(true)
    expect(wrapper.find('.el-divider-stub').exists()).toBe(true)
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(true)
    expect(r.data.extendedFields?.tender_chapter).toBe('Ch.3')
  })

  it('submit() returns invalid when extended required field is empty', async () => {
    setExtendedFields([
      { key: 'tender_chapter', label: '招标章节号', fieldType: 'text', required: true, placeholder: '', options: null },
    ])
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: { name: 'X', extendedFields: {} } },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(false)
    expect(r.message).toContain('招标章节号')
  })
})
