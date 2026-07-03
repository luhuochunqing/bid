// Input: mocked projectsApi.getDetail + Pinia project store
// Output: regression coverage for getProjectById error propagation (ProjectLoadError)
// Pos: src/stores/ - Project store tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const getDetailMock = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({
  httpClient: { get: vi.fn() },
  resourcesApi: { expenses: { getList: vi.fn() } },
  projectsApi: {
    getDetail: getDetailMock,
    uploadDocument: vi.fn(),
  },
}))

vi.mock('@/api/modules/taskStatusDict.js', () => ({
  taskStatusDictApi: { list: vi.fn() },
}))

vi.mock('@/api/modules/taskExtendedField.js', () => ({
  taskExtendedFieldApi: { list: vi.fn() },
}))

vi.mock('@/api/modules/taskDeliverables.js', () => ({
  createTaskDeliverable: vi.fn(),
  deleteTaskDeliverable: vi.fn(),
}))

import { useProjectStore } from './project.js'
import { PROJECT_LOAD_ERROR_TYPE } from '@/utils/projectErrors.js'

/**
 * 构造 axios 风格的错误对象
 * @param {number} status HTTP 状态码
 * @param {string} message 错误消息
 * @returns {Error} 带 response.status 的错误对象
 */
function makeAxiosError(status, message = `Request failed with status code ${status}`) {
  const error = new Error(message)
  error.response = { status, data: { msg: message } }
  return error
}

describe('useProjectStore.getProjectById 错误传播', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getDetailMock.mockReset()
  })

  it('API 返回 403 时抛出 ProjectLoadError，errorType 为 no-permission', async () => {
    getDetailMock.mockRejectedValue(makeAxiosError(403))
    const store = useProjectStore()

    await expect(store.getProjectById(138)).rejects.toMatchObject({
      name: 'ProjectLoadError',
      errorType: PROJECT_LOAD_ERROR_TYPE.NO_PERMISSION
    })
  })

  it('API 返回 404 时抛出 ProjectLoadError，errorType 为 not-found', async () => {
    getDetailMock.mockRejectedValue(makeAxiosError(404))
    const store = useProjectStore()

    await expect(store.getProjectById(99999)).rejects.toMatchObject({
      name: 'ProjectLoadError',
      errorType: PROJECT_LOAD_ERROR_TYPE.NOT_FOUND
    })
  })

  it('API 返回 500 时抛出 ProjectLoadError，errorType 为 network-error', async () => {
    getDetailMock.mockRejectedValue(makeAxiosError(500))
    const store = useProjectStore()

    await expect(store.getProjectById(138)).rejects.toMatchObject({
      name: 'ProjectLoadError',
      errorType: PROJECT_LOAD_ERROR_TYPE.NETWORK_ERROR
    })
  })

  it('API 抛出非 axios 错误（无 response.status）时，errorType 为 network-error', async () => {
    getDetailMock.mockRejectedValue(new Error('Network Error'))
    const store = useProjectStore()

    await expect(store.getProjectById(138)).rejects.toMatchObject({
      name: 'ProjectLoadError',
      errorType: PROJECT_LOAD_ERROR_TYPE.NETWORK_ERROR
    })
  })

  it('API 成功返回时，getProjectById 返回 project 且 currentProject 被设置', async () => {
    const mockProject = { id: 138, name: '测试项目', tasks: [] }
    getDetailMock.mockResolvedValue({ success: true, data: mockProject })
    const store = useProjectStore()

    const result = await store.getProjectById(138)
    expect(result).toEqual(mockProject)
    expect(store.currentProject).toEqual(mockProject)
  })

  it('API 返回 success=false 时抛出 ProjectLoadError，errorType 为 not-found', async () => {
    getDetailMock.mockResolvedValue({ success: false, data: null })
    const store = useProjectStore()

    await expect(store.getProjectById(138)).rejects.toMatchObject({
      name: 'ProjectLoadError',
      errorType: PROJECT_LOAD_ERROR_TYPE.NOT_FOUND
    })
    expect(store.currentProject).toBeNull()
  })

  it('existingProject 缓存命中时，直接返回缓存项目不调 API', async () => {
    const cachedProject = { id: 138, name: '缓存项目', tasks: [] }
    const store = useProjectStore()
    store.projects = [cachedProject]

    const result = await store.getProjectById(138)
    expect(result).toEqual(cachedProject)
    expect(store.currentProject).toEqual(cachedProject)
    expect(getDetailMock).not.toHaveBeenCalled()
  })

  it('抛出错误时 currentProject 被设为 null', async () => {
    getDetailMock.mockRejectedValue(makeAxiosError(403))
    const store = useProjectStore()
    store.currentProject = { id: 138, name: '旧项目' }

    await expect(store.getProjectById(138)).rejects.toThrow()
    expect(store.currentProject).toBeNull()
  })
})
