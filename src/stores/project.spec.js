// Input: mocked projectsApi/resourcesApi/httpClient and Pinia project store
// Output: regression coverage for task attachment and deliverable upload orchestration
// Pos: src/stores/ - Project store tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const uploadDocumentMock = vi.hoisted(() => vi.fn())
const createTaskDeliverableMock = vi.hoisted(() => vi.fn())
const deleteTaskDeliverableMock = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({
  httpClient: { get: vi.fn() },
  resourcesApi: { expenses: { getList: vi.fn() } },
  projectsApi: { uploadDocument: uploadDocumentMock },
}))

vi.mock('@/api/modules/taskStatusDict.js', () => ({
  taskStatusDictApi: { list: vi.fn() },
}))

vi.mock('@/api/modules/taskExtendedField.js', () => ({
  taskExtendedFieldApi: { list: vi.fn() },
}))

vi.mock('@/api/modules/taskDeliverables.js', () => ({
  createTaskDeliverable: createTaskDeliverableMock,
  deleteTaskDeliverable: deleteTaskDeliverableMock,
}))

import { useProjectStore } from './project.js'

describe('useProjectStore task attachments and deliverables', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    uploadDocumentMock.mockReset()
    createTaskDeliverableMock.mockReset()
    deleteTaskDeliverableMock.mockReset()
  })

  it('uploads task attachment files as project documents without creating deliverable', async () => {
    const store = useProjectStore()
    store.currentProject = {
      id: 12,
      tasks: [{ id: 31, name: '技术方案', attachments: [] }],
    }
    const file = new File(['参考文档'], '参考文档.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    uploadDocumentMock.mockResolvedValue({
      success: true,
      data: {
        id: 801,
        name: '参考文档.docx',
        size: '1KB',
        fileType: 'docx',
        fileUrl: 'project-documents://12/参考文档.docx',
      },
    })

    const saved = await store.uploadTaskAttachment(12, 31, {
      name: '参考文档.docx',
      file,
      uploaderId: 9,
      uploaderName: '测试用户',
    })

    const formData = uploadDocumentMock.mock.calls[0][1]
    expect(uploadDocumentMock).toHaveBeenCalledWith(12, expect.any(FormData))
    expect(formData.get('file')).toBe(file)
    expect(formData.get('documentCategory')).toBe('TASK_ATTACHMENT')
    expect(formData.get('linkedEntityType')).toBe('TASK')
    expect(formData.get('linkedEntityId')).toBe('31')
    expect(formData.get('uploaderId')).toBe('9')
    expect(createTaskDeliverableMock).not.toHaveBeenCalled()
    expect(saved.id).toBe(801)
    expect(store.currentProject.tasks[0].attachments).toEqual([saved])
  })

  it('uploads task deliverable files as project documents before creating the task record', async () => {
    const store = useProjectStore()
    store.currentProject = {
      id: 12,
      tasks: [{ id: 31, name: '技术方案', deliverables: [] }],
    }
    const file = new File(['技术方案'], '技术方案.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    uploadDocumentMock.mockResolvedValue({
      success: true,
      data: {
        id: 701,
        name: '技术方案.docx',
        size: '1KB',
        fileType: 'docx',
        fileUrl: 'project-documents://12/技术方案.docx',
      },
    })
    createTaskDeliverableMock.mockResolvedValue({
      success: true,
      data: {
        id: 501,
        name: '技术方案.docx',
        deliverableType: 'TECHNICAL',
        url: 'project-documents://12/技术方案.docx',
      },
    })

    const saved = await store.addDeliverable(12, 31, {
      name: '技术方案.docx',
      deliverableType: 'TECHNICAL',
      file,
      uploaderId: 9,
      uploaderName: '测试用户',
    })

    const formData = uploadDocumentMock.mock.calls[0][1]
    expect(uploadDocumentMock).toHaveBeenCalledWith(12, expect.any(FormData))
    expect(formData.get('file')).toBe(file)
    expect(formData.get('documentCategory')).toBe('TASK_DELIVERABLE')
    expect(formData.get('linkedEntityType')).toBe('TASK')
    expect(formData.get('linkedEntityId')).toBe('31')
    expect(formData.get('uploaderId')).toBe('9')
    expect(createTaskDeliverableMock).toHaveBeenCalledWith(12, 31, expect.objectContaining({
      name: '技术方案.docx',
      deliverableType: 'TECHNICAL',
      size: '1KB',
      fileType: 'docx',
      url: 'project-documents://12/技术方案.docx',
    }))
    expect(saved.id).toBe(501)
    expect(store.currentProject.tasks[0].deliverables).toEqual([saved])
    expect(store.currentProject.tasks[0].hasDeliverable).toBe(true)
  })

  it('rejects uploadTaskAttachment when projectId is missing', async () => {
    const store = useProjectStore()
    const file = new File(['参考文档'], '参考文档.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    await expect(store.uploadTaskAttachment(null, 31, { file }))
      .rejects.toThrow('项目ID不能为空')
    expect(uploadDocumentMock).not.toHaveBeenCalled()
  })

  it('rejects uploadTaskAttachment when taskId is missing', async () => {
    const store = useProjectStore()
    const file = new File(['参考文档'], '参考文档.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    await expect(store.uploadTaskAttachment(12, null, { file }))
      .rejects.toThrow('任务ID不能为空')
    expect(uploadDocumentMock).not.toHaveBeenCalled()
  })

  it('rejects uploadTaskAttachment when file is missing', async () => {
    const store = useProjectStore()
    await expect(store.uploadTaskAttachment(12, 31, {}))
      .rejects.toThrow('任务附件文件不能为空')
    expect(uploadDocumentMock).not.toHaveBeenCalled()
  })

  it('rejects uploadTaskAttachment when uploadDocument API fails', async () => {
    const store = useProjectStore()
    store.currentProject = {
      id: 12,
      tasks: [{ id: 31, name: '技术方案', attachments: [] }],
    }
    const file = new File(['参考文档'], '参考文档.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    uploadDocumentMock.mockResolvedValue({ success: false, message: '存储空间不足' })

    await expect(store.uploadTaskAttachment(12, 31, { file }))
      .rejects.toThrow('存储空间不足')
    expect(store.currentProject.tasks[0].attachments).toEqual([])
  })

  it('rejects addDeliverable when file is missing', async () => {
    const store = useProjectStore()
    await expect(store.addDeliverable(12, 31, { name: '无文件交付物' }))
      .rejects.toThrow('任务交付物文件不能为空')
    expect(uploadDocumentMock).not.toHaveBeenCalled()
    expect(createTaskDeliverableMock).not.toHaveBeenCalled()
  })

  it('rejects addDeliverable when uploadDocument API fails', async () => {
    const store = useProjectStore()
    store.currentProject = {
      id: 12,
      tasks: [{ id: 31, name: '技术方案', deliverables: [] }],
    }
    const file = new File(['技术方案'], '技术方案.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    uploadDocumentMock.mockResolvedValue({ success: false, message: '网络超时' })

    await expect(store.addDeliverable(12, 31, { file }))
      .rejects.toThrow('网络超时')
    expect(createTaskDeliverableMock).not.toHaveBeenCalled()
  })

  it('rejects addDeliverable when createTaskDeliverable API fails', async () => {
    const store = useProjectStore()
    store.currentProject = {
      id: 12,
      tasks: [{ id: 31, name: '技术方案', deliverables: [] }],
    }
    const file = new File(['技术方案'], '技术方案.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    uploadDocumentMock.mockResolvedValue({
      success: true,
      data: {
        id: 701,
        name: '技术方案.docx',
        size: '1KB',
        fileType: 'docx',
        fileUrl: 'project-documents://12/技术方案.docx',
      },
    })
    createTaskDeliverableMock.mockResolvedValue({ success: false, msg: '任务状态不允许添加交付物' })

    await expect(store.addDeliverable(12, 31, { file }))
      .rejects.toThrow('任务状态不允许添加交付物')
  })
})
