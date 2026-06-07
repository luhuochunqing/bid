import { describe, expect, it, vi } from 'vitest'
import { useProjectCreateSubmit } from './useProjectCreateSubmit.js'

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn()
  }
}))

vi.mock('@/api', () => ({
  projectsApi: {
    createTask: vi.fn(),
    decomposeTasks: vi.fn()
  }
}))

vi.mock('@/api/modules/customerOpportunity', () => ({
  default: {
    convertToProject: vi.fn()
  }
}))

function deferred() {
  let resolve
  let reject
  const promise = new Promise((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

describe('useProjectCreateSubmit', () => {
  it('ignores duplicate manual submit while the first create is pending', async () => {
    const createProjectDeferred = deferred()
    const projectStore = {
      createProject: vi.fn(() => createProjectDeferred.promise)
    }
    const router = { push: vi.fn() }
    const submitting = { value: false }
    const handlers = useProjectCreateSubmit({
      projectStore,
      router,
      sourceInfo: {},
      submitting,
      decomposing: { value: false },
      buildApiProjectPayload: () => ({ name: '重复立项', tenderId: 88001, managerId: 2 }),
      buildTaskCreatePayloads: () => [],
      hasGlobalHttpErrorMessage: () => false
    })

    const firstSubmit = handlers.handleSubmit()
    const secondSubmit = handlers.handleSubmit()

    expect(projectStore.createProject).toHaveBeenCalledTimes(1)

    createProjectDeferred.resolve({ id: 21 })
    await Promise.all([firstSubmit, secondSubmit])
  })

  it('ignores duplicate auto breakdown submit while the first create is pending', async () => {
    const createProjectDeferred = deferred()
    const projectStore = {
      createProject: vi.fn(() => createProjectDeferred.promise)
    }
    const handlers = useProjectCreateSubmit({
      projectStore,
      router: { push: vi.fn() },
      sourceInfo: {},
      submitting: { value: false },
      decomposing: { value: false },
      buildApiProjectPayload: () => ({ name: '重复立项', tenderId: 88001, managerId: 2 }),
      buildTaskCreatePayloads: () => [],
      hasGlobalHttpErrorMessage: () => false
    })

    const firstSubmit = handlers.handleCreateAndDecompose()
    const secondSubmit = handlers.handleCreateAndDecompose()

    expect(projectStore.createProject).toHaveBeenCalledTimes(1)

    createProjectDeferred.resolve({ id: 21 })
    await Promise.allSettled([firstSubmit, secondSubmit])
  })
})
