import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useProjectDetailTaskActions } from './useProjectDetailTaskActions.js'
import { useProjectDetailTasks } from './useProjectDetailTasks.js'

describe('useProjectDetailTaskActions', () => {
  it('API 项目新增任务时把 Markdown content 发送到后端 content 字段', async () => {
    const createTask = vi.fn().mockResolvedValue({
      success: true,
      data: {
        id: 601,
        name: '编写技术响应',
        content: '# 技术响应\n- 保留列表',
        status: 'TODO',
      },
    })
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }
    const message = { success: vi.fn(), error: vi.fn(), warning: vi.fn() }

    const { handleSaveTask } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: {},
      projectsApi: { createTask },
      isApiProject: ref(true),
      message,
      state,
      workflow: {},
    })

    await handleSaveTask({
      mode: 'create',
      data: {
        name: '编写技术响应',
        content: '# 技术响应\n- 保留列表',
        extendedFields: { chapter: '扩展值ABC' },
        owner: '测试用户',
        priority: 'high',
        deadline: '2026-06-01',
      },
    })

    expect(createTask).toHaveBeenCalledWith('12', expect.objectContaining({
      title: '编写技术响应',
      description: '',
      content: '# 技术响应\n- 保留列表',
      extendedFields: { chapter: '扩展值ABC' },
      priority: 'HIGH',
    }))
    expect(state.project.value.tasks[0]).toEqual(expect.objectContaining({
      id: 601,
      content: '# 技术响应\n- 保留列表',
    }))
    expect(message.success).toHaveBeenCalledWith('任务已新增')
  })

  it('API 项目新增任务时使用选中的组织人员作为真实 assignee', async () => {
    const createTask = vi.fn().mockResolvedValue({
      success: true,
      data: {
        id: 602,
        name: '准备商务文件',
        assigneeId: 10,
        owner: '张经理',
        status: 'TODO',
      },
    })
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }

    const { handleSaveTask } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: {},
      projectsApi: { createTask },
      isApiProject: ref(true),
      message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleSaveTask({
      mode: 'create',
      data: {
        name: '准备商务文件',
        owner: '张经理',
        assigneeId: 10,
        assigneeDeptCode: 'BID',
        assigneeDeptName: '投标管理部',
        assigneeRoleCode: 'manager',
        assigneeRoleName: '经理',
        priority: 'medium',
      },
    })

    expect(createTask).toHaveBeenCalledWith('12', expect.objectContaining({
      assigneeId: 10,
      assigneeName: '张经理',
      assigneeDeptCode: 'BID',
      assigneeDeptName: '投标管理部',
      assigneeRoleCode: 'manager',
      assigneeRoleName: '经理',
    }))
  })

  it('API 项目新增任务后把表单附件上传为任务附件', async () => {
    const file = new File(['附件内容'], '任务附件.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
    const createTask = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 603, name: '准备附件任务', status: 'TODO' },
    })
    const uploadTaskAttachment = vi.fn().mockResolvedValue({ id: 901, name: '任务附件.docx', url: '/files/901' })
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }

    const { handleSaveTask } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: { uploadTaskAttachment },
      projectsApi: { createTask },
      isApiProject: ref(true),
      message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleSaveTask({
      mode: 'create',
      data: { name: '准备附件任务', priority: 'medium', attachments: [file] },
    })

    expect(uploadTaskAttachment).toHaveBeenCalledWith('12', 603, expect.objectContaining({
      name: '任务附件.docx',
      documentCategory: 'TASK_ATTACHMENT',
      file,
      uploaderId: 9,
      uploaderName: '测试用户',
    }))
    expect(state.project.value.tasks[0].attachments).toEqual([expect.objectContaining({ id: 901 })])
  })

  it('API 项目新增任务时附件上传失败不阻断任务创建', async () => {
    const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const file = new File(['附件内容'], '任务附件.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
    const createTask = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 604, title: '带失败附件的任务', status: 'TODO' },
    })
    const uploadTaskAttachment = vi.fn().mockRejectedValue(new Error('网络超时'))
    const warning = vi.fn()
    const done = vi.fn()
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }

    const { handleSaveTask } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: { uploadTaskAttachment },
      projectsApi: { createTask },
      isApiProject: ref(true),
      message: { success: vi.fn(), error: vi.fn(), warning },
      state,
      workflow: {},
    })

    await handleSaveTask({
      mode: 'create',
      data: { name: '带失败附件的任务', priority: 'medium', attachments: [file] },
      done,
    })

    expect(state.project.value.tasks).toHaveLength(1)
    expect(state.project.value.tasks[0].name).toBe('带失败附件的任务')
    expect(warning).toHaveBeenCalledWith('任务已新增，但附件上传失败，请重试')
    expect(done).not.toHaveBeenCalled()
    consoleWarnSpy.mockRestore()
  })

  it('API 项目新增任务后把执行人本人表单交付物上传为任务交付物', async () => {
    const file = new File(['交付物内容'], '技术方案.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
    const createTask = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 606, title: '准备交付物任务', assigneeId: 9, status: 'TODO' },
    })
    const addDeliverable = vi.fn().mockResolvedValue({ id: 902, name: '技术方案.docx', url: '/files/902' })
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }

    const { handleSaveTask } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: { addDeliverable },
      projectsApi: { createTask },
      isApiProject: ref(true),
      message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleSaveTask({
      mode: 'create',
      data: { name: '准备交付物任务', priority: 'medium', assigneeId: 9, deliverableFiles: [file] },
    })

    expect(addDeliverable).toHaveBeenCalledWith('12', 606, expect.objectContaining({
      name: '技术方案.docx',
      deliverableType: 'DOCUMENT',
      file,
      uploaderId: 9,
      uploaderName: '测试用户',
    }))
    expect(state.project.value.tasks[0].deliverables).toEqual([expect.objectContaining({ id: 902 })])
    expect(state.project.value.tasks[0].hasDeliverable).toBe(true)
  })

  it('API 项目新增任务选择他人为执行人时不立即上传交付物', async () => {
    const file = new File(['交付物内容'], '他人交付物.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
    const createTask = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 607, title: '他人执行任务', assigneeId: 10, status: 'TODO' },
    })
    const addDeliverable = vi.fn()
    const warning = vi.fn()
    const done = vi.fn()
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }

    const { handleSaveTask } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: { addDeliverable },
      projectsApi: { createTask },
      isApiProject: ref(true),
      message: { success: vi.fn(), error: vi.fn(), warning },
      state,
      workflow: {},
    })

    await handleSaveTask({
      mode: 'create',
      data: { name: '他人执行任务', priority: 'medium', assigneeId: 10, deliverableFiles: [file] },
      done,
    })

    expect(addDeliverable).not.toHaveBeenCalled()
    expect(warning).toHaveBeenCalledWith('仅任务执行人本人可上传交付物，请让执行人打开任务后上传')
    expect(done).not.toHaveBeenCalled()
    expect(state.project.value.tasks[0]).toEqual(expect.objectContaining({ id: 607, assigneeId: 10 }))
  })

  it('API 项目新增任务无附件时不调用 uploadTaskAttachment', async () => {
    const createTask = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 605, title: '无附件任务', status: 'TODO' },
    })
    const uploadTaskAttachment = vi.fn()
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }

    const { handleSaveTask } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: { uploadTaskAttachment },
      projectsApi: { createTask },
      isApiProject: ref(true),
      message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleSaveTask({ mode: 'create', data: { name: '无附件任务', priority: 'medium' } })

    expect(uploadTaskAttachment).not.toHaveBeenCalled()
    expect(state.project.value.tasks).toHaveLength(1)
  })

  it('API 项目点击拆解任务调用后端拆解接口并写入任务，不打开评分弹窗', async () => {
    const success = vi.fn()
    const error = vi.fn()
    const decomposeTasks = vi.fn().mockResolvedValue({
      success: true,
      data: [
        {
          id: 501,
          name: '资格文件整理',
          status: 'TODO',
          deliverables: null,
          hasDeliverable: false,
        },
      ],
    })
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }

    const { handleGenerateTasks } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: {},
      projectsApi: { decomposeTasks },
      isApiProject: ref(true),
      message: { success, error, warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleGenerateTasks()

    expect(decomposeTasks).toHaveBeenCalledWith('12')
    expect(state.scoreDraftDialogVisible.value).toBe(false)
    expect(state.project.value.tasks).toEqual([
      expect.objectContaining({
        id: 501,
        name: '资格文件整理',
        deliverables: [],
        hasDeliverable: false,
      }),
    ])
    expect(success).toHaveBeenCalledWith('已拆解生成 1 个任务')
    expect(error).not.toHaveBeenCalled()
  })

  it('API 项目上传标书拆解文件后调用独立拆解接口，不启动 AI 初稿', async () => {
    const file = new File(['招标正文'], '招标文件.docx')
    const success = vi.fn()
    const projectsApi = {
      getTenderBreakdownReadiness: vi.fn().mockResolvedValue({
        success: true,
        data: { ready: true },
      }),
      parseTenderBreakdown: vi.fn().mockResolvedValue({
        success: true,
        data: { document: { snapshotId: 601 } },
      }),
    }
    const { handleTenderBreakdownUpload } = useProjectDetailTaskActions({
      route: { params: { id: 12 } },
      userStore: { userName: '小王' },
      projectStore: {},
      projectsApi,
      isApiProject: { value: true },
      message: { success, error: vi.fn(), warning: vi.fn() },
      state: {
        project: { value: { id: 12, tasks: [] } },
        activities: { value: [] },
        tenderBreakdownDialogVisible: { value: true },
        tenderBreakdownParsing: { value: false },
      },
      workflow: {},
    })

    const result = await handleTenderBreakdownUpload(file)

    expect(result).toBe(false)
    expect(projectsApi.getTenderBreakdownReadiness).toHaveBeenCalledWith(12)
    expect(projectsApi.parseTenderBreakdown).toHaveBeenCalledWith(12, file)
    expect(success).toHaveBeenCalledWith('招标文件已拆解，可继续生成任务或标书初稿')
  })

  it('API 项目已有解析快照时点击解析入口直接复用，不打开上传弹窗', async () => {
    const success = vi.fn()
    const getLatestTenderBreakdown = vi.fn().mockResolvedValue({
      success: true,
      data: {
        document: {
          name: '招标文件.docx',
          snapshotId: 601,
        },
      },
    })
    const state = {
      project: { value: { id: 12, tasks: [] } },
      activities: { value: [] },
      tenderBreakdownDialogVisible: { value: false },
      tenderBreakdownParsing: { value: false },
    }
    const { handleOpenTenderBreakdown } = useProjectDetailTaskActions({
      route: { params: { id: 12 } },
      userStore: { userName: '小王' },
      projectStore: {},
      projectsApi: { getLatestTenderBreakdown },
      isApiProject: { value: true },
      message: { success, error: vi.fn(), warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleOpenTenderBreakdown()

    expect(getLatestTenderBreakdown).toHaveBeenCalledWith(12)
    expect(state.tenderBreakdownDialogVisible.value).toBe(false)
    expect(success).toHaveBeenCalledWith('已复用已解析的招标文件「招标文件.docx」，可直接拆解任务或生成标书初稿')
  })

  it('API 项目没有解析快照时点击解析入口才打开上传弹窗', async () => {
    const getLatestTenderBreakdown = vi.fn().mockResolvedValue({
      success: true,
      data: null,
    })
    const state = {
      project: { value: { id: 12, tasks: [] } },
      activities: { value: [] },
      tenderBreakdownDialogVisible: { value: false },
      tenderBreakdownParsing: { value: false },
    }
    const { handleOpenTenderBreakdown } = useProjectDetailTaskActions({
      route: { params: { id: 12 } },
      userStore: { userName: '小王' },
      projectStore: {},
      projectsApi: { getLatestTenderBreakdown },
      isApiProject: { value: true },
      message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleOpenTenderBreakdown()

    expect(getLatestTenderBreakdown).toHaveBeenCalledWith(12)
    expect(state.tenderBreakdownDialogVisible.value).toBe(true)
  })

  it('API 项目无解析快照但已有上传标书时点击解析入口直接复用上传文件', async () => {
    const success = vi.fn()
    const projectsApi = {
      getLatestTenderBreakdown: vi.fn().mockResolvedValue({
        success: true,
        data: null,
      }),
      getTenderBreakdownReadiness: vi.fn().mockResolvedValue({
        success: true,
        data: { ready: true },
      }),
      parseUploadedTenderBreakdown: vi.fn().mockResolvedValue({
        success: true,
        data: {
          document: {
            name: '已上传招标文件.docx',
            snapshotId: 701,
          },
        },
      }),
    }
    const state = {
      project: { value: { id: 12, tasks: [] } },
      activities: { value: [] },
      tenderBreakdownDialogVisible: { value: false },
      tenderBreakdownParsing: { value: false },
    }
    const { handleOpenTenderBreakdown } = useProjectDetailTaskActions({
      route: { params: { id: 12 } },
      userStore: { userName: '小王' },
      projectStore: {},
      projectsApi,
      isApiProject: { value: true },
      message: { success, error: vi.fn(), warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleOpenTenderBreakdown()

    expect(projectsApi.getLatestTenderBreakdown).toHaveBeenCalledWith(12)
    expect(projectsApi.getTenderBreakdownReadiness).toHaveBeenCalledWith(12)
    expect(projectsApi.parseUploadedTenderBreakdown).toHaveBeenCalledWith(12)
    expect(state.tenderBreakdownDialogVisible.value).toBe(false)
    expect(success).toHaveBeenCalledWith('已复用项目已上传的招标文件「已上传招标文件.docx」，可直接拆解任务或生成标书初稿')
  })

  it('最新解析快照接口暂不可用时继续尝试复用已上传招标文件', async () => {
    const success = vi.fn()
    const error = vi.fn()
    const getLatestTenderBreakdown = vi.fn().mockRejectedValue({
      response: {
        status: 404,
        data: {
          msg: 'No static resource api/projects/12/tender-breakdown/latest.',
        },
      },
    })
    const getTenderBreakdownReadiness = vi.fn().mockResolvedValue({
      success: true,
      data: { ready: true },
    })
    const parseUploadedTenderBreakdown = vi.fn().mockResolvedValue({
      success: true,
      data: {
        document: {
          name: '已上传招标文件.docx',
          snapshotId: 701,
        },
      },
    })
    const state = {
      project: { value: { id: 12, tasks: [] } },
      activities: { value: [] },
      tenderBreakdownDialogVisible: { value: false },
      tenderBreakdownParsing: { value: false },
    }
    const { handleOpenTenderBreakdown } = useProjectDetailTaskActions({
      route: { params: { id: 12 } },
      userStore: { userName: '小王' },
      projectStore: {},
      projectsApi: { getLatestTenderBreakdown, getTenderBreakdownReadiness, parseUploadedTenderBreakdown },
      isApiProject: { value: true },
      message: { success, error, warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleOpenTenderBreakdown()

    expect(getLatestTenderBreakdown).toHaveBeenCalledWith(12)
    expect(getTenderBreakdownReadiness).toHaveBeenCalledWith(12)
    expect(parseUploadedTenderBreakdown).toHaveBeenCalledWith(12)
    expect(state.tenderBreakdownDialogVisible.value).toBe(false)
    expect(success).toHaveBeenCalledWith('已复用项目已上传的招标文件「已上传招标文件.docx」，可直接拆解任务或生成标书初稿')
    expect(error).not.toHaveBeenCalled()
  })

  it('已上传招标文件复用接口暂不可用时回退到上传弹窗', async () => {
    const error = vi.fn()
    const projectsApi = {
      getLatestTenderBreakdown: vi.fn().mockResolvedValue({
        success: true,
        data: null,
      }),
      getTenderBreakdownReadiness: vi.fn().mockResolvedValue({
        success: true,
        data: { ready: true },
      }),
      parseUploadedTenderBreakdown: vi.fn().mockRejectedValue({
        response: {
          status: 404,
          data: {
            message: 'No static resource api/projects/12/tender-breakdown/reuse-uploaded.',
          },
        },
      }),
    }
    const state = {
      project: { value: { id: 12, tasks: [] } },
      activities: { value: [] },
      tenderBreakdownDialogVisible: { value: false },
      tenderBreakdownParsing: { value: false },
    }
    const { handleOpenTenderBreakdown } = useProjectDetailTaskActions({
      route: { params: { id: 12 } },
      userStore: { userName: '小王' },
      projectStore: {},
      projectsApi,
      isApiProject: { value: true },
      message: { success: vi.fn(), error, warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleOpenTenderBreakdown()

    expect(projectsApi.parseUploadedTenderBreakdown).toHaveBeenCalledWith(12)
    expect(state.tenderBreakdownDialogVisible.value).toBe(true)
    expect(error).not.toHaveBeenCalled()
  })

  it('API 项目缺少 DeepSeek 配置时上传前提示配置指引且不解析文件', async () => {
    const file = new File(['招标正文'], '招标文件.docx')
    const warning = vi.fn()
    const projectsApi = {
      getTenderBreakdownReadiness: vi.fn().mockResolvedValue({
        success: true,
        data: {
          ready: false,
          message: 'DeepSeek API Key 未配置。请管理员到系统设置 → AI 模型配置中填写 DeepSeek provider key，或在服务端设置 DEEPSEEK_API_KEY 后重启。',
          settingsPath: '/settings',
        },
      }),
      parseTenderBreakdown: vi.fn(),
    }
    const { handleTenderBreakdownUpload } = useProjectDetailTaskActions({
      route: { params: { id: 12 } },
      userStore: { userName: '小王' },
      projectStore: {},
      projectsApi,
      isApiProject: { value: true },
      message: { success: vi.fn(), error: vi.fn(), warning },
      state: {
        project: { value: { id: 12, tasks: [] } },
        activities: { value: [] },
        tenderBreakdownDialogVisible: { value: true },
        tenderBreakdownParsing: { value: false },
      },
      workflow: {},
    })

    const result = await handleTenderBreakdownUpload(file)

    expect(result).toBe(false)
    expect(projectsApi.getTenderBreakdownReadiness).toHaveBeenCalledWith(12)
    expect(projectsApi.parseTenderBreakdown).not.toHaveBeenCalled()
    expect(warning).toHaveBeenCalledWith('DeepSeek API Key 未配置。请管理员到系统设置 → AI 模型配置中填写 DeepSeek provider key，或在服务端设置 DEEPSEEK_API_KEY 后重启。')
  })

  it('API 项目解析中重复上传标书文件时不再次调用后端', async () => {
    const file = new File(['招标正文'], '招标文件.docx')
    const warning = vi.fn()
    const projectsApi = {
      parseTenderBreakdown: vi.fn(),
    }
    const { handleTenderBreakdownUpload } = useProjectDetailTaskActions({
      route: { params: { id: 12 } },
      userStore: { userName: '小王' },
      projectStore: {},
      projectsApi,
      isApiProject: { value: true },
      message: { success: vi.fn(), error: vi.fn(), warning },
      state: {
        project: { value: { id: 12, tasks: [] } },
        activities: { value: [] },
        tenderBreakdownDialogVisible: { value: true },
        tenderBreakdownParsing: { value: true },
      },
      workflow: {},
    })

    const result = await handleTenderBreakdownUpload(file)

    expect(result).toBe(false)
    expect(projectsApi.parseTenderBreakdown).not.toHaveBeenCalled()
    expect(warning).toHaveBeenCalledWith('正在解析招标文件，请稍候')
  })

  it('API 项目拆解无来源时只展示一次后端业务错误', async () => {
    const success = vi.fn()
    const error = vi.fn()
    const decomposeTasks = vi.fn().mockRejectedValue({
      message: 'Request failed with status code 400',
      response: {
        data: {
          msg: '未找到可用于拆解任务的标书拆解结果',
        },
      },
    })
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }

    const { handleGenerateTasks } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: {},
      projectsApi: { decomposeTasks },
      isApiProject: ref(true),
      message: { success, error, warning: vi.fn() },
      state,
      workflow: {},
    })

    await handleGenerateTasks()

    expect(error).toHaveBeenCalledTimes(1)
    expect(error).toHaveBeenCalledWith('未找到可用于拆解任务的标书拆解结果')
    expect(success).not.toHaveBeenCalled()
  })

  it('API 项目缺少标书拆解来源但已有评分草稿时打开评分确认弹窗', async () => {
    const success = vi.fn()
    const error = vi.fn()
    const warning = vi.fn()
    const decomposeTasks = vi.fn().mockRejectedValue({
      message: 'Request failed with status code 400',
      response: {
        data: {
          msg: '未找到可用于拆解任务的标书拆解结果',
        },
      },
    })
    const getScoreDrafts = vi.fn().mockResolvedValue({
      success: true,
      data: [
        {
          id: 2101,
          status: 'DRAFT',
          generatedTaskTitle: '准备商务响应文件',
        },
      ],
    })
    const state = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }

    const { handleGenerateTasks } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: {},
      projectsApi: { decomposeTasks, getScoreDrafts },
      isApiProject: ref(true),
      message: { success, error, warning },
      state,
      workflow: {},
    })

    await handleGenerateTasks()

    expect(decomposeTasks).toHaveBeenCalledWith('12')
    expect(getScoreDrafts).toHaveBeenCalledWith('12')
    expect(state.scoreDraftDialogVisible.value).toBe(true)
    expect(warning).toHaveBeenCalledWith('已找到评分草稿，请在评分标准拆解中确认后生成正式任务')
    expect(error).not.toHaveBeenCalled()
    expect(success).not.toHaveBeenCalled()
  })

  it('legacy detail task composable reuses real API task decomposition', async () => {
    const success = vi.fn()
    const error = vi.fn()
    const decomposeTasks = vi.fn().mockResolvedValue({
      success: true,
      data: [{ id: 701, name: '商务标：商务响应', deliverables: null }],
    })
    const context = {
      project: ref({ id: 12, name: '测试项目', tasks: [] }),
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: {},
      projectsApi: { decomposeTasks },
      isApiProject: ref(true),
      message: { success, error, warning: vi.fn() },
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
      deliverableTypeMap: {},
      handleInitiateProcess: vi.fn(),
    }

    const { handleGenerateTasks } = useProjectDetailTasks(context)

    await handleGenerateTasks()

    expect(decomposeTasks).toHaveBeenCalledWith('12')
    expect(context.scoreDraftDialogVisible.value).toBe(false)
    expect(context.project.value.tasks).toEqual([
      expect.objectContaining({
        id: 701,
        name: '商务标：商务响应',
        deliverables: [],
      }),
    ])
    expect(success).toHaveBeenCalledWith('已拆解生成 1 个任务')
    expect(error).not.toHaveBeenCalled()
  })
})

describe('handleSaveTask edit branch', () => {
  const buildEditCtx = ({ updateTask, projectTasks }) => {
    const state = {
      project: ref({ id: 1, tasks: projectTasks }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }
    const message = { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
    return {
      ctx: {
        route: { params: { id: '1' } },
        userStore: { userName: '测试', currentUser: { id: 1 } },
        projectStore: { updateTask },
        projectsApi: {},
        isApiProject: ref(true),
        message,
        state,
        workflow: {},
      },
      state,
      message,
    }
  }

  it('calls projectStore.updateTask with mapped dto and syncs card', async () => {
    const updateTask = vi.fn().mockResolvedValue({
      id: 7,
      title: 'New',
      content: 'md',
      status: 'TODO',
      priority: 'HIGH',
      dueDate: '2026-06-01',
    })
    const tasks = [{ id: 7, name: 'Old', content: '', status: 'TODO' }]
    const { ctx, state, message } = buildEditCtx({ updateTask, projectTasks: tasks })

    const { handleSaveTask } = useProjectDetailTaskActions(ctx)
    await handleSaveTask({
      mode: 'edit',
      data: { id: 7, name: 'New', content: 'md', status: 'TODO', priority: 'high', deadline: '2026-06-01' },
    })

    expect(updateTask).toHaveBeenCalledWith(1, 7, {
      title: 'New',
      content: 'md',
      status: 'TODO',
      priority: 'HIGH',
      dueDate: '2026-06-01',
    })
    const updated = state.project.value.tasks[0]
    expect(updated.name).toBe('New')
    expect(updated.content).toBe('md')
    expect(updated.deadline).toBe('2026-06-01')
    expect(message.success).toHaveBeenCalledWith('任务已更新')
  })

  it('uploads deliverableFiles through addDeliverable after editing task', async () => {
    const file = new File(['交付物内容'], '编辑交付物.pdf', { type: 'application/pdf' })
    const updateTask = vi.fn().mockResolvedValue({
      id: 7,
      title: 'New',
      assigneeId: 1,
      status: 'TODO',
      priority: 'HIGH',
    })
    const addDeliverable = vi.fn().mockResolvedValue({ id: 903, name: '编辑交付物.pdf', url: '/files/903' })
    const tasks = [{ id: 7, name: 'Old', assigneeId: 1, status: 'TODO', deliverables: [] }]
    const { ctx, state } = buildEditCtx({ updateTask, projectTasks: tasks })
    ctx.projectStore.addDeliverable = addDeliverable

    const { handleSaveTask } = useProjectDetailTaskActions(ctx)
    await handleSaveTask({
      mode: 'edit',
      data: { id: 7, name: 'New', priority: 'high', deliverableFiles: [file] },
    })

    expect(updateTask).toHaveBeenCalledWith(1, 7, {
      title: 'New',
      priority: 'HIGH',
    })
    expect(addDeliverable).toHaveBeenCalledWith('1', 7, expect.objectContaining({
      name: '编辑交付物.pdf',
      deliverableType: 'DOCUMENT',
      file,
    }))
    expect(state.project.value.tasks[0].deliverables).toEqual([expect.objectContaining({ id: 903 })])
  })

  it('shows error toast when updateTask rejects', async () => {
    const updateTask = vi.fn().mockRejectedValue(new Error('boom'))
    const tasks = [{ id: 7, name: 'Old' }]
    const { ctx, message } = buildEditCtx({ updateTask, projectTasks: tasks })

    const { handleSaveTask } = useProjectDetailTaskActions(ctx)
    await handleSaveTask({ mode: 'edit', data: { id: 7, name: 'X' } })

    expect(message.error).toHaveBeenCalled()
  })
})

// CO-370: 任务交付物和完成情况说明在状态流转后丢失
describe('CO-370 useProjectDetailTaskActions', () => {
  const buildCo370Ctx = ({ updateTaskStatus, updateTask, projectTasks, projectStore = {} }) => {
    const state = {
      project: ref({ id: 1, name: '测试项目', tasks: projectTasks }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }
    const message = { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
    return {
      ctx: {
        route: { params: { id: '1' } },
        userStore: { userName: '测试用户', currentUser: { id: 9 } },
        projectStore,
        projectsApi: { updateTaskStatus, updateTask },
        isApiProject: ref(true),
        message,
        state,
        workflow: {},
      },
      state,
      message,
    }
  }

  // 场景1: handleSubmitReview 必须在 updateTaskStatus 之前调用 uploadTaskFilesWithFallback 上传交付物
  it('场景1: handleSubmitReview 在 API 模式下提交审核时先上传交付物再更新状态', async () => {
    const file = new File(['交付物内容'], '交付物.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
    const updateTaskStatus = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 42, name: '提交审核任务', status: 'REVIEW' },
    })
    const addDeliverable = vi.fn().mockResolvedValue({ id: 901, name: '交付物.docx', url: '/files/901' })
    const tasks = [{ id: 42, name: '提交审核任务', status: 'TODO', assigneeId: 9, deliverables: [] }]
    const { ctx } = buildCo370Ctx({
      updateTaskStatus,
      projectTasks: tasks,
      projectStore: { addDeliverable },
    })

    const { handleSubmitReview } = useProjectDetailTaskActions(ctx)
    await handleSubmitReview({
      id: 42,
      status: 'REVIEW',
      deliverableFiles: [file],
    })

    expect(addDeliverable).toHaveBeenCalledWith('1', 42, expect.objectContaining({
      name: '交付物.docx',
      file,
    }))
    expect(updateTaskStatus).toHaveBeenCalledWith('1', 42, 'REVIEW', undefined, undefined)
  })

  it('CO-458: handleSubmitReview 把 completionNotes 通过 updateTaskStatus 第5个参数传递', async () => {
    const updateTaskStatus = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 42, name: '提交审核任务', status: 'REVIEW', completionNotes: '已完成全部交付内容' },
    })
    const tasks = [{ id: 42, name: '提交审核任务', status: 'TODO', assigneeId: 9, deliverables: [] }]
    const { ctx } = buildCo370Ctx({
      updateTaskStatus,
      projectTasks: tasks,
      projectStore: {},
    })

    const { handleSubmitReview } = useProjectDetailTaskActions(ctx)
    await handleSubmitReview({
      id: 42,
      status: 'REVIEW',
      completionNotes: '已完成全部交付内容，请审核',
      deliverableFiles: [],
    })

    expect(updateTaskStatus).toHaveBeenCalledWith('1', 42, 'REVIEW', undefined, '已完成全部交付内容，请审核')
  })

  // 场景4: handleTaskStatusChange 后端返回不含 deliverables 时，前端内存中的 deliverables 不被清空
  it('场景4: handleTaskStatusChange 状态流转后 deliverables 不被后端返回的空值覆盖', async () => {
    const updateTaskStatus = vi.fn().mockResolvedValue({
      success: true,
      // 后端返回不含 deliverables 字段（模拟旧后端或字段缺失场景）
      data: { id: 42, name: '已审核任务', status: 'REVIEW' },
    })
    const existingDeliverables = [{ id: 901, name: '已有交付物.docx', url: '/files/901' }]
    const tasks = [{ id: 42, name: '已审核任务', status: 'TODO', deliverables: existingDeliverables.slice() }]
    const { ctx, state } = buildCo370Ctx({
      updateTaskStatus,
      projectTasks: tasks,
    })

    const { handleTaskStatusChange } = useProjectDetailTaskActions(ctx)
    await handleTaskStatusChange(state.project.value.tasks[0], 'REVIEW')

    expect(state.project.value.tasks[0].deliverables).toEqual(existingDeliverables)
  })

  // 场景4: handleSubmitReview 后端返回不含 deliverables 时，前端内存中的 deliverables 不被清空
  it('场景4: handleSubmitReview 提交审核后 deliverables 不被后端返回的空值覆盖', async () => {
    const updateTaskStatus = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 42, name: '已提交审核任务', status: 'REVIEW' },
    })
    const existingDeliverables = [{ id: 902, name: '审核前交付物.docx', url: '/files/902' }]
    const tasks = [{ id: 42, name: '已提交审核任务', status: 'TODO', assigneeId: 9, deliverables: existingDeliverables.slice() }]
    const { ctx, state } = buildCo370Ctx({
      updateTaskStatus,
      projectTasks: tasks,
      projectStore: {},
    })

    const { handleSubmitReview } = useProjectDetailTaskActions(ctx)
    await handleSubmitReview({
      id: 42,
      status: 'REVIEW',
      deliverableFiles: [],
    })

    expect(state.project.value.tasks[0].deliverables).toEqual(existingDeliverables)
  })

  // CO-465: 交付物上传失败时不得乐观更新 task 状态，且 updateTaskStatus 不应被调用
  it('CO-465: 交付物上传失败时 task.status 保持 TODO 且 updateTaskStatus 不被调用', async () => {
    const file = new File(['交付物'], '失败.docx', { type: 'application/octet-stream' })
    const updateTaskStatus = vi.fn().mockResolvedValue({ success: true, data: { id: 42, status: 'REVIEW' } })
    // addDeliverable 抛错 → uploadTaskFilesWithFallback 返回 false
    const addDeliverable = vi.fn().mockRejectedValue(new Error('上传失败'))
    const tasks = [{ id: 42, name: '任务', status: 'TODO', assigneeId: 9, deliverables: [] }]
    const { ctx, state } = buildCo370Ctx({
      updateTaskStatus,
      projectTasks: tasks,
      projectStore: { addDeliverable },
    })

    const { handleSubmitReview } = useProjectDetailTaskActions(ctx)
    await handleSubmitReview({
      id: 42,
      status: 'REVIEW',
      deliverableFiles: [file],
      completionNotes: '完成情况说明',
    })

    // 关键断言：后端 updateTaskStatus 不应被调用
    expect(updateTaskStatus).not.toHaveBeenCalled()
    // 关键断言：task.status 不应被乐观更新为 REVIEW，保持原 TODO
    expect(state.project.value.tasks[0].status).toBe('TODO')
    // 关键断言：completionNotes 不应被乐观写入 task
    expect(state.project.value.tasks[0].completionNotes).toBeUndefined()
  })

  // CO-465: 成功提交后 pushActivity 和 message.success 才被调用（验证时序）
  it('CO-465: 成功提交后 task 状态更新为 REVIEW 且 pushActivity 被调用', async () => {
    const updateTaskStatus = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 42, name: '任务', status: 'REVIEW', completionNotes: '完成说明' },
    })
    const tasks = [{ id: 42, name: '任务', status: 'TODO', assigneeId: 9, deliverables: [] }]
    const { ctx, state } = buildCo370Ctx({
      updateTaskStatus,
      projectTasks: tasks,
      projectStore: {},
    })

    const { handleSubmitReview } = useProjectDetailTaskActions(ctx)
    await handleSubmitReview({
      id: 42,
      status: 'REVIEW',
      deliverableFiles: [],
      completionNotes: '完成说明',
    })

    // 后端确认成功后 task 状态才更新
    expect(state.project.value.tasks[0].status).toBe('REVIEW')
    expect(state.project.value.tasks[0].completionNotes).toBe('完成说明')
    // pushActivity 在成功后被调用
    expect(state.activities.value.length).toBeGreaterThan(0)
    expect(state.activities.value[0].action).toContain('已提交审核')
  })

  // CO-448: 保证金任务 4 个执行人填写字段（收款方/收款账号/实际缴纳日期/预计归还日期）
  // 在提交审核时必须通过 updateTask 持久化到后端 extendedFields，
  // 不能只调 updateTaskStatus（它只更新 status，不保存 extendedFields）。
  // 回归场景：用户报告"提交审核后 4 字段值丢失"，根因是此处漏调 updateTask。
  it('CO-448: handleSubmitReview 提交审核时通过 updateTask 持久化 extendedFields', async () => {
    const updateTask = vi.fn().mockResolvedValue({ id: 42, title: '缴纳投标保证金' })
    const updateTaskStatus = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 42, name: '缴纳投标保证金', status: 'REVIEW', completionNotes: '已完成' },
    })
    const extendedFields = {
      _taskType: 'deposit-payment',
      depositAmount: 100000,
      depositDeadline: '2026-08-15',
      payee: 'XX招标公司',
      payeeAccount: '6228480000001234567',
      actualPaymentDate: '2026-07-15',
      expectedRefundDate: '2026-09-15',
    }
    const tasks = [{ id: 42, name: '缴纳投标保证金', status: 'TODO', assigneeId: 9, deliverables: [] }]
    const { ctx } = buildCo370Ctx({
      updateTask,
      updateTaskStatus,
      projectTasks: tasks,
      projectStore: {},
    })

    const { handleSubmitReview } = useProjectDetailTaskActions(ctx)
    await handleSubmitReview({
      id: 42,
      status: 'REVIEW',
      completionNotes: '已完成',
      deliverableFiles: [],
      extendedFields,
    })

    // 关键断言：updateTask 必须被调用，且传入 { extendedFields }
    expect(updateTask).toHaveBeenCalledWith(42, { extendedFields })
    // updateTaskStatus 仍然被调用以更新状态
    expect(updateTaskStatus).toHaveBeenCalledWith('1', 42, 'REVIEW', undefined, '已完成')
  })

  // CO-448: 没有 extendedFields 时不应调用 updateTask（避免无意义的 API 调用）
  it('CO-448: handleSubmitReview 无 extendedFields 时不调用 updateTask', async () => {
    const updateTask = vi.fn()
    const updateTaskStatus = vi.fn().mockResolvedValue({
      success: true,
      data: { id: 42, name: '普通任务', status: 'REVIEW' },
    })
    const tasks = [{ id: 42, name: '普通任务', status: 'TODO', assigneeId: 9, deliverables: [] }]
    const { ctx } = buildCo370Ctx({
      updateTask,
      updateTaskStatus,
      projectTasks: tasks,
      projectStore: {},
    })

    const { handleSubmitReview } = useProjectDetailTaskActions(ctx)
    await handleSubmitReview({
      id: 42,
      status: 'REVIEW',
      deliverableFiles: [],
    })

    expect(updateTask).not.toHaveBeenCalled()
    expect(updateTaskStatus).toHaveBeenCalled()
  })

  // CO-448: updateTask 失败时不得继续更新状态（避免字段丢失但状态已变）
  it('CO-448: handleSubmitReview 在 updateTask 失败时不更新状态', async () => {
    const updateTask = vi.fn().mockRejectedValue(new Error('保存失败'))
    const updateTaskStatus = vi.fn().mockResolvedValue({ success: true, data: { id: 42, status: 'REVIEW' } })
    const tasks = [{ id: 42, name: '缴纳投标保证金', status: 'TODO', assigneeId: 9, deliverables: [] }]
    const { ctx, state } = buildCo370Ctx({
      updateTask,
      updateTaskStatus,
      projectTasks: tasks,
      projectStore: {},
    })

    const { handleSubmitReview } = useProjectDetailTaskActions(ctx)
    await handleSubmitReview({
      id: 42,
      status: 'REVIEW',
      deliverableFiles: [],
      extendedFields: { _taskType: 'deposit-payment', payee: 'XX公司' },
    })

    // 关键断言：updateTask 失败时状态不应被乐观更新
    expect(updateTaskStatus).not.toHaveBeenCalled()
    expect(state.project.value.tasks[0].status).toBe('TODO')
  })
})

