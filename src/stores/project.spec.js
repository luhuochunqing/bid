// Input: mocked projectsApi/resourcesApi/httpClient and Pinia project store
// Output: regression coverage for task deliverable upload orchestration
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
  projectsApi: {
    uploadDocument: uploadDocumentMock,
    createTaskDeliverable: createTaskDeliverableMock,
    deleteTaskDeliverable: deleteTaskDeliverableMock,
  },
}))

vi.mock('@/api/modules/taskStatusDict.js', () => ({
  taskStatusDictApi: { list: vi.fn() },
}))

vi.mock('@/api/modules/taskExtendedField.js', () => ({
  taskExtendedFieldApi: { list: vi.fn() },
}))

import { useProjectStore } from './project.js'

describe('useProjectStore task deliverables', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    uploadDocumentMock.mockReset()
    createTaskDeliverableMock.mockReset()
    deleteTaskDeliverableMock.mockReset()
  })

  it('uploads task deliverable files as real project documents before creating the task record', async () => {
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
})
