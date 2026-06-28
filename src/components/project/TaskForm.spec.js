import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, it, expect, vi } from 'vitest'
import { defineComponent, nextTick, reactive, ref } from 'vue'
import TaskForm from './TaskForm.vue'
import { downloadWithFilename } from '@/utils/download.js'
import { ElMessage } from 'element-plus'

const getAssignableCandidatesMock = vi.hoisted(() => vi.fn().mockResolvedValue([
  { id: 9, name: '测试用户', deptCode: 'BID', deptName: '投标管理部', roleCode: 'bid-Team', roleName: '销售' },
  { id: 10, name: '张经理', deptCode: 'BID', deptName: '投标管理部', roleCode: 'manager', roleName: '经理' },
]))

const defaultAssignmentCandidates = [
  { id: 9, name: '测试用户', deptCode: 'BID', deptName: '投标管理部', roleCode: 'bid-Team', roleName: '销售' },
  { id: 10, name: '张经理', deptCode: 'BID', deptName: '投标管理部', roleCode: 'manager', roleName: '经理' },
]

vi.mock('@/api/modules/taskStatusDict.js', () => ({
  taskStatusDictApi: {
    list: vi.fn().mockResolvedValue({ success: true, data: [
      { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false },
      { code: 'REVIEW', name: '待审核', category: 'REVIEW', color: '#e6a23c', sortOrder: 30, initial: false, terminal: false },
      { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, initial: false, terminal: true },
    ]})
  }
}))