// CO-411: 任务状态变更后看板自动刷新
// 根因：CO-397 抽屉审核按钮传入的 task 是 editingTask 浅拷贝，handleTaskStatusChange
// 直接修改拷贝的 status 不会触发 store 中原 task 引用的响应式更新，看板不刷新。
// 修复：根据 task.id 从 state.project.tasks 查找原引用，对原引用做更新。
describe('CO-411: handleTaskStatusChange 看板自动刷新', () => {
  function buildCtx({ projectTasks, updateTaskStatusData }) {
    const state = {
      project: ref({ id: 1, name: '测试项目', tasks: projectTasks }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
      taskDialogVisible: ref(false),
    }
    return {
      ctx: {
        route: { params: { id: '1' } },
        userStore: { userName: '测试用户', currentUser: { id: 9 } },
        projectStore: {},
        projectsApi: { updateTaskStatus: vi.fn().mockResolvedValue({ success: true, data: updateTaskStatusData }) },
        isApiProject: ref(true),
        message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
        state,
        workflow: {},
      },
      state,
    }
  }

  it('传入浅拷贝时，根据 id 定位原 task 引用并更新 status（看板自动刷新）', async () => {
    const originalTask = { id: 42, name: '任务A', status: 'TODO', deliverables: [] }
    const { ctx, state } = buildCtx({
      projectTasks: [originalTask],
      updateTaskStatusData: { id: 42, name: '任务A', status: 'REVIEW' },
    })
    const { handleTaskStatusChange } = useProjectDetailTaskActions(ctx)

    // 模拟 CO-397 抽屉审核按钮传入的浅拷贝
    const shallowCopy = { ...originalTask }
    await handleTaskStatusChange(shallowCopy, 'REVIEW')

    // 原 task 引用的 status 应被更新为 REVIEW
    expect(state.project.value.tasks[0].status).toBe('REVIEW')
    // 浅拷贝本身的 status 不应影响断言（验证修复点是原引用）
    expect(originalTask.status).toBe('REVIEW')
  })

  it('传入原引用时同样正确更新 status', async () => {
    const originalTask = { id: 42, name: '任务B', status: 'TODO', deliverables: [] }
    const { ctx, state } = buildCtx({
      projectTasks: [originalTask],
      updateTaskStatusData: { id: 42, name: '任务B', status: 'COMPLETED' },
    })
    const { handleTaskStatusChange } = useProjectDetailTaskActions(ctx)

    await handleTaskStatusChange(originalTask, 'COMPLETED')

    expect(state.project.value.tasks[0].status).toBe('COMPLETED')
  })

  it('后端返回数据后用原引用 Object.assign，保留 deliverables 不被覆盖', async () => {
    const existingDeliverables = [{ id: 901, name: '交付物.docx', url: '/files/901' }]
    const originalTask = { id: 42, name: '任务C', status: 'REVIEW', deliverables: existingDeliverables.slice() }
    const { ctx, state } = buildCtx({
      projectTasks: [originalTask],
      // 后端返回不含 deliverables
      updateTaskStatusData: { id: 42, name: '任务C', status: 'COMPLETED' },
    })
    const { handleTaskStatusChange } = useProjectDetailTaskActions(ctx)

    const shallowCopy = { ...originalTask }
    await handleTaskStatusChange(shallowCopy, 'COMPLETED')

    // 原 task 引用的 deliverables 应保留
    expect(state.project.value.tasks[0].deliverables).toEqual(existingDeliverables)
    expect(state.project.value.tasks[0].status).toBe('COMPLETED')
  })
})