vi.mock('@/api/modules/users.js', () => ({
  usersApi: {
    getAssignableCandidates: getAssignableCandidatesMock,
    search: vi.fn().mockResolvedValue([]),
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
      roleCode: 'bid-Team',
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

vi.mock('@/utils/download.js', () => ({
  downloadWithFilename: vi.fn(),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: { warning: vi.fn() },
  }
})

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
    emits: ['change', 'remove', 'preview'],
    template: '<div class="el-upload-stub" data-test="task-attachment-upload"><slot /><slot name="tip" /><div class="el-upload-list" v-for="f in fileList" :key="f.name"><slot name="file" :file="f">{{ f.name }}</slot></div></div>',
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
    getAssignableCandidatesMock.mockReset()
    getAssignableCandidatesMock.mockResolvedValue(defaultAssignmentCandidates)
    vi.mocked(downloadWithFilename).mockClear()
    vi.mocked(ElMessage.warning).mockClear()
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
    expect(text).toContain('任务执行人')
    expect(text).toContain('任务创建人')
    expect(text).toContain('截止日期')
    expect(text).toContain('交付物上传')
    expect(text).toContain('完成情况说明')
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

  it('view mode does not render el-upload for task attachments, uses independent links', async () => {
    const wrapper = mount(TaskForm, {
      props: {
        mode: 'view',
        modelValue: {
          id: 31,
          name: 'X',
          attachments: [{ id: 801, projectId: 12, name: '任务附件.docx', fileUrl: 'doc-insight://task/file.docx' }],
        },
      },
      global: { stubs: globalStubs },
    })
    await flushPromises()

    // 只读模式不再渲染 el-upload（避免 :disabled 把 #file 插槽里的 <a> 链接锁死），
    // 已保存附件改由独立 .attachment-link 渲染，点击直达下载。
    expect(wrapper.find('[data-test="task-attachment-upload"]').exists()).toBe(false)
    const list = wrapper.find('[data-test="task-attachment-list"]')
    expect(list.exists()).toBe(true)
    expect(list.find('[data-test="task-attachment-file-link"]').text()).toBe('任务附件.docx')
  })

  it('renders task attachment filenames as clickable download links', async () => {
    const wrapper = mount(TaskForm, {
      props: {
        mode: 'view',
        modelValue: {
          id: 31,
          projectId: 12,
          name: 'X',
          attachments: [{ id: 801, projectId: 12, name: '任务附件.docx' }],
        },
      },
      global: { stubs: globalStubs },
    })
    await flushPromises()

    const link = wrapper.find('[data-test="task-attachment-file-link"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toBe('任务附件.docx')
  })

  it('calls downloadWithFilename when clicking task attachment link', async () => {
    const wrapper = mount(TaskForm, {
      props: {
        mode: 'view',
        modelValue: {
          id: 31,
          projectId: 12,
          name: 'X',
          attachments: [{ id: 801, projectId: 12, name: '任务附件.docx' }],
        },
      },
      global: { stubs: globalStubs },
    })
    await flushPromises()

    const link = wrapper.find('[data-test="task-attachment-file-link"]')
    await link.trigger('click')

    expect(downloadWithFilename).toHaveBeenCalledWith(
      '/api/projects/12/documents/801/download',
      '任务附件.docx'
    )
  })

  it('does not render link for unsaved (no id) attachments in view mode', async () => {
    const wrapper = mount(TaskForm, {
      props: {
        mode: 'view',
        modelValue: {
          id: 31,
          projectId: 12,
          name: 'X',
          attachments: [{ name: '未保存附件.docx' }],
        },
      },
      global: { stubs: globalStubs },
    })
    await flushPromises()

    // 未保存附件（无 id）在只读模式既不进 el-upload（被 v-if 隐），也不进 savedAttachments，
    // 因此页面不应出现任何附件链接，也不会触发下载。
    expect(wrapper.find('[data-test="task-attachment-list"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="task-attachment-file-link"]').exists()).toBe(false)
    expect(downloadWithFilename).not.toHaveBeenCalled()
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
      global: { stubs: { ...globalStubs, UserPicker: false } },
    })
    await flushAssigneeLoad()

    const userPicker = wrapper.findComponent({ name: 'UserPicker' })
    await userPicker.vm.$emit('select', {
      id: 9,
      name: '测试用户',
      deptCode: 'BID',
      deptName: '投标管理部',
      roleCode: 'bid-Team',
      roleName: '销售',
    })
    await flushPromises()

    const r = wrapper.vm.submit()

    expect(getAssignableCandidatesMock).toHaveBeenCalled()
    expect(r.valid).toBe(true)
    expect(r.data).toMatchObject({
      assigneeId: 9,
      owner: '测试用户',
      assignee: '测试用户',
      department: '投标管理部',
      assigneeDeptCode: 'BID',
      assigneeRoleCode: 'bid-Team',
    })
  })

  it('changing the owner selects a real colleague instead of free text', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: { name: 'X' } },
      global: { stubs: { ...globalStubs, UserPicker: false } },
    })
    await flushAssigneeLoad()

    const userPicker = wrapper.findComponent({ name: 'UserPicker' })
    await userPicker.vm.$emit('select', {
      id: 10,
      name: '张经理',
      deptCode: 'BID',
      deptName: '投标管理部',
      roleCode: 'manager',
      roleName: '经理',
    })
    await flushPromises()

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
      props: { mode: 'edit', modelValue: { name: 'X', status: 'REVIEW' } },
      global: { stubs: globalStubs },
    })
    await flushPromises()
    const r = wrapper.vm.submit()
    expect(r.data.status).toBe('REVIEW')
  })

  it('seeds edit owner from task data before async candidates finish', async () => {
    getAssignableCandidatesMock.mockImplementationOnce(() => new Promise(() => {}))
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

    expect(getAssignableCandidatesMock).not.toHaveBeenCalled()
    expect(r.data).toMatchObject({
      assigneeId: 28,
      owner: 'ERI-92 E2E',
      assignee: 'ERI-92 E2E',
      department: '投标管理部',
      roleName: '管理员',
    })
  })

  it('does not enter a v-model echo loop when normalizing owner fields', async () => {
    getAssignableCandidatesMock.mockImplementationOnce(() => new Promise(() => {}))
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

  it('view mode renders saved deliverables as downloadable links, not as disabled upload items', async () => {
    const wrapper = mount(TaskForm, {
      props: {
        mode: 'view',
        modelValue: {
          id: 31,
          projectId: 12,
          name: 'X',
          deliverables: [{ id: 5, name: '附件3-商铺平面图.pdf', url: 'doc-insight://tender-file/12/x.pdf' }],
        },
      },
      global: { stubs: globalStubs },
    })
    await flushPromises()

    // 已保存交付物应渲染为可下载链接（deliverable-link）
    const links = wrapper.findAll('.deliverable-link')
    expect(links).toHaveLength(1)
    expect(links[0].text()).toBe('附件3-商铺平面图.pdf')

    // 只读模式不渲染 el-upload（v-if 隐），避免灰色禁用重复条目
    expect(wrapper.find('[data-test="task-deliverable-upload"]').exists()).toBe(false)
  })

  it('clicking a deliverable link triggers downloadDeliverable', async () => {
    const wrapper = mount(TaskForm, {
      props: {
        mode: 'view',
        modelValue: {
          id: 31,
          projectId: 12,
          name: 'X',
          deliverables: [{ id: 5, name: '附件3-商铺平面图.pdf', url: 'doc-insight://tender-file/12/x.pdf' }],
        },
      },
      global: { stubs: globalStubs },
    })
    await flushPromises()

    await wrapper.find('.deliverable-link').trigger('click')
    expect(downloadWithFilename).toHaveBeenCalled()
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
